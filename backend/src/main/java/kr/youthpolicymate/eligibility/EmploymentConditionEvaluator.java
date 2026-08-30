package kr.youthpolicymate.eligibility;

import java.util.Objects;
import java.util.Optional;

import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.NOT_MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.MISSING_USER_INPUT;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.UNRESOLVED_POLICY;
import static kr.youthpolicymate.eligibility.EmploymentAnswer.Response.APPLIES;
import static kr.youthpolicymate.eligibility.EmploymentAnswer.Response.DOES_NOT_APPLY;

public final class EmploymentConditionEvaluator {

    private EmploymentConditionEvaluator() {
    }

    public static ConditionAssessment evaluate(EmploymentCondition condition, Optional<EmploymentAnswer> answer) {
        Objects.requireNonNull(condition, "비교할 취업 조건이 필요합니다.");
        Objects.requireNonNull(answer, "취업 답변의 존재 여부가 필요합니다.");

        return switch (condition) {
            case EmploymentCondition.Unresolved unresolved -> new ConditionAssessment(
                    unresolved.conditionId(), unresolved.appliedCondition(), Optional.empty(),
                    unresolved.referenceDate(), UNKNOWN, Optional.of(UNRESOLVED_POLICY),
                    unresolved.reason(), unresolved.evidence());
            case EmploymentCondition.NoRestriction noRestriction -> new ConditionAssessment(
                    noRestriction.conditionId(), noRestriction.appliedCondition(), Optional.empty(),
                    noRestriction.referenceDate(), MET, Optional.empty(),
                    "원문에서 취업 상태에 제한이 없음을 확인해 취업 답변 없이 이 항목을 충족합니다.",
                    noRestriction.evidence());
            case EmploymentCondition.FactRequirement requirement -> evaluateFact(requirement, answer);
        };
    }

    private static ConditionAssessment evaluateFact(
            EmploymentCondition.FactRequirement condition, Optional<EmploymentAnswer> answer
    ) {
        var referenceDate = Optional.of(condition.fact().referenceDate());
        if (answer.isEmpty()) {
            return new ConditionAssessment(condition.conditionId(), condition.appliedCondition(), Optional.empty(),
                    referenceDate, UNKNOWN, Optional.of(MISSING_USER_INPUT),
                    "정책에서 정의한 취업 사실에 대한 기준일의 답변이 필요합니다.", condition.evidence());
        }

        var suppliedAnswer = answer.orElseThrow();
        // 개정·정의·날짜가 다른 답변을 현재 조건의 답변으로 다시 붙이지 않는다.
        if (!condition.fact().equals(suppliedAnswer.fact())) {
            return new ConditionAssessment(condition.conditionId(), condition.appliedCondition(), Optional.empty(),
                    referenceDate, UNKNOWN, Optional.of(MISSING_USER_INPUT),
                    "입력한 답변의 정책·개정·조건 정의 또는 기준일이 현재 조건과 다릅니다. 현재 조건에 다시 답변해야 합니다.",
                    condition.evidence());
        }

        var comparedValue = Optional.of(suppliedAnswer.response().label());
        if (suppliedAnswer.response() == EmploymentAnswer.Response.UNKNOWN) {
            return new ConditionAssessment(condition.conditionId(), condition.appliedCondition(), comparedValue,
                    referenceDate, UNKNOWN, Optional.of(MISSING_USER_INPUT),
                    "취업 사실에 모름으로 답해 요구 조건과 비교할 수 없습니다.", condition.evidence());
        }

        var requiredResponse = condition.requiredToApply() ? APPLIES : DOES_NOT_APPLY;
        boolean meetsRequirement = suppliedAnswer.response() == requiredResponse;
        String explanation = meetsRequirement
                ? "기준일의 취업 답변이 정책에서 요구하는 해당 여부와 일치합니다."
                : "기준일의 취업 답변이 정책에서 요구하는 해당 여부와 일치하지 않습니다.";

        return new ConditionAssessment(condition.conditionId(), condition.appliedCondition(), comparedValue,
                referenceDate, meetsRequirement ? MET : NOT_MET, Optional.empty(), explanation, condition.evidence());
    }
}
