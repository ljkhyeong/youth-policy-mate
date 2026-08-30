package kr.youthpolicymate.eligibility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.NOT_MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.MISSING_USER_INPUT;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.UNRESOLVED_POLICY;
import static kr.youthpolicymate.eligibility.EligibilityStatus.NEEDS_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AgeConditionEvaluatorTest {

    private static final SourceEvidence EVIDENCE = new SourceEvidence(
            "test-age-source-revision-1", "테스트용 연령 조건", Optional.of("정책 기준일과 연령 범위를 지정한 인공 자료"));
    private static final EvaluationBasis BASIS = new EvaluationBasis(
            "test-policy", "revision-1", "age-v1", Instant.parse("2026-08-30T01:00:00Z"));

    @ParameterizedTest(name = "기준일 {0}: 만 {1}세 → {2}")
    @CsvSource({
            "2025-08-31, 24, NOT_MET",
            "2025-09-01, 25, MET",
            "2029-08-31, 28, MET",
            "2029-09-01, 29, MET",
            "2030-08-31, 29, MET",
            "2030-09-01, 30, NOT_MET"
    })
    @DisplayName("정책 기준일의 생일 경계와 최소·최대 연령 포함 여부를 비교한다")
    void comparesCompletedYearsAtInclusiveBoundaries(String referenceDate, int age, ConditionAssessment.Outcome outcome) {
        var condition = new AgeCondition.CompletedYears("age", 25, 29, LocalDate.parse(referenceDate), EVIDENCE);

        var assessment = AgeConditionEvaluator.evaluate(condition, Optional.of(LocalDate.of(2000, 9, 1)));

        assertThat(assessment.outcome()).isEqualTo(outcome);
        assertThat(assessment.comparedValue()).contains("만 " + age + "세");
        assertThat(assessment.uncertainty()).isEmpty();
    }

    @ParameterizedTest(name = "윤일 생일의 기준일 {0}: 만 {1}세 → {2}")
    @CsvSource({
            "2020-02-28, 19, NOT_MET",
            "2020-02-29, 20, MET",
            "2021-02-28, 20, MET",
            "2021-03-01, 21, NOT_MET"
    })
    @DisplayName("2월 29일 생일은 평년 2월 28일로 앞당기지 않고 완전히 경과한 연수로 비교한다")
    void handlesLeapDayWithCompletedYears(String referenceDate, int age, ConditionAssessment.Outcome outcome) {
        var condition = new AgeCondition.CompletedYears("age", 20, 20, LocalDate.parse(referenceDate), EVIDENCE);

        var assessment = AgeConditionEvaluator.evaluate(condition, Optional.of(LocalDate.of(2000, 2, 29)));

        assertThat(assessment.outcome()).isEqualTo(outcome);
        assertThat(assessment.comparedValue()).contains("만 " + age + "세");
    }

    @Test
    @DisplayName("생년월일이 없으면 연령 불충족 대신 사용자 정보 누락을 반환한다")
    void missingBirthDateNeedsUserInput() {
        var condition = new AgeCondition.CompletedYears("age", 25, 29, LocalDate.of(2026, 8, 30), EVIDENCE);

        var assessment = AgeConditionEvaluator.evaluate(condition, Optional.empty());

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(MISSING_USER_INPUT);
        assertThat(assessment.comparedValue()).isEmpty();
        assertThat(new EligibilityDecision(BASIS, PolicyReview.complete(), List.of(assessment)).status())
                .isEqualTo(NEEDS_REVIEW);
    }

    @ParameterizedTest(name = "생년월일 입력 여부: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("기준일 미해석은 사용자 입력 누락보다 먼저 보존하고 다른 항목의 불충족으로 덮지 않는다")
    void unresolvedPolicyTakesPriority(boolean hasBirthDate) {
        var condition = new AgeCondition.Unresolved("age", "만 25세 이상 29세 이하", Optional.empty(),
                "원문의 연령 기준일을 확인할 수 없습니다.", EVIDENCE);
        var birthDate = hasBirthDate ? Optional.of(LocalDate.of(2000, 9, 1)) : Optional.<LocalDate>empty();

        var assessment = AgeConditionEvaluator.evaluate(condition, birthDate);
        var otherMismatch = new ConditionAssessment("other", "다른 필수 조건", Optional.of("테스트 비교값"),
                Optional.empty(), NOT_MET, Optional.empty(), "다른 필수 조건을 충족하지 않습니다.", EVIDENCE);

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(UNRESOLVED_POLICY);
        assertThat(assessment.referenceDate()).isEmpty();
        assertThat(assessment.comparedValue()).isEmpty();
        assertThat(assessment.explanation()).isEqualTo(condition.reason());
        assertThat(new EligibilityDecision(BASIS, PolicyReview.complete(), List.of(otherMismatch, assessment)).status())
                .isEqualTo(NEEDS_REVIEW);
    }

    @Test
    @DisplayName("만 나이로 해석하지 않은 조건은 기준일이 있어도 임의로 계산하지 않는다")
    void unresolvedAgeDefinitionRetainsKnownReferenceDate() {
        var referenceDate = LocalDate.of(2026, 8, 30);
        var condition = new AgeCondition.Unresolved("age", "출생연도별 대상 범위", Optional.of(referenceDate),
                "출생연도 조건의 적용 범위를 추가 확인해야 합니다.", EVIDENCE);

        var assessment = AgeConditionEvaluator.evaluate(condition, Optional.of(LocalDate.of(2000, 9, 1)));

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.referenceDate()).contains(referenceDate);
        assertThat(assessment.appliedCondition()).isEqualTo(condition.appliedCondition());
        assertThat(assessment.comparedValue()).isEmpty();
        assertThat(assessment.evidence()).isEqualTo(EVIDENCE);
    }

    @Test
    @DisplayName("생년월일보다 이른 정책 기준일을 음수 나이나 연령 불충족으로 바꾸지 않는다")
    void inconsistentDatesNeedReview() {
        var condition = new AgeCondition.CompletedYears("age", 0, 10, LocalDate.of(2000, 8, 31), EVIDENCE);

        var assessment = AgeConditionEvaluator.evaluate(condition, Optional.of(LocalDate.of(2000, 9, 1)));

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(UNRESOLVED_POLICY);
        assertThat(assessment.comparedValue()).isEmpty();
        assertThat(assessment.explanation()).contains("생년월일과 정책 기준일");
    }

    @Test
    @DisplayName("서비스 대상 연령으로 범위를 덮지 않고 근거를 보존하며 다른 예외 검토를 생략하지 않는다")
    void retainsExplicitRangeAndEvidenceWithoutApprovingEntirePolicy() {
        var referenceDate = LocalDate.of(2026, 8, 30);
        var condition = new AgeCondition.CompletedYears("age", 35, 40, referenceDate, EVIDENCE);
        var birthDate = Optional.of(LocalDate.of(1987, 1, 1));

        var assessment = AgeConditionEvaluator.evaluate(condition, birthDate);
        var pending = new PolicyReview.PendingIssue("다른 참여 제한을 확인해야 합니다.", EVIDENCE);
        var decision = new EligibilityDecision(BASIS, PolicyReview.incomplete(List.of(pending)), List.of(assessment));

        assertThat(assessment).isEqualTo(AgeConditionEvaluator.evaluate(condition, birthDate));
        assertThat(assessment.outcome()).isEqualTo(MET);
        assertThat(assessment.conditionId()).isEqualTo("age");
        assertThat(assessment.appliedCondition()).isEqualTo("2026-08-30 기준 만 35세 이상 40세 이하");
        assertThat(assessment.comparedValue()).contains("만 39세");
        assertThat(assessment.referenceDate()).contains(referenceDate);
        assertThat(assessment.evidence()).isEqualTo(EVIDENCE);
        assertThat(decision.basis()).isEqualTo(BASIS);
        assertThat(decision.status()).isEqualTo(NEEDS_REVIEW);
    }

    @ParameterizedTest(name = "최소 {0}세, 최대 {1}세")
    @CsvSource({"-1, 29", "30, 29"})
    @DisplayName("음수 또는 역전된 범위를 확인 완료 조건으로 만들지 않는다")
    void rejectsInvalidResolvedRange(int minimum, int maximum) {
        assertThatIllegalArgumentException().isThrownBy(() -> new AgeCondition.CompletedYears(
                "age", minimum, maximum, LocalDate.of(2026, 8, 30), EVIDENCE));
    }
}
