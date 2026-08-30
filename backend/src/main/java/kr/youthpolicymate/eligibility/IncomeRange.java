package kr.youthpolicymate.eligibility;

import java.math.BigDecimal;
import java.util.Objects;

// 단위와 변환 근거를 확인한 원화 구간이다. 원천의 빈 경계를 Unbounded로 바꾸지 않는다.
public record IncomeRange(Boundary lower, Boundary upper) {

    public IncomeRange {
        lower = Objects.requireNonNull(lower, "소득 하한 또는 확인한 하한 제한 없음이 필요합니다.");
        upper = Objects.requireNonNull(upper, "소득 상한 또는 확인한 상한 제한 없음이 필요합니다.");
        if (lower instanceof Unbounded && upper instanceof Unbounded) {
            throw new IllegalArgumentException("금액 구간에는 적어도 한쪽 경계가 필요합니다. 모름과 소득 제한 없음은 별도로 표현합니다.");
        }
        if (lower instanceof Amount low && upper instanceof Amount high) {
            int comparison = low.value().compareTo(high.value());
            if (comparison > 0 || (comparison == 0 && (!low.inclusive() || !high.inclusive()))) {
                throw new IllegalArgumentException("소득 구간의 하한이 상한보다 크거나 구간에 포함되는 금액이 없습니다.");
            }
        }
    }

    public static IncomeRange exact(BigDecimal amount) {
        return new IncomeRange(new Amount(amount, true), new Amount(amount, true));
    }

    public boolean contains(IncomeRange other) {
        return containsLower(other.lower()) && containsUpper(other.upper());
    }

    public boolean isDisjointFrom(IncomeRange other) {
        return endsBefore(upper, other.lower()) || endsBefore(other.upper(), lower);
    }

    private boolean containsLower(Boundary other) {
        if (lower instanceof Unbounded) {
            return true;
        }
        if (other instanceof Unbounded) {
            return false;
        }
        var limit = (Amount) lower;
        var candidate = (Amount) other;
        int comparison = candidate.value().compareTo(limit.value());
        return comparison > 0 || (comparison == 0 && (limit.inclusive() || !candidate.inclusive()));
    }

    private boolean containsUpper(Boundary other) {
        if (upper instanceof Unbounded) {
            return true;
        }
        if (other instanceof Unbounded) {
            return false;
        }
        var limit = (Amount) upper;
        var candidate = (Amount) other;
        int comparison = candidate.value().compareTo(limit.value());
        return comparison < 0 || (comparison == 0 && (limit.inclusive() || !candidate.inclusive()));
    }

    private static boolean endsBefore(Boundary upper, Boundary lower) {
        if (upper instanceof Amount high && lower instanceof Amount low) {
            int comparison = high.value().compareTo(low.value());
            return comparison < 0 || (comparison == 0 && (!high.inclusive() || !low.inclusive()));
        }
        return false;
    }

    public String description() {
        if (lower instanceof Amount low && upper instanceof Amount high
                && low.value().compareTo(high.value()) == 0) {
            return low.value().toPlainString() + "원";
        }
        String lowerText = lower instanceof Amount low
                ? low.value().toPlainString() + "원 " + (low.inclusive() ? "이상" : "초과") : "하한 제한 없음";
        String upperText = upper instanceof Amount high
                ? high.value().toPlainString() + "원 " + (high.inclusive() ? "이하" : "미만") : "상한 제한 없음";
        return lowerText + " · " + upperText;
    }

    public sealed interface Boundary permits Amount, Unbounded {
    }

    public record Amount(BigDecimal value, boolean inclusive) implements Boundary {
        public Amount {
            value = Objects.requireNonNull(value, "명시적인 소득 금액이 필요합니다.").stripTrailingZeros();
        }
    }

    public enum Unbounded implements Boundary {
        INSTANCE
    }
}
