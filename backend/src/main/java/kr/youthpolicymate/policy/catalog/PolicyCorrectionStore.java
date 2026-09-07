package kr.youthpolicymate.policy.catalog;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!preview")
public class PolicyCorrectionStore {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public PolicyCorrectionStore(JdbcClient jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    Optional<Active> active(String number) {
        return jdbc.sql("""
                SELECT c.id, c.field, c.value, c.status, s.raw_policy::text AS source, conflict.captured_at AS conflict_at
                FROM policy_corrections c JOIN policy_source_snapshots s ON s.id = c.source_snapshot_id
                LEFT JOIN policy_source_snapshots conflict ON conflict.id = c.conflict_snapshot_id
                WHERE c.policy_number = :number AND c.status <> 'RELEASED'
                """).param("number", number).query((rs, row) -> {
                    var at = rs.getObject("conflict_at", OffsetDateTime.class);
                    return new Active(rs.getObject("id", UUID.class), rs.getString("field"), rs.getString("value"),
                            rs.getString("status"), mapper.readTree(rs.getString("source")), at == null ? null : at.toInstant());
                }).optional();
    }

    void conflict(UUID id, long snapshotId) {
        jdbc.sql("UPDATE policy_corrections SET status = 'CONFLICT', conflict_snapshot_id = :snapshot WHERE id = :id")
                .param("snapshot", snapshotId).param("id", id).update();
    }

    public static String sourceKey(String field) { return switch (field) {
        case "TITLE" -> "plcyNm";
        case "ORGANIZATION" -> "sprvsnInstCdNm";
        default -> throw new IllegalArgumentException("보정 항목을 확인해주세요.");
    }; }

    public static String sourceValue(JsonNode raw, String field) { return raw.path(sourceKey(field)).asString("").strip(); }

    record Active(UUID id, String field, String value, String status, JsonNode source, Instant conflictAt) {}
}
