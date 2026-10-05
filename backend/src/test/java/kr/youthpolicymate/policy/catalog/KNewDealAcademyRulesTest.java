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

class KNewDealAcademyRulesTest {
    private static final String NUMBER = "20260714005400113258";
    private static final String VERSION = "k-newdeal-academy-2026-v1";
    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");
    private static final Map<String, String> ANSWERS = Map.of("age", "AGE_15_TO_34", "employment", "NOT_INSURED",
            "business", "NONE", "education", "NOT_ENROLLED", "training", "NO", "participation", "NONE_OR_ONCE");

    @Test @DisplayName("공통 조건을 모두 충족해도 구직등록·국적·선발은 추가 확인으로 남긴다")
    void keepsRemainingChecksWhenCommonCriteriaMet() {
        var result = evaluate(Map.of());
        assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(result.remainingChecks()).anyMatch(text -> text.contains("구직등록"))
                .anyMatch(text -> text.contains("국적")).anyMatch(text -> text.contains("증빙"));
    }

    @Test @DisplayName("서울 날짜의 신청일 연령을 비교하고 만 35세 이상은 군복무 연장을 미확인으로 남긴다")
    void comparesAgeOnSeoulDateAndKeepsMilitaryExtensionUnknown() {
        Map.of("2011-09-12", MET, "2011-09-13", NOT_MET, "1991-09-13", MET, "1991-09-12", UNKNOWN, "1980-01-01", UNKNOWN)
                .forEach((birth, expected) -> assertThat(age(birth, NOW).outcome()).as(birth).isEqualTo(expected));
        assertThat(age("1991-09-12", Instant.parse("2026-09-11T14:59:59Z")).outcome()).isEqualTo(MET);
        assertThat(age("1991-09-12", Instant.parse("2026-09-11T15:00:00Z")).explanation()).contains("만 35~39세", "만 40세 이상");
        assertThat(evaluate(Map.of("age", "EXTENSION_CONFIRMED")).commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(evaluate(Map.of("age", "EXTENSION_PENDING")).commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        for (var rejected : List.of("OVER_LIMIT", "UNDER_15")) {
            assertThat(evaluate(Map.of("age", rejected)).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        }
    }

    @Test @DisplayName("인정받은 근로·사업자 예외는 충족하고 예외가 없으면 불충족, 확인 중이면 미확인이다")
    void distinguishesEmploymentAndBusinessExceptions() {
        for (var accepted : List.of(Map.of("employment", "EXCEPTION_CONFIRMED"), Map.of("business", "INACTIVE_CONFIRMED"))) {
            assertThat(evaluate(accepted).commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        }
        for (var rejected : List.of(Map.of("employment", "EMPLOYED_NO_EXCEPTION"), Map.of("business", "ACTIVE_NO_EXCEPTION"),
                Map.of("training", "CONTINUES"), Map.of("participation", "TWICE"), Map.of("participation", "OVERLAPPING"))) {
            assertThat(evaluate(rejected).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        }
        for (var pending : List.of("employment", "business", "training", "participation")) {
            assertThat(evaluate(Map.of(pending, "UNKNOWN")).commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        }
    }

    @Test @DisplayName("아카데미 시작 전에 해소될 재학·직업훈련 수강은 증빙 확인 전까지 불충족으로 단정하지 않는다")
    void keepsRestrictionsResolvedBeforeStartUnknown() {
        for (var resolving : List.of(Map.of("education", "RESOLVING_BEFORE_START"), Map.of("training", "ENDS_BEFORE_START"))) {
            assertThat(evaluate(resolving).commonCriteriaStatus()).as(resolving.toString()).isEqualTo(NEEDS_REVIEW);
        }
        assertThat(evaluate(Map.of("training", "ENDS_BEFORE_START")).checks()).filteredOn(check -> check.label().equals("다른 직업훈련 수강"))
                .singleElement().satisfies(check -> assertThat(check.explanation()).contains("시작 전에 끝나는지"));
    }

    @Test @DisplayName("재학 예외를 충족으로 반영하고 안내마다 다른 1년 미만 휴학은 불충족으로 단정하지 않는다")
    void keepsShortLeaveUnknownBecauseSourcesDiffer() {
        for (var exception : List.of("GRADUATING", "LONG_LEAVE", "VOCATIONAL_RECOMMENDED")) {
            assertThat(evaluate(Map.of("education", exception)).commonCriteriaStatus()).as(exception).isEqualTo(ELIGIBLE);
        }
        var shortLeave = evaluate(Map.of("education", "SHORT_LEAVE"));
        assertThat(shortLeave.commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        assertThat(shortLeave.checks()).filteredOn(check -> check.label().equals("재학 상태")).singleElement()
                .satisfies(check -> assertThat(check.explanation()).contains("1년 미만"));
        assertThat(evaluate(Map.of("education", "ENROLLED_NO_EXCEPTION")).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
    }

    @Test @DisplayName("미응답은 미확인이며 2026년 모집 기간 밖에는 질문과 기본 연령을 적용하지 않는다")
    void limitsReviewedPeriodAndKeepsMissingAnswersUnknown() {
        var rule = PolicyRuleFixtures.rule(NUMBER);
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            rule.validate(factory.getValidator());
        }
        assertThat(rule.evaluate(1, new Request(1, VERSION, List.of()), NOW).checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(rule.appliesAt(Instant.parse("2026-07-06T15:00:00Z"))).isTrue();
        assertThat(rule.appliesAt(Instant.parse("2026-12-31T14:59:59Z"))).isTrue();
        var input = new BasicConditions(LocalDate.parse("2000-01-01"), "강남구", BasicConditions.EmploymentStatus.NOT_EMPLOYED);
        for (var value : List.of("2026-07-06T14:59:59Z", "2026-12-31T15:00:00Z")) {
            var now = Instant.parse(value);
            assertThat(rule.appliesAt(now)).isFalse();
            assertThat(PolicyRuleFixtures.comparisons(input, now)).doesNotContainKey(NUMBER);
        }
    }

    private Check age(String birth, Instant now) {
        return PolicyRuleFixtures.age(NUMBER, LocalDate.parse(birth), now);
    }

    private Evaluation evaluate(Map<String, String> changes) {
        var values = new HashMap<>(ANSWERS);
        values.putAll(changes);
        return PolicyRuleFixtures.rule(NUMBER).evaluate(1, new Request(1, VERSION,
                values.entrySet().stream().map(entry -> new Answer(entry.getKey(), entry.getValue())).toList()), NOW);
    }
}
