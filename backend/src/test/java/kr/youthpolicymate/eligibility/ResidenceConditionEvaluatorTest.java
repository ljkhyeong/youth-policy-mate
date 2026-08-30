package kr.youthpolicymate.eligibility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.MISSING_USER_INPUT;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.UNRESOLVED_POLICY;
import static kr.youthpolicymate.eligibility.EligibilityStatus.ELIGIBLE;
import static kr.youthpolicymate.eligibility.EligibilityStatus.INELIGIBLE;
import static kr.youthpolicymate.eligibility.EligibilityStatus.NEEDS_REVIEW;
import static kr.youthpolicymate.eligibility.ResidenceCondition.Area.NATIONWIDE;
import static kr.youthpolicymate.eligibility.ResidenceCondition.Area.SEOUL;
import static kr.youthpolicymate.eligibility.ResidenceCondition.Area.SEOUL_DISTRICTS;
import static kr.youthpolicymate.eligibility.SeoulDistrict.GANGDONG;
import static kr.youthpolicymate.eligibility.SeoulDistrict.GANGNAM;
import static kr.youthpolicymate.eligibility.SeoulDistrict.MAPO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ResidenceConditionEvaluatorTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 8, 30);
    private static final SourceEvidence EVIDENCE = new SourceEvidence(
            "test-residence-source-revision-1", "테스트용 거주 조건", Optional.of("허용 지역과 기준일을 지정한 인공 자료"));
    private static final EvaluationBasis BASIS = new EvaluationBasis(
            "test-policy", "revision-1", "residence-v1", Instant.parse("2026-08-30T01:00:00Z"));

    @ParameterizedTest
    @EnumSource(value = ResidenceCondition.Area.class, names = {"NATIONWIDE", "SEOUL"})
    @DisplayName("확인한 전국·서울 전체 거주 조건에는 서울 자치구 거주자를 포함한다")
    void includesSeoulResidentInWiderAreas(ResidenceCondition.Area area) {
        var condition = new ResidenceCondition.RegisteredIn("residence", area, Set.of(), REFERENCE_DATE, EVIDENCE);

        var assessment = ResidenceConditionEvaluator.evaluate(condition,
                Optional.of(new SeoulResidence(MAPO, REFERENCE_DATE)));

        assertThat(assessment.outcome()).isEqualTo(MET);
        assertThat(assessment.uncertainty()).isEmpty();
        assertThat(assessment.appliedCondition()).isEqualTo(
                REFERENCE_DATE + " 기준 주민등록상 " + (area == NATIONWIDE ? "전국" : "서울특별시") + " 거주");
    }

    @ParameterizedTest(name = "거주 자치구 {0} → {1}")
    @CsvSource({"GANGNAM, MET", "MAPO, MET", "GANGDONG, NOT_MET"})
    @DisplayName("복수 허용 자치구 중 하나와 일치해야 하며 서울 전체로 넓히지 않는다")
    void matchesAnyExplicitDistrict(SeoulDistrict district, ConditionAssessment.Outcome outcome) {
        var condition = new ResidenceCondition.RegisteredIn(
                "residence", SEOUL_DISTRICTS, Set.of(GANGNAM, MAPO), REFERENCE_DATE, EVIDENCE);

        var assessment = ResidenceConditionEvaluator.evaluate(condition,
                Optional.of(new SeoulResidence(district, REFERENCE_DATE)));

        assertThat(assessment.outcome()).isEqualTo(outcome);
        assertThat(assessment.uncertainty()).isEmpty();
    }

    @Test
    @DisplayName("거주지 누락을 서비스 대상이 서울이라는 이유로 충족 처리하지 않는다")
    void missingResidenceNeedsUserInput() {
        var condition = new ResidenceCondition.RegisteredIn("residence", SEOUL, Set.of(), REFERENCE_DATE, EVIDENCE);

        var assessment = ResidenceConditionEvaluator.evaluate(condition, Optional.empty());

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(MISSING_USER_INPUT);
        assertThat(assessment.referenceDate()).contains(REFERENCE_DATE);
        assertThat(assessment.comparedValue()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-08-29", "2026-08-31"})
    @DisplayName("기준일 하루 전후의 같은 주소도 정책 기준일의 거주 정보로 대체하지 않는다")
    void mismatchedResidenceDateNeedsInputForPolicyDate(String asOfDate) {
        var condition = new ResidenceCondition.RegisteredIn(
                "residence", SEOUL_DISTRICTS, Set.of(MAPO), REFERENCE_DATE, EVIDENCE);
        var residence = new SeoulResidence(MAPO, LocalDate.parse(asOfDate));

        var assessment = ResidenceConditionEvaluator.evaluate(condition, Optional.of(residence));

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(MISSING_USER_INPUT);
        assertThat(assessment.referenceDate()).contains(REFERENCE_DATE);
        assertThat(assessment.comparedValue()).contains(residence.description());
        assertThat(assessment.explanation()).contains(asOfDate, REFERENCE_DATE.toString());
        assertThat(new EligibilityDecision(BASIS, PolicyReview.complete(), List.of(assessment)).status())
                .isEqualTo(NEEDS_REVIEW);
    }

    @ParameterizedTest(name = "거주 정보 입력 여부: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("지역 코드와 기준일 미해석을 전국 허용이나 사용자 정보 누락으로 바꾸지 않는다")
    void unresolvedPolicyTakesPriority(boolean hasResidence) {
        var condition = new ResidenceCondition.Unresolved("residence", "원문의 지역 조건", Optional.empty(),
                "지역 코드와 거주 기준일의 의미를 확인해야 합니다.", EVIDENCE);
        var residence = hasResidence ? Optional.of(new SeoulResidence(MAPO, REFERENCE_DATE))
                : Optional.<SeoulResidence>empty();
        var ageMismatch = AgeConditionEvaluator.evaluate(
                new AgeCondition.CompletedYears("age", 19, 24, REFERENCE_DATE, EVIDENCE),
                Optional.of(LocalDate.of(1990, 1, 1)));

        var assessment = ResidenceConditionEvaluator.evaluate(condition, residence);

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(UNRESOLVED_POLICY);
        assertThat(assessment.referenceDate()).isEmpty();
        assertThat(assessment.comparedValue()).isEmpty();
        assertThat(assessment.explanation()).isEqualTo(condition.reason());
        assertThat(new EligibilityDecision(BASIS, PolicyReview.complete(), List.of(ageMismatch, assessment)).status())
                .isEqualTo(NEEDS_REVIEW);
    }

    @Test
    @DisplayName("거주 기간·직장 소재지 예외는 자치구가 맞아도 임의로 비교하지 않는다")
    void unresolvedResidenceDefinitionRetainsKnownReferenceDate() {
        var condition = new ResidenceCondition.Unresolved("residence", "서울 1년 이상 거주 또는 서울 소재 직장 재직",
                Optional.of(REFERENCE_DATE), "거주 기간과 직장 소재지의 선택 요건을 확인해야 합니다.", EVIDENCE);

        var assessment = ResidenceConditionEvaluator.evaluate(condition,
                Optional.of(new SeoulResidence(MAPO, REFERENCE_DATE)));

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(UNRESOLVED_POLICY);
        assertThat(assessment.referenceDate()).contains(REFERENCE_DATE);
        assertThat(assessment.appliedCondition()).isEqualTo(condition.appliedCondition());
        assertThat(assessment.comparedValue()).isEmpty();
        assertThat(assessment.evidence()).isEqualTo(EVIDENCE);
    }

    @Test
    @DisplayName("허용 자치구와 근거를 보존하고 집합 입력 순서가 달라도 같은 설명을 만든다")
    void preservesEvidenceAndStableDistrictOrder() {
        var originalDistricts = new LinkedHashSet<>(List.of(MAPO, GANGNAM));
        var condition = new ResidenceCondition.RegisteredIn(
                "residence", SEOUL_DISTRICTS, originalDistricts, REFERENCE_DATE, EVIDENCE);
        var reversed = new ResidenceCondition.RegisteredIn(
                "residence", SEOUL_DISTRICTS, new LinkedHashSet<>(List.of(GANGNAM, MAPO)), REFERENCE_DATE, EVIDENCE);
        var residence = Optional.of(new SeoulResidence(MAPO, REFERENCE_DATE));
        originalDistricts.clear();

        var assessment = ResidenceConditionEvaluator.evaluate(condition, residence);

        assertThat(assessment).isEqualTo(ResidenceConditionEvaluator.evaluate(reversed, residence));
        assertThat(assessment.conditionId()).isEqualTo("residence");
        assertThat(assessment.appliedCondition()).isEqualTo("2026-08-30 기준 주민등록상 서울특별시 강남구, 마포구 중 한 곳 거주");
        assertThat(assessment.comparedValue()).contains("2026-08-30 기준 서울특별시 마포구");
        assertThat(assessment.referenceDate()).contains(REFERENCE_DATE);
        assertThat(assessment.evidence()).isEqualTo(EVIDENCE);
    }

    @Test
    @DisplayName("연령과 전국 거주가 맞아도 신청 주체 검토가 남으면 신청 가능으로 확정하지 않는다")
    void matchingAgeAndResidenceDoNotCompletePolicyReview() {
        var age = AgeConditionEvaluator.evaluate(
                new AgeCondition.CompletedYears("age", 19, 34, REFERENCE_DATE, EVIDENCE),
                Optional.of(LocalDate.of(2000, 1, 1)));
        var residence = ResidenceConditionEvaluator.evaluate(
                new ResidenceCondition.RegisteredIn("residence", NATIONWIDE, Set.of(), REFERENCE_DATE, EVIDENCE),
                Optional.of(new SeoulResidence(MAPO, REFERENCE_DATE)));
        var pending = new PolicyReview.PendingIssue("서울 소재 대학 소속 동아리의 신청 요건을 확인해야 합니다.", EVIDENCE);

        var decision = new EligibilityDecision(BASIS, PolicyReview.incomplete(List.of(pending)), List.of(age, residence));

        assertThat(age.outcome()).isEqualTo(MET);
        assertThat(residence.outcome()).isEqualTo(MET);
        assertThat(decision.basis()).isEqualTo(BASIS);
        assertThat(decision.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(new EligibilityDecision(BASIS, PolicyReview.complete(), List.of(age, residence)).status())
                .isEqualTo(ELIGIBLE);
    }

    @Test
    @DisplayName("필수 자치구가 명확히 다르면 생년월일 누락이 있어도 불충족을 유지한다")
    void districtMismatchRemainsDefinitiveWithMissingBirthDate() {
        var age = AgeConditionEvaluator.evaluate(
                new AgeCondition.CompletedYears("age", 19, 34, REFERENCE_DATE, EVIDENCE), Optional.empty());
        var residence = ResidenceConditionEvaluator.evaluate(
                new ResidenceCondition.RegisteredIn("residence", SEOUL_DISTRICTS, Set.of(MAPO), REFERENCE_DATE, EVIDENCE),
                Optional.of(new SeoulResidence(GANGDONG, REFERENCE_DATE)));

        assertThat(new EligibilityDecision(BASIS, PolicyReview.complete(), List.of(age, residence)).status())
                .isEqualTo(INELIGIBLE);
    }

    @ParameterizedTest
    @EnumSource(ResidenceCondition.Area.class)
    @DisplayName("빈 자치구 제한이나 전국·서울 전체와 자치구 제한을 섞은 조건을 거절한다")
    void rejectsContradictoryAreaAndDistricts(ResidenceCondition.Area area) {
        Set<SeoulDistrict> districts = area == SEOUL_DISTRICTS ? Set.of() : Set.of(MAPO);

        assertThatIllegalArgumentException().isThrownBy(() -> new ResidenceCondition.RegisteredIn(
                "residence", area, districts, REFERENCE_DATE, EVIDENCE));
    }
}
