package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.PolicyAiRecoveryHeartbeat.HeartbeatPlan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextClosedEvent;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@Profile("!preview")
@ConditionalOnProperty(prefix = "app.ai-recovery.heartbeat", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(PolicyAiRecoveryHeartbeatConfiguration.Settings.class)
class PolicyAiRecoveryHeartbeatConfiguration {
    @Bean
    @DependsOn("aiReservationRecoveryLeaseRenewalStore")
    PolicyAiRecoveryHeartbeatScheduler policyAiRecoveryHeartbeatScheduler(Settings settings) {
        // 종료 대기 중에도 갱신 저장소와 그 DB 의존성을 사용할 수 있도록 파괴 순서를 지정한다.
        return new PolicyAiRecoveryHeartbeatScheduler(settings.threads(), settings.shutdownGrace());
    }

    @Bean
    PolicyAiRecoveryHeartbeat policyAiRecoveryHeartbeat(
            AiReservationRecoveryLeaseRenewalStore renewalStore,
            PolicyAiRecoveryHeartbeatScheduler scheduler,
            Settings settings) {
        return new PolicyAiRecoveryHeartbeat(
                renewalStore, scheduler, Clock.systemUTC(),
                new HeartbeatPlan(settings.interval(), settings.leaseDuration()));
    }

    @Bean
    ApplicationListener<ContextClosedEvent> policyAiRecoveryHeartbeatShutdown(
            ApplicationContext context, PolicyAiRecoveryHeartbeatScheduler scheduler) {
        return event -> {
            if (event.getApplicationContext() == context) scheduler.beginShutdown();
        };
    }

    @ConfigurationProperties(prefix = "app.ai-recovery.heartbeat")
    record Settings(int threads, Duration shutdownGrace, Duration interval, Duration leaseDuration) {}
}
