package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.PolicyAiResult.Request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

// 한도·비용 미확인을 0원으로 대체하지 않는다. 실제 가격 계산·예약·정산은 별도다.
public record AiRequestBudget(Optional<Balance> balance, Optional<CostCeiling> costCeiling) {
    public AiRequestBudget {
        Objects.requireNonNull(balance, "AI 예산 설정 여부가 필요합니다.");
        Objects.requireNonNull(costCeiling, "요청 최대 비용 확인 여부가 필요합니다.");
    }

    public record Balance(String budgetId, Instant startsAt, Instant endsAt, BigDecimal limitWon,
                          BigDecimal confirmedWon, BigDecimal reservedWon) {
        public Balance {
            requireText(budgetId, "내부 AI 예산 식별자가 필요합니다.");
            Objects.requireNonNull(startsAt, "예산 시작 시각이 필요합니다.");
            Objects.requireNonNull(endsAt, "예산 종료 시각이 필요합니다.");
            if (!startsAt.isBefore(endsAt)) throw new IllegalArgumentException("예산 종료는 시작보다 늦어야 합니다.");
            limitWon = nonNegative(limitWon);
            confirmedWon = nonNegative(confirmedWon);
            reservedWon = nonNegative(reservedWon);
        }

        public BigDecimal remainingWon() { return limitWon.subtract(confirmedWon).subtract(reservedWon); }
        public boolean contains(Instant at) { return !at.isBefore(startsAt) && at.isBefore(endsAt); }
    }

    public record CostCeiling(Request request, String pricingVersion, Instant validUntil, BigDecimal maximumWon) {
        public CostCeiling {
            Objects.requireNonNull(request, "비용을 산정한 예정 요청이 필요합니다.");
            requireText(pricingVersion, "가격 산정 버전이 필요합니다.");
            Objects.requireNonNull(validUntil, "비용 유효 종료 시각이 필요합니다.");
            if (!request.preparedAt().isBefore(validUntil)) {
                throw new IllegalArgumentException("비용 유효 종료는 요청 준비보다 늦어야 합니다.");
            }
            maximumWon = nonNegative(maximumWon);
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
