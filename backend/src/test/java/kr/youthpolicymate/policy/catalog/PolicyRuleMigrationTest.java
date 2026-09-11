package kr.youthpolicymate.policy.catalog;

import jakarta.validation.Validation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;

class PolicyRuleMigrationTest {
    static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");

    @Test @DisplayName("9개 정책의 조건별 선택지 조합과 혼합 답변이 이전 판정·근거·표시 값·추가 안내를 보존한다")
    void preservesExistingRules() {
        var random = new Random(24);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (var type : LegacyPolicyRules.TYPES) {
                var dependencies = LegacyPolicyRules.DEPENDENCIES.get(type.getSimpleName());
                if (dependencies == null) continue;
                var number = LegacyPolicyRules.constant(type, "NUMBER");
                var definition = PolicyRuleFixtures.rule(number);
                definition.validate(factory.getValidator());
                assertThat(definition.questionnaire(1, definition.contentHash(), NOW)).isEqualTo(LegacyPolicyRules.questions(number, NOW));
                for (var group : dependencies) {
                    var ids = Set.of(group.split(","));
                    combinations(definition, definition.questions().stream().filter(q -> ids.contains(q.id())).toList(), 0, new ArrayList<>());
                }
                for (int sample = 0; sample < 100; sample++) {
                    var answers = new ArrayList<Answer>();
                    for (var question : definition.questions()) {
                        int option = random.nextInt(question.options().size() + 1);
                        if (option < question.options().size()) answers.add(new Answer(question.id(), question.options().get(option).value()));
                    }
                    compare(definition, answers);
                }
            }
        }
    }
    private void combinations(PolicyRuleDefinition definition, List<Question> questions, int index, List<Answer> answers) {
        if (index == questions.size()) { compare(definition, answers); return; }
        var question = questions.get(index);
        combinations(definition, questions, index + 1, answers);
        for (var option : question.options()) {
            answers.add(new Answer(question.id(), option.value()));
            combinations(definition, questions, index + 1, answers);
            answers.removeLast();
        }
    }
    private void compare(PolicyRuleDefinition definition, List<Answer> answers) {
        var request = new Request(1, definition.versionAt(NOW), List.copyOf(answers));
        assertThat(definition.evaluate(1, request, NOW)).as("정책 %s, 답변 %s", definition.policyNumber(), answers)
                .isEqualTo(LegacyPolicyRules.evaluate(definition.policyNumber(), request, NOW));
    }
    @Test @DisplayName("연령 비교는 윤년·서울 자정·병역 예외 경계를 보존하고 답변 변환과 같은 결과를 낸다")
    void preservesAgeBoundaries() {
        for (var definition : PolicyRuleFixtures.DEFINITIONS.values()) {
            if (definition.ageBinding() == null && definition.birthBinding() == null) continue;
            for (var date : List.of("2026-02-28T14:59:59Z", "2026-02-28T15:00:00Z", "2026-09-12T00:00:00Z")) {
                var now = Instant.parse(date);
                if (!definition.appliesAt(now)) continue;
                for (var birthText : List.of("1983-01-01", "1986-01-01", "1986-01-02", "1991-01-01", "1992-02-29", "2007-01-01", "2007-12-31", "2011-05-31", "2011-06-01")) {
                    var birth = LocalDate.parse(birthText);
                    var comparison = definition.compareBirth(birth, now);
                    assertThat(comparison.age()).isEqualTo(LegacyPolicyRules.age(definition.policyNumber(), birth, now));
                    var response = definition.evaluate(1, new Request(1, definition.versionAt(now), definition.prefill(birth, now)), now);
                    assertThat(response.checks().getFirst().outcome()).isEqualTo(comparison.age().outcome());
                }
            }
        }
    }
    @Test @DisplayName("기준 월이 바뀌면 전월 답변을 거부하고 새 월의 범위를 표시한다")
    void rejectsPreviousMonth() {
        var rule = PolicyRuleFixtures.rule(KPassRules.NUMBER);
        var september = Instant.parse("2026-09-30T14:59:59Z");
        var october = september.plusSeconds(1);
        assertThatThrownBy(() -> rule.evaluate(1, new Request(1, rule.versionAt(september), List.of()), october)).isInstanceOf(IllegalArgumentException.class);
        assertThat(rule.questionnaire(1, rule.contentHash(), october).scope()).startsWith("2026년 10월");
    }
}
