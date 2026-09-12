package kr.youthpolicymate.member;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Profile("!preview")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MemberAccountService {
    private final JdbcClient jdbc;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public MemberAccountService(JdbcClient jdbc, FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.jdbc = jdbc;
        this.sessions = sessions;
    }

    @Transactional
    public void withdraw(UUID member) {
        // DELETE의 회원 행 잠금은 조건 저장·알림 배정과 같은 회원 단위 변경을 직렬화한다.
        jdbc.sql("DELETE FROM members WHERE id = :id").param("id", member).update();
        // 세션 저장소는 별도 트랜잭션을 사용한다. 정리에 실패하면 회원 데이터 삭제를 롤백한다.
        sessions.findByPrincipalName(member.toString()).keySet().forEach(sessions::deleteById);
    }
}
