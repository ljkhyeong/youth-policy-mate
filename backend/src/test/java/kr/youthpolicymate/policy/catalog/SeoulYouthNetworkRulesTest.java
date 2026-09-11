package kr.youthpolicymate.policy.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;
import static kr.youthpolicymate.eligibility.EligibilityStatus.*;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static org.assertj.core.api.Assertions.*;

class SeoulYouthNetworkRulesTest {
    private static final Instant NOW = Instant.parse("2026-09-05T01:00:00Z");

    @Test @DisplayName("서울 거주·대학·직장·인정된 사업장 중 하나면 지역 조건을 충족하되 최종 선발은 별도 확인한다")
    void acceptsEachSeoulConnectionAndKeepsSelectionPending() {
        for (var connection : List.of("RESIDENT", "UNIVERSITY", "WORKPLACE", "BUSINESS_CONFIRMED")) {
            var result = evaluate("BASE_RANGE", connection, "NOT_APPLICABLE", "NONE");
            assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
            assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
            assertThat(result.checks()).extracting(Check::outcome).containsExactly(MET, MET, MET, MET);
            assertThat(result.explanation()).contains("접수가 마감");
            assertThat(result.remainingChecks()).anyMatch(s -> s.contains("500자"))
                    .anyMatch(s -> s.contains("70점")).anyMatch(s -> s.contains("종합 평가"));
        }
        var result = evaluate("BASE_RANGE", "NONE_CONFIRMED", "NOT_APPLICABLE", "NONE");
        assertThat(result.checks()).extracting(Check::outcome).containsExactly(MET, NOT_MET, MET, MET);
        assertThat(result.commonCriteriaStatus()).isEqualTo(INELIGIBLE);
    }

