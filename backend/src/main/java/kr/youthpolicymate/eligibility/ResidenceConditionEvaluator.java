package kr.youthpolicymate.eligibility;

import java.util.Objects;
import java.util.Optional;

import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.NOT_MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.MISSING_USER_INPUT;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.UNRESOLVED_POLICY;

public final class ResidenceConditionEvaluator {

    private ResidenceConditionEvaluator() {
    }

    public static ConditionAssessment evaluate(ResidenceCondition condition, Optional<SeoulResidence> residence) {
        Objects.requireNonNull(condition, "비교할 거주 조건이 필요합니다.");
        Objects.requireNonNull(residence, "주민등록상 거주 정보의 존재 여부가 필요합니다.");

        return switch (condition) {
            case ResidenceCondition.Unresolved unresolved -> new ConditionAssessment(
                    unresolved.conditionId(), unresolved.appliedCondition(), Optional.empty(),
                    unresolved.referenceDate(), UNKNOWN, Optional.of(UNRESOLVED_POLICY),
                    unresolved.reason(), unresolved.evidence());
            case ResidenceCondition.RegisteredIn registeredIn -> evaluateRegisteredIn(registeredIn, residence);
        };
    }

    private static ConditionAssessment evaluateRegisteredIn(
            ResidenceCondition.RegisteredIn condition, Optional<SeoulResidence> residence
    ) {
        var referenceDate = Optional.of(condition.referenceDate());
        if (residence.isEmpty()) {
            return new ConditionAssessment(condition.conditionId(), condition.appliedCondition(), Optional.empty(),
                    referenceDate, UNKNOWN, Optional.of(MISSING_USER_INPUT),
                    "정책 기준일의 주민등록상 거주 자치구가 필요합니다.", condition.evidence());
        }

        var suppliedResidence = residence.orElseThrow();
        var comparedValue = Optional.of(suppliedResidence.description());
        // 현재 주소로 과거 거주를 추정하거나 하루 차이의 기준일도 임의로 맞추지 않는다.
        if (!condition.referenceDate().equals(suppliedResidence.asOfDate())) {
            return new ConditionAssessment(condition.conditionId(), condition.appliedCondition(), comparedValue,
                    referenceDate, UNKNOWN, Optional.of(MISSING_USER_INPUT),
                    "입력된 거주지는 " + suppliedResidence.asOfDate() + " 기준입니다. 정책 기준일 "
                            + condition.referenceDate() + "의 주민등록상 거주 자치구가 필요합니다.", condition.evidence());
        }

        boolean meetsArea = switch (condition.area()) {
            case NATIONWIDE, SEOUL -> true;
            case SEOUL_DISTRICTS -> condition.districts().contains(suppliedResidence.district());
        };
        String explanation = meetsArea
                ? "정책 기준일의 주민등록상 거주지가 확인한 허용 지역에 포함됩니다."
                : "정책 기준일의 주민등록상 거주 자치구가 확인한 허용 자치구에 포함되지 않습니다.";

        return new ConditionAssessment(condition.conditionId(), condition.appliedCondition(), comparedValue,
                referenceDate, meetsArea ? MET : NOT_MET, Optional.empty(), explanation, condition.evidence());
    }
}
