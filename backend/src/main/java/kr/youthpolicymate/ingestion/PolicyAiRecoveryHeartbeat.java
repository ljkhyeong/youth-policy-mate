package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiReservationRecoveryLeaseRenewalStore.RenewalCommand;
import kr.youthpolicymate.ingestion.AiReservationRecoveryLeaseRenewalStore.RenewalDecision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryLeaseRenewalStore.RenewalOutcome;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Attempt;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Inspection;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Outcome;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// 외부 확인과 별개로 짧은 임대 갱신만 예약하고, 갱신 실패 뒤의 확인 결과는 적용 경계로 넘기지 않는다.
public final class PolicyAiRecoveryHeartbeat {
    private final AiReservationRecoveryLeaseRenewalStore renewalStore;
    private final HeartbeatScheduler scheduler;
    private final Clock clock;
    private final HeartbeatPlan plan;

    public PolicyAiRecoveryHeartbeat(AiReservationRecoveryLeaseRenewalStore renewalStore,
                                     HeartbeatScheduler scheduler,
                                     Clock clock,
                                     HeartbeatPlan plan) {
        this.renewalStore = Objects.requireNonNull(renewalStore, "AI 예약 복구 임대 갱신 저장소가 필요합니다.");
        this.scheduler = Objects.requireNonNull(scheduler, "AI 예약 복구 heartbeat 스케줄러가 필요합니다.");
        this.clock = Objects.requireNonNull(clock, "AI 예약 복구 heartbeat 시계가 필요합니다.");
        this.plan = Objects.requireNonNull(plan, "AI 예약 복구 heartbeat 계획이 필요합니다.");
    }

    public InspectionResult inspect(Inspection inspection, PolicyAiRecoveryPort recoveryPort) {
        Objects.requireNonNull(inspection, "AI 예약 복구 확인 대상이 필요합니다.");
        Objects.requireNonNull(recoveryPort, "AI 예약 복구 확인 포트가 필요합니다.");

        var state = new HeartbeatState(inspection.attempt().leaseUntil());
        Cancellation cancellation = Objects.requireNonNull(
                scheduler.schedule(plan.interval(), plan.interval(),
                        () -> renew(inspection.attempt(), state)),
                "AI 예약 복구 heartbeat 취소 경계가 필요합니다.");

        Outcome outcome;
        try {
            outcome = Objects.requireNonNull(
                    recoveryPort.inspect(inspection), "AI 예약 복구 확인 결과가 필요합니다.");
        } finally {
            synchronized (state) {
                state.inspectionFinished = true;
            }
            cancellation.cancel();
        }

        synchronized (state) {
            if (state.failure != null) throw state.failure;
            if (state.rejected != null) return new RenewalRejected(state.rejected);
            return new InspectionCompleted(outcome);
        }
    }

    private void renew(Attempt attempt, HeartbeatState state) {
        synchronized (state) {
            if (state.inspectionFinished || state.rejected != null || state.failure != null) return;

            try {
                Instant renewedAt = clock.instant();
                Instant renewedLeaseUntil = renewedAt.plus(plan.leaseDuration());
                if (!state.observedLeaseUntil.isBefore(renewedLeaseUntil)) return;

                RenewalOutcome renewal = renewalStore.renew(new RenewalCommand(
                        UUID.randomUUID().toString(), attempt.reservationId(), attempt.attemptId(),
                        attempt.attemptNumber(), attempt.ownerId(), state.observedLeaseUntil,
                        renewedAt, renewedLeaseUntil));
                if (renewal.decision() == RenewalDecision.RENEWED
                        || renewal.decision() == RenewalDecision.REPLAYED) {
                    state.observedLeaseUntil = renewal.record()
                            .orElseThrow(() -> new IllegalStateException(
                                    "성공한 AI 예약 복구 임대 갱신 기록이 필요합니다."))
                            .renewedLeaseUntil();
                    return;
                }
                state.rejected = renewal;
            } catch (RuntimeException failure) {
                state.failure = failure;
            }
        }
    }

    public record HeartbeatPlan(Duration interval, Duration leaseDuration) {
        public HeartbeatPlan {
            requirePositive(interval, "AI 예약 복구 heartbeat 주기는 양수여야 합니다.");
            requirePositive(leaseDuration, "AI 예약 복구 heartbeat 임대 길이는 양수여야 합니다.");
            if (leaseDuration.compareTo(interval) <= 0) {
                throw new IllegalArgumentException("AI 예약 복구 heartbeat 임대 길이는 주기보다 길어야 합니다.");
            }
        }

        private static void requirePositive(Duration value, String message) {
            Objects.requireNonNull(value, message);
            if (value.isNegative() || value.isZero()) throw new IllegalArgumentException(message);
        }
    }

    public sealed interface InspectionResult permits InspectionCompleted, RenewalRejected {}

    public record InspectionCompleted(Outcome outcome) implements InspectionResult {
        public InspectionCompleted {
            Objects.requireNonNull(outcome, "완료한 AI 예약 복구 확인 결과가 필요합니다.");
        }
    }

    public record RenewalRejected(RenewalOutcome renewal) implements InspectionResult {
        public RenewalRejected {
            Objects.requireNonNull(renewal, "거절된 AI 예약 복구 임대 갱신 결과가 필요합니다.");
            if (renewal.decision() == RenewalDecision.RENEWED
                    || renewal.decision() == RenewalDecision.REPLAYED) {
                throw new IllegalArgumentException("성공한 임대 갱신을 heartbeat 거절로 반환할 수 없습니다.");
            }
        }
    }

    @FunctionalInterface
    public interface HeartbeatScheduler {
        Cancellation schedule(Duration initialDelay, Duration delay, Runnable heartbeat);

        static HeartbeatScheduler scheduled(ScheduledExecutorService executor) {
            Objects.requireNonNull(executor, "AI 예약 복구 heartbeat 실행기가 필요합니다.");
            return (initialDelay, delay, heartbeat) -> {
                Objects.requireNonNull(initialDelay, "AI 예약 복구 heartbeat 최초 대기 시간이 필요합니다.");
                Objects.requireNonNull(delay, "AI 예약 복구 heartbeat 반복 주기가 필요합니다.");
                Objects.requireNonNull(heartbeat, "AI 예약 복구 heartbeat 작업이 필요합니다.");
                var future = executor.scheduleWithFixedDelay(
                        heartbeat, initialDelay.toNanos(), delay.toNanos(), TimeUnit.NANOSECONDS);
                return () -> future.cancel(false);
            };
        }
    }

    @FunctionalInterface
    public interface Cancellation {
        void cancel();
    }

    private static final class HeartbeatState {
        private boolean inspectionFinished;
        private Instant observedLeaseUntil;
        private RenewalOutcome rejected;
        private RuntimeException failure;

        private HeartbeatState(Instant observedLeaseUntil) {
            this.observedLeaseUntil = observedLeaseUntil;
        }
    }
}
