package kr.youthpolicymate.policy.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;
import static kr.youthpolicymate.eligibility.EligibilityStatus.*;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static org.assertj.core.api.Assertions.*;

class YouthHousingSavingsRulesTest {
    private static final Instant NOW = Instant.parse("2026-09-05T01:00:00Z");

    @Test @DisplayName("기본 생년월일은 오늘 가입할 때의 연령만 비교하고 병역기간을 추정하지 않는다")
    void comparesBirthDateUsingExistingAgeRules() {
        var below = PolicyRuleFixtures.age(YouthHousingSavingsRules.NUMBER, LocalDate.parse("2007-09-06"), NOW);
        var adult = PolicyRuleFixtures.age(YouthHousingSavingsRules.NUMBER, LocalDate.parse("2007-09-05"), NOW);
        var upper = PolicyRuleFixtures.age(YouthHousingSavingsRules.NUMBER, LocalDate.parse("1991-09-06"), NOW);
        var military = PolicyRuleFixtures.age(YouthHousingSavingsRules.NUMBER, LocalDate.parse("1991-09-05"), NOW);
        assertThat(below.outcome()).isEqualTo(NOT_MET);
        assertThat(adult.outcome()).isEqualTo(MET);
        assertThat(upper.outcome()).isEqualTo(MET);
        assertThat(military.outcome()).isEqualTo(UNKNOWN);
        assertThat(military.explanation()).contains("병역기간 차감");
        assertThat(adult.providedValue()).isEqualTo("만 19세 (2026-09-05 · 서울)");
        assertThat(adult.evidence()).isEqualTo(evaluate("AGE_19_TO_34", "UNKNOWN", "UNKNOWN", "UNKNOWN").checks().getFirst().evidence());
    }

    @Test @DisplayName("생일의 서울 자정에 연령이 바뀌고 윤일 출생도 날짜 계산으로 비교한다")
    void usesSeoulBirthdayBoundary() {
        var birth = LocalDate.parse("2007-09-06");
        assertThat(PolicyRuleFixtures.age(YouthHousingSavingsRules.NUMBER, birth, Instant.parse("2026-09-05T14:59:59Z")).outcome()).isEqualTo(NOT_MET);
        assertThat(PolicyRuleFixtures.age(YouthHousingSavingsRules.NUMBER, birth, Instant.parse("2026-09-05T15:00:00Z")).outcome()).isEqualTo(MET);
        var leapBirth = LocalDate.parse("1992-02-29");
        assertThat(PolicyRuleFixtures.age(YouthHousingSavingsRules.NUMBER, leapBirth, Instant.parse("2026-02-28T00:00:00Z")).providedValue()).startsWith("만 33세");
        assertThat(PolicyRuleFixtures.age(YouthHousingSavingsRules.NUMBER, leapBirth, Instant.parse("2026-03-01T00:00:00Z")).providedValue()).startsWith("만 34세");
    }

    @Test @DisplayName("연령·본인 무주택·소득 충족과 은행의 가입·혜택 심사를 구분한다")
    void separatesCheckedCriteriaFromBankReview() {
        var result = evaluate("AGE_19_TO_34", "NO_HOME", "PREVIOUS_YEAR", "UP_TO_50M");
        assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(result.checks()).extracting(Check::outcome).containsExactly(MET, MET, MET);
        assertThat(result.checks().get(2).providedValue()).contains("2025년", "5,000만 원 이하");
        assertThat(result.remainingChecks()).anyMatch(s -> s.contains("1인 1계좌"))
                .anyMatch(s -> s.contains("우대금리·비과세·소득공제")).anyMatch(s -> s.contains("대출"));
        assertThat(result.ruleVersion()).isEqualTo(YouthHousingSavingsRules.VERSION);
        assertThat(result.evaluatedAt()).isEqualTo(NOW);
    }

