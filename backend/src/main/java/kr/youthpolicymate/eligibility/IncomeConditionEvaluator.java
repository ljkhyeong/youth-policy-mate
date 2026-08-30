package kr.youthpolicymate.eligibility;

import java.util.Objects;
import java.util.Optional;

import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.NOT_MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.MISSING_USER_INPUT;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.UNRESOLVED_POLICY;

public final class IncomeConditionEvaluator {

    private IncomeConditionEvaluator() {
    }

    public static ConditionAssessment evaluate(IncomeCondition condition, Optional<IncomeAnswer> answer) {
        Objects.requireNonNull(condition, "비교할 소득 조건이 필요합니다.");
        Objects.requireNonNull(answer, "소득 답변의 존재 여부가 필요합니다.");

        return switch (condition) {
            case IncomeCondition.Unresolved unresolved -> new ConditionAssessment(
                    unresolved.conditionId(), unresolved.appliedCondition(), Optional.empty(),
                    unresolved.referenceDate(), UNKNOWN, Optional.of(UNRESOLVED_POLICY),
                    unresolved.reason(), unresolved.evidence());
            case IncomeCondition.NoRestriction noRestriction -> new ConditionAssessment(
                    noRestriction.conditionId(), noRestriction.appliedCondition(), Optional.empty(),
                    noRestriction.referenceDate(), MET, Optional.empty(),
                    "원문에서 소득 제한이 없음을 확인해 소득 답변 없이 이 항목을 충족합니다.", noRestriction.evidence());
            case IncomeCondition.RangeRequirement requirement -> evaluateRange(requirement, answer);
        };
    }

    private static ConditionAssessment evaluateRange(
            IncomeCondition.RangeRequirement condition, Optional<IncomeAnswer> answer
    ) {
        if (answer.isEmpty()) {
            return missingInput(condition, Optional.empty(), "정책이 정한 소득 정의·대상·기간에 맞는 금액 구간이 필요합니다.");
        }

        var suppliedAnswer = answer.orElseThrow();
        if (!condition.basis().equals(suppliedAnswer.basis())) {
            return missingInput(condition, Optional.empty(),
                    "답변의 정책·개정·소득 정의·대상·기간·적용 기준 또는 기준일이 현재 조건과 다릅니다. 현재 기준으로 다시 확인해야 합니다.");
        }
        if (suppliedAnswer instanceof IncomeAnswer.Unknown) {
            return missingInput(condition, Optional.of("모름"), "소득에 모름으로 답해 허용 구간과 비교할 수 없습니다.");
        }

        var range = ((IncomeAnswer.KnownRange) suppliedAnswer).range();
        var comparedValue = Optional.of(range.description());
        if (condition.allowedRange().contains(range)) {
            return new ConditionAssessment(condition.conditionId(), condition.appliedCondition(), comparedValue,
                    condition.basis().referenceDate(), MET, Optional.empty(),
                    "입력한 소득 구간 전체가 정책의 허용 구간에 포함됩니다.", condition.evidence());
        }
        if (condition.allowedRange().isDisjointFrom(range)) {
            return new ConditionAssessment(condition.conditionId(), condition.appliedCondition(), comparedValue,
                    condition.basis().referenceDate(), NOT_MET, Optional.empty(),
                    "입력한 소득 구간이 정책의 허용 구간과 겹치지 않습니다.", condition.evidence());
        }
        return missingInput(condition, comparedValue,
                "입력한 소득 구간이 정책의 허용 구간과 일부만 겹칩니다. 경계에 맞춰 더 좁은 구간을 확인해야 합니다.");
    }

    private static ConditionAssessment missingInput(
            IncomeCondition.RangeRequirement condition, Optional<String> comparedValue, String explanation
    ) {
        return new ConditionAssessment(condition.conditionId(), condition.appliedCondition(), comparedValue,
                condition.basis().referenceDate(), UNKNOWN, Optional.of(MISSING_USER_INPUT), explanation, condition.evidence());
    }
}
