package kr.youthpolicymate.member;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
@Profile("!preview")
public class MemberIdentityStore {
    private final JdbcClient jdbc;
    public MemberIdentityStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional
    public UUID login(String provider, String subject, String displayName) {
        return jdbc.sql("""
                INSERT INTO members(id, provider, provider_subject, display_name)
                VALUES (:id, :provider, :subject, :name)
                ON CONFLICT (provider, provider_subject) DO UPDATE SET display_name = EXCLUDED.display_name
                RETURNING id
                """).param("id", UUID.randomUUID()).param("provider", provider).param("subject", subject)
                .param("name", displayName).query(UUID.class).single();
    }
}
