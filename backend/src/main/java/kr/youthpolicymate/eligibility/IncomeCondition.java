package kr.youthpolicymate.eligibility;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public sealed interface IncomeCondition {

    String conditionId();

    String appliedCondition();

    SourceEvidence evidence();

    record RangeRequirement(
            IncomeBasis basis,
            IncomeRange allowedRange,
            String amountBasis,
            SourceEvidence evidence
    ) implements IncomeCondition {
        public RangeRequirement {
            basis = Objects.requireNonNull(basis, "소득 비교 기준이 필요합니다.");
            allowedRange = Objects.requireNonNull(allowedRange, "확인한 소득 허용 구간이 필요합니다.");
            if (amountBasis == null || amountBasis.isBlank()) {
                throw new IllegalArgumentException("원문 금액 단위와 원화 변환 여부를 확인한 근거가 필요합니다.");
            }
            evidence = Objects.requireNonNull(evidence, "소득 조건의 원문 근거가 필요합니다.");
        }

        @Override
        public String conditionId() {
            return basis.conditionId();
        }

        @Override
        public String appliedCondition() {
            return basis.description() + " · 허용 구간: " + allowedRange.description() + " · 금액 근거: " + amountBasis;
        }
    }

    // 관련 본문·예외에서 소득 제한 없음을 확인한 경우에만 사용한다.
    record NoRestriction(
            String conditionId,
            Optional<LocalDate> referenceDate,
            SourceEvidence evidence
    ) implements IncomeCondition {
        public NoRestriction {
            if (conditionId == null || conditionId.isBlank()) {
                throw new IllegalArgumentException("소득 조건 식별자가 필요합니다.");
            }
            referenceDate = Objects.requireNonNull(referenceDate, "소득 기준일의 존재 여부가 필요합니다.");
            evidence = Objects.requireNonNull(evidence, "소득 제한 없음의 원문 근거가 필요합니다.");
        }

        @Override
        public String appliedCondition() {
            return referenceDate.map(date -> date + " 기준 ").orElse("") + "소득 제한 없음";
        }
    }

    record Unresolved(
            String conditionId,
            String appliedCondition,
            Optional<LocalDate> referenceDate,
            String reason,
            SourceEvidence evidence
    ) implements IncomeCondition {
        public Unresolved {
            if (conditionId == null || conditionId.isBlank()
                    || appliedCondition == null || appliedCondition.isBlank()
                    || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("소득 조건 식별자, 확인할 조건과 미해석 이유가 필요합니다.");
            }
            referenceDate = Objects.requireNonNull(referenceDate, "소득 기준일의 존재 여부가 필요합니다.");
            evidence = Objects.requireNonNull(evidence, "미해석 소득 조건의 원문 근거가 필요합니다.");
        }
    }
}