    @Test @DisplayName("확인한 병역기간 차감은 연령에만 적용하고 무주택·소득 조건을 면제하지 않는다")
    void limitsMilitaryAgeExceptionToAge() {
        assertThat(evaluate("MILITARY_AGE_CONFIRMED", "NO_HOME", "PREVIOUS_YEAR", "UP_TO_50M").checks())
                .extracting(Check::outcome).containsOnly(MET);
        var failed = evaluate("MILITARY_AGE_CONFIRMED", "OWNS_HOME", "PREVIOUS_YEAR", "OVER_50M");
        assertThat(failed.checks()).extracting(Check::outcome).containsExactly(MET, NOT_MET, NOT_MET);
        assertThat(failed.commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        assertThat(failed.status()).isEqualTo(NEEDS_REVIEW);
        for (var age : List.of("UNDER_19", "NO_MILITARY_DEDUCTION", "OVER_LIMIT_CONFIRMED")) {
            assertThat(evaluate(age, "NO_HOME", "PREVIOUS_YEAR", "UP_TO_50M").checks().getFirst().outcome()).isEqualTo(NOT_MET);
        }
        assertThat(evaluate("MILITARY_AGE_PENDING", "NO_HOME", "PREVIOUS_YEAR", "UP_TO_50M").commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
    }

    @Test @DisplayName("확인된 소득서류 기준과 금액을 함께 비교하고 미확인 소득을 0원으로 취급하지 않는다")
    void requiresIncomeBasisAndAmount() {
        for (var basis : List.of("PREVIOUS_YEAR", "EARLIER_YEAR_CONFIRMED", "ANNUALIZED_CONFIRMED")) {
            assertThat(evaluate("AGE_19_TO_34", "NO_HOME", basis, "UP_TO_50M").checks().get(2).outcome()).isEqualTo(MET);
            assertThat(evaluate("AGE_19_TO_34", "NO_HOME", basis, "OVER_50M").checks().get(2).outcome()).isEqualTo(NOT_MET);
            assertThat(evaluate("AGE_19_TO_34", "NO_HOME", basis, "UNKNOWN").checks().get(2).outcome()).isEqualTo(UNKNOWN);
            assertThat(evaluate("AGE_19_TO_34", "NO_HOME", basis, "TAX_EXEMPT_ONLY").checks().get(2).outcome()).isEqualTo(UNKNOWN);
        }
        assertThat(evaluate("AGE_19_TO_34", "NO_HOME", "UNKNOWN", "UP_TO_50M").commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
    }

    @Test @DisplayName("확인된 군복무자 소득 예외와 비과세 소득 답변이 일치할 때만 소득 조건을 충족한다")
    void requiresMatchingMilitaryIncomeAnswers() {
        assertThat(evaluate("AGE_19_TO_34", "NO_HOME", "MILITARY_CONFIRMED", "TAX_EXEMPT_ONLY").checks())
                .extracting(Check::outcome).containsOnly(MET);
        for (var amount : List.of("UP_TO_50M", "OVER_50M", "UNKNOWN")) {
            var result = evaluate("AGE_19_TO_34", "NO_HOME", "MILITARY_CONFIRMED", amount);
            assertThat(result.checks().get(2).outcome()).isEqualTo(UNKNOWN);
            assertThat(result.checks().get(2).explanation()).contains("다시 확인");
        }
        assertThat(evaluate("AGE_19_TO_34", "NO_HOME", "UNKNOWN", "TAX_EXEMPT_ONLY").checks().get(2).outcome()).isEqualTo(UNKNOWN);
    }

    @Test @DisplayName("미응답은 추가 확인으로 남기고 지원하지 않는 질문·답변과 중복 답변을 거절한다")
    void handlesMissingAndInvalidAnswers() {
        var result = PolicyRuleFixtures.rule(YouthHousingSavingsRules.NUMBER).evaluate(1, new Request(1, YouthHousingSavingsRules.VERSION, List.of()), NOW);
        assertThat(result.checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(result.commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        assertThat(evaluate("UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN").checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        for (var answers : List.of(List.of(new Answer("monthlyRides", "ZERO")), List.of(new Answer("incomeAmount", "ZERO")),
                List.of(new Answer("homeOwnership", "NO_HOME"), new Answer("homeOwnership", "OWNS_HOME")))) {
            assertThatThrownBy(() -> PolicyRuleFixtures.rule(YouthHousingSavingsRules.NUMBER).evaluate(1, new Request(1, YouthHousingSavingsRules.VERSION, answers), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test @DisplayName("서울 기준 검토 연도 밖에서는 연도별 소득 질문을 재사용하지 않는다")
    void boundsReviewedYearInSeoul() {
        assertThat(PolicyRuleFixtures.rule(YouthHousingSavingsRules.NUMBER).appliesAt(Instant.parse("2025-12-31T14:59:59Z"))).isFalse();
        assertThat(PolicyRuleFixtures.rule(YouthHousingSavingsRules.NUMBER).appliesAt(Instant.parse("2025-12-31T15:00:00Z"))).isTrue();
        assertThat(PolicyRuleFixtures.rule(YouthHousingSavingsRules.NUMBER).appliesAt(Instant.parse("2026-12-31T14:59:59Z"))).isTrue();
        assertThat(PolicyRuleFixtures.rule(YouthHousingSavingsRules.NUMBER).appliesAt(Instant.parse("2026-12-31T15:00:00Z"))).isFalse();
        assertThatThrownBy(() -> PolicyRuleFixtures.rule(YouthHousingSavingsRules.NUMBER).evaluate(1, new Request(1, YouthHousingSavingsRules.VERSION, List.of()),
                Instant.parse("2026-12-31T15:00:00Z"))).isInstanceOf(IllegalArgumentException.class);
    }

    private Evaluation evaluate(String age, String home, String basis, String amount) {
        return PolicyRuleFixtures.rule(YouthHousingSavingsRules.NUMBER).evaluate(1, new Request(1, YouthHousingSavingsRules.VERSION,
                List.of(new Answer("age", age), new Answer("homeOwnership", home), new Answer("incomeBasis", basis), new Answer("incomeAmount", amount))), NOW);
    }
}
