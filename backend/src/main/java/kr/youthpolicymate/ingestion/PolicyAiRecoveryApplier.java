package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationLifecycleStore.Decision;
import kr.youthpolicymate.ingestion.AiBudgetReservationLifecycleStore.Snapshot;
import kr.youthpolicymate.ingestion.AiBudgetReservationLifecycleStore.Transition;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Attempt;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Completion;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.CompletionDecision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.CompletionOutcome;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.RecoveryFence;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.RecoveryResult;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.ChargeFound;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.CheckFailed;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.NoChargeFound;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.NotDispatched;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Outcome;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.ResponseFound;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.Billing;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.ConfirmedCharge;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.ConfirmedNoCharge;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.PendingCharge;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
@Profile("!preview")
public class PolicyAiRecoveryApplier {
    private final AiBudgetReservationLifecycleStore lifecycleStore;
    private final AiReservationRecoveryStore recoveryStore;

    public PolicyAiRecoveryApplier(AiBudgetReservationLifecycleStore lifecycleStore,
                                   AiReservationRecoveryStore recoveryStore) {
        this.lifecycleStore = lifecycleStore;
        this.recoveryStore = recoveryStore;
    }

    // 외부 확인이 끝난 뒤 호출한다. 예약 상태 변경과 복구 시도 완료를 한 트랜잭션에 묶는다.
    @Transactional
    public Application apply(Attempt attempt, Snapshot observed, Outcome outcome, Instant appliedAt) {
        Objects.requireNonNull(attempt, "AI 예약 복구 시도가 필요합니다.");
        Objects.requireNonNull(observed, "확인한 AI 요청 예약 상태가 필요합니다.");
        Objects.requireNonNull(outcome, "AI 예약 복구 확인 결과가 필요합니다.");
        Objects.requireNonNull(appliedAt, "AI 예약 복구 결과 적용 시각이 필요합니다.");

        Optional<Transition> transition = applyReservationTransition(attempt, observed, outcome, appliedAt);
        var result = completionResult(outcome, transition);
        var completion = recoveryStore.complete(new Completion(
                attempt.attemptId(), attempt.ownerId(), appliedAt, result));

        if (transition.filter(PolicyAiRecoveryApplier::changedReservation).isPresent()
                && completion.decision() != CompletionDecision.COMPLETED
                && completion.decision() != CompletionDecision.REPLAYED) {
            throw new IllegalStateException("AI 예약 상태를 바꾼 복구 시도를 완료하지 못했습니다.");
        }
        return new Application(transition, completion);
    }

    private Optional<Transition> applyReservationTransition(Attempt attempt, Snapshot observed,
                                                            Outcome outcome, Instant appliedAt) {
        if (outcome == CheckFailed.INSTANCE || outcome == PolicyAiRecoveryPort.ReviewRequired.INSTANCE) {
            return Optional.empty();
        }

        RecoveryFence fence = RecoveryFence.from(attempt, observed, appliedAt);
        if (outcome instanceof ResponseFound response) {
            return applyBilling(attempt.reservationId(), response.billing(), fence);
        }
        if (outcome instanceof ChargeFound charge) {
            return Optional.of(lifecycleStore.settleUnderRecovery(
                    attempt.reservationId(), charge.confirmation(), fence));
        }
        if (outcome instanceof NoChargeFound noCharge) {
            return Optional.of(lifecycleStore.releaseAfterNoChargeUnderRecovery(
                    attempt.reservationId(), noCharge.confirmation(), fence));
        }
        var notDispatched = (NotDispatched) outcome;
        return Optional.of(lifecycleStore.cancelBeforeDispatchUnderRecovery(
                attempt.reservationId(), notDispatched.cancellation(), fence));
    }

    private Optional<Transition> applyBilling(String reservationId, Billing billing, RecoveryFence fence) {
        if (billing == PendingCharge.INSTANCE) return Optional.empty();
        if (billing instanceof ConfirmedCharge confirmed) {
            return Optional.of(lifecycleStore.settleUnderRecovery(
                    reservationId, confirmed.confirmation(), fence));
        }
        var noCharge = (ConfirmedNoCharge) billing;
        return Optional.of(lifecycleStore.releaseAfterNoChargeUnderRecovery(
                reservationId, noCharge.confirmation(), fence));
    }

    private static RecoveryResult completionResult(Outcome outcome, Optional<Transition> transition) {
        if (outcome == CheckFailed.INSTANCE) return RecoveryResult.CHECK_FAILED;
        if (outcome == PolicyAiRecoveryPort.ReviewRequired.INSTANCE) {
            return RecoveryResult.MANUAL_REVIEW_REQUIRED;
        }
        if (outcome instanceof ResponseFound response && response.billing() == PendingCharge.INSTANCE) {
            return RecoveryResult.CHECK_COMPLETED;
        }
        return transition.filter(PolicyAiRecoveryApplier::changedReservation).isPresent()
                ? RecoveryResult.CHECK_COMPLETED : RecoveryResult.MANUAL_REVIEW_REQUIRED;
    }

    private static boolean changedReservation(Transition transition) {
        return transition.decision() == Decision.SETTLED
                || transition.decision() == Decision.SETTLED_OVER_RESERVATION
                || transition.decision() == Decision.RELEASED_NO_CHARGE
                || transition.decision() == Decision.RELEASED_BEFORE_DISPATCH
                || transition.decision() == Decision.REPLAYED;
    }

    public record Application(Optional<Transition> transition, CompletionOutcome completion) {
        public Application {
            Objects.requireNonNull(transition, "AI 예약 상태 변경 결과의 존재 여부가 필요합니다.");
            Objects.requireNonNull(completion, "AI 예약 복구 시도 완료 결과가 필요합니다.");
        }
    }
}
