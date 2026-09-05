package kr.youthpolicymate.ingestion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyAiRecoveryHeartbeatSchedulerTest {
    @Test
    @DisplayName("종료 대기 중 새 확인은 막고 기존 갱신은 확인이 끝날 때까지 유지한다")
    void drainsActiveInspectionWhileRejectingNewRegistrations() throws Exception {
        var draining = new AtomicBoolean();
        var renewedDuringShutdown = new CountDownLatch(1);
        try (var scheduler = new PolicyAiRecoveryHeartbeatScheduler(1, Duration.ofSeconds(5));
             var closer = Executors.newSingleThreadExecutor()) {
            var registration = scheduler.schedule(Duration.ZERO, Duration.ofMillis(10), () -> {
                if (draining.get()) renewedDuringShutdown.countDown();
            });
            try {
                scheduler.beginShutdown();
                draining.set(true);
                var shutdown = closer.submit(scheduler::close);
                assertThat(renewedDuringShutdown.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(shutdown.isDone()).isFalse();
                assertThatThrownBy(() -> scheduler.schedule(Duration.ZERO, Duration.ofSeconds(1), () -> {}))
                        .isInstanceOf(RejectedExecutionException.class);

                registration.cancel();
                registration.verifyCompletion();
                shutdown.get(3, TimeUnit.SECONDS);
                assertThat(scheduler.isTerminated()).isTrue();
                scheduler.close();
                registration.cancel();
                assertThatCode(registration::verifyCompletion).doesNotThrowAnyException();
            } finally {
                registration.cancel();
            }
        }
    }

    @Test
    @DisplayName("종료 기한 이후 결과는 거절하고 실행 중인 갱신은 강제 인터럽트하지 않는다")
    void rejectsLateCompletionWithoutInterruptingRunningRenewal() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        try (var scheduler = new PolicyAiRecoveryHeartbeatScheduler(1, Duration.ofMillis(20))) {
            var registration = scheduler.schedule(Duration.ZERO, Duration.ofMillis(10), () -> {
                entered.countDown();
                try {
                    release.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException interruption) {
                    interrupted.set(true);
                } finally {
                    exited.countDown();
                }
            });
            try {
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                scheduler.close();
                assertThat(scheduler.isTerminated()).isFalse();
                assertThat(interrupted.get()).isFalse();
                registration.cancel();
                assertThatThrownBy(registration::verifyCompletion).isInstanceOf(RejectedExecutionException.class);
                scheduler.close();
                assertThatThrownBy(registration::verifyCompletion).isInstanceOf(RejectedExecutionException.class);
            } finally {
                release.countDown();
                assertThat(exited.await(3, TimeUnit.SECONDS)).isTrue();
                assertThat(interrupted.get()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("종료 대기 인터럽트는 응답 적용을 차단하고 호출 스레드의 인터럽트 상태를 보존한다")
    void preservesInterruptAndRejectsPendingCompletion() throws Exception {
        try (var scheduler = new PolicyAiRecoveryHeartbeatScheduler(1, Duration.ofSeconds(5));
             var closer = Executors.newSingleThreadExecutor()) {
            var registration = scheduler.schedule(Duration.ofDays(1), Duration.ofDays(1), () -> {});
            assertThat(closer.submit(() -> {
                Thread.currentThread().interrupt();
                scheduler.close();
                return Thread.currentThread().isInterrupted();
            }).get(3, TimeUnit.SECONDS)).isTrue();
            registration.cancel();
            assertThatThrownBy(registration::verifyCompletion).isInstanceOf(RejectedExecutionException.class);
        }
    }
}
