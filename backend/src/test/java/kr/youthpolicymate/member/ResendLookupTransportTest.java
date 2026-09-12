package kr.youthpolicymate.member;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class ResendLookupTransportTest {
    @Test @DisplayName("발송 비활성 상태에서도 조회 전용 키로 GET만 요청하고 상태 외 공급자 응답과 오류를 노출하지 않는다")
    void retrievesWithSeparateKeyAndSanitizesResults() throws Exception {
        UUID message = UUID.randomUUID();
        var status = new AtomicInteger(200);
        var body = new AtomicReference<>("""
                {"id":"%s","last_event":"delivered","to":["private@example.test"],"html":"private-body"}
                """.formatted(message));
        var requests = new ArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " " + exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var env = new MockEnvironment().withProperty("app.email.enabled", "false")
                    .withProperty("app.email.resend.api-key", "send-only-key")
                    .withProperty("app.email.resend.base-url", "http://127.0.0.1:" + server.getAddress().getPort());
            assertThatThrownBy(() -> new ResendEmailLookup(env, RestClient.builder()).retrieve(message))
                    .isInstanceOfSatisfying(ResendEmailLookup.Unavailable.class,
                            failure -> assertThat(failure.reason()).isEqualTo(ResendEmailLookup.Reason.NOT_CONFIGURED));
            assertThat(requests).isEmpty();
            var lookup = new ResendEmailLookup(env.withProperty("app.email.resend.read-api-key", "lookup-key"), RestClient.builder());
            assertThat(lookup.retrieve(message)).isEqualTo(ResendEmailLookup.Event.DELIVERED);
            body.set("{\"id\":\"" + message + "\",\"last_event\":\"new-provider-event\"}");
            assertThat(lookup.retrieve(message)).isEqualTo(ResendEmailLookup.Event.UNKNOWN);
            for (var invalid : new String[]{"{}", "{\"id\":\"" + UUID.randomUUID() + "\"}", "not-json-private-body"}) {
                body.set(invalid);
                assertThatThrownBy(() -> lookup.retrieve(message)).isInstanceOf(ResendEmailLookup.Unavailable.class)
                        .hasMessage("이메일 공급자 상태를 조회할 수 없습니다.").hasNoCause();
            }
            body.set("{\"message\":\"private-provider-error\"}");
            for (var failureCase : Map.of(401, ResendEmailLookup.Reason.ACCESS_DENIED, 403, ResendEmailLookup.Reason.ACCESS_DENIED,
                    404, ResendEmailLookup.Reason.NOT_FOUND, 429, ResendEmailLookup.Reason.RATE_LIMITED, 503, ResendEmailLookup.Reason.UNAVAILABLE).entrySet()) {
                status.set(failureCase.getKey());
                int before = requests.size();
                assertThatThrownBy(() -> lookup.retrieve(message)).isInstanceOfSatisfying(ResendEmailLookup.Unavailable.class,
                        failure -> assertThat(failure.reason()).isEqualTo(failureCase.getValue()))
                        .hasMessageNotContaining("private-provider-error").hasNoCause();
                assertThat(requests).hasSize(before + 1);
            }
            assertThat(requests).allMatch(request -> request.equals("GET /emails/" + message + " Bearer lookup-key"));
        } finally { server.stop(0); }
    }
}
