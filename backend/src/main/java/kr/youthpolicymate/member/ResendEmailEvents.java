package kr.youthpolicymate.member;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Profile("!preview")
public class ResendEmailEvents {
    private static final Map<String, String> STATES = Map.of(
            "email.sent", "SENT", "email.delivered", "DELIVERED", "email.delivery_delayed", "DELAYED",
            "email.failed", "FAILED", "email.bounced", "BOUNCED", "email.complained", "COMPLAINED", "email.suppressed", "SUPPRESSED");
    private static final Set<String> BLOCKED = Set.of("BOUNCED", "COMPLAINED", "SUPPRESSED");
    private final JdbcClient jdbc;
    private final MemberEmailStore emails;

    public ResendEmailEvents(JdbcClient jdbc, MemberEmailStore emails) { this.jdbc = jdbc; this.emails = emails; }

    @Transactional
    public void receive(String type, Instant occurredAt, UUID outbox, UUID messageId) {
        String state = STATES.get(type);
        if (state == null) return;
        var owner = jdbc.sql("SELECT member_id FROM member_email_outbox WHERE id = :id AND provider = 'resend'")
                .param("id", outbox).query(UUID.class).optional();
        if (owner.isEmpty()) return;
        emails.lock(owner.get());
        // 중복·순서 역전과 API 응답보다 먼저 도착한 웹훅을 같은 Outbox 상태로 처리한다.
        int changed = jdbc.sql("""
                UPDATE member_email_outbox SET state = :state, provider_message_id = :message,
                    provider_event_at = :occurred, finished_at = :occurred, code_cipher = NULL
                WHERE id = :id AND provider = 'resend' AND state NOT IN ('PENDING','CANCELED')
                    AND (provider_message_id IS NULL OR provider_message_id = :message)
                    AND (provider_event_at IS NULL OR provider_event_at < :occurred
                        OR (:blocked AND state NOT IN ('BOUNCED','COMPLAINED','SUPPRESSED')))
                    AND (state NOT IN ('FAILED','BOUNCED','COMPLAINED','SUPPRESSED') OR :blocked)
                    AND (state <> 'DELAYED' OR :state <> 'SENT')
                    AND (state <> 'DELIVERED' OR :state NOT IN ('SENT','DELAYED'))
                """).param("state", state).param("message", messageId).param("occurred", MemberEmailStore.at(occurredAt))
                .param("id", outbox).param("blocked", BLOCKED.contains(state)).update();
        if (changed == 0 || !BLOCKED.contains(state)) return;
        jdbc.sql("""
                UPDATE member_email_settings s SET enabled = false, consented_at = NULL, verified_at = NULL,
                    code_hash = NULL, expires_at = NULL, delivery_issue = :state
                FROM member_email_outbox o WHERE o.id = :id AND s.member_id = o.member_id AND s.version = o.settings_version
                """).param("state", state).param("id", outbox).update();
        jdbc.sql("""
                UPDATE member_email_outbox pending SET state = 'CANCELED', code_cipher = NULL
                FROM member_email_outbox source WHERE source.id = :id AND pending.member_id = source.member_id
                    AND pending.settings_version = source.settings_version AND pending.state = 'PENDING'
                """).param("id", outbox).update();
    }
}
