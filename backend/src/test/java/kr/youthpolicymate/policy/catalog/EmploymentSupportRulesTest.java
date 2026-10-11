package kr.youthpolicymate.policy.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;
import static kr.youthpolicymate.eligibility.ConditionOutcome.*;
import static kr.youthpolicymate.eligibility.EligibilityStatus.*;
import static org.assertj.core.api.Assertions.*;

class EmploymentSupportRulesTest {
    private static final String NUMBER = "20260710005400113256";
    private static final String VERSION = "employment-support-2026-v1";
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    private static final Map<String, String> ANSWERS = Map.of("age", "AGE_15_TO_34", "employment", "NOT_WORKING",
            "school", "NOT_ENROLLED", "unemploymentBenefit", "NONE", "pastParticipation", "NEVER",
            "householdIncome", "UP_TO_60", "assets", "UP_TO_4", "workExperience", "YES");

    @Test @DisplayName("참여 제한이 없으면 공통 조건을 충족하고 고용센터 확인 사항은 추가 확인으로 남긴다")
    void keepsJobCenterChecksWhenCommonCriteriaMet() {
        var result = evaluate(Map.of());
        assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(type(result).explanation()).contains("요건심사형");
        assertThat(type(result).providedValue()).isEqualTo("가구 소득 60% 이하 · 재산 4억 원 이하 · 취업경험 100일 또는 800시간 이상");
        assertThat(result.remainingChecks()).anyMatch(text -> text.contains("구직등록")).anyMatch(text -> text.contains("생계급여"));
    }

    @Test @DisplayName("청년은 소득·재산과 관계없이 Ⅱ유형으로 참여할 수 있고 기준 안이면 선발형을 안내한다")
    void youthAlwaysHasTypeTwoAndSelectionWithinLimits() {
        assertThat(typeExplanation(Map.of("workExperience", "NO"))).contains("선발형(청년 특례)");
        assertThat(typeExplanation(Map.of("householdIncome", "FROM_100_TO_120", "assets", "FROM_4_TO_5"))).contains("선발형(청년 특례)");
        for (var changes : List.of(Map.of("householdIncome", "OVER_120"), Map.of("assets", "OVER_5"), Map.of("householdIncome", "UNKNOWN"))) {
            var result = evaluate(changes);
            assertThat(result.commonCriteriaStatus()).as(changes.toString()).isEqualTo(ELIGIBLE);
            assertThat(type(result).explanation()).as(changes.toString()).startsWith("청년은 소득·재산과 관계없이 Ⅱ유형");
        }
        // 병역 가산으로 청년 기준이면 재산 5억 원까지 요건심사형이다.
        assertThat(typeExplanation(Map.of("age", "EXTENSION_CONFIRMED", "assets", "FROM_4_TO_5"))).contains("요건심사형");
    }

