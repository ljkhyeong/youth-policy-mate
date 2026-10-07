package kr.youthpolicymate.member;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.URI;
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
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var sendOnly = new EmailProperties(false, null, null, new EmailProperties.Resend("send-only-key", null, null, base));
            assertThatThrownBy(() -> new ResendEmailLookup(sendOnly, RestClient.builder()).retrieve(message))
                    .isInstanceOfSatisfying(ResendEmailLookup.Unavailable.class,
                            failure -> assertThat(failure.reason()).isEqualTo(ResendEmailLookup.Reason.NOT_CONFIGURED));
            assertThat(requests).isEmpty();
            var lookup = new ResendEmailLookup(new EmailProperties(false, null, null,
                    new EmailProperties.Resend("send-only-key", "lookup-key", null, base)), RestClient.builder());
            assertThat(lookup.retrieve(message)).isEqualTo(ResendEmailLookup.Event.DELIVERED);
            body.set("{\"id\":\"" + message + "\",\"last_event\":\"new-provider-event\"}");
            assertThat(lookup.retrieve(message)).isEqualTo(ResendEmailLookup.Event.UNKNOWN);
            for (var invalid : new String[]{"{}", "{\"id\":\"" + UUID.randomUUID() + "\"}", "not-json-private-body"}) {
                body.set(invalid);
                assertThatThrownBy(() -> lookup.retrieve(message)).isInstanceOf(ResendEmailLookup.Unavailable.class)
                        .hasMessage("Resend 상태를 불러오지 못했습니다. 잠시 후 다시 조회해주세요.").hasNoCause();
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
