package kr.youthpolicymate.member;

import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Profile("!preview")
public class ResendMemberEmailSender {
    /** 공급자가 요청을 명확히 거절해 접수되지 않은 발송. 응답 본문에 주소·본문이 있을 수 있어 담지 않는다. */
    public static class Rejected extends RuntimeException {
        Rejected() { super("이메일 공급자가 발송 요청을 거절했습니다."); }
    }

    private final RestClient client;
    private final boolean enabled;
    private final String from;

    ResendMemberEmailSender(EmailProperties properties, RestClient.Builder builder) {
        enabled = properties.enabled();
        from = properties.from();
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = builder.requestFactory(factory)
                .baseUrl(properties.resend().baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.resend().apiKey())
                .defaultHeader("User-Agent", "youth-policy-mate/1.0")
                .build();
    }

    public boolean available() { return enabled; }

    public UUID send(UUID requestId, String address, String subject, String body, Map<String, String> headers) {
        if (!enabled) throw new IllegalStateException("이메일 발송이 꺼져 있습니다.");
        try {
            var result = client.post().uri("/emails").header("Idempotency-Key", "email/" + requestId)
                    .body(Map.of("from", from, "to", List.of(address), "subject", subject, "text", body,
                            "tags", List.of(Map.of("name", "outbox_id", "value", requestId.toString())), "headers", headers))
                    .retrieve().body(Receipt.class);
            if (result == null || result.id() == null) throw new IllegalStateException("이메일 접수 결과를 확인할 수 없습니다.");
            return result.id();
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            if (status >= 400 && status < 500 && status != 408 && status != 409) throw new Rejected();
            // 응답에 주소·본문이 포함될 수 있으므로 공급자 오류를 그대로 보관하지 않는다.
            throw new IllegalStateException("이메일 접수 결과를 확인할 수 없습니다.");
        }
    }

    private record Receipt(UUID id) {}
}
