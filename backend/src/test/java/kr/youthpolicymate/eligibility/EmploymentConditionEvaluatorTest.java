package kr.youthpolicymate.eligibility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.MISSING_USER_INPUT;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.UNRESOLVED_POLICY;
import static kr.youthpolicymate.eligibility.EligibilityStatus.ELIGIBLE;
import static kr.youthpolicymate.eligibility.EligibilityStatus.INELIGIBLE;
import static kr.youthpolicymate.eligibility.EligibilityStatus.NEEDS_REVIEW;
import static kr.youthpolicymate.eligibility.EmploymentAnswer.Response.APPLIES;
import static kr.youthpolicymate.eligibility.EmploymentAnswer.Response.DOES_NOT_APPLY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EmploymentConditionEvaluatorTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 8, 30);
    private static final String DEFINITION = "테스트에서 정의한 임금근로 재직 상태";
    private static final EmploymentFact FACT = new EmploymentFact(
            "test-policy", "revision-1", "employment", DEFINITION, REFERENCE_DATE);
    private static final SourceEvidence EVIDENCE = new SourceEvidence(
            "test-employment-source-revision-1", "테스트용 취업 조건", Optional.of("정의·기준일·요구 방향을 지정한 인공 자료"));
    private static final EvaluationBasis BASIS = new EvaluationBasis(
            "test-policy", "revision-1", "employment-v1", Instant.parse("2026-08-30T01:00:00Z"));

    @ParameterizedTest(name = "해당 요구 {0}, 답변 {1} → {2}")
    @CsvSource({
            "true, APPLIES, MET",
            "true, DOES_NOT_APPLY, NOT_MET",
            "false, APPLIES, NOT_MET",
            "false, DOES_NOT_APPLY, MET"
    })
    @DisplayName("사용자 답변과 정책이 요구하는 해당·비해당 방향을 따로 비교한다")
    void comparesResponseWithRequiredDirection(
            boolean requiredToApply, EmploymentAnswer.Response response, ConditionAssessment.Outcome outcome
    ) {
        var condition = new EmploymentCondition.FactRequirement(FACT, requiredToApply, EVIDENCE);

        var assessment = EmploymentConditionEvaluator.evaluate(condition, Optional.of(new EmploymentAnswer(FACT, response)));

        assertThat(assessment.outcome()).isEqualTo(outcome);
        assertThat(assessment.comparedValue()).contains(response.label());
        assertThat(assessment.appliedCondition()).endsWith(requiredToApply ? "· 해당해야 함" : "· 해당하지 않아야 함");
        assertThat(assessment.uncertainty()).isEmpty();
    }

    @Test
    @DisplayName("답변 누락을 비해당 답변으로 채워 금지 조건을 충족시키지 않는다")
    void missingAnswerDoesNotImplyDoesNotApply() {
        var condition = new EmploymentCondition.FactRequirement(FACT, false, EVIDENCE);

        var assessment = EmploymentConditionEvaluator.evaluate(condition, Optional.empty());

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(MISSING_USER_INPUT);
        assertThat(assessment.comparedValue()).isEmpty();
        assertThat(assessment.referenceDate()).contains(REFERENCE_DATE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("모름 답변은 해당·비해당 어느 요구에서도 미확인으로 보존한다")
    void unknownAnswerNeverSatisfiesOrFailsRequirement(boolean requiredToApply) {
        var condition = new EmploymentCondition.FactRequirement(FACT, requiredToApply, EVIDENCE);
        var answer = new EmploymentAnswer(FACT, EmploymentAnswer.Response.UNKNOWN);

        var assessment = EmploymentConditionEvaluator.evaluate(condition, Optional.of(answer));

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(MISSING_USER_INPUT);
        assertThat(assessment.comparedValue()).contains("모름");
    }

    @ParameterizedTest
    @MethodSource("differentFacts")
    @DisplayName("정책·개정·조건·정의·기준일 중 하나라도 다른 답변은 현재 조건에 재사용하지 않는다")
    void mismatchedFactNeedsNewAnswer(EmploymentFact answeredFact) {
        var condition = new EmploymentCondition.FactRequirement(FACT, true, EVIDENCE);

        var assessment = EmploymentConditionEvaluator.evaluate(condition,
                Optional.of(new EmploymentAnswer(answeredFact, APPLIES)));

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(MISSING_USER_INPUT);
        assertThat(assessment.referenceDate()).contains(REFERENCE_DATE);
        assertThat(assessment.comparedValue()).isEmpty();
        assertThat(assessment.evidence()).isEqualTo(EVIDENCE);
        assertThat(new EligibilityDecision(BASIS, PolicyReview.complete(), List.of(assessment)).status())
                .isEqualTo(NEEDS_REVIEW);
    }

    private static Stream<EmploymentFact> differentFacts() {
        return Stream.of(
                new EmploymentFact("other-policy", "revision-1", "employment", DEFINITION, REFERENCE_DATE),
                new EmploymentFact("test-policy", "revision-0", "employment", DEFINITION, REFERENCE_DATE),
                new EmploymentFact("test-policy", "revision-1", "other-condition", DEFINITION, REFERENCE_DATE),
                new EmploymentFact("test-policy", "revision-1", "employment", "다른 취업 사실의 정의", REFERENCE_DATE),
                new EmploymentFact("test-policy", "revision-1", "employment", DEFINITION, REFERENCE_DATE.minusDays(1)));
    }

    @ParameterizedTest(name = "불필요한 답변 전달 여부: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("확인한 취업 제한 없음은 답변을 요구하거나 결과에 복사하지 않는다")
    void verifiedNoRestrictionDoesNotRequireAnswer(boolean hasAnswer) {
        var condition = new EmploymentCondition.NoRestriction("employment", Optional.empty(), EVIDENCE);
        var answer = hasAnswer ? Optional.of(new EmploymentAnswer(FACT, DOES_NOT_APPLY))
                : Optional.<EmploymentAnswer>empty();

        var assessment = EmploymentConditionEvaluator.evaluate(condition, answer);

        assertThat(assessment.outcome()).isEqualTo(MET);
        assertThat(assessment.uncertainty()).isEmpty();
        assertThat(assessment.appliedCondition()).isEqualTo("취업 상태 제한 없음");
        assertThat(assessment.referenceDate()).isEmpty();
        assertThat(assessment.comparedValue()).isEmpty();
        assertThat(assessment.evidence()).isEqualTo(EVIDENCE);
    }

    @ParameterizedTest(name = "답변 입력 여부: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("정책 코드·정의·기준일 미해석은 답변이나 다른 조건 불충족으로 덮지 않는다")
    void unresolvedPolicyTakesPriority(boolean hasAnswer) {
        var condition = new EmploymentCondition.Unresolved("employment", "원문의 취업 조건", Optional.empty(),
                "취업 코드의 정의와 기준일을 확인해야 합니다.", EVIDENCE);
        var answer = hasAnswer ? Optional.of(new EmploymentAnswer(FACT, APPLIES)) : Optional.<EmploymentAnswer>empty();
        var ageMismatch = AgeConditionEvaluator.evaluate(
                new AgeCondition.CompletedYears("age", 19, 24, REFERENCE_DATE, EVIDENCE),
                Optional.of(LocalDate.of(1990, 1, 1)));

        var assessment = EmploymentConditionEvaluator.evaluate(condition, answer);

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(UNRESOLVED_POLICY);
        assertThat(assessment.referenceDate()).isEmpty();
        assertThat(assessment.comparedValue()).isEmpty();
        assertThat(assessment.explanation()).isEqualTo(condition.reason());
        assertThat(new EligibilityDecision(BASIS, PolicyReview.complete(), List.of(ageMismatch, assessment)).status())
                .isEqualTo(NEEDS_REVIEW);
    }

    @Test
    @DisplayName("기준일이 있어도 미해석 복합 조건을 단일 취업 답변으로 풀지 않는다")
    void unresolvedAlternativesRetainKnownDateAndEvidence() {
        var condition = new EmploymentCondition.Unresolved("employment", "재직 또는 별도 프리랜서 요건",
                Optional.of(REFERENCE_DATE), "복수 취업 요건의 관계와 예외를 확인해야 합니다.", EVIDENCE);

        var assessment = EmploymentConditionEvaluator.evaluate(condition, Optional.of(new EmploymentAnswer(FACT, APPLIES)));

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(UNRESOLVED_POLICY);
        assertThat(assessment.referenceDate()).contains(REFERENCE_DATE);
        assertThat(assessment.appliedCondition()).isEqualTo(condition.appliedCondition());
        assertThat(assessment.evidence()).isEqualTo(EVIDENCE);
    }

    @Test
    @DisplayName("연령·거주·취업이 맞아도 다른 예외 검토를 생략하지 않고 같은 근거를 보존한다")
    void preservesEvidenceWithoutCompletingPolicyReview() {
        var condition = new EmploymentCondition.FactRequirement(FACT, true, EVIDENCE);
        var equivalentFact = new EmploymentFact("test-policy", "revision-1", "employment", DEFINITION, REFERENCE_DATE);
        var answer = Optional.of(new EmploymentAnswer(equivalentFact, APPLIES));
        var age = AgeConditionEvaluator.evaluate(
                new AgeCondition.CompletedYears("age", 19, 34, REFERENCE_DATE, EVIDENCE),
                Optional.of(LocalDate.of(2000, 1, 1)));
        var residence = ResidenceConditionEvaluator.evaluate(
                new ResidenceCondition.RegisteredIn("residence", ResidenceCondition.Area.SEOUL, Set.of(), REFERENCE_DATE, EVIDENCE),
                Optional.of(new SeoulResidence(SeoulDistrict.MAPO, REFERENCE_DATE)));

        var assessment = EmploymentConditionEvaluator.evaluate(condition, answer);
        var conditions = List.of(age, residence, assessment);
        var pending = new PolicyReview.PendingIssue("재학 관련 참여 제한을 확인해야 합니다.", EVIDENCE);
        var decision = new EligibilityDecision(BASIS, PolicyReview.incomplete(List.of(pending)), conditions);

        assertThat(assessment).isEqualTo(EmploymentConditionEvaluator.evaluate(condition, answer));
        assertThat(assessment.conditionId()).isEqualTo("employment");
        assertThat(assessment.appliedCondition()).isEqualTo("2026-08-30 기준 " + DEFINITION + " · 해당해야 함");
        assertThat(assessment.referenceDate()).contains(REFERENCE_DATE);
        assertThat(assessment.comparedValue()).contains("해당함");
        assertThat(assessment.evidence()).isEqualTo(EVIDENCE);
        assertThat(decision.basis()).isEqualTo(BASIS);
        assertThat(decision.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(new EligibilityDecision(BASIS, PolicyReview.complete(), conditions).status()).isEqualTo(ELIGIBLE);
    }

    @Test
    @DisplayName("취업 제한이 없어도 필요한 재학 정보가 없으면 전체 신청 가능으로 만들지 않는다")
    void noEmploymentRestrictionDoesNotResolveMissingEducationInput() {
        var employment = EmploymentConditionEvaluator.evaluate(
                new EmploymentCondition.NoRestriction("employment", Optional.of(REFERENCE_DATE), EVIDENCE), Optional.empty());
        var education = new ConditionAssessment("education", "원문에서 확인한 재학 제한", Optional.empty(),
                Optional.of(REFERENCE_DATE), UNKNOWN, Optional.of(MISSING_USER_INPUT), "재학 정보가 필요합니다.", EVIDENCE);

        assertThat(employment.referenceDate()).contains(REFERENCE_DATE);
        assertThat(employment.appliedCondition()).isEqualTo("2026-08-30 기준 취업 상태 제한 없음");
        assertThat(new EligibilityDecision(BASIS, PolicyReview.complete(), List.of(employment, education)).status())
                .isEqualTo(NEEDS_REVIEW);
    }

    @Test
    @DisplayName("확인한 취업 필수 요건 불충족은 생년월일 누락으로 숨기지 않는다")
    void definitiveMismatchRemainsWithOtherMissingInput() {
        var employment = EmploymentConditionEvaluator.evaluate(
                new EmploymentCondition.FactRequirement(FACT, true, EVIDENCE),
                Optional.of(new EmploymentAnswer(FACT, DOES_NOT_APPLY)));
        var age = AgeConditionEvaluator.evaluate(
                new AgeCondition.CompletedYears("age", 19, 34, REFERENCE_DATE, EVIDENCE), Optional.empty());

        assertThat(new EligibilityDecision(BASIS, PolicyReview.complete(), List.of(employment, age)).status())
                .isEqualTo(INELIGIBLE);
    }

    @Test
    @DisplayName("정의가 없는 값을 확인한 취업 사실로 만들지 않는다")
    void rejectsMissingFactDefinition() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EmploymentFact(
                "test-policy", "revision-1", "employment", " ", REFERENCE_DATE));
    }
}