    @Test @DisplayName("출생일 범위와 인정된 군복무 연장만 연령 충족으로 비교하며 다른 제한은 면제하지 않는다")
    void appliesMilitaryExtensionOnlyToAge() {
        var military = evaluate("MILITARY_EXTENSION_CONFIRMED", "UNIVERSITY", "APPLIES", "CONFIRMED");
        assertThat(military.checks()).extracting(Check::outcome).containsExactly(MET, MET, NOT_MET, NOT_MET);
        assertThat(military.commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        assertThat(military.status()).isEqualTo(NEEDS_REVIEW);
        for (var age : List.of("TOO_YOUNG", "OLDER_NO_EXTENSION", "EXTENDED_LIMIT_EXCEEDED")) {
            assertThat(evaluate(age, "RESIDENT", "NOT_APPLICABLE", "NONE").checks().getFirst().outcome()).isEqualTo(NOT_MET);
        }
        var pending = evaluate("MILITARY_EXTENSION_PENDING", "RESIDENT", "NOT_APPLICABLE", "NONE");
        assertThat(pending.commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        assertThat(pending.checks().getFirst().explanation()).contains("연장 후 기준 충족 여부");
        assertThat(evaluate("BASE_RANGE", "RESIDENT", "NOT_APPLICABLE", "NONE").checks().getFirst().providedValue())
                .isEqualTo("1986.1.2.~2007.1.1. 출생");
    }

    @Test @DisplayName("연임과 위촉 제한은 독립적으로 비교하고 이력·생활권 미확인을 불충족으로 바꾸지 않는다")
    void separatesPriorRestrictionsAndUnconfirmedAnswers() {
        assertThat(evaluate("BASE_RANGE", "RESIDENT", "APPLIES", "NONE").checks())
                .extracting(Check::outcome).containsExactly(MET, MET, NOT_MET, MET);
        assertThat(evaluate("BASE_RANGE", "RESIDENT", "NOT_APPLICABLE", "CONFIRMED").checks())
                .extracting(Check::outcome).containsExactly(MET, MET, MET, NOT_MET);
        var unknown = evaluate("BASE_RANGE", "UNKNOWN", "UNKNOWN", "UNKNOWN");
        assertThat(unknown.checks()).extracting(Check::outcome).containsExactly(MET, UNKNOWN, UNKNOWN, UNKNOWN);
        assertThat(unknown.commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
    }

    @Test @DisplayName("미응답을 그대로 표시하고 다른 정책 질문·임의 값·중복 답변을 거절한다")
    void handlesMissingAndInvalidAnswers() {
        var result = PolicyRuleFixtures.rule(SeoulYouthNetworkRules.NUMBER).evaluate(1, new Request(1, SeoulYouthNetworkRules.VERSION, List.of()), NOW);
        assertThat(result.checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(result.checks()).extracting(Check::providedValue).containsOnly("미응답");
        for (var answers : List.of(List.of(new Answer("homeOwnership", "NO_HOME")), List.of(new Answer("seoulConnection", "SEOUL_CODE")),
                List.of(new Answer("consecutiveTerms", "APPLIES"), new Answer("consecutiveTerms", "NOT_APPLICABLE")))) {
            assertThatThrownBy(() -> PolicyRuleFixtures.rule(SeoulYouthNetworkRules.NUMBER).evaluate(1, new Request(1, SeoulYouthNetworkRules.VERSION, answers), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test @DisplayName("서울 시각의 접수 시작·마감을 안내하되 접수 마감으로 개인 조건 결과를 바꾸지 않는다")
    void separatesRecruitmentWindowFromCriteria() {
        var before = Instant.parse("2026-05-19T23:59:59Z");
        var open = Instant.parse("2026-05-20T00:00:00Z");
        var beforeClose = Instant.parse("2026-05-29T07:59:59Z");
        var close = Instant.parse("2026-05-29T08:00:00Z");
        assertThat(PolicyRuleFixtures.questions(SeoulYouthNetworkRules.NUMBER, 1, before).reason()).contains("접수 전");
        assertThat(PolicyRuleFixtures.questions(SeoulYouthNetworkRules.NUMBER, 1, open).reason()).doesNotContain("접수 전", "접수가 마감");
        assertThat(PolicyRuleFixtures.questions(SeoulYouthNetworkRules.NUMBER, 1, beforeClose).reason()).doesNotContain("접수가 마감");
        assertThat(PolicyRuleFixtures.questions(SeoulYouthNetworkRules.NUMBER, 1, close).reason()).contains("접수가 마감", "17:00(서울)");
        for (var now : List.of(before, open, beforeClose, close)) {
            var result = PolicyRuleFixtures.rule(SeoulYouthNetworkRules.NUMBER).evaluate(1, request("BASE_RANGE", "RESIDENT", "NOT_APPLICABLE", "NONE"), now);
            assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
            assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
            assertThat(result.evaluatedAt()).isEqualTo(now);
        }
    }

    @Test @DisplayName("검토 연도를 서울 자정으로 구분하고 다음 해 모집 기준으로 재사용하지 않는다")
    void boundsReviewedYearInSeoul() {
        assertThat(PolicyRuleFixtures.rule(SeoulYouthNetworkRules.NUMBER).appliesAt(Instant.parse("2025-12-31T14:59:59Z"))).isFalse();
        assertThat(PolicyRuleFixtures.rule(SeoulYouthNetworkRules.NUMBER).appliesAt(Instant.parse("2025-12-31T15:00:00Z"))).isTrue();
        assertThat(PolicyRuleFixtures.rule(SeoulYouthNetworkRules.NUMBER).appliesAt(Instant.parse("2026-12-31T14:59:59Z"))).isTrue();
        assertThat(PolicyRuleFixtures.rule(SeoulYouthNetworkRules.NUMBER).appliesAt(Instant.parse("2026-12-31T15:00:00Z"))).isFalse();
        assertThatThrownBy(() -> PolicyRuleFixtures.rule(SeoulYouthNetworkRules.NUMBER).evaluate(1, new Request(1, SeoulYouthNetworkRules.VERSION, List.of()),
                Instant.parse("2026-12-31T15:00:00Z"))).isInstanceOf(IllegalArgumentException.class);
    }

    private Request request(String age, String connection, String terms, String restriction) {
        return new Request(1, SeoulYouthNetworkRules.VERSION, List.of(new Answer("birthRange", age), new Answer("seoulConnection", connection),
                new Answer("consecutiveTerms", terms), new Answer("priorDisqualification", restriction)));
    }
    private Evaluation evaluate(String age, String connection, String terms, String restriction) {
        return PolicyRuleFixtures.rule(SeoulYouthNetworkRules.NUMBER).evaluate(1, request(age, connection, terms, restriction), NOW);
    }
}
