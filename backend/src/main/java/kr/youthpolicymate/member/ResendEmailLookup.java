package kr.youthpolicymate.member;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
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
    public static class Unavailable extends RuntimeException {
        private final Reason reason;
        public Unavailable(Reason reason) { super("이메일 공급자 상태를 조회할 수 없습니다."); this.reason = reason; }
        public Reason reason() { return reason; }
    }
    private final RestClient client;
    private final boolean configured;

    public ResendEmailLookup(Environment env, RestClient.Builder builder) {
        String key = env.getProperty("app.email.resend.read-api-key", "");
        configured = !key.isBlank();
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = builder.requestFactory(factory)
                .baseUrl(env.getProperty("app.email.resend.base-url", "https://api.resend.com"))
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
