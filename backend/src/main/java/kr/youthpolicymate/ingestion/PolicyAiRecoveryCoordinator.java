package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationLifecycleStore.Snapshot;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Phase;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Attempt;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.ClaimDecision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.ClaimOutcome;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Lease;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Status;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryApplier.Application;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Inspection;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Outcome;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.ResponseFound;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.ReviewRequired;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

// 외부 확인 중에는 Spring 트랜잭션을 열지 않는다. 상태 적용은 PolicyAiRecoveryApplier가 짧게 처리한다.
public final class PolicyAiRecoveryCoordinator {
    private final AiReservationRecoveryStore recoveryStore;
    private final AiBudgetReservationLifecycleStore lifecycleStore;
    private final PolicyAiRecoveryApplier applier;
    private final PolicyAiRecoveryPort recoveryPort;
    private final Clock clock;

    public PolicyAiRecoveryCoordinator(AiReservationRecoveryStore recoveryStore,
                                       AiBudgetReservationLifecycleStore lifecycleStore,
                                       PolicyAiRecoveryApplier applier,
                                       PolicyAiRecoveryPort recoveryPort,
                                       Clock clock) {
        this.recoveryStore = Objects.requireNonNull(recoveryStore, "AI 예약 복구 저장소가 필요합니다.");
        this.lifecycleStore = Objects.requireNonNull(lifecycleStore, "AI 예약 상태 저장소가 필요합니다.");
        this.applier = Objects.requireNonNull(applier, "AI 예약 복구 결과 적용기가 필요합니다.");
        this.recoveryPort = Objects.requireNonNull(recoveryPort, "AI 예약 복구 확인 포트가 필요합니다.");
        this.clock = Objects.requireNonNull(clock, "AI 예약 복구 시계가 필요합니다.");
    }

    public Run recoverNext(Lease lease) {
        Objects.requireNonNull(lease, "AI 예약 복구 임대 정보가 필요합니다.");
        ClaimOutcome claim = recoveryStore.claimNext(lease);
        if (claim.decision() != ClaimDecision.CLAIMED && claim.decision() != ClaimDecision.REPLAYED) {
            return new NotStarted(StopReason.CLAIM_REJECTED, claim);
        }

        Attempt attempt = claim.attempt().orElseThrow();
        if (attempt.status() != Status.ACTIVE) {
            return new NotStarted(StopReason.ATTEMPT_NOT_ACTIVE, claim);
        }
        Optional<Snapshot> reservation = lifecycleStore.find(attempt.reservationId());
        if (reservation.isEmpty()) {
            return new NotStarted(StopReason.RESERVATION_NOT_FOUND, claim);
        }

        var inspection = new Inspection(reservation.orElseThrow(), attempt);
        Outcome outcome = isTerminal(inspection.reservation().phase())
                ? ReviewRequired.INSTANCE
                : Objects.requireNonNull(recoveryPort.inspect(inspection), "AI 예약 복구 확인 결과가 필요합니다.");
        validateResponseTiming(inspection, outcome);
        Application application = applier.apply(attempt, inspection.reservation(), outcome, clock.instant());
        return new Recovered(claim, inspection, outcome, application);
    }

    private static boolean isTerminal(Phase phase) {
        return phase == Phase.SETTLED || phase == Phase.CANCELLED || phase == Phase.RELEASED_NO_CHARGE;
    }

    private static void validateResponseTiming(Inspection inspection, Outcome outcome) {
        if (!(outcome instanceof ResponseFound response)) return;
        var dispatch = inspection.reservation().dispatch()
                .orElseThrow(() -> new IllegalStateException("외부 호출 전 예약에서 AI 응답을 복구할 수 없습니다."));
        if (response.result().recordedAt().isBefore(dispatch.dispatchedAt())) {
            throw new IllegalStateException("복구한 AI 응답 확인은 외부 호출보다 빠를 수 없습니다.");
        }
    }

    public sealed interface Run permits NotStarted, Recovered {}

    public enum StopReason { CLAIM_REJECTED, ATTEMPT_NOT_ACTIVE, RESERVATION_NOT_FOUND }

    public record NotStarted(StopReason reason, ClaimOutcome claim) implements Run {
        public NotStarted {
            Objects.requireNonNull(reason, "AI 예약 복구 중단 사유가 필요합니다.");
            Objects.requireNonNull(claim, "AI 예약 복구 소유권 결과가 필요합니다.");
        }
    }

    public record Recovered(
            ClaimOutcome claim, Inspection inspection, Outcome outcome, Application application
    ) implements Run {
        public Recovered {
            Objects.requireNonNull(claim, "AI 예약 복구 소유권 결과가 필요합니다.");
            Objects.requireNonNull(inspection, "AI 예약 복구 확인 대상이 필요합니다.");
            Objects.requireNonNull(outcome, "AI 예약 복구 확인 결과가 필요합니다.");
            Objects.requireNonNull(application, "AI 예약 복구 적용 결과가 필요합니다.");
        }
    }
}
