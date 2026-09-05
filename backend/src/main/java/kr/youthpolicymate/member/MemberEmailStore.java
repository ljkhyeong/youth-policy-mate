package kr.youthpolicymate.member;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@Profile("!preview")
public class MemberEmailStore {
    private final JdbcClient jdbc;
    private final EmailCrypto crypto;
    private final MemberEmailSender sender;
    private final Clock clock;
    public MemberEmailStore(JdbcClient jdbc, EmailCrypto crypto, MemberEmailSender sender, Clock clock) {
        this.jdbc = jdbc; this.crypto = crypto; this.sender = sender; this.clock = clock;
    }
    static boolean validAddress(String value) {
        return value != null && value.length() <= 254 && value.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+");
    }
    void lock(UUID member) {
        if (jdbc.sql("SELECT id FROM members WHERE id = :id FOR UPDATE").param("id", member).query(UUID.class).optional().isEmpty())
            throw new org.springframework.security.access.AccessDeniedException("회원 확인이 필요합니다.");
    }
    private boolean available() { return crypto.ready() && sender.available(); }
    private void requireAvailable() { if (!available()) throw new EmailException(503, "EMAIL_UNAVAILABLE", "이메일 발송 설정을 준비하고 있어요."); }
    static String context(UUID member, UUID version, String purpose) { return member + ":" + version + ":" + purpose; }
    @Transactional
    public Settings settings(UUID member) {
        lock(member);
        return jdbc.sql("""
                SELECT s.*, (SELECT o.state FROM member_email_outbox o WHERE o.member_id = s.member_id
                    AND o.settings_version = s.version AND o.kind = 'VERIFICATION' ORDER BY o.created_at DESC LIMIT 1) AS delivery
                FROM member_email_settings s WHERE member_id = :member
                """).param("member", member).query((rs, row) -> {
                    var version = rs.getObject("version", UUID.class);
                    String address = crypto.ready() ? crypto.decrypt(context(member, version, "address"), rs.getString("address_cipher")) : null;
                    var expires = rs.getObject("expires_at", OffsetDateTime.class);
                    boolean pending = expires != null && clock.instant().isBefore(expires.toInstant()) && rs.getInt("attempts") < 5;
                    return new Settings(available(), true, address, rs.getObject("verified_at") != null, rs.getBoolean("enabled"),
                            pending ? expires.toInstant() : null, rs.getString("delivery"));
                }).optional().orElse(new Settings(available(), false, null, false, false, null, null));
    }
    @Transactional
    public void request(UUID member, String address) {
        requireAvailable();
        if (!validAddress(address)) throw new IllegalArgumentException();
        lock(member);
        Instant now = clock.instant();
        var recent = jdbc.sql("SELECT created_at FROM member_email_outbox WHERE member_id = :member AND kind = 'VERIFICATION' AND created_at > :since ORDER BY created_at DESC")
                .param("member", member).param("since", at(now.minusSeconds(3600)))
                .query((rs, row) -> rs.getObject(1, OffsetDateTime.class).toInstant()).list();
        if (recent.size() >= 3 || (!recent.isEmpty() && now.isBefore(recent.getFirst().plusSeconds(60))))
            throw new EmailException(429, "EMAIL_RATE_LIMITED", "확인 메일은 60초 간격, 시간당 3회까지 요청할 수 있어요.");
        cancel(member);
        UUID version = UUID.randomUUID(); String code = crypto.code();
        Instant expires = now.plusSeconds(600);
        jdbc.sql("""
                INSERT INTO member_email_settings(member_id, version, address_cipher, code_hash, expires_at)
                VALUES (:member, :version, :address, :hash, :expires)
                ON CONFLICT (member_id) DO UPDATE SET version = EXCLUDED.version, address_cipher = EXCLUDED.address_cipher,
                    code_hash = EXCLUDED.code_hash, expires_at = EXCLUDED.expires_at, attempts = 0,
                    verified_at = NULL, enabled = false, consented_at = NULL
                """).param("member", member).param("version", version)
                .param("address", crypto.encrypt(context(member, version, "address"), address))
                .param("hash", crypto.hash(context(member, version, "code"), code)).param("expires", at(expires)).update();
        jdbc.sql("""
                INSERT INTO member_email_outbox(id, member_id, settings_version, kind, code_cipher, state, created_at, expires_at)
                VALUES (:id, :member, :version, 'VERIFICATION', :code, 'PENDING', :now, :expires)
                """).param("id", UUID.randomUUID()).param("member", member).param("version", version)
                .param("code", crypto.encrypt(context(member, version, "code"), code)).param("now", at(now)).param("expires", at(expires)).update();
    }
    @Transactional
    public boolean confirm(UUID member, String code) {
        requireAvailable(); lock(member);
        var check = jdbc.sql("SELECT version, code_hash, expires_at, attempts FROM member_email_settings WHERE member_id = :member")
                .param("member", member).query((rs, row) -> new Check(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, OffsetDateTime.class), rs.getInt(4))).optional();
        if (check.isEmpty() || check.get().hash() == null) return false;
        var value = check.get();
        if (!clock.instant().isBefore(value.expires().toInstant()) || value.attempts() >= 5) {
            clearCode(member); return false;
        }
        boolean matches = code != null && code.matches("[0-9]{8}") && MessageDigest.isEqual(
                value.hash().getBytes(StandardCharsets.US_ASCII), crypto.hash(context(member, value.version(), "code"), code).getBytes(StandardCharsets.US_ASCII));
        if (matches) {
            jdbc.sql("UPDATE member_email_settings SET verified_at = :now WHERE member_id = :member")
                    .param("member", member).param("now", at(clock.instant())).update();
            clearCode(member);
        } else {
            jdbc.sql("UPDATE member_email_settings SET attempts = attempts + 1 WHERE member_id = :member").param("member", member).update();
            if (value.attempts() == 4) clearCode(member);
        }
        // 실패 횟수도 커밋한 뒤 컨트롤러가 오류 응답을 만든다.
        return matches;
    }
    private void clearCode(UUID member) {
        jdbc.sql("UPDATE member_email_settings SET code_hash = NULL, expires_at = NULL WHERE member_id = :member").param("member", member).update();
        jdbc.sql("""
                UPDATE member_email_outbox SET code_cipher = NULL, state = CASE WHEN state = 'PENDING' THEN 'CANCELED' ELSE state END
                WHERE member_id = :member AND kind = 'VERIFICATION'
                """).param("member", member).update();
    }
    @Transactional
    public void consent(UUID member, boolean enabled) {
        lock(member);
        if (enabled) {
            requireAvailable();
            int changed = jdbc.sql("UPDATE member_email_settings SET enabled = true, consented_at = COALESCE(consented_at,:now) WHERE member_id = :member AND verified_at IS NOT NULL")
                    .param("member", member).param("now", at(clock.instant())).update();
            if (changed == 0) throw new EmailException(409, "EMAIL_NOT_VERIFIED", "먼저 이메일 주소를 확인해주세요.");
        } else {
            jdbc.sql("UPDATE member_email_settings SET enabled = false, consented_at = NULL WHERE member_id = :member").param("member", member).update();
            jdbc.sql("UPDATE member_email_outbox SET state = 'CANCELED' WHERE member_id = :member AND kind = 'POLICY' AND state = 'PENDING'").param("member", member).update();
        }
    }
    @Transactional
    public void remove(UUID member) {
        lock(member); cancel(member);
        jdbc.sql("DELETE FROM member_email_settings WHERE member_id = :member").param("member", member).update();
    }
    private void cancel(UUID member) {
        jdbc.sql("UPDATE member_email_outbox SET code_cipher = NULL, state = CASE WHEN state = 'PENDING' THEN 'CANCELED' ELSE state END WHERE member_id = :member")
                .param("member", member).update();
    }
    static OffsetDateTime at(Instant instant) { return instant.atOffset(java.time.ZoneOffset.UTC); }
    private record Check(UUID version, String hash, OffsetDateTime expires, int attempts) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "MemberEmailSettings", requiredProperties = {"available", "addressRegistered", "address", "verified", "enabled", "verificationExpiresAt", "verificationDelivery"})
    public record Settings(boolean available, boolean addressRegistered, @Schema(types = {"string", "null"}) String address,
                           boolean verified, boolean enabled, @Schema(types = {"string", "null"}, format = "date-time") Instant verificationExpiresAt,
                           @Schema(types = {"string", "null"}, allowableValues = {"PENDING", "SENDING", "SENT", "FAILED", "UNKNOWN", "CANCELED"}) String verificationDelivery) {}
    public static class EmailException extends RuntimeException {
        final int status; final String code;
        EmailException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    }
}
