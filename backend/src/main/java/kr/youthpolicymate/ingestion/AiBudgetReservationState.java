package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiRequestBudget.Balance;
import kr.youthpolicymate.ingestion.AiRequestBudget.CostCeiling;
import kr.youthpolicymate.ingestion.PolicyAiRequestAdmission.ReservationRequired;
import kr.youthpolicymate.ingestion.PolicyAiResult.Request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// 외부 호출을 수행하지 않는다. DB에서는 잔액 확인과 예약 저장을 한 트랜잭션으로 적용해야 한다.
public final class AiBudgetReservationState {
    private final Balance balance;
    private final Map<String, Reservation> reservations;

    private AiBudgetReservationState(Balance balance, Map<String, Reservation> reservations) {
        this.balance = balance;
        this.reservations = Map.copyOf(reservations);
    }

    public static AiBudgetReservationState open(Balance balance) {
        return new AiBudgetReservationState(Objects.requireNonNull(balance, "AI 예산 잔액이 필요합니다."), Map.of());
    }

    public Transition reserve(String reservationId, ReservationRequired required, Instant at) {
        requireText(reservationId, "AI 요청 예약 식별자가 필요합니다.");
        Objects.requireNonNull(required, "사전 판단의 예약 필요 결과가 필요합니다.");
        Objects.requireNonNull(at, "예약 시각이 필요합니다.");
        var requiredBalance = Objects.requireNonNull(required.balance(), "예약 판단에 사용한 AI 예산 잔액이 필요합니다.");
        var cost = Objects.requireNonNull(required.cost(), "요청 최대 비용이 필요합니다.");
        if (at.isBefore(cost.request().preparedAt())) throw new IllegalArgumentException("요청 준비 전에 예약할 수 없습니다.");
        if (!balance.budgetId().equals(requiredBalance.budgetId())) return unchanged(Decision.STALE_BALANCE);

        var sameId = reservations.get(reservationId);
        if (sameId != null) {
            return unchanged(sameId.hold().matches(cost) ? Decision.REPLAYED : Decision.RESERVATION_ID_CONFLICT);
        }
        if (reservations.values().stream().anyMatch(reservation -> reservation.hold().request().equals(cost.request()))) {
            return unchanged(Decision.REQUEST_ALREADY_RESERVED);
        }
        if (!balance.equals(requiredBalance)) return unchanged(Decision.STALE_BALANCE);
        if (!balance.contains(at)) return unchanged(Decision.BUDGET_PERIOD_INACTIVE);
        if (!at.isBefore(cost.validUntil())) return unchanged(Decision.COST_EXPIRED);
        if (balance.remainingWon().signum() <= 0 || cost.maximumWon().compareTo(balance.remainingWon()) > 0) {
            return unchanged(Decision.BUDGET_LIMIT);
        }

        var hold = new ReservationHold(reservationId, cost.request(), cost.pricingVersion(), cost.maximumWon(), at);
        return changed(Decision.RESERVED, new Held(hold), balance.confirmedWon(),
                balance.reservedWon().add(hold.maximumWon()));
    }

    public Transition dispatch(String reservationId, Dispatch dispatch) {
        var reservation = find(reservationId);
        if (reservation.isEmpty()) return unchanged(Decision.RESERVATION_NOT_FOUND);
        var current = reservation.orElseThrow();
        Objects.requireNonNull(dispatch, "외부 호출 식별 정보가 필요합니다.");

        if (current instanceof Held held) {
            requireNotBefore(dispatch.dispatchedAt(), held.hold().reservedAt(), "외부 호출은 예약보다 빠를 수 없습니다.");
            return replace(Decision.DISPATCHED, new Dispatched(held.hold(), dispatch));
        }
        var previous = dispatchOf(current);
        if (previous.isPresent()) {
            return unchanged(previous.orElseThrow().equals(dispatch) ? Decision.REPLAYED : Decision.DISPATCH_CONFLICT);
        }
        return unchanged(Decision.INVALID_STATE);
    }

