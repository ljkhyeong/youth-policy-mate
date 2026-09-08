package kr.youthpolicymate.policy.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;
import static kr.youthpolicymate.eligibility.EligibilityStatus.*;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static org.assertj.core.api.Assertions.*;

class HaetsalronYouthRulesTest {
    private static final Instant NOW = Instant.parse("2026-09-08T03:00:00Z");
    private static final Map<String, String> ANSWERS = Map.of("age", "AGE_19_TO_34", "applicantType", "PREPARING_CONFIRMED",
            "incomeBasis", "CONFIRMED", "annualIncome", "UP_TO_35M", "lifetimeLimit", "REMAINING_CONFIRMED");

    @Test @DisplayName("취업준비생·사회초년생·청년사업자 요건을 비교하되 보증과 대출 심사는 남긴다")
    void comparesReviewedTypesWithoutPromisingApproval() {
        for (var type : List.of("PREPARING_CONFIRMED", "EARLY_EMPLOYEE", "YOUNG_BUSINESS")) {
            var result = evaluate(Map.of("applicantType", type));
            assertThat(result.checks()).extracting(Check::outcome).containsExactly(MET, MET, MET, MET);
            assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
            assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
            assertThat(result.remainingChecks()).anyMatch(s -> s.contains("재산 보유"))
                    .anyMatch(s -> s.contains("기간별·용도별 한도"))
                    .anyMatch(s -> s.contains("은행 대출심사"));
        }
        assertThat(evaluate(Map.of("applicantType", "NOT_TARGET_CONFIRMED")).checks().get(1).outcome()).isEqualTo(NOT_MET);
        assertThat(evaluate(Map.of("applicantType", "PENDING")).checks().get(1).outcome()).isEqualTo(UNKNOWN);
    }

    @Test @DisplayName("소득 상한 초과는 반영하되 증빙·산정 기준 미확인은 금액으로 대신하지 않는다")
    void requiresConfirmedIncomeBasis() {
        var over = evaluate(Map.of("annualIncome", "OVER_35M"));
        assertThat(over.checks().get(2).outcome()).isEqualTo(NOT_MET);
        assertThat(over.commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        assertThat(over.status()).isEqualTo(NEEDS_REVIEW);
        for (var amount : List.of("UP_TO_35M", "OVER_35M", "UNKNOWN")) {
            assertThat(evaluate(Map.of("incomeBasis", "PENDING", "annualIncome", amount)).checks().get(2).outcome()).isEqualTo(UNKNOWN);
        }
        assertThat(evaluate(Map.of("annualIncome", "UNKNOWN")).checks().get(2).outcome()).isEqualTo(UNKNOWN);
    }

    @Test @DisplayName("생애 한도 소진과 확인 중을 구분하고 상환으로 복원하지 않음을 안내한다")
    void keepsLifetimeLimitSeparateFromOutstandingDebt() {
        var used = evaluate(Map.of("lifetimeLimit", "EXHAUSTED_CONFIRMED"));
        assertThat(used.checks().getLast().outcome()).isEqualTo(NOT_MET);
        assertThat(used.checks().getLast().evidence()).contains("1,200만 원", "상환해도", "복원되지");
        assertThat(evaluate(Map.of("lifetimeLimit", "PENDING")).checks().getLast().outcome()).isEqualTo(UNKNOWN);
    }

    @Test @DisplayName("만 19세 생일부터 충족하고 만 35세 생일에는 불충족이며 질문과 결과가 같다")
    void comparesAgeBoundaries() {
        var dates = Map.of("2007-09-09", NOT_MET, "2007-09-08", MET, "1991-09-09", MET, "1991-09-08", NOT_MET);
        dates.forEach((date, expected) -> {
            var basic = HaetsalronYouthRules.ageCheck(LocalDate.parse(date), NOW);
            assertThat(basic.outcome()).as(date).isEqualTo(expected);
            assertThat(basic.providedValue()).contains("2026-09-08", "서울");
            assertThat(basic.evidence()).contains("연령 상한 연장이 아니에요");
        });
        for (var answer : List.of("UNDER_19", "OVER_34")) {
            assertThat(evaluate(Map.of("age", answer)).checks().getFirst().outcome()).isEqualTo(NOT_MET);
        }
        var birthday = LocalDate.parse("2007-09-08");
        assertThat(HaetsalronYouthRules.ageCheck(birthday, Instant.parse("2026-09-07T14:59:59Z")).outcome()).isEqualTo(NOT_MET);
        assertThat(HaetsalronYouthRules.ageCheck(birthday, Instant.parse("2026-09-07T15:00:00Z")).outcome()).isEqualTo(MET);
    }

    @Test @DisplayName("미응답은 미확인으로 남기고 서울 날짜의 검토 연도를 벗어나면 재사용하지 않는다")
    void keepsMissingAnswersAndReviewedYear() {
        var empty = new Request(1, HaetsalronYouthRules.VERSION, List.of());
        var result = HaetsalronYouthRules.evaluate(1, empty, NOW);
        assertThat(result.checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(result.checks().getFirst().providedValue()).isEqualTo("미응답");
        assertThat(HaetsalronYouthRules.appliesAt(Instant.parse("2025-12-31T14:59:59Z"))).isFalse();
        assertThat(HaetsalronYouthRules.appliesAt(Instant.parse("2025-12-31T15:00:00Z"))).isTrue();
        assertThat(HaetsalronYouthRules.appliesAt(Instant.parse("2026-12-31T14:59:59Z"))).isTrue();
        var nextYear = Instant.parse("2026-12-31T15:00:00Z");
        assertThat(HaetsalronYouthRules.appliesAt(nextYear)).isFalse();
        assertThatThrownBy(() -> HaetsalronYouthRules.evaluate(1, empty, nextYear)).isInstanceOf(IllegalArgumentException.class);
    }

    private Evaluation evaluate(Map<String, String> changes) {
        var values = new HashMap<>(ANSWERS);
        values.putAll(changes);
        return HaetsalronYouthRules.evaluate(1, new Request(1, HaetsalronYouthRules.VERSION,
                values.entrySet().stream().map(e -> new Answer(e.getKey(), e.getValue())).toList()), NOW);
    }
}
