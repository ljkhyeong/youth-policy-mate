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

class AiStudyLoanRulesTest {
    private static final String NUMBER = "20260903005400113371";
    private static final String VERSION = "ai-study-loan-2026-2-v1";
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    private static final Map<String, String> ANSWERS = Map.of("age", "UP_TO_35", "nationality", "YES",
            "department", "TARGET", "enrollment", "UNDERGRADUATE", "credits", "AT_LEAST_12", "creditException", "NONE");

    @Test @DisplayName("신청 자격을 모두 충족해도 대학 등록과 대출 제한 사유는 추가 확인으로 남기고 마감 시각을 안내한다")
    void keepsRegistrationChecksAndDeadline() {
        var result = evaluate(Map.of());
        assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(result.explanation()).startsWith("2026학년도 2학기 신청은 11월 17일 18시에 마감돼요.");
        assertThat(result.remainingChecks()).anyMatch(text -> text.contains("대학 등록")).anyMatch(text -> text.contains("중복지원자"));
    }

    @Test @DisplayName("대상학과는 재단에 등록된 주전공 학과만 충족하고 확인 전에는 불충족으로 단정하지 않는다")
    void requiresRegisteredDepartment() {
        assertThat(evaluate(Map.of("department", "HUB_UNIVERSITY")).commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(evaluate(Map.of("department", "NOT_TARGET")).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        var unchecked = evaluate(Map.of("department", "UNKNOWN"));
        assertThat(unchecked.commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        assertThat(unchecked.checks()).filteredOn(check -> check.label().equals("대상학과")).singleElement()
                .satisfies(check -> assertThat(check.explanation()).contains("대상학과 조회"));
    }

    @Test @DisplayName("대학원생·졸업유예·학점은행제·미등록 휴학과 해외이주 신고자는 대상이 아니다")
    void rejectsExcludedEnrollmentAndNationality() {
        for (var rejected : List.of(Map.of("enrollment", "GRADUATE_SCHOOL"), Map.of("enrollment", "GRADUATION_DEFERRED"),
                Map.of("enrollment", "CREDIT_BANK"), Map.of("enrollment", "UNREGISTERED_LEAVE"), Map.of("nationality", "EXCLUDED"))) {
            assertThat(evaluate(rejected).commonCriteriaStatus()).as(rejected.toString()).isEqualTo(INELIGIBLE);
        }
    }

    @Test @DisplayName("12학점 미만은 예외가 없다고 답했을 때만 불충족이고 첫 학기·졸업학년·특별승인은 충족이다")
    void appliesCreditExceptions() {
        assertThat(evaluate(Map.of("credits", "BELOW_12")).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        var unanswered = new HashMap<>(ANSWERS);
        unanswered.remove("creditException");
        unanswered.put("credits", "BELOW_12");
        assertThat(evaluateExactly(unanswered).commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
        for (var accepted : List.of(Map.of("credits", "FIRST_SEMESTER"), Map.of("credits", "SCHOOL_MINIMUM"),
                Map.of("credits", "BELOW_12", "creditException", "GRADUATING_YEAR"), Map.of("credits", "BELOW_12", "creditException", "SPECIAL_APPROVAL"))) {
            assertThat(evaluate(accepted).commonCriteriaStatus()).as(accepted.toString()).isEqualTo(ELIGIBLE);
        }
        assertThat(evaluate(Map.of("credits", "BELOW_12")).checks()).filteredOn(check -> check.label().equals("직전학기 이수학점"))
                .singleElement().extracting(Check::providedValue).isEqualTo("12학점 미만 · 예외 해당 없어요");
    }

    @Test @DisplayName("신청일 만 35세까지 충족하고 만 36세 이상은 성인학습자 예외를 확인하게 한다")
    void comparesAgeWithAdultLearnerException() {
        Map.of("1990-10-13", MET, "1990-10-12", UNKNOWN, "2007-01-01", MET)
                .forEach((birth, expected) -> assertThat(age(birth).outcome()).as(birth).isEqualTo(expected));
        assertThat(age("1990-10-12").explanation()).contains("만 45세");
        assertThat(evaluate(Map.of("age", "EXCEPTION_CONFIRMED")).commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(evaluate(Map.of("age", "OVER_LIMIT")).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
    }

    @Test @DisplayName("신청 시작 전·마감 뒤 안내를 구분하고 2학기 신청 기간이 끝나면 질문과 기본 연령을 적용하지 않는다")
    void limitsApplicationPeriod() {
        var rule = PolicyRuleFixtures.rule(NUMBER);
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            rule.validate(factory.getValidator());
        }
        assertThat(rule.evaluate(1, new Request(1, VERSION, List.of()), NOW).checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(PolicyRuleFixtures.questions(NUMBER, 1, Instant.parse("2026-06-30T23:59:59Z")).reason()).startsWith("신청 전이에요.");
        assertThat(PolicyRuleFixtures.questions(NUMBER, 1, Instant.parse("2026-11-17T09:00:00Z")).reason()).contains("마감됐어요");
        assertThat(rule.appliesAt(Instant.parse("2026-06-30T15:00:00Z"))).isTrue();
        assertThat(rule.appliesAt(Instant.parse("2026-11-17T14:59:59Z"))).isTrue();
        var input = new BasicConditions(LocalDate.parse("2000-01-01"), kr.youthpolicymate.eligibility.SeoulDistrict.GANGNAM, BasicConditions.EmploymentStatus.NOT_EMPLOYED);
        for (var value : List.of("2026-06-30T14:59:59Z", "2026-11-17T15:00:00Z")) {
            var now = Instant.parse(value);
            assertThat(rule.appliesAt(now)).isFalse();
            assertThat(PolicyRuleFixtures.comparisons(input, now)).doesNotContainKey(NUMBER);
        }
    }

    private Check age(String birth) {
        return PolicyRuleFixtures.age(NUMBER, LocalDate.parse(birth), NOW);
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
