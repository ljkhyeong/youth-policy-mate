package kr.youthpolicymate.policy.catalog;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
                .anyMatch(s -> s.contains("다른 비용"))
                .anyMatch(s -> s.contains("생계·의료·주거급여"));
        assertThat(result.explanation()).contains("상반기", "마감");
    }

    @Test @DisplayName("주택 소유 예외 미확인은 불충족으로 바꾸지 않고 다른 요건을 면제하지 않는다")
    void preservesHousingException() {
        assertThat(evaluate("EXCEPTION_PENDING").checks().get(3).outcome()).isEqualTo(UNKNOWN);
        assertThat(evaluate("EXCEPTION_PENDING").commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        assertThat(evaluate("OWNS_NO_EXCEPTION").checks().get(3).outcome()).isEqualTo(NOT_MET);
        var request = new Request(1, MovingFeeRules.VERSION, List.of(new Answer("homeOwnership", "EXCEPTION_PENDING"), new Answer("move", "OUTSIDE")));
        var result = MovingFeeRules.evaluate(1, request, NOW);
        assertThat(result.checks()).extracting(Check::outcome).containsExactly(UNKNOWN, NOT_MET, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN);
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
    }

    @Test @DisplayName("공고 보험료 기준 이하·초과를 비교하되 소득 증빙과 최종 자격은 기관 확인으로 남긴다")
    void comparesConfirmedIncomeAgainstTheNotice() {
        var within = evaluate("NO_HOME", "WITHIN_LIMIT");
        var above = evaluate("NO_HOME", "ABOVE_LIMIT");
        assertThat(within.checks().getLast().outcome()).isEqualTo(MET);
        assertThat(above.checks().getLast().outcome()).isEqualTo(NOT_MET);
        assertThat(above.commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        assertThat(above.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(within.checks().getLast().evidence()).contains("2026년 3월", "장기요양보험료 제외", "150%", "피부양자", "부양자");
    }

    @Test @DisplayName("부양자 보험료·가구 기준·대체 증빙 미확인은 0원이나 소득 초과로 바꾸지 않는다")
    void preservesIncomeUncertaintyAndItsReason() {
        for (var pending : List.of("DEPENDENT_PENDING", "HOUSEHOLD_PENDING", "DOCUMENTS_PENDING", "UNKNOWN")) {
            var result = evaluate("NO_HOME", pending);
            assertThat(result.checks().getLast().outcome()).isEqualTo(UNKNOWN);
            assertThat(result.commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
            assertThat(result.checks().subList(0, 5)).extracting(Check::outcome).containsOnly(MET);
        }
        assertThat(evaluate("NO_HOME", "DEPENDENT_PENDING").checks().getLast().explanation()).contains("0원", "부양자", "2026년 3월");
        assertThat(evaluate("NO_HOME", "HOUSEHOLD_PENDING").checks().getLast().explanation()).contains("가구원 수", "가입 유형");
        assertThat(evaluate("NO_HOME", "DOCUMENTS_PENDING").checks().getLast().explanation()).contains("대체 소득 증빙");
        assertThat(evaluate("OWNS_NO_EXCEPTION", "DOCUMENTS_PENDING").checks().get(3).outcome()).isEqualTo(NOT_MET);
    }

    @Test @DisplayName("미응답을 미확인으로 남기며 공통 답변 검증을 적용한다")
    void handlesUnknownAndInvalidAnswers() {
        var result = MovingFeeRules.evaluate(1, new Request(1, MovingFeeRules.VERSION, List.of()), NOW);
        assertThat(result.checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(result.checks()).extracting(Check::providedValue).containsOnly("미응답");
        for (var answers : List.of(List.of(new Answer("income", "LOW")), List.of(new Answer("move", "SEOUL")),
                List.of(new Answer("move", "COMPLETED"), new Answer("move", "OUTSIDE")))) {
            assertThatThrownBy(() -> MovingFeeRules.evaluate(1, new Request(1, MovingFeeRules.VERSION, answers), NOW)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test @DisplayName("접수 시작·마감 시각과 다음 해 질문 만료를 구분한다")
    void boundsApplicationAndReviewedYear() {
        assertThat(MovingFeeRules.questionnaire(1, Instant.parse("2026-04-01T00:59:59Z")).reason()).contains("접수 전");
        assertThat(MovingFeeRules.questionnaire(1, Instant.parse("2026-04-01T01:00:00Z")).reason()).doesNotContain("접수 전", "마감됐어요");
        assertThat(MovingFeeRules.questionnaire(1, Instant.parse("2026-04-14T08:59:59Z")).reason()).doesNotContain("마감됐어요");
        assertThat(MovingFeeRules.questionnaire(1, Instant.parse("2026-04-14T09:00:00Z")).reason()).contains("마감됐어요");
        assertThat(MovingFeeRules.appliesAt(Instant.parse("2026-12-31T14:59:59Z"))).isTrue();
        assertThat(MovingFeeRules.appliesAt(Instant.parse("2026-12-31T15:00:00Z"))).isFalse();
        assertThatThrownBy(() -> MovingFeeRules.evaluate(1, new Request(1, MovingFeeRules.VERSION, List.of()), Instant.parse("2026-12-31T15:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    private Evaluation evaluate(String home) {
        return evaluate(home, "WITHIN_LIMIT");
    }
    private Evaluation evaluate(String home, String income) {
        return MovingFeeRules.evaluate(1, new Request(1, MovingFeeRules.VERSION, List.of(new Answer("birthRange", "IN_RANGE"),
                new Answer("move", "COMPLETED"), new Answer("contract", "ALL"), new Answer("homeOwnership", home),
                new Answer("housingCost", "WITHIN_LIMIT"), new Answer("income", income))), NOW);
    }
}
