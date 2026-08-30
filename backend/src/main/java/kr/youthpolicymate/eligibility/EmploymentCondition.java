package kr.youthpolicymate.eligibility;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public sealed interface EmploymentCondition {

    String conditionId();

    String appliedCondition();

    SourceEvidence evidence();

    record FactRequirement(
            EmploymentFact fact,
            boolean requiredToApply,
            SourceEvidence evidence
    ) implements EmploymentCondition {

        public FactRequirement {
            fact = Objects.requireNonNull(fact, "비교할 취업 사실이 필요합니다.");
            evidence = Objects.requireNonNull(evidence, "취업 조건의 원문 근거가 필요합니다.");
        }

        @Override
        public String conditionId() {
            return fact.conditionId();
        }

        @Override
        public String appliedCondition() {
            return fact.referenceDate() + " 기준 " + fact.definition() + " · "
                    + (requiredToApply ? "해당해야 함" : "해당하지 않아야 함");
        }
    }

    // 관련 본문과 예외까지 확인한 취업 제한 없음이다. 빈 원천 코드의 대체값이 아니다.
    record NoRestriction(
            String conditionId,
            Optional<LocalDate> referenceDate,
            SourceEvidence evidence
    ) implements EmploymentCondition {

        public NoRestriction {
            if (conditionId == null || conditionId.isBlank()) {
                throw new IllegalArgumentException("취업 조건 식별자가 필요합니다.");
            }
            referenceDate = Objects.requireNonNull(referenceDate, "취업 기준일의 존재 여부가 필요합니다.");
            evidence = Objects.requireNonNull(evidence, "취업 제한 없음의 원문 근거가 필요합니다.");
        }

        @Override
        public String appliedCondition() {
            return referenceDate.map(date -> date + " 기준 ").orElse("") + "취업 상태 제한 없음";
        }
    }

    record Unresolved(
            String conditionId,
            String appliedCondition,
            Optional<LocalDate> referenceDate,
            String reason,
            SourceEvidence evidence
    ) implements EmploymentCondition {

        public Unresolved {
            if (conditionId == null || conditionId.isBlank()
                    || appliedCondition == null || appliedCondition.isBlank()
                    || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("취업 조건 식별자, 확인할 조건과 미해석 이유가 필요합니다.");
            }
            referenceDate = Objects.requireNonNull(referenceDate, "취업 기준일의 존재 여부가 필요합니다.");
            evidence = Objects.requireNonNull(evidence, "미해석 취업 조건의 원문 근거가 필요합니다.");
        }
    }
}
