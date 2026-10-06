package kr.youthpolicymate.member;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@Profile("!preview")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBooleanProperty("app.email.enabled")
public class MemberEmailScheduler {
    private final MemberEmailDelivery delivery;
    public MemberEmailScheduler(MemberEmailDelivery delivery) { this.delivery = delivery; }
    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void deliver() { delivery.deliverPending(); }
}
