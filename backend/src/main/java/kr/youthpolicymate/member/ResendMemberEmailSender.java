package kr.youthpolicymate.member;

import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.MailPreparationException;
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
@ConditionalOnProperty(name = "app.email.provider", havingValue = "resend")
public class ResendMemberEmailSender implements MemberEmailSender {
    private final RestClient client;
    private final boolean enabled;
    private final String from;

    public ResendMemberEmailSender(Environment env, EmailCrypto crypto, Validator validator, RestClient.Builder builder) {
        enabled = env.getProperty("app.email.enabled", Boolean.class, false);
        from = env.getProperty("app.email.from", "");
        String key = env.getProperty("app.email.resend.api-key", "");
        if (enabled && (!crypto.ready() || key.isBlank()
                || !validator.validateValue(MemberEmailAddress.class, "address", from).isEmpty())) {
            throw new IllegalStateException("Resend 활성화에는 암호화 키·API 키·발신 주소가 필요합니다.");
        }
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = builder.requestFactory(factory)
                .baseUrl(env.getProperty("app.email.resend.base-url", "https://api.resend.com"))
                .defaultHeader("Authorization", "Bearer " + key).build();
    }

    @Override public boolean available() { return enabled; }
    @Override public String provider() { return "resend"; }

    @Override public String send(UUID requestId, String address, String subject, String body) {
        if (!enabled) throw new IllegalStateException("이메일 발송이 꺼져 있습니다.");
        try {
            var result = client.post().uri("/emails").header("Idempotency-Key", "email/" + requestId)
                    .body(Map.of("from", from, "to", List.of(address), "subject", subject, "text", body,
                            "tags", List.of(Map.of("name", "outbox_id", "value", requestId.toString()))))
                    .retrieve().body(Receipt.class);
            if (result == null || result.id() == null) throw new IllegalStateException("이메일 접수 결과를 확인할 수 없습니다.");
            return result.id().toString();
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            if (status >= 400 && status < 500 && status != 408 && status != 409) {
                throw new MailPreparationException("이메일 공급자가 발송 요청을 거절했습니다.");
            }
            // 응답에 주소·본문이 포함될 수 있으므로 공급자 오류를 그대로 보관하지 않는다.
            throw new IllegalStateException("이메일 접수 결과를 확인할 수 없습니다.");
        }
    }

    private record Receipt(UUID id) {}
}
