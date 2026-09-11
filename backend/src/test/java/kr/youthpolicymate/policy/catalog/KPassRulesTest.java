package kr.youthpolicymate.policy.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;
import static kr.youthpolicymate.eligibility.EligibilityStatus.*;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static org.assertj.core.api.Assertions.*;

class KPassRulesTest {
    private static final Instant NOW = Instant.parse("2026-09-05T01:00:00Z");

    @Test @DisplayName("기본 가입·이용요건만 비교하고 청년 환급률이나 실제 지급을 확정하지 않는다")
    void separatesCommonCriteriaFromRefund() {
        var result = evaluate("ADULT", "REGISTERED", "CONFIRMED", "AT_LEAST_15");
        assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(result.checks()).extracting(Check::outcome).containsOnly(MET);
        assertThat(result.scope()).startsWith("2026년 9월");
        assertThat(result.ruleVersion()).isEqualTo("k-pass-2026-v1-2026-09");
        assertThat(result.checks().getFirst().evidence()).contains("만 19세 이상", "만 35세 이상도 가입", "청년 환급률은 만 19~34세");
        assertThat(result.remainingChecks()).anyMatch(s -> s.contains("환급률과 금액은 K-패스에서 확인"));
        assertThat(result.remainingChecks()).anyMatch(s -> s.contains("KTX·SRT"));
    }

    @Test @DisplayName("가입 첫 달의 1~14회 예외를 적용하되 이후 달과 실제 0회는 현재 횟수 미달로 구분한다")
    void appliesFirstMonthExceptionOnlyToPositiveRides() {
        var first = evaluate("ADULT", "REGISTERED", "CONFIRMED", "FIRST_MONTH_1_TO_14");
        assertThat(first.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(first.checks().getLast().explanation()).contains("첫 달", "15회 미만도 인정", "다음 달");
        for (var rides : List.of("LATER_MONTH_1_TO_14", "ZERO")) {
            var result = evaluate("ADULT", "REGISTERED", "CONFIRMED", rides);
            assertThat(result.checks()).extracting(Check::outcome).containsExactly(MET, MET, MET, NOT_MET);
            assertThat(result.commonCriteriaStatus()).isEqualTo(INELIGIBLE);
            assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
            assertThat(result.checks().getLast().explanation()).contains("월말까지 이용 내역이 늘면 결과가 달라질 수 있어요");
        }
    }

    @Test @DisplayName("주소 확인·이용내역 반영 대기와 미응답은 불충족이나 실제 0회가 아니다")
    void preservesPendingAndUnknownAnswers() {
        var result = evaluate("ADULT", "REGISTERED", "PENDING", "PENDING");
        assertThat(result.checks()).extracting(Check::outcome).containsExactly(MET, MET, UNKNOWN, UNKNOWN);
        assertThat(result.commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        assertThat(result.checks().getLast().explanation()).contains("이용내역 반영이 끝나면 다시 답해주세요");
        assertThat(evaluate("UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN").checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(PolicyRuleFixtures.rule(KPassRules.NUMBER).evaluate(1, new Request(1, PolicyRuleFixtures.rule(KPassRules.NUMBER).versionAt(NOW), List.of()), NOW).checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
    }

    @Test @DisplayName("연령 미달과 카드 발급만 한 상태는 첫 달 횟수 예외로 충족 처리하지 않는다")
    void doesNotExtendFirstMonthExceptionToOtherConditions() {
        var young = evaluate("UNDER_19", "REGISTERED", "CONFIRMED", "FIRST_MONTH_1_TO_14");
        assertThat(young.checks()).extracting(Check::outcome).containsExactly(NOT_MET, MET, MET, MET);
        for (var registration : List.of("CARD_ONLY", "NOT_REGISTERED")) {
            var result = evaluate("ADULT", registration, "CONFIRMED", "FIRST_MONTH_1_TO_14");
            assertThat(result.checks()).extracting(Check::outcome).containsExactly(MET, NOT_MET, MET, MET);
            assertThat(result.commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        }
    }

    @Test @DisplayName("질문의 대상 월은 서울 자정에 바뀌며 검토한 연도가 지난 규칙은 실행하지 않는다")
    void identifiesMonthAndReviewedYearInSeoul() {
        assertThat(PolicyRuleFixtures.rule(KPassRules.NUMBER).versionAt(Instant.parse("2026-09-30T14:59:59Z"))).endsWith("2026-09");
        assertThat(PolicyRuleFixtures.rule(KPassRules.NUMBER).versionAt(Instant.parse("2026-09-30T15:00:00Z"))).endsWith("2026-10");
        assertThat(PolicyRuleFixtures.questions(KPassRules.NUMBER, 1, Instant.parse("2026-09-30T15:00:00Z")).scope()).startsWith("2026년 10월");
        assertThat(PolicyRuleFixtures.rule(KPassRules.NUMBER).appliesAt(Instant.parse("2026-12-31T14:59:59Z"))).isTrue();
        assertThat(PolicyRuleFixtures.rule(KPassRules.NUMBER).appliesAt(Instant.parse("2026-12-31T15:00:00Z"))).isFalse();
        assertThatThrownBy(() -> PolicyRuleFixtures.rule(KPassRules.NUMBER).evaluate(1, new Request(1, PolicyRuleFixtures.rule(KPassRules.NUMBER).versionAt(NOW), List.of()), Instant.parse("2026-12-31T15:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("다른 정책 질문·임의 답변·한 질문의 중복 답변은 거절한다")
    void rejectsInvalidAnswers() {
        for (var answers : List.of(List.of(new Answer("remainingUses", "ONE")), List.of(new Answer("monthlyRides", "TEN")),
                List.of(new Answer("monthlyRides", "AT_LEAST_15"), new Answer("monthlyRides", "ZERO")))) {
            assertThatThrownBy(() -> PolicyRuleFixtures.rule(KPassRules.NUMBER).evaluate(1, new Request(1, PolicyRuleFixtures.rule(KPassRules.NUMBER).versionAt(NOW), answers), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private Evaluation evaluate(String age, String registration, String residence, String rides) {
        return PolicyRuleFixtures.rule(KPassRules.NUMBER).evaluate(1, new Request(1, PolicyRuleFixtures.rule(KPassRules.NUMBER).versionAt(NOW), List.of(new Answer("age", age),
                new Answer("registration", registration), new Answer("residence", residence), new Answer("monthlyRides", rides))), NOW);
    }
}
