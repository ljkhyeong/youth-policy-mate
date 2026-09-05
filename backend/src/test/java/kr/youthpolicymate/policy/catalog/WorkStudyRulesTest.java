package kr.youthpolicymate.policy.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;
import static kr.youthpolicymate.eligibility.EligibilityStatus.*;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static org.assertj.core.api.Assertions.*;

class WorkStudyRulesTest {
    private static final Instant NOW = Instant.parse("2026-09-05T01:00:00Z");

    @Test @DisplayName("공통요건을 충족해도 대학별 검토가 남은 전체 자격은 추가 확인으로 유지한다")
    void separatesCommonCriteriaFromWholePolicy() {
        var result = evaluate("YES", "AT_LEAST_70", "UNKNOWN", "UP_TO_9", "UNKNOWN");
        assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(result.checks()).extracting(Check::outcome).containsOnly(MET);
        assertThat(result.remainingChecks()).isNotEmpty();
        assertThat(result.revision()).isEqualTo(2);
        assertThat(result.ruleVersion()).isEqualTo(WorkStudyRules.VERSION);
        assertThat(result.evaluatedAt()).isEqualTo(NOW);
    }

    @Test @DisplayName("기준 미달이면서 예외 여부를 모르면 불충족으로 확정하지 않는다")
    void keepsUnconfirmedExceptionsUnknown() {
        var result = evaluate("YES", "BELOW_70", "UNKNOWN", "ABOVE_9", "UNKNOWN");
        assertThat(result.commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        assertThat(result.checks().get(2).outcome()).isEqualTo(UNKNOWN);
        assertThat(result.checks().get(3).outcome()).isEqualTo(UNKNOWN);
    }

    @Test @DisplayName("기준 미달과 적용 제외 대상 아님을 함께 답한 경우 해당 공통요건만 불충족이다")
    void comparesConfirmedFailures() {
        var result = evaluate("YES", "BELOW_70", "NONE", "ABOVE_9", "NONE");
        assertThat(result.commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(result.checks().get(2).outcome()).isEqualTo(NOT_MET);
        assertThat(result.checks().get(3).outcome()).isEqualTo(NOT_MET);
    }

    @Test @DisplayName("성적 없음·지원구간 미산정은 기준 미달로 취급하지 않는다")
    void doesNotInventMissingValues() {
        var result = evaluate("YES", "NOT_ISSUED", "NONE", "NOT_CALCULATED", "NONE");
        assertThat(result.commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        assertThat(result.checks().get(2).outcome()).isEqualTo(UNKNOWN);
        assertThat(result.checks().get(3).outcome()).isEqualTo(UNKNOWN);
        var empty = WorkStudyRules.evaluate(2, new Request(2, WorkStudyRules.VERSION, List.of()), NOW);
        assertThat(empty.checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
    }

    @Test @DisplayName("적용 제외를 확인받았다는 답변에 따라 해당 기준을 제외한다")
    void usesConfirmedExceptionAnswer() {
        var result = evaluate("YES", "BELOW_70", "CONFIRMED", "ABOVE_9", "CONFIRMED");
        assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(result.checks().get(2).explanation()).contains("적용 제외를 확인받았다는 답변에 따라");
        assertThat(evaluate("NO", "AT_LEAST_70", "UNKNOWN", "UP_TO_9", "UNKNOWN").checks().getFirst().outcome()).isEqualTo(NOT_MET);
    }

    @Test @DisplayName("없는 질문·지원하지 않는 답변·중복 답변은 비교 전에 거절한다")
    void rejectsInvalidAnswers() {
        for (var answers : List.of(List.of(new Answer("salary", "5000000")), List.of(new Answer("income", "salary")),
                List.of(new Answer("income", "UP_TO_9"), new Answer("income", "ABOVE_9")))) {
            assertThatThrownBy(() -> WorkStudyRules.evaluate(2, new Request(2, WorkStudyRules.VERSION, answers), NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private Evaluation evaluate(String nationality, String grade, String gradeException, String income, String incomeException) {
        return WorkStudyRules.evaluate(2, new Request(2, WorkStudyRules.VERSION, List.of(new Answer("nationality", nationality),
                new Answer("enrollment", "YES"), new Answer("grade", grade), new Answer("gradeException", gradeException),
                new Answer("income", income), new Answer("incomeException", incomeException))), NOW);
    }
}