    public Transition markOutcomeUnknown(String reservationId, UncertainOutcome uncertain) {
        var reservation = find(reservationId);
        if (reservation.isEmpty()) return unchanged(Decision.RESERVATION_NOT_FOUND);
        var current = reservation.orElseThrow();
        Objects.requireNonNull(uncertain, "결과 미확인 정보가 필요합니다.");

        if (current instanceof Dispatched dispatched) {
            requireNotBefore(uncertain.recordedAt(), dispatched.dispatch().dispatchedAt(),
                    "결과 미확인은 외부 호출보다 빠를 수 없습니다.");
            return replace(Decision.OUTCOME_UNKNOWN, new OutcomeUnknown(dispatched.hold(), dispatched.dispatch(), uncertain));
        }
        if (current instanceof OutcomeUnknown unknown) {
            return unchanged(unknown.uncertain().equals(uncertain) ? Decision.REPLAYED : Decision.OUTCOME_ALREADY_UNKNOWN);
        }
        return unchanged(current instanceof TerminalReservation ? Decision.TERMINAL_CONFLICT : Decision.INVALID_STATE);
    }

    public Transition settle(String reservationId, ChargeConfirmation confirmation) {
        var reservation = find(reservationId);
        if (reservation.isEmpty()) return unchanged(Decision.RESERVATION_NOT_FOUND);
        var current = reservation.orElseThrow();
        Objects.requireNonNull(confirmation, "청구 확인 정보가 필요합니다.");

        if (current instanceof Settled settled) {
            return unchanged(settled.confirmation().equals(confirmation) ? Decision.REPLAYED : Decision.TERMINAL_CONFLICT);
        }
        if (current instanceof TerminalReservation) return unchanged(Decision.TERMINAL_CONFLICT);
        var dispatch = dispatchOf(current);
        if (dispatch.isEmpty()) return unchanged(Decision.INVALID_STATE);
        requireNotBefore(confirmation.confirmedAt(), lastActivityAt(current),
                "청구 확인은 현재 예약 상태보다 빠를 수 없습니다.");

        var hold = current.hold();
        var next = new Settled(hold, dispatch.orElseThrow(), confirmation);
        var decision = confirmation.actualWon().compareTo(hold.maximumWon()) > 0
                ? Decision.SETTLED_OVER_RESERVATION : Decision.SETTLED;
        return changed(decision, next, balance.confirmedWon().add(confirmation.actualWon()),
                balance.reservedWon().subtract(hold.maximumWon()));
    }

    public Transition cancelBeforeDispatch(String reservationId, Cancellation cancellation) {
        var reservation = find(reservationId);
        if (reservation.isEmpty()) return unchanged(Decision.RESERVATION_NOT_FOUND);
        var current = reservation.orElseThrow();
        Objects.requireNonNull(cancellation, "호출 전 취소 정보가 필요합니다.");

        if (current instanceof Cancelled cancelled) {
            return unchanged(cancelled.cancellation().equals(cancellation) ? Decision.REPLAYED : Decision.TERMINAL_CONFLICT);
        }
        if (!(current instanceof Held held)) {
            return unchanged(current instanceof TerminalReservation ? Decision.TERMINAL_CONFLICT : Decision.INVALID_STATE);
        }
        requireNotBefore(cancellation.cancelledAt(), held.hold().reservedAt(), "호출 전 취소는 예약보다 빠를 수 없습니다.");
        return changed(Decision.RELEASED_BEFORE_DISPATCH, new Cancelled(held.hold(), cancellation),
                balance.confirmedWon(), balance.reservedWon().subtract(held.hold().maximumWon()));
    }

    public Transition releaseAfterNoCharge(String reservationId, NoChargeConfirmation confirmation) {
        var reservation = find(reservationId);
        if (reservation.isEmpty()) return unchanged(Decision.RESERVATION_NOT_FOUND);
        var current = reservation.orElseThrow();
        Objects.requireNonNull(confirmation, "무과금 확인 정보가 필요합니다.");

        if (current instanceof ReleasedNoCharge released) {
            return unchanged(released.confirmation().equals(confirmation) ? Decision.REPLAYED : Decision.TERMINAL_CONFLICT);
        }
        if (current instanceof TerminalReservation) return unchanged(Decision.TERMINAL_CONFLICT);
        var dispatch = dispatchOf(current);
        if (dispatch.isEmpty()) return unchanged(Decision.INVALID_STATE);
        requireNotBefore(confirmation.confirmedAt(), lastActivityAt(current),
                "무과금 확인은 현재 예약 상태보다 빠를 수 없습니다.");

        var hold = current.hold();
        return changed(Decision.RELEASED_NO_CHARGE,
                new ReleasedNoCharge(hold, dispatch.orElseThrow(), confirmation), balance.confirmedWon(),
                balance.reservedWon().subtract(hold.maximumWon()));
    }

