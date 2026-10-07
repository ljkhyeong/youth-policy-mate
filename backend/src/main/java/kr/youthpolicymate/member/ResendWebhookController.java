package kr.youthpolicymate.member;

import com.svix.Webhook;
import com.svix.exceptions.WebhookVerificationException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ResendWebhookController {
    private final Webhook verifier;
    private final ObjectMapper mapper;
    private final ResendEmailEvents events;

    ResendWebhookController(EmailProperties properties, ObjectMapper mapper, ResendEmailEvents events) throws Exception {
        // 발송을 켜면 서명 키가 필수다(EmailProperties). 끈 상태에서 키가 없으면 수신 경로를 404로 닫는다.
        String secret = properties.resend().webhookSecret();
        verifier = StringUtils.hasText(secret) ? new Webhook(secret) : null;
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
            if (!event.path("data").path("tags").hasNonNull("outbox_id")) return ResponseEntity.ok().build();
            occurred = Instant.parse(event.path("created_at").asString());
            outbox = UUID.fromString(event.path("data").path("tags").path("outbox_id").asString());
            message = UUID.fromString(event.path("data").path("email_id").asString());
        } catch (RuntimeException failure) { return ResponseEntity.badRequest().build(); }
        events.receive(type, occurred, outbox, message);
        return ResponseEntity.ok().build();
    }
}
