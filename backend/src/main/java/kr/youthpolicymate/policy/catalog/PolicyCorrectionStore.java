package kr.youthpolicymate.policy.catalog;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PolicyCorrectionStore {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public PolicyCorrectionStore(JdbcClient jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    Optional<Active> active(String number) {
        return jdbc.sql("""
                SELECT c.id, c.field, c.value, c.status, s.raw_policy::text AS source
                FROM policy_corrections c JOIN policy_source_snapshots s ON s.id = c.source_snapshot_id
                WHERE c.policy_number = :number AND c.status <> 'RELEASED'
                """).param("number", number).query((rs, row) -> new Active(rs.getObject("id", UUID.class),
                        Field.valueOf(rs.getString("field")), rs.getString("value"), rs.getString("status"),
                        mapper.readTree(rs.getString("source")))).optional();
    }

    void conflict(UUID id, long snapshotId) {
        jdbc.sql("UPDATE policy_corrections SET status = 'CONFLICT', conflict_snapshot_id = :snapshot WHERE id = :id")
                .param("snapshot", snapshotId).param("id", id).update();
    }

    /** 보정할 수 있는 표시 항목과 원천 필드. toString을 재정의하면 Jackson 3 기본 설정에서 JSON 값이 바뀐다. */
    public enum Field {
        TITLE("plcyNm"), ORGANIZATION("sprvsnInstCdNm");

        private final String sourceKey;

        Field(String sourceKey) { this.sourceKey = sourceKey; }

        public String sourceKey() { return sourceKey; }

        public String sourceValue(JsonNode raw) { return raw.path(sourceKey).asString("").strip(); }
    }

    record Active(UUID id, Field field, String value, String status, JsonNode source) {}
}
