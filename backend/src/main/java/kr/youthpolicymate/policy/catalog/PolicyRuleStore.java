package kr.youthpolicymate.policy.catalog;

import jakarta.validation.Validator;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

@Repository
@Profile("!preview")
public class PolicyRuleStore {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final Clock clock;
    public PolicyRuleStore(JdbcClient jdbc, ObjectMapper mapper, Validator validator, Clock clock) {
        this.jdbc = jdbc; this.mapper = mapper; this.validator = validator; this.clock = clock;
    }
    @Transactional
    public UUID draft(PolicyRuleDefinition definition, String actor, String reason) {
        definition.validate(validator);
        if (actor == null || actor.isBlank() || reason == null || reason.isBlank()) throw new IllegalArgumentException("작업자와 변경 사유가 필요합니다.");
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO policy_rule_versions(id, policy_number, rule_version, definition, created_by, reason)
                VALUES (:id, :number, :version, CAST(:definition AS jsonb), :actor, :reason)
                """).param("id", id).param("number", definition.policyNumber()).param("version", definition.ruleVersion())
                .param("definition", mapper.writeValueAsString(definition)).param("actor", actor).param("reason", reason).update();
        return id;
    }
    @Transactional
    public void publish(UUID id, String expectedVersion, String actor) {
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("작업자가 필요합니다.");
        var definition = definition(id);
        definition.validate(validator);
        var hash = jdbc.sql("SELECT content_hash FROM policies WHERE policy_number = :number AND current_revision > 0 FOR UPDATE")
                .param("number", definition.policyNumber()).query(String.class).optional().orElseThrow(() -> new IllegalStateException("정책 원문이 없습니다."));
        var current = jdbc.sql("""
                SELECT v.rule_version FROM policy_rule_heads h JOIN policy_rule_versions v ON v.id = h.version_id
                WHERE h.policy_number = :number
                """).param("number", definition.policyNumber()).query(String.class).optional().orElse("none");
        if (!current.equals(expectedVersion)) throw new IllegalStateException("적용 버전이 바뀌었습니다. 최신 상태를 확인해주세요.");
        if (!hash.equals(definition.contentHash())) throw new IllegalStateException("원문이 바뀌었습니다. 새 원문을 검토해주세요.");
        var now = clock.instant();
        if (!definition.appliesAt(now)) throw new IllegalStateException("현재 적용할 수 없는 기간입니다.");
        if (jdbc.sql("UPDATE policy_rule_versions SET published_at = :at, published_by = :actor WHERE id = :id AND published_at IS NULL")
                .param("id", id).param("at", now.atOffset(ZoneOffset.UTC)).param("actor", actor).update() != 1)
            throw new IllegalStateException("이미 적용한 버전입니다. 새 초안을 등록해주세요.");
        jdbc.sql("""
                INSERT INTO policy_rule_heads(policy_number, version_id) VALUES (:number, :id)
                ON CONFLICT (policy_number) DO UPDATE SET version_id = EXCLUDED.version_id
                """).param("number", definition.policyNumber()).param("id", id).update();
    }
    public PolicyRuleDefinition definition(UUID id) {
        return jdbc.sql("SELECT definition::text FROM policy_rule_versions WHERE id = :id")
                .param("id", id).query(String.class).optional().map(json -> mapper.readValue(json, PolicyRuleDefinition.class))
                .orElseThrow(() -> new IllegalArgumentException("초안을 찾을 수 없습니다."));
    }
    List<PolicyRuleDefinition> published() {
        return jdbc.sql("SELECT v.definition::text FROM policy_rule_heads h JOIN policy_rule_versions v ON v.id = h.version_id")
                .query((rs, row) -> mapper.readValue(rs.getString(1), PolicyRuleDefinition.class)).list();
    }
    public List<Status> status() {
        var now = clock.instant();
        return jdbc.sql("""
                SELECT v.id, v.definition::text, v.published_at, h.version_id, p.content_hash
                FROM policy_rule_versions v LEFT JOIN policy_rule_heads h ON h.policy_number = v.policy_number
                LEFT JOIN policies p ON p.policy_number = v.policy_number ORDER BY v.created_at, v.id
                """).query((rs, row) -> {
            var d = mapper.readValue(rs.getString("definition"), PolicyRuleDefinition.class);
            var state = rs.getObject("published_at") == null ? "초안"
                    : !rs.getObject("id").equals(rs.getObject("version_id")) ? "이전 버전"
                    : rs.getString("content_hash") == null ? "원문 없음"
                    : !d.contentHash().equals(rs.getString("content_hash")) ? "원문 변경 · 재검토"
                    : now.isBefore(d.validFrom()) ? "적용 전" : !d.appliesAt(now) ? "기간 만료 · 재검토" : "적용 중";
            return new Status(rs.getObject("id", UUID.class), d.policyNumber(), d.ruleVersion(), state);
        }).list();
    }
    public record Status(UUID id, String policyNumber, String ruleVersion, String state) {}
}
