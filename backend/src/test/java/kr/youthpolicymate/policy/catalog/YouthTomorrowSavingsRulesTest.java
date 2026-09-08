package kr.youthpolicymate.policy.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;
import static kr.youthpolicymate.eligibility.EligibilityStatus.*;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static org.assertj.core.api.Assertions.*;

class YouthTomorrowSavingsRulesTest {
    private static final Instant NOW = Instant.parse("2026-09-08T03:00:00Z");

    @Test @DisplayName("자활근로와 인정된 공공 일자리는 소득을 비교하되 수집 안내 충돌과 최종 심사는 남긴다")
    void acceptsRecognizedWorkWithoutCertifyingEligibility() {
        for (var work : List.of("EMPLOYMENT_OR_BUSINESS", "SELF_RELIANCE", "PUBLIC_WORK_CONFIRMED")) {
            var result = evaluate("IN_RANGE", work, "AT_LEAST_100K", "UP_TO_50_CONFIRMED", "NO_HISTORY");
            assertThat(result.checks()).extracting(Check::outcome).containsExactly(MET, MET, MET, MET);
            assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
            assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
            assertThat(result.remainingChecks()).anyMatch(s -> s.contains("소득·연령 기준 차이"))
                    .anyMatch(s -> s.contains("선정 심사"));
            assertThat(result.explanation()).contains("마감", "신규 가입 조건만");
        }
    }

    @Test @DisplayName("월 10만 원 경계와 소득 유형을 함께 보고 인정 여부나 증빙 확인 중이면 미확인으로 남긴다")
    void separatesWorkRecognitionAndIncomeAmount() {
        assertThat(evaluate("IN_RANGE", "EMPLOYMENT_OR_BUSINESS", "BELOW_100K", "UP_TO_50_CONFIRMED", "NO_HISTORY")
                .checks().get(1).outcome()).isEqualTo(NOT_MET);
        for (var work : List.of("EXCLUDED_ONLY", "UNPAID_ONLY", "NO_WORK")) {
            assertThat(evaluate("IN_RANGE", work, "AT_LEAST_100K", "UP_TO_50_CONFIRMED", "NO_HISTORY")
                    .checks().get(1).outcome()).isEqualTo(NOT_MET);
        }
        var pendingWork = evaluate("IN_RANGE", "PUBLIC_WORK_PENDING", "AT_LEAST_100K", "UP_TO_50_CONFIRMED", "NO_HISTORY");
        assertThat(pendingWork.checks().get(1).outcome()).isEqualTo(UNKNOWN);
        assertThat(pendingWork.checks().get(1).explanation()).contains("인건비 지원 방식", "별도 채용");
        var pendingIncome = evaluate("IN_RANGE", "SELF_RELIANCE", "DOCUMENTS_PENDING", "UP_TO_50_CONFIRMED", "NO_HISTORY");
        assertThat(pendingIncome.checks().get(1).outcome()).isEqualTo(UNKNOWN);
        assertThat(pendingIncome.checks().get(1).explanation()).contains("0원으로 처리하지 않아요");
    }

