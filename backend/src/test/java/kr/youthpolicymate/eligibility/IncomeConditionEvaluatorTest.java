package kr.youthpolicymate.eligibility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.NOT_MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.MISSING_USER_INPUT;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.UNRESOLVED_POLICY;
import static kr.youthpolicymate.eligibility.EligibilityStatus.INELIGIBLE;
import static kr.youthpolicymate.eligibility.EligibilityStatus.NEEDS_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class IncomeConditionEvaluatorTest {

    // 모든 금액·기준은 경계 비교용 인공 자료이며 실제 정책 기준표가 아니다.
    private static final IncomeBasis.Period PERIOD = new IncomeBasis.Period(
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "테스트에서 정한 연간 합계");
    private static final IncomeBasis BASIS = new IncomeBasis("test-policy", "revision-1", "income",
            IncomeBasis.Personal.INSTANCE, "테스트에서 정한 세전 소득", PERIOD, Optional.empty(), Optional.empty());
    private static final SourceEvidence EVIDENCE = new SourceEvidence("test-income-source-revision-1",
            "테스트용 소득 조건", Optional.of("원화 단위·대상 기간·포함 경계를 지정한 인공 자료"));
    private static final EvaluationBasis EVALUATION_BASIS = new EvaluationBasis(
            "test-policy", "revision-1", "income-v1", Instant.parse("2026-08-30T01:00:00Z"));

    @ParameterizedTest(name = "{0}~{2} 허용, {4}~{6} 입력 → {8}")
    @CsvSource({
            "10, true, 20, true, 12, true, 18, true, MET",
            "10, true, 20, true, 10, true, 20, true, MET",
            "10, false, 20, false, 10, false, 20, false, MET",
            "10, false, 20, true, 10, true, 20, true, UNKNOWN",
            "10, true, 20, false, 10, true, 20, true, UNKNOWN",
            "10, true, 20, true, 0, true, 9, true, NOT_MET",
            "10, true, 20, true, 21, true, 30, true, NOT_MET",
            "10, true, 20, true, 5, true, 15, true, UNKNOWN",
            "10, true, 20, true, 15, true, 25, true, UNKNOWN",
            "10, true, 20, true, 0, true, 30, true, UNKNOWN",
            "10, true, 20, true, 20, true, 30, true, UNKNOWN",
            "10, true, 20, false, 20, true, 30, true, NOT_MET",
            "10, true, 20, true, 20, false, 30, true, NOT_MET",
            "10, true, 20, true, 0, true, 10, true, UNKNOWN",
            "10, false, 20, true, 0, true, 10, true, NOT_MET",
            "10, true, 20, true, 0, true, 10, false, NOT_MET",
            "10, true, unbounded, false, 20, true, unbounded, false, MET",
            "unbounded, false, 20, true, unbounded, false, 10, true, MET",
            "10, true, 20, true, 15, true, unbounded, false, UNKNOWN",
            "10, true, 20, true, unbounded, false, 15, true, UNKNOWN"
    })
    @DisplayName("구간의 전체 포함·분리·일부 겹침과 양 끝의 포함 여부를 비교한다")
    void comparesWholeRanges(
            String policyLow, boolean policyLowInclusive, String policyHigh, boolean policyHighInclusive,
            String userLow, boolean userLowInclusive, String userHigh, boolean userHighInclusive,
            ConditionAssessment.Outcome expected
    ) {
        var condition = requirement(range(policyLow, policyLowInclusive, policyHigh, policyHighInclusive));
        var input = range(userLow, userLowInclusive, userHigh, userHighInclusive);

        var assessment = IncomeConditionEvaluator.evaluate(condition, known(input));

        assertThat(assessment.outcome()).isEqualTo(expected);
        assertThat(assessment.comparedValue()).contains(input.description());
        assertThat(assessment.uncertainty()).isEqualTo(expected == UNKNOWN ? Optional.of(MISSING_USER_INPUT) : Optional.empty());
        if (expected == UNKNOWN) {
            assertThat(assessment.explanation()).contains("더 좁은 구간");
        }
    }

    @ParameterizedTest(name = "경계 {0}원, 포함 {1} → {2}")
    @CsvSource({"10, true, MET", "10, false, NOT_MET", "20, true, MET", "20, false, NOT_MET"})
    @DisplayName("정확한 금액이 하한·상한과 같으면 이상·초과·이하·미만을 구분한다")
    void comparesExactBoundary(String value, boolean inclusive, ConditionAssessment.Outcome expected) {
        var condition = requirement(range("10", inclusive, "20", inclusive));

        var assessment = IncomeConditionEvaluator.evaluate(condition, known(IncomeRange.exact(new BigDecimal(value))));

        assertThat(assessment.outcome()).isEqualTo(expected);
        assertThat(assessment.uncertainty()).isEmpty();
    }

    @Test
    @DisplayName("소수 경계를 반올림하지 않고 같은 금액의 소수 자릿수 차이는 무시한다")
    void comparesExactDecimalsWithoutRounding() {
        var condition = requirement(range("0", true, "0.30", true));

        var equal = IncomeConditionEvaluator.evaluate(condition, known(IncomeRange.exact(new BigDecimal("0.300"))));
        var greater = IncomeConditionEvaluator.evaluate(condition, known(IncomeRange.exact(new BigDecimal("0.300000000000000001"))));

        assertThat(equal.outcome()).isEqualTo(MET);
        assertThat(equal.comparedValue()).contains("0.3원");
        assertThat(greater.outcome()).isEqualTo(NOT_MET);
        assertThat(greater.comparedValue()).contains("0.300000000000000001원");
    }

    @Test
    @DisplayName("명시적인 0원은 금액이며 누락·모름은 0원으로 바꾸지 않는다")
    void distinguishesZeroMissingAndUnknown() {
        var condition = requirement(IncomeRange.exact(BigDecimal.ZERO));

        var zero = IncomeConditionEvaluator.evaluate(condition, known(IncomeRange.exact(BigDecimal.ZERO)));
        var missing = IncomeConditionEvaluator.evaluate(condition, Optional.empty());
        var unknown = IncomeConditionEvaluator.evaluate(condition, Optional.of(new IncomeAnswer.Unknown(BASIS)));

        assertThat(zero.outcome()).isEqualTo(MET);
        assertThat(zero.comparedValue()).contains("0원");
        assertThat(missing.outcome()).isEqualTo(UNKNOWN);
        assertThat(missing.uncertainty()).contains(MISSING_USER_INPUT);
        assertThat(missing.comparedValue()).isEmpty();
        assertThat(unknown.outcome()).isEqualTo(UNKNOWN);
        assertThat(unknown.uncertainty()).contains(MISSING_USER_INPUT);
        assertThat(unknown.comparedValue()).contains("모름");
    }

    @ParameterizedTest
    @MethodSource("differentBases")
    @DisplayName("정책·개정·소득 정의·대상·기간·적용 기준이 다른 금액은 재사용하지 않는다")
    void requiresAnswerForMatchingBasis(IncomeBasis answeredBasis) {
        var condition = requirement(range("0", true, "20", true));

        var assessment = IncomeConditionEvaluator.evaluate(condition,
                Optional.of(new IncomeAnswer.KnownRange(answeredBasis, IncomeRange.exact(BigDecimal.TEN))));

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(MISSING_USER_INPUT);
        assertThat(assessment.comparedValue()).isEmpty();
        assertThat(assessment.referenceDate()).isEmpty();
        assertThat(assessment.evidence()).isEqualTo(EVIDENCE);
    }

    private static Stream<IncomeBasis> differentBases() {
        return Stream.of(
                new IncomeBasis("other-policy", BASIS.policyRevision(), BASIS.conditionId(), BASIS.subject(), BASIS.definition(), PERIOD, Optional.empty(), Optional.empty()),
                new IncomeBasis(BASIS.policyId(), "revision-0", BASIS.conditionId(), BASIS.subject(), BASIS.definition(), PERIOD, Optional.empty(), Optional.empty()),
                new IncomeBasis(BASIS.policyId(), BASIS.policyRevision(), "other-condition", BASIS.subject(), BASIS.definition(), PERIOD, Optional.empty(), Optional.empty()),
                new IncomeBasis(BASIS.policyId(), BASIS.policyRevision(), BASIS.conditionId(), BASIS.subject(), "테스트에서 정한 세후 소득", PERIOD, Optional.empty(), Optional.empty()),
                basis(new IncomeBasis.Household("테스트 가구원 범위", Optional.of(2), Optional.empty(), Optional.empty()), PERIOD, Optional.empty(), Optional.empty()),
                basis(BASIS.subject(), new IncomeBasis.Period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), PERIOD.calculation()), Optional.empty(), Optional.empty()),
                basis(BASIS.subject(), new IncomeBasis.Period(PERIOD.startInclusive(), PERIOD.endInclusive(), "테스트에서 정한 월평균"), Optional.empty(), Optional.empty()),
                basis(BASIS.subject(), PERIOD, Optional.of(new IncomeBasis.Standard("테스트 기준표", Year.of(2026), "table-1")), Optional.empty()),
                basis(BASIS.subject(), PERIOD, Optional.empty(), Optional.of(LocalDate.of(2026, 8, 30))));
    }

    @Test
    @DisplayName("가구 범위·가구원 수·유형·산정일과 기준표 개정 변경은 다시 확인한다")
    void householdAndTableContextMustMatch() {
        var date = LocalDate.of(2026, 3, 1);
        var household = new IncomeBasis.Household("테스트 가구원 범위", Optional.of(2), Optional.of("테스트 유형"), Optional.of(date));
        var standard = new IncomeBasis.Standard("테스트 기준표", Year.of(2026), "table-1");
        var currentBasis = basis(household, PERIOD, Optional.of(standard), Optional.empty());
        var condition = new IncomeCondition.RangeRequirement(currentBasis, range("0", true, "20", true), "인공 원화 금액, 변환 없음", EVIDENCE);
        var differentSubjects = List.of(
                new IncomeBasis.Household("다른 가구원 범위", household.memberCount(), household.type(), household.referenceDate()),
                new IncomeBasis.Household(household.definition(), Optional.of(3), household.type(), household.referenceDate()),
                new IncomeBasis.Household(household.definition(), household.memberCount(), Optional.of("다른 유형"), household.referenceDate()),
                new IncomeBasis.Household(household.definition(), household.memberCount(), household.type(), Optional.of(date.minusDays(1))));
        var differentStandards = List.of(
                new IncomeBasis.Standard(standard.name(), Year.of(2025), standard.revision()),
                new IncomeBasis.Standard(standard.name(), standard.year(), "table-0"));

        for (var subject : differentSubjects) {
            assertMismatchedAnswer(condition, basis(subject, PERIOD, Optional.of(standard), Optional.empty()));
        }
        for (var table : differentStandards) {
            assertMismatchedAnswer(condition, basis(household, PERIOD, Optional.of(table), Optional.empty()));
        }
        var matching = IncomeConditionEvaluator.evaluate(condition,
                Optional.of(new IncomeAnswer.KnownRange(currentBasis, IncomeRange.exact(BigDecimal.TEN))));
        assertThat(matching.outcome()).isEqualTo(MET);
        assertThat(matching.appliedCondition()).contains("가구원 2명", "가구 유형: 테스트 유형", "가구 산정일: 2026-03-01",
                "테스트 기준표", "2026년", "개정 table-1");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("확인한 소득 제한 없음은 금액을 요구하거나 결과에 복사하지 않는다")
    void noRestrictionDoesNotReadIncome(boolean hasAnswer) {
        var condition = new IncomeCondition.NoRestriction("income", Optional.empty(), EVIDENCE);

        var assessment = IncomeConditionEvaluator.evaluate(condition,
                hasAnswer ? known(IncomeRange.exact(BigDecimal.TEN)) : Optional.empty());

        assertThat(assessment.outcome()).isEqualTo(MET);
        assertThat(assessment.uncertainty()).isEmpty();
        assertThat(assessment.comparedValue()).isEmpty();
        assertThat(assessment.referenceDate()).isEmpty();
        var pending = new PolicyReview.PendingIssue("다른 참여 제한을 확인해야 합니다.", EVIDENCE);
        assertThat(new EligibilityDecision(EVALUATION_BASIS, PolicyReview.incomplete(List.of(pending)), List.of(assessment)).status())
                .isEqualTo(NEEDS_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"원천 금액 단위와 빈 상한의 의미가 미확인입니다.", "건강보험료·중위소득 비율 산정은 지원하지 않습니다."})
    @DisplayName("정책 미해석은 입력한 소득과 다른 조건 불충족보다 먼저 보류한다")
    void unresolvedPolicyDoesNotUseIncome(String reason) {
        var condition = new IncomeCondition.Unresolved("income", "원문의 소득 조건", Optional.empty(), reason, EVIDENCE);

        var assessment = IncomeConditionEvaluator.evaluate(condition, known(IncomeRange.exact(BigDecimal.TEN)));
        var ageMismatch = AgeConditionEvaluator.evaluate(new AgeCondition.CompletedYears(
                "age", 19, 24, LocalDate.of(2026, 8, 30), EVIDENCE), Optional.of(LocalDate.of(1990, 1, 1)));

        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(UNRESOLVED_POLICY);
        assertThat(assessment.comparedValue()).isEmpty();
        assertThat(assessment.explanation()).isEqualTo(reason);
        assertThat(new EligibilityDecision(EVALUATION_BASIS, PolicyReview.complete(), List.of(assessment, ageMismatch)).status())
                .isEqualTo(NEEDS_REVIEW);
    }

    @Test
    @DisplayName("확인한 소득 불충족은 다른 입력 누락으로 숨기지 않는다")
    void definiteMismatchTakesPriorityOverMissingInput() {
        var income = IncomeConditionEvaluator.evaluate(requirement(range("0", true, "5", true)), known(IncomeRange.exact(BigDecimal.TEN)));
        var employment = EmploymentConditionEvaluator.evaluate(new EmploymentCondition.FactRequirement(
                new EmploymentFact("test-policy", "revision-1", "employment", "테스트 취업 사실", LocalDate.of(2026, 8, 30)), true, EVIDENCE), Optional.empty());

        assertThat(new EligibilityDecision(EVALUATION_BASIS, PolicyReview.complete(), List.of(income, employment)).status())
                .isEqualTo(INELIGIBLE);
    }

    @Test
    @DisplayName("기간 종료일을 기준일로 만들지 않고 같은 입력의 결과·단위 근거를 보존한다")
    void preservesPeriodEvidenceAndExplicitReferenceDate() {
        var condition = requirement(range("0", true, "20", false));
        var answer = known(IncomeRange.exact(BigDecimal.TEN));

        var assessment = IncomeConditionEvaluator.evaluate(condition, answer);

        assertThat(assessment).isEqualTo(IncomeConditionEvaluator.evaluate(condition, answer));
        assertThat(assessment.referenceDate()).isEmpty();
        assertThat(assessment.appliedCondition()).contains("본인 소득", BASIS.definition(), "단위: 원", "2025-01-01~2025-12-31",
                PERIOD.calculation(), "20원 미만", condition.amountBasis());
        assertThat(assessment.evidence()).isEqualTo(EVIDENCE);
        var datedBasis = basis(BASIS.subject(), PERIOD, Optional.empty(), Optional.of(LocalDate.of(2026, 3, 1)));
        var dated = IncomeConditionEvaluator.evaluate(new IncomeCondition.RangeRequirement(datedBasis,
                condition.allowedRange(), condition.amountBasis(), EVIDENCE),
                Optional.of(new IncomeAnswer.KnownRange(datedBasis, IncomeRange.exact(BigDecimal.TEN))));
        assertThat(dated.referenceDate()).contains(LocalDate.of(2026, 3, 1));
    }

    @ParameterizedTest
    @CsvSource({"20, true, 10, true", "10, false, 10, true", "10, true, 10, false", "unbounded, false, unbounded, false"})
    @DisplayName("역전·빈 구간·양쪽 경계 누락을 판정 결과로 만들지 않는다")
    void rejectsInvalidRanges(String lower, boolean lowInclusive, String upper, boolean highInclusive) {
        assertThatIllegalArgumentException().isThrownBy(() -> range(lower, lowInclusive, upper, highInclusive));
    }

    @Test
    @DisplayName("대상 기간 역전과 금액 단위 근거 누락은 비교 조건으로 만들지 않는다")
    void rejectsUnconfirmedComparisonBasis() {
        assertThatIllegalArgumentException().isThrownBy(() -> new IncomeBasis.Period(PERIOD.endInclusive(), PERIOD.startInclusive(), PERIOD.calculation()));
        assertThatIllegalArgumentException().isThrownBy(() -> new IncomeCondition.RangeRequirement(BASIS, IncomeRange.exact(BigDecimal.ZERO), " ", EVIDENCE));
    }

    private static void assertMismatchedAnswer(IncomeCondition condition, IncomeBasis answeredBasis) {
        var assessment = IncomeConditionEvaluator.evaluate(condition,
                Optional.of(new IncomeAnswer.KnownRange(answeredBasis, IncomeRange.exact(BigDecimal.TEN))));
        assertThat(assessment.outcome()).isEqualTo(UNKNOWN);
        assertThat(assessment.uncertainty()).contains(MISSING_USER_INPUT);
        assertThat(assessment.comparedValue()).isEmpty();
    }

    private static IncomeBasis basis(IncomeBasis.Subject subject, IncomeBasis.Period period,
                                     Optional<IncomeBasis.Standard> standard, Optional<LocalDate> referenceDate) {
        return new IncomeBasis(BASIS.policyId(), BASIS.policyRevision(), BASIS.conditionId(), subject,
                BASIS.definition(), period, standard, referenceDate);
    }

    private static IncomeCondition.RangeRequirement requirement(IncomeRange allowedRange) {
        return new IncomeCondition.RangeRequirement(BASIS, allowedRange, "인공 자료에서 원 단위 확인, 변환·반올림 없음", EVIDENCE);
    }

    private static Optional<IncomeAnswer> known(IncomeRange range) {
        return Optional.of(new IncomeAnswer.KnownRange(BASIS, range));
    }

    private static IncomeRange range(String low, boolean lowInclusive, String high, boolean highInclusive) {
        return new IncomeRange(boundary(low, lowInclusive), boundary(high, highInclusive));
    }

    private static IncomeRange.Boundary boundary(String value, boolean inclusive) {
        return value.equals("unbounded") ? IncomeRange.Unbounded.INSTANCE : new IncomeRange.Amount(new BigDecimal(value), inclusive);
    }
}
