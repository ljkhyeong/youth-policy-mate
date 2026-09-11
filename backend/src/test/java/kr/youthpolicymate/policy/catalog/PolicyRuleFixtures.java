package kr.youthpolicymate.policy.catalog;

import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

final class PolicyRuleFixtures {
    static final Map<String, PolicyRuleDefinition> DEFINITIONS = load();
    static PolicyRuleDefinition exam() { return DEFINITIONS.get(ExamFeeRules.NUMBER); }
    static PolicyRuleDefinition work() { return DEFINITIONS.get(WorkStudyRules.NUMBER); }
    private static Map<String, PolicyRuleDefinition> load() {
        try {
            var matcher = Pattern.compile("\\$rule\\$(.*?)\\$rule\\$", Pattern.DOTALL)
                    .matcher(Files.readString(Path.of("src/main/resources/db/migration/V23__seed_reviewed_policy_rules.sql")));
            var mapper = JsonMapper.builder().build();
            var definitions = new HashMap<String, PolicyRuleDefinition>();
            while (matcher.find()) {
                var definition = mapper.readValue(matcher.group(1), PolicyRuleDefinition.class);
                definitions.put(definition.policyNumber(), definition);
            }
            return Map.copyOf(definitions);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