    @Test @DisplayName("가구 소득인정액의 50% 가입 기준을 적용하고 유지 기준이나 추정 소득으로 대체하지 않는다")
    void usesEnrollmentHouseholdIncomeThreshold() {
        var over = evaluate("IN_RANGE", "EMPLOYMENT_OR_BUSINESS", "AT_LEAST_100K", "OVER_50_CONFIRMED", "ALLOWED_CONFIRMED");
        assertThat(over.checks()).extracting(Check::outcome).containsExactly(MET, MET, NOT_MET, MET);
        assertThat(over.commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        assertThat(over.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(over.checks().get(2).evidence()).contains("50%", "유지 기준과 달라요");
        var pending = evaluate("IN_RANGE", "EMPLOYMENT_OR_BUSINESS", "AT_LEAST_100K", "ASSESSMENT_PENDING", "NO_HISTORY");
        assertThat(pending.checks().get(2).outcome()).isEqualTo(UNKNOWN);
        assertThat(pending.commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
    }

    @Test @DisplayName("기관이 확인한 중복참여 제한은 반영하고 가구원 가입·환수 등 미확인 이력은 단정하지 않는다")
    void keepsParticipationExceptionsIndependent() {
        var restricted = evaluate("IN_RANGE", "EMPLOYMENT_OR_BUSINESS", "AT_LEAST_100K", "UP_TO_50_CONFIRMED", "RESTRICTED_CONFIRMED");
        assertThat(restricted.checks().get(3).outcome()).isEqualTo(NOT_MET);
        var pending = evaluate("IN_RANGE", "EMPLOYMENT_OR_BUSINESS", "AT_LEAST_100K", "UP_TO_50_CONFIRMED", "HISTORY_PENDING");
        assertThat(pending.checks().get(3).outcome()).isEqualTo(UNKNOWN);
        var outsideAge = evaluate("OUTSIDE_RANGE", "SELF_RELIANCE", "AT_LEAST_100K", "UP_TO_50_CONFIRMED", "ALLOWED_CONFIRMED");
        assertThat(outsideAge.checks()).extracting(Check::outcome).containsExactly(NOT_MET, MET, MET, MET);
        assertThat(outsideAge.checks().getFirst().evidence()).contains("1986.5.1.~2011.5.31.");
    }

    @Test @DisplayName("미응답과 모름은 미확인으로 남기고 제출한 답변만 표시한다")
    void preservesMissingAnswers() {
        var unanswered = YouthTomorrowSavingsRules.evaluate(1, new Request(1, YouthTomorrowSavingsRules.VERSION, List.of()), NOW);
        assertThat(unanswered.checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(unanswered.checks().getFirst().providedValue()).isEqualTo("미응답");
        assertThat(evaluate("UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN").checks())
                .extracting(Check::outcome).containsOnly(UNKNOWN);
    }

    @Test @DisplayName("접수 마지막 날과 다음 날을 서울 날짜로 구분하고 다음 연도에는 규칙을 사용하지 않는다")
    void separatesRecruitmentAndReviewedYear() {
        assertThat(YouthTomorrowSavingsRules.periodNotice(Instant.parse("2026-05-03T14:59:59Z"))).contains("접수 전");
        assertThat(YouthTomorrowSavingsRules.periodNotice(Instant.parse("2026-05-03T15:00:00Z"))).doesNotContain("접수 전");
        assertThat(YouthTomorrowSavingsRules.periodNotice(Instant.parse("2026-05-20T14:59:59Z"))).doesNotContain("마감");
        assertThat(YouthTomorrowSavingsRules.periodNotice(Instant.parse("2026-05-20T15:00:00Z"))).contains("마감");
        assertThat(YouthTomorrowSavingsRules.appliesAt(Instant.parse("2025-12-31T14:59:59Z"))).isFalse();
        assertThat(YouthTomorrowSavingsRules.appliesAt(Instant.parse("2025-12-31T15:00:00Z"))).isTrue();
        assertThat(YouthTomorrowSavingsRules.appliesAt(Instant.parse("2026-12-31T14:59:59Z"))).isTrue();
        var nextYear = Instant.parse("2026-12-31T15:00:00Z");
        assertThat(YouthTomorrowSavingsRules.appliesAt(nextYear)).isFalse();
        assertThatThrownBy(() -> YouthTomorrowSavingsRules.evaluate(1, new Request(1, YouthTomorrowSavingsRules.VERSION, List.of()), nextYear))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("기본 생년월일은 모집 출생일 범위의 양 끝 날짜를 포함하고 하루 밖은 불충족으로 비교한다")
    void comparesBirthDateBoundariesForBasicConditions() {
        var cases = java.util.Map.of("1986-04-30", NOT_MET, "1986-05-01", MET, "2011-05-31", MET, "2011-06-01", NOT_MET);
        cases.forEach((date, expected) -> {
            var result = YouthTomorrowSavingsRules.ageCheck(java.time.LocalDate.parse(date));
            assertThat(result.outcome()).as(date).isEqualTo(expected);
            assertThat(result.evidence()).contains("2026년 5월", "1986.5.1.~2011.5.31.");
        });
    }

    private Evaluation evaluate(String age, String work, String income, String household, String participation) {
        return YouthTomorrowSavingsRules.evaluate(1, new Request(1, YouthTomorrowSavingsRules.VERSION, List.of(
                new Answer("birthRange", age), new Answer("workType", work), new Answer("monthlyIncome", income),
                new Answer("householdIncome", household), new Answer("duplicateParticipation", participation))), NOW);
    }
}