    @Test @DisplayName("만 35~69세는 재산 4억 원·소득 100% 기준을 쓰고 100% 초과는 특정계층 확인 전까지 불충족으로 단정하지 않는다")
    void middleAgeUsesGeneralLimitsAndKeepsSpecialGroupsUnknown() {
        assertThat(typeExplanation(Map.of("age", "AGE_35_TO_69"))).contains("요건심사형");
        assertThat(typeExplanation(Map.of("age", "AGE_35_TO_69", "assets", "FROM_4_TO_5"))).startsWith("가구 소득이 중위소득 100% 이하라 Ⅱ유형");
        assertThat(typeExplanation(Map.of("age", "AGE_35_TO_69", "workExperience", "NO"))).contains("선발형(취업경험 부족)");
        var overIncome = evaluate(Map.of("age", "AGE_35_TO_69", "householdIncome", "FROM_100_TO_120"));
        assertThat(overIncome.commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        assertThat(type(overIncome).explanation()).contains("특정계층");
        // 가구 소득을 모르면 중장년의 Ⅱ유형도 확인할 수 없어 불충족으로 단정하지 않는다.
        for (var income : List.of("UNKNOWN", "")) {
            var values = new HashMap<>(ANSWERS);
            values.put("age", "AGE_35_TO_69");
            if (income.isEmpty()) values.remove("householdIncome"); else values.put("householdIncome", income);
            assertThat(type(evaluateExactly(values)).outcome()).as(income).isEqualTo(UNKNOWN);
        }
        // 연령 구간을 모르면 청년·중장년 결과가 같은 경우만 충족으로 안내한다.
        assertThat(type(evaluate(Map.of("age", "EXTENSION_PENDING", "householdIncome", "OVER_120"))).outcome()).isEqualTo(UNKNOWN);
        assertThat(type(evaluate(Map.of("age", "EXTENSION_PENDING", "householdIncome", "FROM_60_TO_100"))).outcome()).isEqualTo(MET);
    }

    @Test @DisplayName("실업급여 수급 여부를 모르거나 답하지 않으면 Ⅰ유형 수당을 단정하지 않는다")
    void doesNotPromiseTypeOneWithoutBenefitAnswer() {
        for (var benefit : List.of("UNKNOWN", "")) {
            var values = new HashMap<>(ANSWERS);
            if (benefit.isEmpty()) values.remove("unemploymentBenefit"); else values.put("unemploymentBenefit", benefit);
            var explanation = type(evaluateExactly(values)).explanation();
            assertThat(explanation).as(benefit).startsWith("청년은 소득·재산과 관계없이 Ⅱ유형").contains("6개월").doesNotContain("구직촉진수당 월");
        }
    }

    @Test @DisplayName("실업급여 수급이 끝난 지 6개월 안이면 참여할 수 있지만 Ⅰ유형 대신 Ⅱ유형을 안내한다")
    void recentUnemploymentBenefitLimitsTypeOne() {
        var result = evaluate(Map.of("unemploymentBenefit", "ENDED_WITHIN_6M"));
        assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(type(result).explanation()).contains("Ⅱ유형", "6개월").doesNotContain("요건심사형");
        assertThat(evaluate(Map.of("unemploymentBenefit", "RECEIVING")).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
    }

    @Test @DisplayName("취업·재학·재참여 제한은 해당하면 불충족, 예외 확인이 필요하면 추가 확인이다")
    void distinguishesRestrictionsAndExceptions() {
        for (var rejected : List.of(Map.of("employment", "WAGE_30H_OR_MORE"), Map.of("employment", "FREELANCE_250_OR_MORE"),
                Map.of("school", "ENROLLED"), Map.of("pastParticipation", "ENDED_WITHIN_1Y"), Map.of("pastParticipation", "PARTICIPATING"))) {
            assertThat(evaluate(rejected).commonCriteriaStatus()).as(rejected.toString()).isEqualTo(INELIGIBLE);
        }
        for (var accepted : List.of(Map.of("employment", "WAGE_UNDER_30H"), Map.of("employment", "INCOME_UNDER_250"),
                Map.of("school", "FINAL_SEMESTER"), Map.of("school", "FLEXIBLE_SCHOOL"), Map.of("pastParticipation", "ENDED_OVER_3Y"))) {
            assertThat(evaluate(accepted).commonCriteriaStatus()).as(accepted.toString()).isEqualTo(ELIGIBLE);
        }
        for (var pending : List.of(Map.of("employment", "BUSINESS_250_OR_MORE"), Map.of("school", "ON_LEAVE"),
                Map.of("school", "HIGH_SCHOOL_GRADUATING"), Map.of("pastParticipation", "ENDED_1_TO_3Y"))) {
            assertThat(evaluate(pending).commonCriteriaStatus()).as(pending.toString()).isEqualTo(NEEDS_REVIEW);
        }
    }

    @Test @DisplayName("생년월일은 서울 날짜의 청년 구간만 채우고 만 35세 이상은 병역 가산·만 70세 이상을 확인하게 한다")
    void comparesBirthOnSeoulDate() {
        Map.of("2011-10-12", MET, "2011-10-13", NOT_MET, "1991-10-13", MET, "1991-10-12", UNKNOWN)
                .forEach((birth, expected) -> assertThat(age(birth, NOW).outcome()).as(birth).isEqualTo(expected));
        assertThat(age("1991-10-13", Instant.parse("2026-10-12T14:59:59Z")).outcome()).isEqualTo(MET);
        assertThat(age("1991-10-13", Instant.parse("2026-10-12T15:00:00Z")).explanation()).contains("병역", "만 70세 이상");
        assertThat(evaluate(Map.of("age", "AGE_35_TO_69")).commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(evaluate(Map.of("age", "EXTENSION_PENDING")).commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        for (var rejected : List.of("OVER_69", "UNDER_15")) {
            assertThat(evaluate(Map.of("age", rejected)).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        }
    }

    @Test @DisplayName("미응답은 미확인이며 2026년 기준을 쓰는 동안만 질문과 기본 연령을 적용한다")
    void limitsReviewedYearAndKeepsMissingAnswersUnknown() {
        var rule = PolicyRuleFixtures.rule(NUMBER);
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            rule.validate(factory.getValidator());
        }
        assertThat(rule.evaluate(1, new Request(1, VERSION, List.of()), NOW).checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(rule.appliesAt(Instant.parse("2025-12-31T15:00:00Z"))).isTrue();
        assertThat(rule.appliesAt(Instant.parse("2026-12-31T14:59:59Z"))).isTrue();
        var input = new BasicConditions(LocalDate.parse("2000-01-01"), kr.youthpolicymate.eligibility.SeoulDistrict.GANGNAM, BasicConditions.EmploymentStatus.NOT_EMPLOYED);
        for (var value : List.of("2025-12-31T14:59:59Z", "2026-12-31T15:00:00Z")) {
            var now = Instant.parse(value);
            assertThat(rule.appliesAt(now)).isFalse();
            assertThat(PolicyRuleFixtures.comparisons(input, now)).doesNotContainKey(NUMBER);
        }
    }

    private Check age(String birth, Instant now) {
        return PolicyRuleFixtures.age(NUMBER, LocalDate.parse(birth), now);
    }

    private String typeExplanation(Map<String, String> changes) {
        return type(evaluate(changes)).explanation();
    }

    private static Check type(Evaluation result) {
        return result.checks().stream().filter(check -> check.label().equals("참여 유형")).findFirst().orElseThrow();
    }

    private Evaluation evaluate(Map<String, String> changes) {
        var values = new HashMap<>(ANSWERS);
        values.putAll(changes);
        return evaluateExactly(values);
    }

    private Evaluation evaluateExactly(Map<String, String> values) {
        return PolicyRuleFixtures.rule(NUMBER).evaluate(1, new Request(1, VERSION,
                values.entrySet().stream().map(entry -> new Answer(entry.getKey(), entry.getValue())).toList()), NOW);
    }
}
