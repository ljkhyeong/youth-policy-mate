package kr.youthpolicymate.policy.catalog;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;
import static kr.youthpolicymate.policy.catalog.PolicyRuleFixtures.*;

class PolicyRuleDefinitionTest {
    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");
    @Test @DisplayName("모든 시드 규칙은 형식·참조 검사를 통과한다")
    void validatesSeededRules() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            DEFINITIONS.values().forEach(definition -> definition.validate(factory.getValidator()));
        }
    }
    @Test @DisplayName("연령 비교는 윤년·서울 자정·병역 예외 경계에서 답변 변환과 같은 결과를 낸다")
    void preservesAgeBoundaries() {
        for (var definition : DEFINITIONS.values()) {
            if (definition.ageBinding() == null && definition.birthBinding() == null) continue;
            for (var date : List.of("2026-02-28T14:59:59Z", "2026-02-28T15:00:00Z", "2026-09-12T00:00:00Z")) {
                var now = Instant.parse(date);
                if (!definition.appliesAt(now)) continue;
                for (var birthText : List.of("1983-01-01", "1986-01-01", "1986-01-02", "1991-01-01", "1992-02-29", "2007-01-01", "2007-12-31", "2011-05-31", "2011-06-01")) {
                    var birth = LocalDate.parse(birthText);
                    var comparison = definition.compareBirth(birth, now);
                    var response = definition.evaluate(1, new Request(1, definition.versionAt(now), definition.prefill(birth, now)), now);
                    assertThat(response.checks().getFirst().outcome()).isEqualTo(comparison.age().outcome());
                }
            }
        }
    }
    @Test @DisplayName("없는 선택지·다른 질문에 의존하는 출생일 규칙·이름이 같은 판정 항목은 등록할 수 없다")
    void rejectsInvalidReferences() {
        var mapper = JsonMapper.builder().build();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var json = mapper.valueToTree(exam());
            ((tools.jackson.databind.node.ObjectNode) json.at("/checks/0/cases/0/when")).putArray("birthRange").add("MISSING");
            assertThatThrownBy(() -> mapper.treeToValue(json, PolicyRuleDefinition.class).validate(factory.getValidator())).isInstanceOf(IllegalArgumentException.class);
            var other = mapper.valueToTree(exam());
            ((tools.jackson.databind.node.ObjectNode) other.at("/checks/0/cases/0/when")).putArray("exam").add("HRDK_TECHNICAL");
            assertThatThrownBy(() -> mapper.treeToValue(other, PolicyRuleDefinition.class).validate(factory.getValidator())).isInstanceOf(IllegalArgumentException.class);
            var sameLabel = mapper.valueToTree(exam());
            ((tools.jackson.databind.node.ObjectNode) sameLabel.at("/checks/1")).put("label", sameLabel.at("/checks/0/label").asString());
            assertThatThrownBy(() -> mapper.treeToValue(sameLabel, PolicyRuleDefinition.class).validate(factory.getValidator()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("판정 항목 이름");
        }
    }
    @Test @DisplayName("양쪽 출생일 경계를 포함하고 학적·소득 답변은 생년월일에서 추정하지 않는다")
    void mapsOnlyReviewedBirthAnswers() {
        assertThat(exam().prefill(LocalDate.parse("1991-01-01"), NOW)).containsExactly(new Answer("birthRange", "ON_OR_AFTER_1991_01_01"));
        assertThat(exam().prefill(LocalDate.parse("1990-12-31"), NOW)).containsExactly(new Answer("birthRange", "BEFORE_1991_01_01"));
        assertThat(work().prefill(LocalDate.parse("2000-01-01"), NOW)).isEmpty();
    }
}
