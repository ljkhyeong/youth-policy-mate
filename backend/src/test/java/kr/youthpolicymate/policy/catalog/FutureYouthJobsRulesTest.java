package kr.youthpolicymate.policy.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static kr.youthpolicymate.eligibility.EligibilityStatus.*;
import static org.assertj.core.api.Assertions.*;

class FutureYouthJobsRulesTest {
    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");
    private static final Map<String, String> ANSWERS = Map.of("birthRange", "BASE_RANGE", "residence", "SEOUL",
            "employment", "NOT_WORKING", "education", "NOT_ENROLLED", "business", "NONE", "publicJob", "NO");

    @Test @DisplayName("근로시간과 계약기간 중 하나만 예외에 해당해도 근로 조건을 충족한다")
    void acceptsEitherEmploymentException() {
        for (var value : List.of("NOT_WORKING", "UP_TO_30_HOURS", "UNDER_3_MONTHS")) {
            var result = evaluate(Map.of("employment", value));
            assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
            assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
            assertThat(result.remainingChecks()).anyMatch(text -> text.contains("증빙"))
                    .anyMatch(text -> text.contains("직무별"));
        }
        assertThat(evaluate(Map.of("employment", "OVER_LIMITS")).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        assertThat(evaluate(Map.of("employment", "UNKNOWN")).commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
    }

    @Test @DisplayName("재학·휴학과 사업자등록을 일괄 제외하지 않고 확인된 증빙 예외를 반영한다")
    void distinguishesEducationAndBusinessExceptions() {
        for (var business : List.of("NONE", "INACTIVE_CONFIRMED", "RENTAL_EXCEPTION_CONFIRMED")) {
            assertThat(evaluate(Map.of("education", "EXCEPTION_CONFIRMED", "business", business)).commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        }
        for (var rejected : List.of(Map.of("education", "EXCLUDED_CONFIRMED"), Map.of("business", "ACTIVE_NO_EXCEPTION"),
                Map.of("residence", "OUTSIDE"), Map.of("publicJob", "YES"))) {
            assertThat(evaluate(rejected).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        }
        for (var pending : List.of("education", "business", "residence", "publicJob")) {
            assertThat(evaluate(Map.of(pending, "UNKNOWN")).commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        }
    }

    @Test @DisplayName("공고 출생일 양끝을 포함하고 연령 상한 초과 시 군복무 예외를 미확인으로 남긴다")
    void preservesAgeBoundariesAndMilitaryException() {
        Map.of("1985-12-31", UNKNOWN, "1986-01-01", MET, "2007-12-31", MET, "2008-01-01", NOT_MET)
                .forEach((birth, expected) -> {
                    var input = new BasicConditions(LocalDate.parse(birth), "강남구", BasicConditions.EmploymentStatus.EMPLOYED);
                    var comparison = PolicyRuleFixtures.comparisons(input, NOW).get(FutureYouthJobsRules.NUMBER);
                    assertThat(comparison.age().outcome()).as(birth).isEqualTo(expected);
                    assertThat(comparison.periodNotice()).contains("5월", "마감");
                });
        assertThat(evaluate(Map.of("birthRange", "EXTENSION_CONFIRMED")).commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(evaluate(Map.of("birthRange", "EXTENSION_PENDING")).commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        assertThat(evaluate(Map.of("birthRange", "OLDER_NOT_ELIGIBLE")).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
    }

    @Test @DisplayName("모집 전과 마감 시점을 구분하고 원문 변경 시 기존 접수 기간을 재사용하지 않는다")
    void usesApplicationPeriodInsteadOfAnnouncementPeriod() {
        var raw = tools.jackson.databind.json.JsonMapper.builder().build().createObjectNode()
                .put("aplyPrdSeCd", "0057001").put("aplyYmd", "20260504 ~ 20260531");
        var may4 = Instant.parse("2026-05-03T15:00:00Z");
        assertThat(PolicyRecruitment.from(FutureYouthJobsRules.NUMBER, 1, FutureYouthJobsRules.CONTENT_HASH, raw, may4).status())
                .isEqualTo(kr.youthpolicymate.policy.RecruitmentStatus.BEFORE_OPENING);
        assertThat(PolicyRecruitment.from(FutureYouthJobsRules.NUMBER, 2, "changed", raw, may4).status())
                .isEqualTo(kr.youthpolicymate.policy.RecruitmentStatus.OPEN);
        assertThat(PolicyRuleFixtures.rule(FutureYouthJobsRules.NUMBER).periodNotice().at(Instant.parse("2026-05-17T14:59:59Z"))).contains("접수 전");
        assertThat(PolicyRuleFixtures.rule(FutureYouthJobsRules.NUMBER).periodNotice().at(Instant.parse("2026-05-17T15:00:00Z"))).doesNotContain("접수 전", "마감됐어요");
        assertThat(PolicyRuleFixtures.rule(FutureYouthJobsRules.NUMBER).periodNotice().at(Instant.parse("2026-05-31T14:59:59Z"))).doesNotContain("마감됐어요");
        assertThat(PolicyRuleFixtures.rule(FutureYouthJobsRules.NUMBER).periodNotice().at(Instant.parse("2026-05-31T15:00:00Z"))).contains("마감됐어요");
        var window = PolicyRecruitmentWindow.from(FutureYouthJobsRules.NUMBER, FutureYouthJobsRules.CONTENT_HASH, raw);
        assertThat(window.opensAt().toInstant()).isEqualTo(Instant.parse("2026-05-17T15:00:00Z"));
        assertThat(window.closesAt().toInstant()).isEqualTo(Instant.parse("2026-05-31T15:00:00Z"));
    }

    @Test @DisplayName("미응답은 미확인이며 공고 이전·다음 연도에는 질문과 기본 연령을 적용하지 않는다")
    void limitsReviewedPeriodAndKeepsMissingAnswersUnknown() {
        var request = new Request(1, FutureYouthJobsRules.VERSION, List.of());
        assertThat(PolicyRuleFixtures.rule(FutureYouthJobsRules.NUMBER).evaluate(1, request, NOW).checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(PolicyRuleFixtures.rule(FutureYouthJobsRules.NUMBER).appliesAt(Instant.parse("2026-05-03T15:00:00Z"))).isTrue();
        assertThat(PolicyRuleFixtures.rule(FutureYouthJobsRules.NUMBER).appliesAt(Instant.parse("2026-12-31T14:59:59Z"))).isTrue();
        var input = new BasicConditions(LocalDate.parse("2000-01-01"), "강남구", BasicConditions.EmploymentStatus.NOT_EMPLOYED);
        for (var value : List.of("2026-05-03T14:59:59Z", "2026-12-31T15:00:00Z")) {
            var now = Instant.parse(value);
            assertThat(PolicyRuleFixtures.rule(FutureYouthJobsRules.NUMBER).appliesAt(now)).isFalse();
            assertThat(PolicyRuleFixtures.comparisons(input, now)).doesNotContainKey(FutureYouthJobsRules.NUMBER);
            assertThatThrownBy(() -> PolicyRuleFixtures.rule(FutureYouthJobsRules.NUMBER).evaluate(1, request, now)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private Evaluation evaluate(Map<String, String> changes) {
        var values = new HashMap<>(ANSWERS);
        values.putAll(changes);
        return PolicyRuleFixtures.rule(FutureYouthJobsRules.NUMBER).evaluate(1, new Request(1, FutureYouthJobsRules.VERSION,
                values.entrySet().stream().map(entry -> new Answer(entry.getKey(), entry.getValue())).toList()), NOW);
    }
}
