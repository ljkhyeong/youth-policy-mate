package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationLifecycleStore.Transition;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Dispatch;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.Billing;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.ConfirmedCharge;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.ConfirmedNoCharge;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.Invocation;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.PendingCharge;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.ResponseReceived;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.Uncertain;
import kr.youthpolicymate.ingestion.PolicyAiRequestAdmission.ReservationRequired;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

// Spring 트랜잭션을 열지 않는다. 각 저장소 호출이 끝난 뒤 외부 실행 포트를 호출한다.
public final class PolicyAiExecutionCoordinator {
    private final AiBudgetReservationStore reservationStore;
    private final AiBudgetReservationLifecycleStore lifecycleStore;
    private final PolicyAiExecutionPort executionPort;

    public PolicyAiExecutionCoordinator(AiBudgetReservationStore reservationStore,
                                        AiBudgetReservationLifecycleStore lifecycleStore,
                                        PolicyAiExecutionPort executionPort) {
        this.reservationStore = Objects.requireNonNull(reservationStore, "AI 예산 예약 저장소가 필요합니다.");
        this.lifecycleStore = Objects.requireNonNull(lifecycleStore, "AI 예약 상태 저장소가 필요합니다.");
        this.executionPort = Objects.requireNonNull(executionPort, "AI 실행 포트가 필요합니다.");
    }

    public Run execute(String reservationId, ReservationRequired required, Instant reservedAt, Dispatch dispatch) {
        return execute(reservationId, required, reservedAt, dispatch, () -> lifecycleStore.dispatch(reservationId, dispatch));
    }

    Run execute(String reservationId, ReservationRequired required, Instant reservedAt, Dispatch dispatch,
                Supplier<Transition> guardedDispatch) {
        Objects.requireNonNull(required, "AI 요청의 예약 필요 결과가 필요합니다.");
        Objects.requireNonNull(reservedAt, "AI 요청 예약 시각이 필요합니다.");
        Objects.requireNonNull(dispatch, "외부 호출 식별 정보가 필요합니다.");
        if (dispatch.dispatchedAt().isBefore(reservedAt)) {
            throw new IllegalArgumentException("외부 호출은 예산 예약보다 빠를 수 없습니다.");
        }
        var cost = Objects.requireNonNull(required.cost(), "AI 요청 최대 비용이 필요합니다.");
        var invocation = new Invocation(reservationId, cost.request(), dispatch);
        var reservation = reservationStore.reserve(reservationId, required, reservedAt);
        if (reservation.decision() != AiBudgetReservationStore.Decision.RESERVED
                && reservation.decision() != AiBudgetReservationStore.Decision.REPLAYED) {
            return new NotStarted(StopReason.RESERVATION_REJECTED, reservation, Optional.empty());
        }

        var dispatched = guardedDispatch.get();
        if (dispatched.decision() == AiBudgetReservationLifecycleStore.Decision.REPLAYED) {
            return new NotStarted(StopReason.ALREADY_DISPATCHED, reservation, Optional.of(dispatched));
        }
        if (dispatched.decision() != AiBudgetReservationLifecycleStore.Decision.DISPATCHED) {
            return new NotStarted(StopReason.DISPATCH_REJECTED, reservation, Optional.of(dispatched));
        }

        var outcome = Objects.requireNonNull(executionPort.execute(invocation), "AI 실행 결과가 필요합니다.");
        if (outcome instanceof Uncertain uncertain) {
            var transition = lifecycleStore.markOutcomeUnknown(reservationId, uncertain.uncertain());
            return new UncertainRun(reservation, transition, uncertain.uncertain());
        }
        var response = (ResponseReceived) outcome;
        validateResponse(invocation, response);
        var transition = completeBilling(reservationId, dispatched, response.billing());
        return new Responded(reservation, transition, response.result(), response.billing());
    }

    private Transition completeBilling(String reservationId, Transition dispatched, Billing billing) {
        if (billing == PendingCharge.INSTANCE) return dispatched;
        if (billing instanceof ConfirmedCharge confirmed) {
            return lifecycleStore.settle(reservationId, confirmed.confirmation());
        }
        var noCharge = (ConfirmedNoCharge) billing;
        return lifecycleStore.releaseAfterNoCharge(reservationId, noCharge.confirmation());
    }

    private static void validateResponse(Invocation invocation, ResponseReceived response) {
        var result = response.result();
        if (!result.request().equals(invocation.request())) {
            throw new IllegalStateException("AI 실행 포트가 다른 요청의 결과를 반환했습니다.");
        }
        if (result.recordedAt().isBefore(invocation.dispatch().dispatchedAt())) {
            throw new IllegalStateException("AI 응답 확인은 외부 호출보다 빠를 수 없습니다.");
        }
        if (response.billing() instanceof ConfirmedCharge confirmed
                && confirmed.confirmation().confirmedAt().isBefore(result.recordedAt())) {
            throw new IllegalStateException("청구 확인은 AI 응답 확인보다 빠를 수 없습니다.");
        }
        if (response.billing() instanceof ConfirmedNoCharge noCharge
                && noCharge.confirmation().confirmedAt().isBefore(result.recordedAt())) {
            throw new IllegalStateException("무과금 확인은 AI 응답 확인보다 빠를 수 없습니다.");
        }
    }

    public sealed interface Run permits NotStarted, UncertainRun, Responded {}

    public enum StopReason { RESERVATION_REJECTED, DISPATCH_REJECTED, ALREADY_DISPATCHED }

    public record NotStarted(
            StopReason reason, AiBudgetReservationStore.Attempt reservation, Optional<Transition> dispatch
    ) implements Run {
        public NotStarted {
            Objects.requireNonNull(reason, "AI 실행 중단 사유가 필요합니다.");
            Objects.requireNonNull(reservation, "AI 예산 예약 결과가 필요합니다.");
            Objects.requireNonNull(dispatch, "외부 호출 기록 결과의 존재 여부가 필요합니다.");
        }
    }

    public record UncertainRun(
            AiBudgetReservationStore.Attempt reservation, Transition transition,
            AiBudgetReservationState.UncertainOutcome uncertain
    ) implements Run {
        public UncertainRun {
            Objects.requireNonNull(reservation, "AI 예산 예약 결과가 필요합니다.");
            Objects.requireNonNull(transition, "결과 미확인 저장 결과가 필요합니다.");
            Objects.requireNonNull(uncertain, "결과 미확인 정보가 필요합니다.");
        }
    }

    public record Responded(
            AiBudgetReservationStore.Attempt reservation, Transition transition,
            PolicyAiResult result, Billing billing
    ) implements Run {
        public Responded {
            Objects.requireNonNull(reservation, "AI 예산 예약 결과가 필요합니다.");
            Objects.requireNonNull(transition, "청구 상태 반영 결과가 필요합니다.");
            Objects.requireNonNull(result, "분류한 AI 응답 결과가 필요합니다.");
            Objects.requireNonNull(billing, "AI 응답의 청구 확인 상태가 필요합니다.");
        }
    }
}
