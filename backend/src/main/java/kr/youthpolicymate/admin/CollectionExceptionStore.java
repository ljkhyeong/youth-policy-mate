package kr.youthpolicymate.admin;

import kr.youthpolicymate.policy.catalog.PolicyContent;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!preview")
@Transactional(readOnly = true)
class CollectionExceptionStore {
    private static final String FIELDS = """
            i.run_id, i.item_index, p.page_number, i.outcome, i.attempts, i.updated_at,
            CASE WHEN jsonb_typeof(i.raw_policy -> 'plcyNo') = 'string'
                 THEN i.raw_policy ->> 'plcyNo' END AS source_number
            """;
    private static final String FAILURES = """
            FROM ontong_collection_items i JOIN ontong_collection_pages p ON p.run_id = i.run_id
            WHERE i.outcome IN ('INVALID_ITEM', 'STORE_FAILED')
            """;
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    CollectionExceptionStore(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    CollectionExceptions.Page list(int page, int pageSize) {
        var items = jdbc.sql("SELECT " + FIELDS + FAILURES + """
                ORDER BY i.updated_at DESC NULLS LAST, p.request_sequence DESC, i.item_index
                LIMIT :limit OFFSET :offset
                """)
                .param("limit", pageSize + 1).param("offset", (page - 1) * pageSize)
                .query((rs, row) -> item(rs)).list();
        var hasNext = items.size() > pageSize;
        return new CollectionExceptions.Page(hasNext ? items.subList(0, pageSize) : items, page, pageSize, hasNext);
    }

    Optional<CollectionExceptions.Detail> detail(UUID runId, int itemIndex) {
        return jdbc.sql("SELECT " + FIELDS + ", i.raw_policy::text AS raw_policy " + FAILURES
                        + " AND i.run_id = :runId AND i.item_index = :itemIndex")
                .param("runId", runId).param("itemIndex", itemIndex)
                .query((rs, row) -> new CollectionExceptions.Detail(item(rs), rs.getString("raw_policy"), null))
                .optional().map(detail -> new CollectionExceptions.Detail(detail.item(), detail.rawPolicyJson(),
                        currentPolicy(detail.item().policyNumber()).orElse(null)));
    }

    private Optional<CollectionExceptions.CurrentPolicy> currentPolicy(String number) {
        if (number == null) return Optional.empty();
        return jdbc.sql("""
                SELECT policy_number, current_revision, last_collected_at, content::text AS content
                FROM policies WHERE policy_number = :number AND current_revision > 0
                """).param("number", number).query((rs, row) -> new CollectionExceptions.CurrentPolicy(
                        rs.getString("policy_number"), rs.getLong("current_revision"),
                        rs.getObject("last_collected_at", OffsetDateTime.class).toInstant(),
                        mapper.readValue(rs.getString("content"), PolicyContent.class))).optional();
    }

    private static CollectionExceptions.Item item(ResultSet rs) throws SQLException {
        var number = rs.getString("source_number");
        if (number != null) number = number.strip();
        if (number != null && !number.matches("[0-9]{1,100}")) number = null;
        var updatedAt = rs.getObject("updated_at", OffsetDateTime.class);
        return new CollectionExceptions.Item(rs.getObject("run_id", UUID.class), rs.getInt("item_index"),
                rs.getInt("page_number"), CollectionExceptions.Outcome.valueOf(rs.getString("outcome")),
                rs.getInt("attempts"), updatedAt == null ? null : updatedAt.toInstant(), number);
    }
}
