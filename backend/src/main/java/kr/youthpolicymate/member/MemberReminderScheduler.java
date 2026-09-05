package kr.youthpolicymate.member;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!preview")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "app.reminders.enabled", havingValue = "true", matchIfMissing = true)
public class MemberReminderScheduler {
    private final JdbcClient jdbc;
    private final MemberPolicyStore store;
    private final LocalTime deliveryTime;
    private final Clock clock;
    public MemberReminderScheduler(JdbcClient jdbc, MemberPolicyStore store, Environment environment, Clock clock) {
        this.jdbc = jdbc; this.store = store; this.clock = clock;
        deliveryTime = LocalTime.parse(environment.getProperty("REMINDER_DELIVERY_TIME", "09:00"));
    }

    @Scheduled(fixedDelayString = "${REMINDER_POLL_MS:60000}", initialDelayString = "${REMINDER_POLL_MS:60000}")
    public void deliver() {
        var now = ZonedDateTime.now(clock).withZoneSameInstant(ZoneId.of("Asia/Seoul"));
        if (now.toLocalTime().isBefore(deliveryTime)) return;
        try {
            var members = jdbc.sql("""
                    SELECT member_id FROM policy_reminders WHERE state = 'PENDING' AND due_on <= :today
                    UNION SELECT s.member_id FROM saved_policies s JOIN policies p ON p.policy_number = s.policy_number
                    WHERE s.current_revision <> p.current_revision ORDER BY member_id LIMIT 100
                    """).param("today", now.toLocalDate()).query(UUID.class).list();
            for (var member : members) {
                try { store.deliver(member); }
                catch (RuntimeException exception) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("회원 마감 알림 처리 실패. 다음 실행에서 다시 확인합니다."); }
            }
        } catch (RuntimeException exception) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("마감 알림 대상 조회 실패. DB 연결을 확인하세요."); }
    }
}
