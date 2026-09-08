package kr.youthpolicymate.policy.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;
import static kr.youthpolicymate.eligibility.EligibilityStatus.*;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static org.assertj.core.api.Assertions.*;

class GuaranteeFeeRulesTest {
    private static final Instant NOW = Instant.parse("2026-09-08T03:00:00Z");
    private static final Map<String, String> ANSWERS = Map.of("guarantee", "VALID_PAID", "deposit", "UP_TO_300M",
            "homeOwnership", "NO_HOME", "applicantType", "YOUTH", "incomeBasis", "CONFIRMED", "annualIncome", "UP_TO_50M");

    @Test @DisplayName("확인한 조건이 모두 충족해도 서류·중복지원·예산 심사는 남긴다")
    void leavesFinalReviewAndPaymentUndecided() {
        var result = evaluate(Map.of());
        assertThat(result.checks()).extracting(Check::outcome).containsExactly(MET, MET, MET, MET);
        assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(result.remainingChecks()).anyMatch(s -> s.contains("동일 보증서"))
                .anyMatch(s -> s.contains("예산 소진"));
        assertThat(GuaranteeFeeRules.questionnaire(1).reason()).contains("청년 외 연령도 대상");
    }

    @Test @DisplayName("청년·청년 외·신혼부부별 소득 상한을 포함하고 초과 구간을 구분한다")
    void comparesIncomeByApplicantType() {
        var amounts = List.of("UP_TO_50M", "OVER_50_TO_60M", "OVER_60_TO_75M", "OVER_75M");
        var expected = Map.of("YOUTH", List.of(MET, NOT_MET, NOT_MET, NOT_MET),
                "OTHER", List.of(MET, MET, NOT_MET, NOT_MET), "NEWLYWED", List.of(MET, MET, MET, NOT_MET));
        expected.forEach((type, outcomes) -> {
            for (int i = 0; i < amounts.size(); i++) {
                var result = evaluate(Map.of("applicantType", type, "annualIncome", amounts.get(i)));
                assertThat(result.checks().getLast().outcome()).as(type + " / " + amounts.get(i)).isEqualTo(outcomes.get(i));
                assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
            }
        });
    }

    @Test @DisplayName("소득 기준·지원 유형·금액을 모르면 낮거나 높은 금액도 임의로 판정하지 않는다")
    void requiresIncomeBasisAndType() {
        for (var basis : List.of("PENDING", "UNKNOWN")) {
            for (var amount : List.of("UP_TO_50M", "OVER_75M")) {
                var result = evaluate(Map.of("incomeBasis", basis, "annualIncome", amount));
                assertThat(result.checks().getLast().outcome()).isEqualTo(UNKNOWN);
                assertThat(result.checks().getLast().explanation()).contains("합산 범위·대상 연도·증빙");
            }
        }
        assertThat(evaluate(Map.of("applicantType", "UNKNOWN")).checks().getLast().outcome()).isEqualTo(UNKNOWN);
        assertThat(evaluate(Map.of("annualIncome", "UNKNOWN")).checks().getLast().outcome()).isEqualTo(UNKNOWN);
    }

    @Test @DisplayName("미가입·만료·미납은 가입 확인 중과 구분하고 보증금 초과·소유 주택도 반영한다")
    void comparesGuaranteeDepositAndOwnership() {
        for (var answer : List.of("NOT_JOINED", "EXPIRED", "UNPAID")) {
            var result = evaluate(Map.of("guarantee", answer));
            assertThat(result.checks().getFirst().outcome()).isEqualTo(NOT_MET);
            assertThat(result.commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        }
        assertThat(evaluate(Map.of("guarantee", "PENDING")).checks().getFirst().outcome()).isEqualTo(UNKNOWN);
        assertThat(evaluate(Map.of("deposit", "OVER_300M")).checks().get(1).outcome()).isEqualTo(NOT_MET);
        var home = evaluate(Map.of("homeOwnership", "OWNS_HOME")).checks().get(2);
        assertThat(home.outcome()).isEqualTo(NOT_MET);
        assertThat(home.evidence()).contains("배우자", "분양권·입주권");
    }

    @Test @DisplayName("빈 답변은 미응답과 미확인으로 남긴다")
    void keepsMissingAnswersUnknown() {
        var result = GuaranteeFeeRules.evaluate(1, new Request(1, GuaranteeFeeRules.VERSION, List.of()), NOW);
        assertThat(result.checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(result.checks().getFirst().providedValue()).isEqualTo("미응답");
    }

    @Test @DisplayName("서울 날짜로 검토 연도를 제한하고 다음 해 규칙으로 재사용하지 않는다")
    void limitsReviewedYear() {
        assertThat(GuaranteeFeeRules.appliesAt(Instant.parse("2025-12-31T14:59:59Z"))).isFalse();
        assertThat(GuaranteeFeeRules.appliesAt(Instant.parse("2025-12-31T15:00:00Z"))).isTrue();
        assertThat(GuaranteeFeeRules.appliesAt(Instant.parse("2026-12-31T14:59:59Z"))).isTrue();
        var nextYear = Instant.parse("2026-12-31T15:00:00Z");
        assertThat(GuaranteeFeeRules.appliesAt(nextYear)).isFalse();
        assertThatThrownBy(() -> GuaranteeFeeRules.evaluate(1, new Request(1, GuaranteeFeeRules.VERSION, List.of()), nextYear))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Evaluation evaluate(Map<String, String> changes) {
        var values = new HashMap<>(ANSWERS);
        values.putAll(changes);
        return GuaranteeFeeRules.evaluate(1, new Request(1, GuaranteeFeeRules.VERSION,
                values.entrySet().stream().map(e -> new Answer(e.getKey(), e.getValue())).toList()), NOW);
    }
}
