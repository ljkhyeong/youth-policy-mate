package kr.youthpolicymate.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Repository
@Profile("!preview")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class PolicyAiRunStore {
    private static final String RUNS = """
            WITH latest AS (
                SELECT DISTINCT ON (request_id) * FROM policy_ai_rule_auto_runs ORDER BY request_id, attempt DESC
            ), runs AS (
                SELECT a.*, q.policy_number, q.revision, q.sequence, p.current_revision, p.content->>'title' AS title,
                    p.current_revision = q.revision AND p.content_hash = q.content_hash AS source_matches,
                    NOT EXISTS (SELECT 1 FROM policy_ai_rule_requests newer
                        WHERE newer.policy_number = q.policy_number AND newer.sequence > q.sequence) AS latest_request,
                    CASE WHEN a.state = 'RUNNING' AND a.lease_until <= :now THEN 'LEASE_EXPIRED' ELSE a.state END AS display_state,
                    c.status AS candidate_status, r.phase, call.response_body IS NOT NULL AS response_stored
                FROM latest a JOIN policy_ai_rule_requests q ON q.id = a.request_id
                JOIN policies p ON p.policy_number = q.policy_number
                LEFT JOIN policy_ai_rule_candidates c ON c.request_id = q.id
                LEFT JOIN policy_ai_rule_calls call ON call.request_id = q.id
                LEFT JOIN ai_request_reservations r ON r.reservation_id = call.reservation_id
            )
            """;
    private final JdbcClient jdbc;
    private final boolean enabled;

    PolicyAiRunStore(JdbcClient jdbc, @Value("${app.ai.auto.enabled:false}") boolean enabled) {
        this.jdbc = jdbc; this.enabled = enabled;
    }

    PolicyAiRuns.Page list(int page, int pageSize, PolicyAiRuns.Filter filter, String query) {
        var now = jdbc.sql("SELECT transaction_timestamp()").query(OffsetDateTime.class).single();
        var params = Map.<String, Object>of("now", now, "filter", filter.name(), "query", query.strip());
        var where = """
                FROM runs WHERE (:filter = 'ALL' OR display_state = :filter)
                AND (position(lower(:query) in lower(title)) > 0 OR position(:query in policy_number) > 0)
                """;
        var total = jdbc.sql(RUNS + "SELECT count(*) " + where).params(params).query(Long.class).single();
        var items = jdbc.sql(RUNS + "SELECT * " + where + "ORDER BY started_at DESC, sequence DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", pageSize).param("offset", (page - 1) * pageSize).query((rs, row) -> {
                    var finished = rs.getObject("finished_at", OffsetDateTime.class);
                    return new PolicyAiRuns.Item(rs.getObject("request_id", UUID.class), rs.getString("policy_number"), rs.getString("title"),
                            rs.getLong("revision"), rs.getLong("current_revision"), rs.getBoolean("source_matches"), rs.getBoolean("latest_request"),
                            rs.getInt("attempt"), PolicyAiRuns.State.valueOf(rs.getString("display_state")),
                            rs.getObject("started_at", OffsetDateTime.class).toInstant(), finished == null ? null : finished.toInstant(),
                            rs.getString("result_code"), rs.getString("candidate_status"), rs.getString("phase"), rs.getBoolean("response_stored"));
                }).list();
        return new PolicyAiRuns.Page(items, page, pageSize, total, (long) page * pageSize < total, now.toInstant(), enabled);
    }
}
