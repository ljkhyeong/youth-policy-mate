package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationState.*;
import kr.youthpolicymate.ingestion.AiRequestBudget.Balance;
import kr.youthpolicymate.ingestion.AiRequestBudget.CostCeiling;
import kr.youthpolicymate.ingestion.PolicyAiRequestAdmission.ReservationRequired;
import kr.youthpolicymate.ingestion.PolicyAiResult.Kind;
import kr.youthpolicymate.ingestion.PolicyAiResult.Request;
import kr.youthpolicymate.policy.PolicyObservation;
import kr.youthpolicymate.policy.PolicyObservation.ContentFingerprint;
import kr.youthpolicymate.policy.PolicyObservation.Readable;
import kr.youthpolicymate.policy.PolicyObservation.SnapshotReference;
import kr.youthpolicymate.policy.PolicyRevisionState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static kr.youthpolicymate.ingestion.AiBudgetReservationState.Decision.*;
import static org.assertj.core.api.Assertions.*;

class AiBudgetReservationStateTest {
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    @DisplayName("요청 최대 비용을 예약하면 예약액과 요청별 보유 상태를 함께 갱신한다")
    void reservesRequestCost() {
        var state = state("100", "20", "5");
        var required = required(state.balance(), request(10), "10.25");

        var transition = state.reserve("reservation-a", required, NOW);

        assertThat(transition.decision()).isEqualTo(RESERVED);
        assertThat(transition.state().balance().reservedWon()).isEqualByComparingTo("15.25");
        assertThat(transition.state().balance().remainingWon()).isEqualByComparingTo("64.75");
        assertThat(transition.state().reservation("reservation-a")).hasValueSatisfying(reservation -> {
            assertThat(reservation.phase()).isEqualTo(Phase.HELD);
            assertThat(reservation.hold().request()).isEqualTo(request(10));
        });
        assertThat(state.balance().reservedWon()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("같은 예약은 재전달로 처리하고 같은 요청의 다른 예약과 식별자 충돌을 막는다")
    void makesReservationIdempotent() {
        var initial = state("100", "0", "0");
        var firstRequest = request(10);
        var required = required(initial.balance(), firstRequest, "10");
        var reserved = initial.reserve("reservation-a", required, NOW).state();

        assertThat(reserved.reserve("reservation-a", required, NOW).decision()).isEqualTo(REPLAYED);
        assertThat(reserved.reserve("reservation-b", required, NOW).decision()).isEqualTo(REQUEST_ALREADY_RESERVED);
        assertThat(reserved.reserve("reservation-a", required(initial.balance(), request(20), "10"), NOW).decision())
                .isEqualTo(RESERVATION_ID_CONFLICT);
        var otherBudget = new Balance("other-budget", initial.balance().startsAt(), initial.balance().endsAt(),
                money("100"), money("0"), money("0"));
        assertThat(reserved.reserve("reservation-a", required(otherBudget, firstRequest, "10"), NOW).decision())
                .isEqualTo(STALE_BALANCE);
        assertThat(reserved.reservations()).hasSize(1);
    }

    @Test
    @DisplayName("잔액 스냅샷이 바뀌면 오래된 판단을 예약에 사용하지 않는다")
    void rejectsStaleBalance() {
        var initial = state("100", "0", "0");
        var first = initial.reserve("reservation-a", required(initial.balance(), request(10), "60"), NOW).state();

        assertThat(first.reserve("reservation-b", required(initial.balance(), request(20), "10"), NOW).decision())
                .isEqualTo(STALE_BALANCE);
        assertThat(first.reserve("reservation-b", required(first.balance(), request(20), "41"), NOW).decision())
                .isEqualTo(BUDGET_LIMIT);
        assertThat(first.balance().reservedWon()).isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("예약 시점에도 예산 기간과 비용 유효기간을 다시 확인한다")
    void rechecksTimeBoundaries() {
        var request = request(10);
        var future = AiBudgetReservationState.open(new Balance("future-budget", NOW.plusSeconds(1),
                NOW.plusSeconds(60), money("100"), money("0"), money("0")));
        var state = state("100", "0", "0");
        var expired = new ReservationRequired(state.balance(),
                new CostCeiling(request, "price-a", NOW, money("1")));

        assertThat(future.reserve("before-budget", required(future.balance(), request, "1"), NOW).decision())
                .isEqualTo(BUDGET_PERIOD_INACTIVE);
        assertThat(state.reserve("expired", expired, NOW).decision()).isEqualTo(COST_EXPIRED);
        assertThat(state.reservations()).isEmpty();
    }

    @Test
    @DisplayName("외부 호출 식별자는 한 번만 기록하고 다른 호출 식별자로 덮어쓰지 않는다")
    void dispatchesIdempotently() {
        var reserved = reserved("10");
        var dispatch = new Dispatch("dispatch-a", NOW.plusSeconds(1));
        var sent = reserved.dispatch("reservation-a", dispatch).state();

        assertThat(sent.dispatch("reservation-a", dispatch).decision()).isEqualTo(REPLAYED);
        assertThat(sent.dispatch("reservation-a", new Dispatch("dispatch-b", NOW.plusSeconds(2))).decision())
                .isEqualTo(DISPATCH_CONFLICT);
        assertThat(sent.reservation("reservation-a").orElseThrow().phase()).isEqualTo(Phase.DISPATCHED);
    }

    @Test
    @DisplayName("타임아웃으로 결과를 확인하지 못해도 예약 금액을 유지한다")
    void retainsMoneyForUnknownOutcome() {
        var dispatched = dispatched("10");
        var uncertain = new UncertainOutcome("unknown-a", NOW.plusSeconds(2), UncertainReason.TIMEOUT);
        var unknown = dispatched.markOutcomeUnknown("reservation-a", uncertain);

        assertThat(unknown.decision()).isEqualTo(OUTCOME_UNKNOWN);
        assertThat(unknown.state().balance().reservedWon()).isEqualByComparingTo("10");
        assertThat(unknown.state().balance().confirmedWon()).isEqualByComparingTo("0");
        assertThat(unknown.state().markOutcomeUnknown("reservation-a", uncertain).decision()).isEqualTo(REPLAYED);
        assertThat(unknown.state().markOutcomeUnknown("reservation-a",
                new UncertainOutcome("unknown-b", NOW.plusSeconds(3), UncertainReason.PROVIDER_STATUS_UNAVAILABLE)).decision())
                .isEqualTo(OUTCOME_ALREADY_UNKNOWN);
    }

    @Test
    @DisplayName("확인한 실제 비용으로 정산하면 최대 비용 예약을 해제하고 확정액을 더한다")
    void settlesKnownCharge() {
        var dispatched = dispatched("10");
        var confirmation = new ChargeConfirmation("charge-a", NOW.plusSeconds(2), money("7.25"));
        var settled = dispatched.settle("reservation-a", confirmation);

        assertThat(settled.decision()).isEqualTo(SETTLED);
        assertThat(settled.state().balance().reservedWon()).isEqualByComparingTo("0");
        assertThat(settled.state().balance().confirmedWon()).isEqualByComparingTo("7.25");
        assertThat(settled.state().settle("reservation-a", confirmation).decision()).isEqualTo(REPLAYED);
    }

    @Test
    @DisplayName("실제 비용이 예약액을 넘으면 사실을 버리지 않고 초과 정산으로 구분한다")
    void recordsChargeAboveReservation() {
        var unknown = dispatched("10").markOutcomeUnknown("reservation-a",
                new UncertainOutcome("unknown-a", NOW.plusSeconds(2), UncertainReason.CONNECTION_LOST)).state();

        var settled = unknown.settle("reservation-a",
                new ChargeConfirmation("charge-a", NOW.plusSeconds(3), money("12.5")));

        assertThat(settled.decision()).isEqualTo(SETTLED_OVER_RESERVATION);
        assertThat(settled.state().balance().reservedWon()).isZero();
        assertThat(settled.state().balance().confirmedWon()).isEqualByComparingTo("12.5");
    }

    @Test
    @DisplayName("외부 호출 전 취소는 예약을 해제하고 이후 호출을 막는다")
    void cancelsBeforeDispatch() {
        var reserved = reserved("10");
        var cancellation = new Cancellation("cancel-a", NOW.plusSeconds(1));
        var cancelled = reserved.cancelBeforeDispatch("reservation-a", cancellation);

        assertThat(cancelled.decision()).isEqualTo(RELEASED_BEFORE_DISPATCH);
        assertThat(cancelled.state().balance().reservedWon()).isZero();
        assertThat(cancelled.state().cancelBeforeDispatch("reservation-a", cancellation).decision()).isEqualTo(REPLAYED);
        assertThat(cancelled.state().dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(2))).decision())
                .isEqualTo(INVALID_STATE);
    }

    @Test
    @DisplayName("외부 호출 후에는 명시적인 무과금 확인이 있어야 예약을 해제한다")
    void releasesOnlyAfterNoChargeConfirmation() {
        var unknown = dispatched("10").markOutcomeUnknown("reservation-a",
                new UncertainOutcome("unknown-a", NOW.plusSeconds(2), UncertainReason.TIMEOUT)).state();

        assertThat(unknown.cancelBeforeDispatch("reservation-a", new Cancellation("cancel-a", NOW.plusSeconds(3))).decision())
                .isEqualTo(INVALID_STATE);
        assertThat(unknown.balance().reservedWon()).isEqualByComparingTo("10");
        var confirmation = new NoChargeConfirmation("no-charge-a", NOW.plusSeconds(3));
        var released = unknown.releaseAfterNoCharge("reservation-a", confirmation);
        assertThat(released.decision()).isEqualTo(RELEASED_NO_CHARGE);
        assertThat(released.state().balance().reservedWon()).isZero();
        assertThat(released.state().releaseAfterNoCharge("reservation-a", confirmation).decision()).isEqualTo(REPLAYED);
    }

    @Test
    @DisplayName("정산·무과금 해제 중 먼저 확정한 종료 결과를 다른 결과로 바꾸지 않는다")
    void rejectsConflictingTerminalResults() {
        var settled = dispatched("10").settle("reservation-a",
                new ChargeConfirmation("charge-a", NOW.plusSeconds(2), money("7"))).state();
        assertThat(settled.releaseAfterNoCharge("reservation-a",
                new NoChargeConfirmation("no-charge-a", NOW.plusSeconds(3))).decision()).isEqualTo(TERMINAL_CONFLICT);
        assertThat(settled.settle("reservation-a",
                new ChargeConfirmation("charge-b", NOW.plusSeconds(3), money("8"))).decision()).isEqualTo(TERMINAL_CONFLICT);
        assertThat(settled.balance().confirmedWon()).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("예약 없는 요청과 허용되지 않은 순서의 상태 변경을 구분한다")
    void rejectsMissingAndOutOfOrderTransitions() {
        var state = state("100", "0", "0");
        assertThat(state.dispatch("missing", new Dispatch("dispatch-a", NOW)).decision()).isEqualTo(RESERVATION_NOT_FOUND);
        var reserved = reserved("10");
        assertThat(reserved.markOutcomeUnknown("reservation-a",
                new UncertainOutcome("unknown-a", NOW.plusSeconds(1), UncertainReason.TIMEOUT)).decision())
                .isEqualTo(INVALID_STATE);
        assertThat(reserved.settle("reservation-a",
                new ChargeConfirmation("charge-a", NOW.plusSeconds(1), money("1"))).decision()).isEqualTo(INVALID_STATE);
    }

    @Test
    @DisplayName("음수 금액과 앞선 시각의 외부 결과는 거절한다")
    void rejectsInvalidMoneyAndTimes() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ChargeConfirmation("charge-a", NOW, money("-0.01")));
        var reserved = reserved("10");
        assertThatIllegalArgumentException().isThrownBy(() ->
                reserved.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.minusNanos(1))));
        var dispatched = dispatched("10");
        assertThatIllegalArgumentException().isThrownBy(() -> dispatched.markOutcomeUnknown("reservation-a",
                new UncertainOutcome("unknown-a", NOW, UncertainReason.TIMEOUT)));
    }

    private static AiBudgetReservationState reserved(String maximum) {
        var state = state("100", "0", "0");
        return state.reserve("reservation-a", required(state.balance(), request(10), maximum), NOW).state();
    }

    private static AiBudgetReservationState dispatched(String maximum) {
        return reserved(maximum).dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1))).state();
    }

    private static AiBudgetReservationState state(String limit, String confirmed, String reserved) {
        return AiBudgetReservationState.open(new Balance("synthetic-budget", NOW.minusSeconds(60), NOW.plusSeconds(60),
                money(limit), money(confirmed), money(reserved)));
    }

    private static ReservationRequired required(Balance balance, Request request, String maximum) {
        return new ReservationRequired(balance,
                new CostCeiling(request, "price-a", NOW.plusSeconds(30), money(maximum)));
    }

    private static Request request(long sequence) {
        var observation = new PolicyObservation("synthetic-policy", 1, NOW.minusSeconds(120), new Readable(
                new SnapshotReference("synthetic-source", "raw-1", "a".repeat(64)),
                new ContentFingerprint("comparison-a", "b".repeat(64))));
        var revision = PolicyRevisionState.empty("synthetic-policy").consider(observation)
                .state().currentRevision().orElseThrow();
        return new Request(revision, Kind.SUMMARY, "generation-a", sequence, NOW.minusSeconds(30));
    }

    private static BigDecimal money(String amount) { return new BigDecimal(amount); }
}
