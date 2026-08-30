package kr.youthpolicymate.eligibility;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.Optional;

import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.NOT_MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.MISSING_USER_INPUT;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.UNRESOLVED_POLICY;

public final class AgeConditionEvaluator {

    private AgeConditionEvaluator() {
    }

    public static ConditionAssessment evaluate(AgeCondition condition, Optional<LocalDate> solarBirthDate) {
        Objects.requireNonNull(condition, "비교할 연령 조건이 필요합니다.");
        Objects.requireNonNull(solarBirthDate, "양력 생년월일의 존재 여부가 필요합니다.");

        return switch (condition) {
            case AgeCondition.Unresolved unresolved -> new ConditionAssessment(
                    unresolved.conditionId(), unresolved.appliedCondition(), Optional.empty(),
                    unresolved.referenceDate(), UNKNOWN, Optional.of(UNRESOLVED_POLICY),
                    unresolved.reason(), unresolved.evidence());
            case AgeCondition.CompletedYears completedYears -> evaluateCompletedYears(completedYears, solarBirthDate);
        };
    }

    private static ConditionAssessment evaluateCompletedYears(
            AgeCondition.CompletedYears condition, Optional<LocalDate> solarBirthDate
    ) {
        var referenceDate = Optional.of(condition.referenceDate());
        if (solarBirthDate.isEmpty()) {
            return new ConditionAssessment(condition.conditionId(), condition.appliedCondition(), Optional.empty(),
                    referenceDate, UNKNOWN, Optional.of(MISSING_USER_INPUT),
                    "연령 비교에 필요한 양력 생년월일이 없습니다.", condition.evidence());
        }

        var birthDate = solarBirthDate.orElseThrow();
        if (birthDate.isAfter(condition.referenceDate())) {
            return new ConditionAssessment(condition.conditionId(), condition.appliedCondition(), Optional.empty(),
                    referenceDate, UNKNOWN, Optional.of(UNRESOLVED_POLICY),
                    "생년월일이 정책 기준일보다 늦어 연령을 비교할 수 없습니다. 생년월일과 정책 기준일을 확인해야 합니다.",
                    condition.evidence());
        }

        // 정책 기준일까지 완전히 경과한 연수만 계산하며 오늘 날짜나 서버 시간대는 사용하지 않는다.
        int age = Period.between(birthDate, condition.referenceDate()).getYears();
        boolean meetsRange = age >= condition.minimumInclusive() && age <= condition.maximumInclusive();
        String explanation = meetsRange
                ? "기준일의 만 나이가 확인한 연령 범위에 포함됩니다."
                : age < condition.minimumInclusive()
                        ? "기준일의 만 나이가 최소 연령보다 낮습니다."
                        : "기준일의 만 나이가 최대 연령을 넘습니다.";

        return new ConditionAssessment(condition.conditionId(), condition.appliedCondition(),
                Optional.of("만 " + age + "세"), referenceDate, meetsRange ? MET : NOT_MET, Optional.empty(),
                explanation, condition.evidence());
    }
}
