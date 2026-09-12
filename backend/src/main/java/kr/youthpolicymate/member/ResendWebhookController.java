package kr.youthpolicymate.member;

import com.svix.Webhook;
import com.svix.exceptions.WebhookVerificationException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Hidden
@RestController
@Profile("!preview")
@ConditionalOnProperty(name = "app.email.provider", havingValue = "resend")
public class ResendWebhookController {
    private final Webhook verifier;
    private final ObjectMapper mapper;
    private final ResendEmailEvents events;

    public ResendWebhookController(Environment env, ObjectMapper mapper, ResendEmailEvents events) throws Exception {
        String secret = env.getProperty("app.email.resend.webhook-secret", "");
        if (secret.isBlank() && env.getProperty("app.email.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Resend 활성화에는 웹훅 서명 키가 필요합니다.");
        }
        verifier = secret.isBlank() ? null : new Webhook(secret);
        this.mapper = mapper; this.events = events;
    }

    @PostMapping("/api/v1/webhooks/resend")
    public ResponseEntity<Void> receive(HttpServletRequest request, @RequestHeader HttpHeaders headers) throws IOException {
        if (verifier == null) return ResponseEntity.notFound().build();
        byte[] bytes = request.getInputStream().readNBytes(65_537);
        if (bytes.length > 65_536) return ResponseEntity.status(413).build();
        String payload = new String(bytes, StandardCharsets.UTF_8);
        try { verifier.verify(payload, headers.asMultiValueMap()); }
        catch (WebhookVerificationException failure) { return ResponseEntity.status(401).build(); }
        String type; Instant occurred; UUID outbox; UUID message;
        try {
            var event = mapper.readTree(payload);
            type = event.path("type").asString("");
            if (!event.path("data").path("tags").hasNonNull("outbox_id")) return ResponseEntity.noContent().build();
            occurred = Instant.parse(event.path("created_at").asString());
            outbox = UUID.fromString(event.path("data").path("tags").path("outbox_id").asString());
            message = UUID.fromString(event.path("data").path("email_id").asString());
        } catch (RuntimeException failure) { return ResponseEntity.badRequest().build(); }
        events.receive(type, occurred, outbox, message);
        return ResponseEntity.noContent().build();
    }
}
