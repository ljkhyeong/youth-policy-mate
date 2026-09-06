package kr.youthpolicymate.ingestion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public final class AiBudgetReservationState {
    private AiBudgetReservationState() {}

    public enum Phase {
        HELD, DISPATCHED, OUTCOME_UNKNOWN, SETTLED, CANCELLED, RELEASED_NO_CHARGE;

        public boolean isTerminal() {
            return this == SETTLED || this == CANCELLED || this == RELEASED_NO_CHARGE;
        }
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

    private static BigDecimal nonNegative(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("원화 금액은 0 이상이어야 합니다.");
        return amount.stripTrailingZeros();
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }
}
