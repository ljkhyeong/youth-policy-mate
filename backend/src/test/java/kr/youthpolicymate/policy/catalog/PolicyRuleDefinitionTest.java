package kr.youthpolicymate.policy.catalog;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;

class PolicyRuleDefinitionTest {
    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");
    @Test @DisplayName("초기 데이터는 기존 모든 선택지 조합과 같은 판정·근거·예외 설명을 제공한다")
    void preservesReviewedDecisions() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (var definition : PolicyRuleFixtures.DEFINITIONS.values()) {
                definition.validate(factory.getValidator());
                compareCombinations(definition, 0, new ArrayList<>());
            }
        }
    }
    private void compareCombinations(PolicyRuleDefinition definition, int index, List<Answer> answers) {
        if (index < definition.questions().size()) {
            var question = definition.questions().get(index);
            compareCombinations(definition, index + 1, answers);
            for (var option : question.options()) {
                answers.add(new Answer(question.id(), option.value()));
                compareCombinations(definition, index + 1, answers);
                answers.removeLast();
            }
            return;
        }
        var request = new Request(1, definition.ruleVersion(), List.copyOf(answers));
        var legacy = definition.policyNumber().equals(ExamFeeRules.NUMBER) ? ExamFeeRules.evaluate(1, request, NOW) : WorkStudyRules.evaluate(1, request, NOW);
        var result = definition.evaluate(1, request, NOW);
        assertThat(result.status()).isEqualTo(legacy.status());
        assertThat(result.commonCriteriaStatus()).isEqualTo(legacy.commonCriteriaStatus());
        assertThat(result.checks()).extracting(Check::outcome, Check::explanation, Check::evidence)
                .containsExactlyElementsOf(legacy.checks().stream().map(c -> org.assertj.core.groups.Tuple.tuple(c.outcome(), c.explanation(), c.evidence())).toList());
    }
    @Test @DisplayName("없는 선택지와 다른 질문에 의존하는 출생일 규칙은 등록할 수 없다")
    void rejectsInvalidReferences() {
        var mapper = JsonMapper.builder().build();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var json = mapper.valueToTree(PolicyRuleFixtures.exam());
            ((tools.jackson.databind.node.ObjectNode) json.at("/checks/0/cases/0/when")).putArray("birthRange").add("MISSING");
            assertThatThrownBy(() -> mapper.treeToValue(json, PolicyRuleDefinition.class).validate(factory.getValidator())).isInstanceOf(IllegalArgumentException.class);
            var other = mapper.valueToTree(PolicyRuleFixtures.exam());
            ((tools.jackson.databind.node.ObjectNode) other.at("/checks/0/cases/0/when")).putArray("exam").add("HRDK_TECHNICAL");
            assertThatThrownBy(() -> mapper.treeToValue(other, PolicyRuleDefinition.class).validate(factory.getValidator())).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test @DisplayName("양쪽 출생일 경계를 포함하고 학적·소득 답변은 생년월일에서 추정하지 않는다")
    void mapsOnlyReviewedBirthAnswers() {
        assertThat(PolicyRuleFixtures.exam().prefill(LocalDate.parse("1991-01-01"))).containsExactly(new Answer("birthRange", "ON_OR_AFTER_1991_01_01"));
        assertThat(PolicyRuleFixtures.exam().prefill(LocalDate.parse("1990-12-31"))).containsExactly(new Answer("birthRange", "BEFORE_1991_01_01"));
        assertThat(PolicyRuleFixtures.work().prefill(LocalDate.parse("2000-01-01"))).isEmpty();
    }
}
