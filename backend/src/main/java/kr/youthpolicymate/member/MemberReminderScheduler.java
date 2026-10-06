package kr.youthpolicymate.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;
import java.util.UUID;

import static kr.youthpolicymate.policy.SeoulTime.SEOUL;

@Component
@EnableScheduling
@Profile("!preview")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty("app.reminders.enabled")
public class MemberReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(MemberReminderScheduler.class);
    private final JdbcClient jdbc;
    private final MemberPolicyStore store;
    private final LocalTime deliveryTime;
    private final Clock clock;
    public MemberReminderScheduler(JdbcClient jdbc, MemberPolicyStore store, Clock clock,
                                   @Value("${app.reminders.delivery-time}") String deliveryTime) {
        this.jdbc = jdbc; this.store = store; this.clock = clock;
        this.deliveryTime = LocalTime.parse(deliveryTime);
    }

    @Scheduled(fixedDelayString = "${app.reminders.poll}", initialDelayString = "${app.reminders.poll}")
    public void deliver() {
        var now = clock.instant().atZone(SEOUL);
        if (now.toLocalTime().isBefore(deliveryTime)) return;
        try {
            var members = jdbc.sql("""
                    SELECT member_id FROM policy_reminders WHERE state = 'PENDING' AND due_on <= :today
                    UNION SELECT s.member_id FROM saved_policies s JOIN policies p ON p.policy_number = s.policy_number
                    WHERE s.current_revision <> p.current_revision ORDER BY member_id LIMIT 100
                    """).param("today", now.toLocalDate()).query(UUID.class).list();
            for (var member : members) {
                try { store.deliver(member); }
                catch (RuntimeException exception) { log.warn("회원 마감 알림 처리 실패. 다음 실행에서 다시 확인합니다."); }
            }
        } catch (RuntimeException exception) { log.warn("마감 알림 대상 조회 실패. DB 연결을 확인하세요."); }
    }
}
