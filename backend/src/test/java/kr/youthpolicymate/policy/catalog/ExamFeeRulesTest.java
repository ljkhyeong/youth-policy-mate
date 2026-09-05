package kr.youthpolicymate.policy.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;
import static kr.youthpolicymate.eligibility.EligibilityStatus.*;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static org.assertj.core.api.Assertions.*;

class ExamFeeRulesTest {
    private static final Instant NOW = Instant.parse("2026-09-05T01:00:00Z");
    @Test @DisplayName("공고의 출생일 범위와 대상 시험·남은 횟수만 비교하고 예산·접수는 확인 사항으로 남긴다")
    void separatesSupportCriteriaFromApplication() {
        var result = evaluate("ON_OR_AFTER_1991_01_01", "HRDK_TECHNICAL", "ONE");
        assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(result.checks()).extracting(Check::outcome).containsOnly(MET);
        assertThat(result.checks().getFirst().evidence()).contains("1991년 1월 1일", "오늘의 만 나이");
        assertThat(result.checks().getFirst().providedValue()).isEqualTo("1991년 1월 1일 또는 그 이후");
        assertThat(result.remainingChecks()).anyMatch(s -> s.contains("예산 소진"));
        assertThat(result.ruleVersion()).isEqualTo(ExamFeeRules.VERSION);
        assertThat(result.evaluatedAt()).isEqualTo(NOW);
    }
    @Test @DisplayName("연령 범위 밖·대상 외 시험·남은 횟수 없음은 해당 항목만 불충족으로 표시한다")
    void comparesExplicitFailures() {
        var age = evaluate("BEFORE_1991_01_01", "HRDK_TECHNICAL", "THREE");
        var exam = evaluate("ON_OR_AFTER_1991_01_01", "OTHER", "TWO");
        var quota = evaluate("ON_OR_AFTER_1991_01_01", "HRDK_TECHNICAL", "ZERO");
        assertThat(age.checks()).extracting(Check::outcome).containsExactly(NOT_MET, MET, MET);
        assertThat(exam.checks()).extracting(Check::outcome).containsExactly(MET, NOT_MET, MET);
        assertThat(quota.checks()).extracting(Check::outcome).containsExactly(MET, MET, NOT_MET);
        assertThat(List.of(age, exam, quota)).extracting(Evaluation::commonCriteriaStatus).containsOnly(INELIGIBLE);
    }
    @Test @DisplayName("모름·미응답과 취소 후 횟수 복구 중을 남은 횟수 또는 불충족으로 계산하지 않는다")
    void keepsUnconfirmedCountsUnknown() {
        var restoring = evaluate("ON_OR_AFTER_1991_01_01", "HRDK_TECHNICAL", "RESTORING");
        assertThat(restoring.commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        assertThat(restoring.checks().get(2).outcome()).isEqualTo(UNKNOWN);
        assertThat(restoring.checks().get(2).explanation()).contains("복구됐는지 확인");
        assertThat(evaluate("UNKNOWN", "UNKNOWN", "UNKNOWN").checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(ExamFeeRules.evaluate(1, new Request(1, ExamFeeRules.VERSION, List.of()), NOW).checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
    }
    @Test @DisplayName("검토한 연도는 서울 자정으로 구분하며 다음 해에 출생일과 연간 한도를 재사용하지 않는다")
    void boundsReviewedYearInSeoul() {
        assertThat(ExamFeeRules.appliesAt(Instant.parse("2025-12-31T14:59:59Z"))).isFalse();
        assertThat(ExamFeeRules.appliesAt(Instant.parse("2025-12-31T15:00:00Z"))).isTrue();
        assertThat(ExamFeeRules.appliesAt(Instant.parse("2026-12-31T14:59:59Z"))).isTrue();
        assertThat(ExamFeeRules.appliesAt(Instant.parse("2026-12-31T15:00:00Z"))).isFalse();
        assertThatThrownBy(() -> ExamFeeRules.evaluate(1, new Request(1, ExamFeeRules.VERSION, List.of()), Instant.parse("2026-12-31T15:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("다른 정책의 질문·임의의 횟수·같은 질문의 중복 답변은 거절한다")
    void rejectsUnsupportedAndDuplicateAnswers() {
        for (var answers : List.of(List.of(new Answer("income", "UP_TO_9")), List.of(new Answer("remainingUses", "FOUR")),
                List.of(new Answer("remainingUses", "ONE"), new Answer("remainingUses", "ZERO")))) {
            assertThatThrownBy(() -> ExamFeeRules.evaluate(1, new Request(1, ExamFeeRules.VERSION, answers), NOW)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    private Evaluation evaluate(String birth, String exam, String remaining) {
        return ExamFeeRules.evaluate(1, new Request(1, ExamFeeRules.VERSION,
                List.of(new Answer("birthRange", birth), new Answer("exam", exam), new Answer("remainingUses", remaining))), NOW);
    }
}
