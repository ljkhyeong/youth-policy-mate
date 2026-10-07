package kr.youthpolicymate.member;

import com.fasterxml.jackson.annotation.JsonProperty;
import kr.youthpolicymate.config.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Component
@Profile("!preview")
public class ResendEmailLookup {
    public enum Event { SENT, DELIVERED, DELIVERY_DELAYED, BOUNCED, COMPLAINED, SUPPRESSED, FAILED, OPENED, CLICKED, SCHEDULED, CANCELED, QUEUED, UNKNOWN }
    public enum Reason { NOT_CONFIGURED, ACCESS_DENIED, NOT_FOUND, RATE_LIMITED, UNAVAILABLE }
    /** 화면이 사유별 안내를 고르도록 503과 `EMAIL_PROVIDER_<사유>` 코드로 응답한다. */
    public static class Unavailable extends ApiException {
        private final Reason reason;
        public Unavailable(Reason reason) {
            super(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_PROVIDER_" + reason.name(), switch (reason) {
                case NOT_CONFIGURED -> "Resend 조회용 API 키가 설정되지 않았습니다.";
                case ACCESS_DENIED -> "Resend 조회용 API 키의 권한을 확인해주세요.";
                case NOT_FOUND -> "Resend에서 발송 기록을 찾지 못했습니다. 전달 실패를 뜻하지 않습니다.";
                case RATE_LIMITED -> "Resend 조회 한도에 도달했습니다. 잠시 후 다시 조회해주세요.";
                case UNAVAILABLE -> "Resend 상태를 불러오지 못했습니다. 잠시 후 다시 조회해주세요.";
            });
            this.reason = reason;
        }
        public Reason reason() { return reason; }
    }
    private final RestClient client;
    private final boolean configured;

    ResendEmailLookup(EmailProperties properties, RestClient.Builder builder) {
        String key = properties.resend().readApiKey();
        configured = StringUtils.hasText(key);
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = builder.requestFactory(factory)
                .baseUrl(properties.resend().baseUrl())
                .defaultHeader("Authorization", "Bearer " + key)
                .defaultHeader("User-Agent", "youth-policy-mate/1.0").build();
    }

    public Event retrieve(UUID messageId) {
        if (!configured) throw new Unavailable(Reason.NOT_CONFIGURED);
        try {
            var email = client.get().uri("/emails/{id}", messageId).retrieve().body(Email.class);
            if (email == null || !messageId.equals(email.id())) throw new Unavailable(Reason.UNAVAILABLE);
            try { return Event.valueOf(email.lastEvent() == null ? "UNKNOWN" : email.lastEvent().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException unknown) { return Event.UNKNOWN; }
        } catch (RestClientResponseException failure) {
            throw new Unavailable(switch (failure.getStatusCode().value()) {
                case 401, 403 -> Reason.ACCESS_DENIED;
                case 404 -> Reason.NOT_FOUND;
                case 429 -> Reason.RATE_LIMITED;
                default -> Reason.UNAVAILABLE;
            });
        } catch (RestClientException failure) {
            throw new Unavailable(Reason.UNAVAILABLE);
        }
    }

    private record Email(UUID id, @JsonProperty("last_event") String lastEvent) {}
}
