package kr.youthpolicymate.ingestion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PolicyAiRecoveryHeartbeatConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PolicyAiRecoveryHeartbeatConfiguration.class);

    @Test
    @DisplayName("기본 설정과 preview에서는 heartbeat 실행기를 만들지 않는다")
    void remainsDisabledByDefaultAndInPreview() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(PolicyAiRecoveryHeartbeatScheduler.class));
        contextRunner.withPropertyValues("app.ai-recovery.heartbeat.enabled=true")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("preview"))
                .run(context -> assertThat(context).doesNotHaveBean(PolicyAiRecoveryHeartbeatScheduler.class));
    }

    @Test
    @DisplayName("활성화할 때 운영값을 빠뜨리거나 주기보다 짧은 임대를 주면 시작하지 않는다")
    void requiresExplicitSettingsAndValidLeaseDuration() {
        withStore().withPropertyValues("app.ai-recovery.heartbeat.enabled=true")
                .run(context -> assertThat(context).hasFailed());
        enabled().withPropertyValues("app.ai-recovery.heartbeat.lease-duration=PT1S")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "AI 예약 복구 heartbeat 임대 길이는 주기보다 길어야 합니다.");
                });
    }

    @Test
    @DisplayName("컨텍스트 종료는 확인 결과를 차단하고 갱신 저장소보다 먼저 실행기를 정리한다")
    void closesSchedulerBeforeItsStoreDependency() {
        var scheduler = new AtomicReference<PolicyAiRecoveryHeartbeatScheduler>();
        var registration = new AtomicReference<PolicyAiRecoveryHeartbeat.Cancellation>();
        var store = new ClosingStore(registration);
        contextRunner.withBean("aiReservationRecoveryLeaseRenewalStore",
                        AiReservationRecoveryLeaseRenewalStore.class, () -> store)
                .withPropertyValues(settings()).run(context -> {
                    assertThat(context).hasSingleBean(PolicyAiRecoveryHeartbeat.class);
                    scheduler.set(context.getBean(PolicyAiRecoveryHeartbeatScheduler.class));
                    registration.set(scheduler.get().schedule(Duration.ofDays(1), Duration.ofDays(1), () -> {}));
                });

        assertThat(store.completionRejectedAtDestruction).isTrue();
        registration.get().cancel();
        assertThatThrownBy(registration.get()::verifyCompletion).isInstanceOf(RejectedExecutionException.class);
        assertThatThrownBy(() -> scheduler.get().schedule(Duration.ZERO, Duration.ofSeconds(1), () -> {}))
                .isInstanceOf(RejectedExecutionException.class);
    }

    private ApplicationContextRunner withStore() {
        return contextRunner.withBean("aiReservationRecoveryLeaseRenewalStore",
                AiReservationRecoveryLeaseRenewalStore.class, () -> mock(AiReservationRecoveryLeaseRenewalStore.class));
    }

    private ApplicationContextRunner enabled() {
        return withStore().withPropertyValues(settings());
    }

    private String[] settings() {
        return new String[]{"app.ai-recovery.heartbeat.enabled=true", "app.ai-recovery.heartbeat.threads=1",
                "app.ai-recovery.heartbeat.shutdown-grace=PT0S", "app.ai-recovery.heartbeat.interval=PT2S",
                "app.ai-recovery.heartbeat.lease-duration=PT7S"};
    }

    private static final class ClosingStore extends AiReservationRecoveryLeaseRenewalStore implements DisposableBean {
        private final AtomicReference<PolicyAiRecoveryHeartbeat.Cancellation> registration;
        private boolean completionRejectedAtDestruction;

        private ClosingStore(AtomicReference<PolicyAiRecoveryHeartbeat.Cancellation> registration) {
            super(mock(JdbcClient.class));
            this.registration = registration;
        }

        @Override
        public void destroy() {
            try {
                registration.get().verifyCompletion();
            } catch (RejectedExecutionException stopped) {
                completionRejectedAtDestruction = true;
            }
        }
    }
}
