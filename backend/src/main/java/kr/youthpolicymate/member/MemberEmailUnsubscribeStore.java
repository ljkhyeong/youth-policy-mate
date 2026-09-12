package kr.youthpolicymate.member;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Profile("!preview")
public class MemberEmailUnsubscribeStore {
    private final JdbcClient jdbc;
    private final MemberEmailStore emails;

    public MemberEmailUnsubscribeStore(JdbcClient jdbc, MemberEmailStore emails) {
        this.jdbc = jdbc; this.emails = emails;
    }

    @Transactional
    public boolean unsubscribe(String token) {
        String hash = EmailCrypto.tokenHash(token);
        var member = jdbc.sql("""
                SELECT m.id FROM members m JOIN member_email_outbox o ON o.member_id = m.id
                WHERE o.unsubscribe_token_hash = :hash FOR UPDATE OF m
                """).param("hash", hash).query(UUID.class).optional();
        if (member.isEmpty()) return false;
        // 회원 잠금을 기다리는 동안 주소·동의가 바뀌거나 링크가 무효화될 수 있다.
        var current = jdbc.sql("""
                SELECT COALESCE(s.version = o.settings_version, false) FROM member_email_outbox o
                LEFT JOIN member_email_settings s ON s.member_id = o.member_id
                WHERE o.unsubscribe_token_hash = :hash
                """).param("hash", hash).query(Boolean.class).optional();
        if (current.isEmpty()) return false;
        if (current.get()) emails.consent(member.get(), false);
        return true;
    }
}
