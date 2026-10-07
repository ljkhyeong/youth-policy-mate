package kr.youthpolicymate.policy.catalog;

import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** 시드 마이그레이션의 공고별 규칙을 그대로 읽은 테스트 자료. */
final class PolicyRuleFixtures {
    static final String EXAM_FEE = "20260527005400113224", WORK_STUDY = "20260821005400113348",
            K_PASS = "20260710005400113257", YOUTH_HOUSING_SAVINGS = "20260616005400113238",
            SEOUL_YOUTH_NETWORK = "20260520005400213208", MOVING_FEE = "20260614005400213232",
            YOUTH_TOMORROW_SAVINGS = "20260430005400113009", GUARANTEE_FEE = "20260527005400113223",
            HAETSALRON_YOUTH = "20260724005400113307", MISO_YOUTH_FUTURE = "20260421005400112773",
            FUTURE_YOUTH_JOBS = "20260722005400213264";
    static final Map<String, PolicyRuleDefinition> DEFINITIONS = load();
    static PolicyRuleDefinition rule(String number) { return DEFINITIONS.get(number); }
    static String version(String number) { return rule(number).ruleVersion(); }
    static String hash(String number) { return rule(number).contentHash(); }
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
    static PolicyRuleDefinition exam() { return rule(EXAM_FEE); }
    static PolicyRuleDefinition work() { return rule(WORK_STUDY); }
    private static Map<String, PolicyRuleDefinition> load() {
        try {
            var mapper = JsonMapper.builder().build();
            var definitions = new HashMap<String, PolicyRuleDefinition>();
            var matcher = Pattern.compile("\\$rule\\$(.*?)\\$rule\\$", Pattern.DOTALL)
                    .matcher(Files.readString(Path.of("src/main/resources/db/migration/V2__seed_reviewed_policy_rules.sql")));
            while (matcher.find()) {
                var definition = mapper.readValue(matcher.group(1), PolicyRuleDefinition.class);
                definitions.put(definition.policyNumber(), definition);
            }
            return Map.copyOf(definitions);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
