package kr.youthpolicymate.policy.catalog;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import kr.youthpolicymate.eligibility.ConditionAssessment.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static kr.youthpolicymate.eligibility.EligibilityStatus.*;
import static org.assertj.core.api.Assertions.*;

class MovingFeeRulesTest {
    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");

    @Test @DisplayName("소득을 포함한 조건 충족과 최종 선정을 구분하고 증빙·중복지원 예외를 안내한다")
    void keepsRemainingRequirements() {
        var result = evaluate("NO_HOME");
        assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(result.checks()).extracting(Check::outcome).containsOnly(MET);
        assertThat(result.remainingChecks()).anyMatch(s -> s.contains("증빙 인정"))
                .anyMatch(s -> s.contains("비용과 지급액은 심사"))
                .anyMatch(s -> s.contains("증빙서류가 인정되는지"));
        assertThat(result.explanation()).contains("상반기", "마감");
    }

    @Test @DisplayName("주택 소유 예외 미확인은 불충족으로 바꾸지 않고 다른 요건을 면제하지 않는다")
    void preservesHousingException() {
        assertThat(evaluate("EXCEPTION_PENDING").checks().get(3).outcome()).isEqualTo(UNKNOWN);
        assertThat(evaluate("EXCEPTION_PENDING").commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        assertThat(evaluate("OWNS_NO_EXCEPTION").checks().get(3).outcome()).isEqualTo(NOT_MET);
        var request = new Request(1, MovingFeeRules.VERSION, List.of(new Answer("homeOwnership", "EXCEPTION_PENDING"), new Answer("move", "OUTSIDE")));
        var result = PolicyRuleFixtures.rule(MovingFeeRules.NUMBER).evaluate(1, request, NOW);
        assertThat(result.checks()).extracting(Check::outcome).containsExactly(UNKNOWN, NOT_MET, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN);
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
    }

    @Test @DisplayName("공고 보험료 기준 이하·초과를 비교하되 소득 증빙과 최종 자격은 기관 확인으로 남긴다")
    void comparesConfirmedIncomeAgainstTheNotice() {
        var within = evaluate("NO_HOME", "WITHIN_LIMIT");
        var above = evaluate("NO_HOME", "ABOVE_LIMIT");
        assertThat(within.checks().get(5).outcome()).isEqualTo(MET);
        assertThat(above.checks().get(5).outcome()).isEqualTo(NOT_MET);
        assertThat(above.commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        assertThat(above.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(within.checks().get(5).evidence()).contains("2026년 3월", "장기요양보험료 제외", "150%", "피부양자", "부양자");
    }

    @Test @DisplayName("부양자 보험료·가구 기준·대체 증빙 미확인은 0원이나 소득 초과로 바꾸지 않는다")
    void preservesIncomeUncertaintyAndItsReason() {
        for (var pending : List.of("DEPENDENT_PENDING", "HOUSEHOLD_PENDING", "DOCUMENTS_PENDING", "UNKNOWN")) {
            var result = evaluate("NO_HOME", pending);
            assertThat(result.checks().get(5).outcome()).isEqualTo(UNKNOWN);
            assertThat(result.commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
            assertThat(result.checks().subList(0, 5)).extracting(Check::outcome).containsOnly(MET);
        }
        assertThat(evaluate("NO_HOME", "DEPENDENT_PENDING").checks().get(5).explanation()).contains("0원", "부양자", "2026년 3월");
        assertThat(evaluate("NO_HOME", "HOUSEHOLD_PENDING").checks().get(5).explanation()).contains("가구원 수", "가입 유형");
        assertThat(evaluate("NO_HOME", "DOCUMENTS_PENDING").checks().get(5).explanation()).contains("대체 소득 증빙");
        assertThat(evaluate("OWNS_NO_EXCEPTION", "DOCUMENTS_PENDING").checks().get(3).outcome()).isEqualTo(NOT_MET);
    }

    @ParameterizedTest(name = "타 기관 {0}, 신청 비용 {1}: {2}")
    @CsvSource({
            "BROKERAGE_ONLY, MOVING, MET", "MOVING_ONLY, BROKERAGE, MET",
            "BROKERAGE_ONLY, BROKERAGE, NOT_MET", "MOVING_ONLY, MOVING, NOT_MET",
            "BROKERAGE_ONLY, BOTH, UNKNOWN", "MOVING_ONLY, BOTH, UNKNOWN",
            "BOTH, BROKERAGE, NOT_MET", "BOTH, MOVING, NOT_MET", "NONE, BOTH, MET"
    })
    @DisplayName("타 기관 한쪽 비용 지원은 신청 비용별로 비교하며 일부 중복을 전체 불충족으로 바꾸지 않는다")
    void comparesOtherSupportByRequestedCost(String other, String requested, Outcome outcome) {
        var result = evaluate("NO_HOME", "WITHIN_LIMIT", "NONE", other, requested);
        assertThat(result.checks().get(6).outcome()).isEqualTo(outcome);
        assertThat(result.commonCriteriaStatus()).isEqualTo(switch (outcome) {
            case MET -> ELIGIBLE; case NOT_MET -> INELIGIBLE; case UNKNOWN -> NEEDS_REVIEW;
        });
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(result.checks().subList(0, 6)).extracting(Check::outcome).containsOnly(MET);
        if ("BROKERAGE_ONLY".equals(other)) assertThat(result.checks().get(6).explanation()).contains("이사비");
        if ("MOVING_ONLY".equals(other)) assertThat(result.checks().get(6).explanation()).contains("중개보수");
    }

    @Test @DisplayName("서울시 재수혜 제한은 타 기관 예외로 면제하지 않고 다른 조건도 유지한다")
    void keepsSeoulLifetimeLimitSeparate() {
        var result = evaluate("EXCEPTION_PENDING", "WITHIN_LIMIT", "RECEIVED", "BROKERAGE_ONLY", "MOVING");
        assertThat(result.checks().get(6).outcome()).isEqualTo(NOT_MET);
        assertThat(result.checks().get(6).explanation()).contains("생애 1회", "다른 비용도 다시 지원받을 수 없어요");
        assertThat(result.checks().get(3).outcome()).isEqualTo(UNKNOWN);
        assertThat(evaluate("NO_HOME", "WITHIN_LIMIT", "RECEIVED", null, null).checks().get(6).outcome()).isEqualTo(NOT_MET);
        assertThat(evaluate("NO_HOME", "WITHIN_LIMIT", null, "BOTH", null).checks().get(6).outcome()).isEqualTo(NOT_MET);
    }

    @Test @DisplayName("지원 이력·신청 비용 미확인은 제한 없음으로 바꾸지 않고 필요한 다음 답변을 안내한다")
    void keepsMissingSupportAnswersUnknown() {
        for (var missing : new String[] {null, "UNKNOWN"}) {
            assertThat(evaluate("NO_HOME", "WITHIN_LIMIT", missing, "NONE", "MOVING").checks().get(6).outcome()).isEqualTo(UNKNOWN);
            assertThat(evaluate("NO_HOME", "WITHIN_LIMIT", "NONE", missing, "MOVING").checks().get(6).outcome()).isEqualTo(UNKNOWN);
            var cost = evaluate("NO_HOME", "WITHIN_LIMIT", "NONE", "BROKERAGE_ONLY", missing);
            assertThat(cost.checks().get(6).outcome()).isEqualTo(UNKNOWN);
            assertThat(cost.checks().get(6).explanation()).contains("지원받으려는 비용을 선택");
        }
        assertThat(evaluate("NO_HOME", "WITHIN_LIMIT", "NONE", "NONE", null).checks().get(6).outcome()).isEqualTo(MET);
        assertThat(evaluate("NO_HOME", "WITHIN_LIMIT", "NONE", "BROKERAGE_ONLY", "BOTH").checks().get(6).explanation())
                .contains("중개보수는 중복", "이사비만 선택");
    }

    @ParameterizedTest(name = "{0} 답변 {2}: {3}")
    @CsvSource({
            "parentRental, 7, CLEAR, MET", "parentRental, 7, RESTRICTED, NOT_MET", "parentRental, 7, UNKNOWN, UNKNOWN",
            "benefitReceipt, 8, CLEAR, MET", "benefitReceipt, 8, RESTRICTED, NOT_MET", "benefitReceipt, 8, UNKNOWN, UNKNOWN",
            "excludedResidency, 9, CLEAR, MET", "excludedResidency, 9, RESTRICTED, NOT_MET", "excludedResidency, 9, UNKNOWN, UNKNOWN"
    })
    @DisplayName("참여 제한을 항목별로 비교하고 다른 미응답 요건을 면제하지 않는다")
    void comparesParticipationRestrictions(String questionId, int index, String value, Outcome outcome) {
        var result = PolicyRuleFixtures.rule(MovingFeeRules.NUMBER).evaluate(1, new Request(1, MovingFeeRules.VERSION, List.of(new Answer(questionId, value))), NOW);
        assertThat(result.checks().get(index).outcome()).isEqualTo(outcome);
        assertThat(result.checks().subList(0, 7)).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
    }

    @Test @DisplayName("선택한 비용에 맞춰 지출 기간과 제외 항목을 안내하며 미선택을 두 비용 신청으로 바꾸지 않는다")
    void guidesEvidenceForRequestedCost() {
        var brokerage = evaluate("NO_HOME", "WITHIN_LIMIT", "NONE", "NONE", "BROKERAGE").remainingChecks().get(2);
        var moving = evaluate("NO_HOME", "WITHIN_LIMIT", "NONE", "NONE", "MOVING").remainingChecks().get(2);
        var both = evaluate("NO_HOME").remainingChecks().get(2);
        var missing = evaluate("NO_HOME", "WITHIN_LIMIT", "NONE", "NONE", null).remainingChecks().get(2);
        assertThat(brokerage).contains("2024.1.1.~2026.4.14.", "재계약·중도 퇴실").doesNotContain("청소·택배");
        assertThat(moving).contains("2024.1.1.~2026.4.14.", "청소·택배·대중교통·택시·렌터카").doesNotContain("재계약");
        assertThat(both).contains("재계약·중도 퇴실", "청소·택배");
        assertThat(missing).contains("확인할 비용을 선택");
    }

    @Test @DisplayName("미응답을 미확인으로 남기며 공통 답변 검증을 적용한다")
    void handlesUnknownAndInvalidAnswers() {
        var result = PolicyRuleFixtures.rule(MovingFeeRules.NUMBER).evaluate(1, new Request(1, MovingFeeRules.VERSION, List.of()), NOW);
        assertThat(result.checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(result.checks().subList(0, 6)).extracting(Check::providedValue).containsOnly("미응답");
        assertThat(result.checks().get(6).providedValue()).isEqualTo("서울시 사업: 미응답 / 타 기관: 미응답 / 신청할 비용: 미응답");
        assertThat(result.checks().subList(7, 10)).extracting(Check::providedValue).containsOnly("미응답");
        for (var answers : List.of(List.of(new Answer("income", "LOW")), List.of(new Answer("move", "SEOUL")),
                List.of(new Answer("move", "COMPLETED"), new Answer("move", "OUTSIDE")))) {
            assertThatThrownBy(() -> PolicyRuleFixtures.rule(MovingFeeRules.NUMBER).evaluate(1, new Request(1, MovingFeeRules.VERSION, answers), NOW)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test @DisplayName("접수 시작·마감 시각과 다음 해 질문 만료를 구분한다")
    void boundsApplicationAndReviewedYear() {
        assertThat(PolicyRuleFixtures.questions(MovingFeeRules.NUMBER, 1, Instant.parse("2026-04-01T00:59:59Z")).reason()).contains("접수 전");
        assertThat(PolicyRuleFixtures.questions(MovingFeeRules.NUMBER, 1, Instant.parse("2026-04-01T01:00:00Z")).reason()).doesNotContain("접수 전", "마감됐어요");
        assertThat(PolicyRuleFixtures.questions(MovingFeeRules.NUMBER, 1, Instant.parse("2026-04-14T08:59:59Z")).reason()).doesNotContain("마감됐어요");
        assertThat(PolicyRuleFixtures.questions(MovingFeeRules.NUMBER, 1, Instant.parse("2026-04-14T09:00:00Z")).reason()).contains("마감됐어요");
        assertThat(PolicyRuleFixtures.rule(MovingFeeRules.NUMBER).appliesAt(Instant.parse("2026-12-31T14:59:59Z"))).isTrue();
        assertThat(PolicyRuleFixtures.rule(MovingFeeRules.NUMBER).appliesAt(Instant.parse("2026-12-31T15:00:00Z"))).isFalse();
        assertThatThrownBy(() -> PolicyRuleFixtures.rule(MovingFeeRules.NUMBER).evaluate(1, new Request(1, MovingFeeRules.VERSION, List.of()), Instant.parse("2026-12-31T15:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    private Evaluation evaluate(String home) {
        return evaluate(home, "WITHIN_LIMIT");
    }
    private Evaluation evaluate(String home, String income) {
        return evaluate(home, income, "NONE", "NONE", "BOTH");
    }
    private Evaluation evaluate(String home, String income, String seoul, String other, String requested) {
        var answers = Stream.of(new Answer("birthRange", "IN_RANGE"),
                new Answer("move", "COMPLETED"), new Answer("contract", "ALL"), new Answer("homeOwnership", home),
                new Answer("housingCost", "WITHIN_LIMIT"), new Answer("income", income), new Answer("seoulSupport", seoul),
                new Answer("otherSupport", other), new Answer("requestedCost", requested),
                new Answer("parentRental", "CLEAR"), new Answer("benefitReceipt", "CLEAR"), new Answer("excludedResidency", "CLEAR")).filter(answer -> answer.value() != null).toList();
        return PolicyRuleFixtures.rule(MovingFeeRules.NUMBER).evaluate(1, new Request(1, MovingFeeRules.VERSION, answers), NOW);
    }
}
