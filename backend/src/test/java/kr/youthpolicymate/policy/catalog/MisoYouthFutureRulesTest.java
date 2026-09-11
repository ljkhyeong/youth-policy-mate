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

class MisoYouthFutureRulesTest {
    private static final Instant NOW = Instant.parse("2026-09-11T03:00:00Z");
    private static final Map<String, String> ANSWERS = Map.of("age", "AGE_19_TO_34", "employment", "UNEMPLOYED",
            "credit", "NO", "welfare", "NO", "earnedIncomeCredit", "NO");

    @Test @DisplayName("세 지원 요건은 각각 하나만 충족해도 되며 여신심사는 남긴다")
    void anyOneCriterionIsSufficient() {
        for (var criterion : List.of("credit", "welfare", "earnedIncomeCredit")) {
            var result = evaluate(Map.of(criterion, "YES"));
            assertThat(result.checks()).extracting(Check::outcome).containsExactly(MET, MET, MET);
            assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
            assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
            assertThat(result.remainingChecks()).anyMatch(s -> s.contains("성실상환·면책 예외"))
                    .anyMatch(s -> s.contains("자금 용도"))
                    .anyMatch(s -> s.contains("재무상담"));
        }
        var otherUnknown = evaluate(Map.of("credit", "UNKNOWN", "welfare", "YES", "earnedIncomeCredit", "UNKNOWN"));
        assertThat(otherUnknown.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
    }

    @Test @DisplayName("미확인 요건이 남으면 비해당으로 단정하지 않고 모두 비해당일 때만 불충족이다")
    void unknownAlternativeIsNotRejected() {
        for (var criterion : List.of("credit", "welfare", "earnedIncomeCredit")) {
            assertThat(evaluate(Map.of(criterion, "UNKNOWN")).commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        }
        assertThat(evaluate(Map.of()).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        var result = MisoYouthFutureRules.evaluate(1, new Request(1, MisoYouthFutureRules.VERSION,
                List.of(new Answer("age", "AGE_19_TO_34"), new Answer("employment", "UNEMPLOYED"))), NOW);
        assertThat(result.checks().getLast().outcome()).isEqualTo(UNKNOWN);
        assertThat(result.checks().getLast().providedValue()).contains("미응답");
    }

    @Test @DisplayName("미취업과 취·창업 초기 유형을 지원하되 기간·겸업 미확인은 남긴다")
    void employmentNeedsReviewedHistory() {
        for (var employment : List.of("UNEMPLOYED", "EARLY_EMPLOYEE", "EARLY_BUSINESS")) {
            assertThat(evaluate(Map.of("employment", employment)).checks().get(1).outcome()).isEqualTo(MET);
        }
        assertThat(evaluate(Map.of("employment", "PENDING")).checks().get(1).outcome()).isEqualTo(UNKNOWN);
        assertThat(evaluate(Map.of("employment", "NOT_TARGET")).checks().get(1).outcome()).isEqualTo(NOT_MET);
    }

    @Test @DisplayName("서울 날짜의 만 19세·35세 생일에 기본 연령과 질문을 같은 기준으로 비교한다")
    void ageBoundariesUseSeoulDate() {
        Map.of("2007-09-12", NOT_MET, "2007-09-11", MET, "1991-09-12", MET, "1991-09-11", NOT_MET)
                .forEach((birth, outcome) -> assertThat(MisoYouthFutureRules.ageCheck(LocalDate.parse(birth), NOW).outcome()).as(birth).isEqualTo(outcome));
        for (var answer : List.of("UNDER_19", "OVER_34")) {
            assertThat(evaluate(Map.of("age", answer)).checks().getFirst().outcome()).isEqualTo(NOT_MET);
        }
        var birthday = LocalDate.parse("2007-09-11");
        assertThat(MisoYouthFutureRules.ageCheck(birthday, Instant.parse("2026-09-10T14:59:59Z")).outcome()).isEqualTo(NOT_MET);
        var afterMidnight = MisoYouthFutureRules.ageCheck(birthday, Instant.parse("2026-09-10T15:00:00Z"));
        assertThat(afterMidnight.outcome()).isEqualTo(MET);
        assertThat(afterMidnight.providedValue()).contains("2026-09-11", "서울");
    }

    @Test @DisplayName("출시 전과 검토 연도 종료 뒤에는 질문을 재사용하지 않고 미응답은 미확인으로 남긴다")
    void restrictsReviewedPeriod() {
        var request = new Request(1, MisoYouthFutureRules.VERSION, List.of());
        assertThat(MisoYouthFutureRules.evaluate(1, request, NOW).checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(MisoYouthFutureRules.appliesAt(Instant.parse("2026-03-30T15:00:00Z"))).isTrue();
        assertThat(MisoYouthFutureRules.appliesAt(Instant.parse("2026-12-31T14:59:59Z"))).isTrue();
        for (var outside : List.of("2026-03-30T14:59:59Z", "2026-12-31T15:00:00Z")) {
            var now = Instant.parse(outside);
            assertThat(MisoYouthFutureRules.appliesAt(now)).isFalse();
            assertThatThrownBy(() -> MisoYouthFutureRules.evaluate(1, request, now)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private Evaluation evaluate(Map<String, String> changes) {
        var answers = new HashMap<>(ANSWERS);
        answers.putAll(changes);
        return MisoYouthFutureRules.evaluate(1, new Request(1, MisoYouthFutureRules.VERSION,
                answers.entrySet().stream().map(e -> new Answer(e.getKey(), e.getValue())).toList()), NOW);
    }
}
