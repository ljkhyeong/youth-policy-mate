package kr.youthpolicymate.member;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class ResendTransportTest {
    @Test
    @DisplayName("Resend 요청에 인증·동일 발송 키·웹훅 연결 태그를 보내고 접수 ID를 반환한다")
    void sendsProviderContractAndClassifiesFailures() throws Exception {
        var mapper = JsonMapper.builder().build();
        var requests = new ArrayList<String>();
        var status = new AtomicInteger(200);
        UUID message = UUID.randomUUID();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        UUID outbox = UUID.randomUUID();
        server.createContext("/emails", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-api-key");
            assertThat(exchange.getRequestHeaders().getFirst("User-Agent")).isEqualTo("youth-policy-mate/1.0");
            assertThat(exchange.getRequestHeaders().getFirst("Idempotency-Key")).isEqualTo("email/" + outbox);
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = (status.get() == 200 ? "{\"id\":\"" + message + "\"}" : "{\"message\":\"private-provider-error\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var properties = new EmailProperties(true, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "sender@example.test",
                    new EmailProperties.Resend("test-api-key", null, null, URI.create("http://127.0.0.1:" + server.getAddress().getPort())));
            var sender = new ResendMemberEmailSender(properties, RestClient.builder());
            assertThat(sender.send(outbox, "recipient@example.test", "인증", "확인 코드: 12345678", java.util.Map.of())).isEqualTo(message);
            var request = mapper.readTree(requests.getFirst());
            assertThat(request.path("to").get(0).asString()).isEqualTo("recipient@example.test");
            assertThat(request.path("text").asString()).contains("12345678");
            assertThat(request.path("tags").get(0).path("name").asString()).isEqualTo("outbox_id");
            assertThat(request.path("tags").get(0).path("value").asString()).isEqualTo(outbox.toString());
            assertThat(request.path("headers").isEmpty()).isTrue();
            var headers = java.util.Map.of("List-Unsubscribe", "<https://policy.example.test/api/v1/email-unsubscribe/token>",
                    "List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            sender.send(outbox, "recipient@example.test", "정책 알림", "본문", headers);
            var policyHeaders = mapper.readTree(requests.getLast()).path("headers");
            assertThat(policyHeaders.path("List-Unsubscribe").asString()).isEqualTo(headers.get("List-Unsubscribe"));
            assertThat(policyHeaders.path("List-Unsubscribe-Post").asString()).isEqualTo("List-Unsubscribe=One-Click");
            status.set(422);
            assertThatThrownBy(() -> sender.send(outbox, "recipient@example.test", "인증", "본문", java.util.Map.of()))
                    .isInstanceOf(ResendMemberEmailSender.Rejected.class).hasMessageNotContaining("private-provider-error");
            status.set(503);
            assertThatThrownBy(() -> sender.send(outbox, "recipient@example.test", "인증", "본문", java.util.Map.of()))
                    .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("private-provider-error");
        } finally { server.stop(0); }
    }
}
