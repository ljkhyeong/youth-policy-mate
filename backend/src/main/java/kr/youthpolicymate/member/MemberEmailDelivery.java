package kr.youthpolicymate.member;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.util.UUID;
import static kr.youthpolicymate.member.MemberEmailStore.*;

@Service
@Profile("!preview")
public class MemberEmailDelivery {
    private final JdbcClient jdbc;
    private final MemberEmailStore emails;
    private final MemberPolicyStore policies;
    private final MemberEmailSender sender;
    private final EmailCrypto crypto;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final String frontend;
    public MemberEmailDelivery(JdbcClient jdbc, MemberEmailStore emails, MemberPolicyStore policies,
                               MemberEmailSender sender, EmailCrypto crypto, Clock clock,
                               PlatformTransactionManager manager, Environment environment) {
        this.jdbc = jdbc; this.emails = emails; this.policies = policies; this.sender = sender;
        this.crypto = crypto; this.clock = clock; this.transaction = new TransactionTemplate(manager);
        this.frontend = environment.getProperty("APP_FRONTEND_URL", "http://127.0.0.1:3000").replaceAll("/+$", "");
    }
    public void deliverPending() {
        if (!sender.available() || !crypto.ready()) return;
        // 중단된 발송은 자동 재시도하지 않는다. SMTP 접수 여부를 알 수 없기 때문이다.
        jdbc.sql("UPDATE member_email_outbox SET state = 'UNKNOWN', code_cipher = NULL, finished_at = :now WHERE state = 'SENDING' AND started_at < :before")
                .param("now", at(clock.instant())).param("before", at(clock.instant().minusSeconds(120))).update();
        jdbc.sql("UPDATE member_email_settings SET code_hash = NULL, expires_at = NULL WHERE expires_at <= :now")
                .param("now", at(clock.instant())).update();
        for (var id : jdbc.sql("SELECT id FROM member_email_outbox WHERE state = 'PENDING' ORDER BY created_at, id LIMIT 50").query(UUID.class).list()) {
            try { deliver(id); }
            catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("이메일 대기 항목 처리 실패: {}", id);
            }
        }
    }
    public void deliver(UUID id) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("이메일 발송은 저장 트랜잭션 밖에서 실행해야 합니다.");
        if (!sender.available() || !crypto.ready()) return;
        var payload = transaction.execute(status -> claim(id));
        if (payload == null) return;
        String outcome;
        try {
            sender.send(payload.address(), payload.subject(), payload.body());
            outcome = "SENT";
        } catch (org.springframework.mail.MailAuthenticationException | org.springframework.mail.MailPreparationException failure) {
            outcome = "FAILED";
        } catch (RuntimeException failure) {
            outcome = "UNKNOWN";
        }
        // 외부 접수 이후 DB 기록에 실패해도 SENDING은 재발송 대상으로 되돌리지 않는다.
        jdbc.sql("UPDATE member_email_outbox SET state = :state, code_cipher = NULL, finished_at = :now WHERE id = :id AND state IN ('SENDING','UNKNOWN')")
                .param("state", outcome).param("now", at(clock.instant())).param("id", id).update();
    }
    private Payload claim(UUID id) {
        var owner = jdbc.sql("SELECT member_id, kind FROM member_email_outbox WHERE id = :id").param("id", id)
                .query((rs, row) -> new Owner(rs.getObject(1, UUID.class), rs.getString(2))).optional();
        if (owner.isEmpty()) return null;
        UUID member = owner.get().member(); emails.lock(member);
        if (owner.get().kind().equals("POLICY")) policies.refresh(member);
        var state = jdbc.sql("SELECT state FROM member_email_outbox WHERE id = :id FOR UPDATE").param("id", id).query(String.class).optional();
        if (state.isEmpty() || !state.get().equals("PENDING")) return null;
        var candidate = jdbc.sql("""
                SELECT o.*, s.address_cipher, n.title, n.message, n.policy_number FROM member_email_outbox o
                JOIN member_email_settings s ON s.member_id = o.member_id AND s.version = o.settings_version
                LEFT JOIN member_notifications n ON n.id = o.notification_id
                WHERE o.id = :id AND (o.expires_at IS NULL OR o.expires_at > :now)
                AND ((o.kind = 'VERIFICATION' AND s.verified_at IS NULL AND s.code_hash IS NOT NULL AND s.expires_at > :now)
                  OR (o.kind = 'POLICY' AND s.verified_at IS NOT NULL AND s.enabled AND EXISTS (
                    SELECT 1 FROM saved_policies saved JOIN policies p ON p.policy_number = saved.policy_number
                    WHERE saved.member_id = o.member_id AND saved.policy_number = n.policy_number
                      AND saved.generation = n.generation AND saved.current_revision = n.policy_revision AND p.current_revision = n.policy_revision)))
                """).param("id", id).param("now", at(clock.instant())).query((rs, row) -> {
                    UUID version = rs.getObject("settings_version", UUID.class);
                    String address = crypto.decrypt(context(member, version, "address"), rs.getString("address_cipher"));
                    if (rs.getString("kind").equals("VERIFICATION")) {
                        String code = crypto.decrypt(context(member, version, "code"), rs.getString("code_cipher"));
                        return new Payload(address, "[청년정책메이트] 이메일 확인 코드",
                                "확인 코드: " + code + "\n\n코드는 요청 후 10분간 사용할 수 있습니다.\n직접 요청하지 않았다면 무시해주세요. 이 메일만으로 알림 수신이 켜지지는 않습니다.");
                    }
                    return new Payload(address, "[청년정책메이트] " + rs.getString("title").replaceAll("[\\r\\n]", " "),
                            rs.getString("message") + "\n\n정책 확인: " + frontend + "/policies/" + rs.getString("policy_number")
                                    + "\n이메일 수신 해제: " + frontend + "/my");
                }).optional();
        if (candidate.isEmpty()) {
            jdbc.sql("UPDATE member_email_outbox SET state = 'CANCELED', code_cipher = NULL, finished_at = :now WHERE id = :id")
                    .param("id", id).param("now", at(clock.instant())).update();
            return null;
        }
        jdbc.sql("UPDATE member_email_outbox SET state = 'SENDING', started_at = :now, code_cipher = NULL WHERE id = :id")
                .param("id", id).param("now", at(clock.instant())).update();
        return candidate.get();
    }
    private record Owner(UUID member, String kind) {}
    private record Payload(String address, String subject, String body) {
        @Override public String toString() { return "이메일 발송 내용 비공개"; }
    }
}