    public Balance balance() { return balance; }
    public Map<String, Reservation> reservations() { return reservations; }
    public Optional<Reservation> reservation(String reservationId) {
        requireText(reservationId, "조회할 AI 요청 예약 식별자가 필요합니다.");
        return Optional.ofNullable(reservations.get(reservationId));
    }

    private Optional<Reservation> find(String reservationId) {
        requireText(reservationId, "AI 요청 예약 식별자가 필요합니다.");
        return Optional.ofNullable(reservations.get(reservationId));
    }

    private Transition unchanged(Decision decision) { return new Transition(decision, this); }

    private Transition replace(Decision decision, Reservation reservation) {
        return changed(decision, reservation, balance.confirmedWon(), balance.reservedWon());
    }

    private Transition changed(Decision decision, Reservation reservation, BigDecimal confirmedWon,
                               BigDecimal reservedWon) {
        var nextReservations = new LinkedHashMap<>(reservations);
        nextReservations.put(reservation.hold().reservationId(), reservation);
        var nextBalance = new Balance(balance.budgetId(), balance.startsAt(), balance.endsAt(), balance.limitWon(),
                confirmedWon, reservedWon);
        return new Transition(decision, new AiBudgetReservationState(nextBalance, nextReservations));
    }

    private static Optional<Dispatch> dispatchOf(Reservation reservation) {
        if (reservation instanceof Dispatched value) return Optional.of(value.dispatch());
        if (reservation instanceof OutcomeUnknown value) return Optional.of(value.dispatch());
        if (reservation instanceof Settled value) return Optional.of(value.dispatch());
        if (reservation instanceof ReleasedNoCharge value) return Optional.of(value.dispatch());
        return Optional.empty();
    }

    private static Instant lastActivityAt(Reservation reservation) {
        if (reservation instanceof OutcomeUnknown value) return value.uncertain().recordedAt();
        return dispatchOf(reservation).orElseThrow().dispatchedAt();
    }

    private static void requireNotBefore(Instant actual, Instant minimum, String message) {
        if (actual.isBefore(minimum)) throw new IllegalArgumentException(message);
    }

