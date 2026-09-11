package kr.youthpolicymate.admin;

import kr.youthpolicymate.policy.catalog.PolicyRuleDefinition;
import kr.youthpolicymate.policy.catalog.PolicyRuleStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Profile("!preview")
@Transactional
class PolicyRuleManagementService {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final PolicyRuleStore rules;
    PolicyRuleManagementService(JdbcClient jdbc, ObjectMapper mapper, PolicyRuleStore rules) {
        this.jdbc = jdbc; this.mapper = mapper; this.rules = rules;
    }

    PolicyRuleActions.Result draft(String number, PolicyRuleActions.Draft request, UUID actor) {
        var definition = parse(request.definitionJson());
        if (!number.equals(definition.policyNumber())) throw new PolicyRuleActions.Invalid();
        var current = lock(number);
        var replay = replay(request.requestId(), number, actor, request.expectedRevision(), request.reason(), PolicyRuleActions.Action.DRAFT, null);
        if (replay.isPresent()) {
            if (!rules.definition(replay.get().versionId()).equals(definition)) throw new PolicyRuleActions.Changed();
            return replay.get();
        }
        if (current.revision() != request.expectedRevision() || !current.hash().equals(definition.contentHash())) throw new PolicyRuleActions.Changed();
        UUID id;
        try { id = rules.draft(definition, actor.toString(), request.reason().strip()); }
        catch (IllegalArgumentException exception) { throw new PolicyRuleActions.Invalid(); }
        return record(request.requestId(), number, id, actor, request.expectedRevision(), request.reason(), PolicyRuleActions.Action.DRAFT, null);
    }

    PolicyRuleActions.Result publish(String number, UUID id, PolicyRuleActions.Publish request, UUID actor) {
        var current = lock(number);
        var replay = replay(request.requestId(), number, actor, request.expectedRevision(), request.reason(), PolicyRuleActions.Action.PUBLISH, request.expectedRuleVersion());
        if (replay.isPresent()) {
            if (!replay.get().versionId().equals(id)) throw new PolicyRuleActions.Changed();
            return replay.get();
        }
        scopedDefinition(number, id);
        if (current.revision() != request.expectedRevision()) throw new PolicyRuleActions.Changed();
        try { rules.publish(id, request.expectedRuleVersion(), actor.toString()); }
        catch (IllegalStateException exception) { throw new PolicyRuleActions.Changed(); }
        catch (IllegalArgumentException exception) { throw new PolicyRuleActions.Invalid(); }
        return record(request.requestId(), number, id, actor, request.expectedRevision(), request.reason(), PolicyRuleActions.Action.PUBLISH, request.expectedRuleVersion());
    }

    @Transactional(readOnly = true)
    public PolicyRuleActions.File file(String number, UUID id) {
        return new PolicyRuleActions.File(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(scopedDefinition(number, id)));
    }

    private PolicyRuleDefinition scopedDefinition(String number, UUID id) {
        return jdbc.sql("SELECT definition::text FROM policy_rule_versions WHERE policy_number = :number AND id = :id")
                .param("number", number).param("id", id).query(String.class).optional()
                .map(json -> mapper.readValue(json, PolicyRuleDefinition.class)).orElseThrow(PolicyRuleActions.Missing::new);
    }

    private PolicyRuleDefinition parse(String json) {
        if (json.getBytes(StandardCharsets.UTF_8).length > 131072) throw new PolicyRuleActions.Invalid();
        try {
            PolicyRuleDefinition definition = mapper.readerFor(PolicyRuleDefinition.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(json);
            if (definition == null) throw new PolicyRuleActions.Invalid();
            return definition;
        } catch (JacksonException exception) { throw new PolicyRuleActions.Invalid(); }
    }

    private Current lock(String number) {
        return jdbc.sql("SELECT current_revision, content_hash FROM policies WHERE policy_number = :number AND current_revision > 0 FOR UPDATE")
                .param("number", number).query((rs, row) -> new Current(rs.getLong("current_revision"), rs.getString("content_hash")))
                .optional().orElseThrow(PolicyRuleActions.Missing::new);
    }

    private Optional<PolicyRuleActions.Result> replay(UUID requestId, String number, UUID actor, long revision, String reason,
                                                     PolicyRuleActions.Action action, String expectedVersion) {
        return jdbc.sql("""
                SELECT a.*, v.rule_version FROM admin_policy_rule_actions a JOIN policy_rule_versions v ON v.id = a.version_id
                WHERE request_id = :request
                """).param("request", requestId).query((rs, row) -> {
            if (!number.equals(rs.getString("policy_number")) || !actor.equals(rs.getObject("actor_id", UUID.class))
                    || revision != rs.getLong("expected_revision") || !reason.strip().equals(rs.getString("reason"))
                    || !action.name().equals(rs.getString("action")) || !java.util.Objects.equals(expectedVersion, rs.getString("expected_rule_version")))
                throw new PolicyRuleActions.Changed();
            return new PolicyRuleActions.Result(requestId, rs.getObject("version_id", UUID.class), number, rs.getString("rule_version"),
                    action, actor, revision, reason.strip(), rs.getObject("performed_at", OffsetDateTime.class).toInstant());
        }).optional();
    }

    private PolicyRuleActions.Result record(UUID requestId, String number, UUID id, UUID actor, long revision, String reason,
                                            PolicyRuleActions.Action action, String expectedVersion) {
        jdbc.sql("""
                INSERT INTO admin_policy_rule_actions(request_id, policy_number, version_id, action, actor_id, expected_revision, expected_rule_version, reason)
                VALUES (:request, :number, :id, :action, :actor, :revision, :expected, :reason)
                """).param("request", requestId).param("number", number).param("id", id).param("action", action.name())
                .param("actor", actor).param("revision", revision).param("expected", expectedVersion).param("reason", reason.strip()).update();
        return replay(requestId, number, actor, revision, reason, action, expectedVersion).orElseThrow();
    }
    private record Current(long revision, String hash) {}
}
