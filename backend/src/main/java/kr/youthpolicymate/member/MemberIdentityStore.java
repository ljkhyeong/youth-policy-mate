package kr.youthpolicymate.member;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 회원 행의 생성·확인·삭제. 세션 저장소가 있는 웹 서버에서만 만든다. */
@Repository
@Profile("!preview")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MemberIdentityStore {
    private final JdbcClient jdbc;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public MemberIdentityStore(JdbcClient jdbc, FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.jdbc = jdbc;
        this.sessions = sessions;
    }

    /**
     * 회원 행을 잠가 조건 저장·알림 배정·이메일 변경을 회원 단위로 직렬화한다. 호출자의 트랜잭션에 참여한다.
     * 웹 서버가 아닌 발송·웹훅 처리에서도 쓰므로 빈 대신 정적 메서드로 둔다.
     */
    static void lock(JdbcClient jdbc, UUID member) {
        if (jdbc.sql("SELECT id FROM members WHERE id = :id FOR UPDATE").param("id", member).query(UUID.class).optional().isEmpty())
            throw new AccessDeniedException("회원 확인이 필요합니다.");
    }

    public boolean exists(UUID member) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM members WHERE id = :id)")
                .param("id", member).query(Boolean.class).single();
    }

    @Transactional
    public UUID login(String provider, String subject) {
        // 충돌 시에도 기존 id를 돌려받고 행을 잠그도록 값이 바뀌지 않는 갱신을 쓴다. DO NOTHING은 행을 반환하지 않는다.
        return jdbc.sql("""
                INSERT INTO members(id, provider, provider_subject)
                VALUES (:id, :provider, :subject)
                ON CONFLICT (provider, provider_subject) DO UPDATE SET provider = EXCLUDED.provider
                RETURNING id
                """).param("id", UUID.randomUUID()).param("provider", provider).param("subject", subject)
                .query(UUID.class).single();
    }

    @Transactional
    public void withdraw(UUID member) {
        // DELETE의 회원 행 잠금은 조건 저장·알림 배정과 같은 회원 단위 변경을 직렬화한다.
        jdbc.sql("DELETE FROM members WHERE id = :id").param("id", member).update();
        // 세션 저장소는 별도 트랜잭션을 사용한다. 정리에 실패하면 회원 데이터 삭제를 롤백한다.
        sessions.findByPrincipalName(member.toString()).keySet().forEach(sessions::deleteById);
    }
}
