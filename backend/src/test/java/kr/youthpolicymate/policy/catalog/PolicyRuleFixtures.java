package kr.youthpolicymate.policy.catalog;

import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

final class PolicyRuleFixtures {
    static final Map<String, PolicyRuleDefinition> DEFINITIONS = load();
    static PolicyRuleDefinition rule(String number) { return DEFINITIONS.get(number); }
    static PolicyQuestions.Check age(String number, java.time.LocalDate birth, java.time.Instant now) { return rule(number).compareBirth(birth, now).age(); }
    static PolicyQuestions.Check age(String number, java.time.LocalDate birth) { return age(number, birth, java.time.Instant.parse("2026-09-05T00:00:00Z")); }
    static PolicyQuestions.Questionnaire questions(String number, long revision, java.time.Instant now) { return rule(number).questionnaire(revision, rule(number).contentHash(), now); }
    static PolicyQuestions.Questionnaire questions(String number, long revision) { return questions(number, revision, java.time.Instant.parse("2026-09-05T00:00:00Z")); }
    static Map<String, PolicyAgeComparison> comparisons(BasicConditions input, java.time.Instant now) {
        var result = new HashMap<String, PolicyAgeComparison>();
        for (var rule : DEFINITIONS.values()) if (rule.appliesAt(now)) {
            var comparison = rule.compareBirth(input.birthDate(), now);
            if (comparison != null) result.put(rule.policyNumber(), comparison);
        }
        return result;
    }
    static PolicyRuleDefinition exam() { return DEFINITIONS.get(ExamFeeRules.NUMBER); }
    static PolicyRuleDefinition work() { return DEFINITIONS.get(WorkStudyRules.NUMBER); }
    private static Map<String, PolicyRuleDefinition> load() {
        try {
            var mapper = JsonMapper.builder().build();
            var definitions = new HashMap<String, PolicyRuleDefinition>();
            for (var file : List.of("V23__seed_reviewed_policy_rules.sql", "V24__migrate_policy_question_rules.sql")) {
                var matcher = Pattern.compile("\\$rule\\$(.*?)\\$rule\\$", Pattern.DOTALL)
                        .matcher(Files.readString(Path.of("src/main/resources/db/migration/" + file)));
                while (matcher.find()) {
                    var definition = mapper.readValue(matcher.group(1), PolicyRuleDefinition.class);
                    definitions.put(definition.policyNumber(), definition);
                }
            }
            return Map.copyOf(definitions);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
