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

    CollectionExceptions.PageFailureList pageFailures(int page, int pageSize) {
        var items = jdbc.sql("""
                SELECT run_id, page_number, state, failure_code, started_at, dispatch_started_at, received_at,
                       raw_body IS NOT NULL AS response_stored
                FROM ontong_collection_pages WHERE state IN ('FETCH_FAILED', 'INVALID_RESPONSE')
                ORDER BY request_sequence DESC LIMIT :limit OFFSET :offset
                """).param("limit", pageSize + 1).param("offset", (page - 1) * pageSize)
                .query((rs, row) -> {
                    var code = rs.getString("failure_code");
                    Integer httpStatus = code != null && code.matches("HTTP_[1-5][0-9]{2}") ? Integer.valueOf(code.substring(5)) : null;
                    var reason = httpStatus != null ? CollectionExceptions.PageFailureReason.HTTP_ERROR : switch (code) {
                        case "API_KEY_MISSING", "NON_JSON_RESPONSE", "SECRET_IN_RESPONSE", "REQUEST_INTERRUPTED",
                             "REQUEST_OR_RESPONSE_FAILED", "RESPONSE_STORE_FAILED", "INVALID_LIST_RESPONSE" -> CollectionExceptions.PageFailureReason.valueOf(code);
                        case null, default -> CollectionExceptions.PageFailureReason.UNKNOWN;
                    };
                    var dispatched = rs.getObject("dispatch_started_at", OffsetDateTime.class);
                    var received = rs.getObject("received_at", OffsetDateTime.class);
                    return new CollectionExceptions.PageFailure(rs.getObject("run_id", UUID.class), rs.getInt("page_number"),
                            CollectionExceptions.PageState.valueOf(rs.getString("state")), reason, httpStatus,
                            rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                            dispatched == null ? null : dispatched.toInstant(), received == null ? null : received.toInstant(),
                            rs.getBoolean("response_stored"));
                }).list();
        var hasNext = items.size() > pageSize;
        return new CollectionExceptions.PageFailureList(hasNext ? items.subList(0, pageSize) : items, page, pageSize, hasNext);
    }

    private Optional<CollectionExceptions.CurrentPolicy> currentPolicy(String number) {
        if (number == null) return Optional.empty();
        return jdbc.sql("""
                SELECT p.policy_number, p.current_revision, p.last_collected_at, p.content::text AS content,
                       s.captured_at AS source_captured_at, previous.revision AS previous_revision,
                       previous.content::text AS previous_content, previous_source.captured_at AS previous_captured_at
                FROM policies p
                JOIN policy_revisions r ON r.policy_number = p.policy_number AND r.revision = p.current_revision
                JOIN policy_source_snapshots s ON s.id = r.source_snapshot_id
                LEFT JOIN policy_revisions previous ON previous.policy_number = p.policy_number AND previous.revision = p.current_revision - 1
                LEFT JOIN policy_source_snapshots previous_source ON previous_source.id = previous.source_snapshot_id
                WHERE p.policy_number = :number AND p.current_revision > 0
                """).param("number", number).query((rs, row) -> new CollectionExceptions.CurrentPolicy(
                        rs.getString("policy_number"), rs.getLong("current_revision"),
                        rs.getObject("last_collected_at", OffsetDateTime.class).toInstant(),
                        mapper.readValue(rs.getString("content"), PolicyContent.class),
                        rs.getObject("source_captured_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("previous_revision") == null ? null : new CollectionExceptions.Revision(
                                rs.getLong("previous_revision"), rs.getObject("previous_captured_at", OffsetDateTime.class).toInstant(),
                                mapper.readValue(rs.getString("previous_content"), PolicyContent.class)))).optional();
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