    private static BigDecimal nonNegative(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("원화 금액은 0 이상이어야 합니다.");
        return amount.stripTrailingZeros();
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

    public enum Decision {
        RESERVED, REPLAYED, RESERVATION_ID_CONFLICT, REQUEST_ALREADY_RESERVED, STALE_BALANCE,
        BUDGET_PERIOD_INACTIVE, COST_EXPIRED, BUDGET_LIMIT, RESERVATION_NOT_FOUND, DISPATCHED,
        DISPATCH_CONFLICT, OUTCOME_UNKNOWN, OUTCOME_ALREADY_UNKNOWN, SETTLED, SETTLED_OVER_RESERVATION,
        RELEASED_BEFORE_DISPATCH, RELEASED_NO_CHARGE, INVALID_STATE, TERMINAL_CONFLICT
    }

    public record Transition(Decision decision, AiBudgetReservationState state) {}

    public enum Phase {
        HELD, DISPATCHED, OUTCOME_UNKNOWN, SETTLED, CANCELLED, RELEASED_NO_CHARGE;

        public boolean isTerminal() {
            return this == SETTLED || this == CANCELLED || this == RELEASED_NO_CHARGE;
        }
    }

    public record ReservationHold(String reservationId, Request request, String pricingVersion,
                                  BigDecimal maximumWon, Instant reservedAt) {
        public ReservationHold {
            requireText(reservationId, "AI 요청 예약 식별자가 필요합니다.");
            Objects.requireNonNull(request, "예약한 AI 예정 요청이 필요합니다.");
            requireText(pricingVersion, "예약에 사용한 가격 산정 버전이 필요합니다.");
            maximumWon = nonNegative(maximumWon);
            Objects.requireNonNull(reservedAt, "예약 시각이 필요합니다.");
            if (reservedAt.isBefore(request.preparedAt())) throw new IllegalArgumentException("요청 준비 전에 예약할 수 없습니다.");
        }

        private boolean matches(CostCeiling cost) {
            return request.equals(cost.request()) && pricingVersion.equals(cost.pricingVersion())
                    && maximumWon.compareTo(cost.maximumWon()) == 0;
        }
    }

    public sealed interface Reservation permits Held, Dispatched, OutcomeUnknown, TerminalReservation {
        ReservationHold hold();
        Phase phase();
    }

    public sealed interface TerminalReservation extends Reservation
            permits Settled, Cancelled, ReleasedNoCharge {}

    public record Held(ReservationHold hold) implements Reservation {
        public Held { Objects.requireNonNull(hold, "예약 금액 정보가 필요합니다."); }
        @Override public Phase phase() { return Phase.HELD; }
    }

    public record Dispatched(ReservationHold hold, Dispatch dispatch) implements Reservation {
        public Dispatched {
            Objects.requireNonNull(hold, "예약 금액 정보가 필요합니다.");
            Objects.requireNonNull(dispatch, "외부 호출 정보가 필요합니다.");
        }
        @Override public Phase phase() { return Phase.DISPATCHED; }
    }

    public record OutcomeUnknown(ReservationHold hold, Dispatch dispatch, UncertainOutcome uncertain)
            implements Reservation {
        public OutcomeUnknown {
            Objects.requireNonNull(hold, "예약 금액 정보가 필요합니다.");
            Objects.requireNonNull(dispatch, "외부 호출 정보가 필요합니다.");
            Objects.requireNonNull(uncertain, "결과 미확인 정보가 필요합니다.");
        }
        @Override public Phase phase() { return Phase.OUTCOME_UNKNOWN; }
    }

    public record Settled(ReservationHold hold, Dispatch dispatch, ChargeConfirmation confirmation)
            implements TerminalReservation {
        public Settled {
            Objects.requireNonNull(hold, "예약 금액 정보가 필요합니다.");
            Objects.requireNonNull(dispatch, "외부 호출 정보가 필요합니다.");
            Objects.requireNonNull(confirmation, "청구 확인 정보가 필요합니다.");
        }
        @Override public Phase phase() { return Phase.SETTLED; }
    }

    public record Cancelled(ReservationHold hold, Cancellation cancellation) implements TerminalReservation {
        public Cancelled {
            Objects.requireNonNull(hold, "예약 금액 정보가 필요합니다.");
            Objects.requireNonNull(cancellation, "호출 전 취소 정보가 필요합니다.");
        }
        @Override public Phase phase() { return Phase.CANCELLED; }
    }

    public record ReleasedNoCharge(ReservationHold hold, Dispatch dispatch, NoChargeConfirmation confirmation)
            implements TerminalReservation {
        public ReleasedNoCharge {
            Objects.requireNonNull(hold, "예약 금액 정보가 필요합니다.");
            Objects.requireNonNull(dispatch, "외부 호출 정보가 필요합니다.");
            Objects.requireNonNull(confirmation, "무과금 확인 정보가 필요합니다.");
        }
        @Override public Phase phase() { return Phase.RELEASED_NO_CHARGE; }
    }

    public record Dispatch(String dispatchId, Instant dispatchedAt) {
        public Dispatch {
            requireText(dispatchId, "외부 호출 식별자가 필요합니다.");
            Objects.requireNonNull(dispatchedAt, "외부 호출 시각이 필요합니다.");
        }
    }

    public record UncertainOutcome(String observationId, Instant recordedAt, UncertainReason reason) {
        public UncertainOutcome {
            requireText(observationId, "결과 미확인 기록 식별자가 필요합니다.");
            Objects.requireNonNull(recordedAt, "결과 미확인 기록 시각이 필요합니다.");
            Objects.requireNonNull(reason, "결과 미확인 사유가 필요합니다.");
        }
    }

    public enum UncertainReason { TIMEOUT, CONNECTION_LOST, PROVIDER_STATUS_UNAVAILABLE }

    public record ChargeConfirmation(String confirmationId, Instant confirmedAt, BigDecimal actualWon) {
        public ChargeConfirmation {
            requireText(confirmationId, "청구 확인 식별자가 필요합니다.");
            Objects.requireNonNull(confirmedAt, "청구 확인 시각이 필요합니다.");
            actualWon = nonNegative(actualWon);
        }
    }

    public record Cancellation(String cancellationId, Instant cancelledAt) {
        public Cancellation {
            requireText(cancellationId, "호출 전 취소 식별자가 필요합니다.");
            Objects.requireNonNull(cancelledAt, "호출 전 취소 시각이 필요합니다.");
        }
    }

    public record NoChargeConfirmation(String confirmationId, Instant confirmedAt) {
        public NoChargeConfirmation {
            requireText(confirmationId, "무과금 확인 식별자가 필요합니다.");
            Objects.requireNonNull(confirmedAt, "무과금 확인 시각이 필요합니다.");
        }
    }
}
