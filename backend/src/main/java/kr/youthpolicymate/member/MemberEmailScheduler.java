package kr.youthpolicymate.member;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@Profile("!preview")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "true")
public class MemberEmailScheduler {
    private final MemberEmailDelivery delivery;
    public MemberEmailScheduler(MemberEmailDelivery delivery) { this.delivery = delivery; }
    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void deliver() { delivery.deliverPending(); }
}
