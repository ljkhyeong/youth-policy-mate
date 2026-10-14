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

class YouthLeapBootcampRulesTest {
    private static final String NUMBER = "20260714005400113259";
    private static final String VERSION = "youth-leap-bootcamp-2026-v1";
    private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
    private static final Map<String, String> ANSWERS = Map.of("age", "AGE_19_TO_34", "work", "NOT_WORKING", "school", "NOT_ENROLLED");

    @Test @DisplayName("공통 조건을 충족해도 NAIS 등재·구직준비도 검사와 대학별 조건은 추가 확인으로 남긴다")
    void keepsUniversityChecksWhenCommonCriteriaMet() {
        var result = evaluate(Map.of());
        assertThat(result.commonCriteriaStatus()).isEqualTo(ELIGIBLE);
        assertThat(result.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(result.remainingChecks()).anyMatch(text -> text.contains("구직준비도")).anyMatch(text -> text.contains("대학"));
    }

    @Test @DisplayName("재직 중이면 불충족이고 단시간 근로·사업자는 대학별 기준을 확인할 때까지 불충족으로 단정하지 않는다")
    void distinguishesWorkStatus() {
        assertThat(evaluate(Map.of("work", "EMPLOYED")).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        for (var pending : List.of("PART_TIME", "BUSINESS", "UNKNOWN")) {
            assertThat(evaluate(Map.of("work", pending)).commonCriteriaStatus()).as(pending).isEqualTo(NEEDS_REVIEW);
        }
    }

    @Test @DisplayName("대학 재학생은 불충족, 장기 휴학·졸업유예는 추천 대상으로 충족, 대학마다 다른 학적은 추가 확인이다")
    void distinguishesEnrollment() {
        assertThat(evaluate(Map.of("school", "ENROLLED")).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        for (var accepted : List.of("LONG_LEAVE", "LONG_DEFERRAL")) {
            var result = evaluate(Map.of("school", accepted));
            assertThat(result.commonCriteriaStatus()).as(accepted).isEqualTo(ELIGIBLE);
            assertThat(result.checks()).filteredOn(check -> check.label().equals("대학 재학")).singleElement()
                    .satisfies(check -> assertThat(check.explanation()).contains("모집 공고"));
        }
        for (var pending : List.of("SHORT_LEAVE_OR_DEFERRAL", "GRADUATING", "GRADUATE_SCHOOL")) {
            assertThat(evaluate(Map.of("school", pending)).commonCriteriaStatus()).as(pending).isEqualTo(NEEDS_REVIEW);
        }
    }

    @Test @DisplayName("서울 날짜의 만 19~34세를 충족으로 보고 만 35세 이상은 대학별 병역 연장을 확인하게 한다")
    void comparesAgeOnSeoulDate() {
        Map.of("2007-10-14", MET, "2007-10-15", NOT_MET, "1991-10-15", MET, "1991-10-14", UNKNOWN)
                .forEach((birth, expected) -> assertThat(age(birth, NOW).outcome()).as(birth).isEqualTo(expected));
        assertThat(age("1991-10-15", Instant.parse("2026-10-14T14:59:59Z")).outcome()).isEqualTo(MET);
        assertThat(age("1991-10-15", Instant.parse("2026-10-14T15:00:00Z")).explanation()).contains("병역", "대학");
        // 공통 안내에 연장 상한이 없어 만 40세 이상도 생년월일만으로 불충족을 확정하지 않는다.
        assertThat(age("1986-01-01", NOW).outcome()).isEqualTo(UNKNOWN);
        assertThat(evaluate(Map.of("age", "OVER_LIMIT")).commonCriteriaStatus()).isEqualTo(INELIGIBLE);
        assertThat(evaluate(Map.of("age", "EXTENSION_PENDING")).commonCriteriaStatus()).isEqualTo(NEEDS_REVIEW);
    }

    @Test @DisplayName("미응답은 미확인이며 1차년도 운영 기간에만 질문과 기본 연령을 적용한다")
    void limitsFirstYearAndKeepsMissingAnswersUnknown() {
        var rule = PolicyRuleFixtures.rule(NUMBER);
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            rule.validate(factory.getValidator());
        }
        assertThat(rule.evaluate(1, new Request(1, VERSION, List.of()), NOW).checks()).extracting(Check::outcome).containsOnly(UNKNOWN);
        assertThat(rule.appliesAt(Instant.parse("2026-07-06T15:00:00Z"))).isTrue();
        assertThat(rule.appliesAt(Instant.parse("2027-02-28T14:59:59Z"))).isTrue();
        var input = new BasicConditions(LocalDate.parse("2000-01-01"), kr.youthpolicymate.eligibility.SeoulDistrict.GANGNAM, BasicConditions.EmploymentStatus.NOT_EMPLOYED);
        for (var value : List.of("2026-07-06T14:59:59Z", "2027-02-28T15:00:00Z")) {
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
