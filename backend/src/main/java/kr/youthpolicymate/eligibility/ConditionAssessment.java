package kr.youthpolicymate.eligibility;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record ConditionAssessment(
        String conditionId,
        String appliedCondition,
        Optional<String> comparedValue,
        Optional<LocalDate> referenceDate,
        Outcome outcome,
        Optional<Uncertainty> uncertainty,
        String explanation,
        SourceEvidence evidence
) {

    public ConditionAssessment {
        if (conditionId == null || conditionId.isBlank()
                || appliedCondition == null || appliedCondition.isBlank()
                || explanation == null || explanation.isBlank()) {
            throw new IllegalArgumentException("조건 식별자, 적용 조건과 판정 이유가 필요합니다.");
        }
        comparedValue = Objects.requireNonNull(comparedValue, "비교값의 존재 여부가 필요합니다.");
        referenceDate = Objects.requireNonNull(referenceDate, "정책 기준일의 존재 여부가 필요합니다.");
        outcome = Objects.requireNonNull(outcome, "항목 판정 결과가 필요합니다.");
        uncertainty = Objects.requireNonNull(uncertainty, "미확인 원인의 존재 여부가 필요합니다.");
        if ((outcome == Outcome.UNKNOWN) != uncertainty.isPresent()) {
            throw new IllegalArgumentException("미확인 항목에는 원인 구분이 필요하며 확정 항목에는 미확인 원인을 둘 수 없습니다.");
        }
        evidence = Objects.requireNonNull(evidence, "항목 판정 근거가 필요합니다.");
    }

    public enum Outcome {
        MET,
        NOT_MET,
        UNKNOWN
    }

    public enum Uncertainty {
        MISSING_USER_INPUT,
        UNRESOLVED_POLICY
    }
}
