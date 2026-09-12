package kr.youthpolicymate.member;

import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailPreparationException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
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
        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var env = new MockEnvironment().withProperty("app.email.enabled", "true")
                    .withProperty("app.email.encryption-key", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                    .withProperty("app.email.from", "sender@example.test").withProperty("app.email.resend.api-key", "test-api-key")
                    .withProperty("app.email.resend.base-url", "http://127.0.0.1:" + server.getAddress().getPort());
            var sender = new ResendMemberEmailSender(env, new EmailCrypto(env), validators.getValidator(), RestClient.builder());
            assertThat(sender.send(outbox, "recipient@example.test", "인증", "확인 코드: 12345678")).isEqualTo(message.toString());
            var request = mapper.readTree(requests.getFirst());
            assertThat(request.path("to").get(0).asString()).isEqualTo("recipient@example.test");
            assertThat(request.path("text").asString()).contains("12345678");
            assertThat(request.path("tags").get(0).path("name").asString()).isEqualTo("outbox_id");
            assertThat(request.path("tags").get(0).path("value").asString()).isEqualTo(outbox.toString());
            status.set(422);
            assertThatThrownBy(() -> sender.send(outbox, "recipient@example.test", "인증", "본문"))
                    .isInstanceOf(MailPreparationException.class).hasMessageNotContaining("private-provider-error");
            status.set(503);
            assertThatThrownBy(() -> sender.send(outbox, "recipient@example.test", "인증", "본문"))
                    .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("private-provider-error");
        } finally { server.stop(0); }
    }
}
