package kr.youthpolicymate.admin;

import kr.youthpolicymate.ingestion.AiProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

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
                    c.status AS candidate_status, call.phase, call.response_body IS NOT NULL AS response_stored
                FROM latest a JOIN policy_ai_rule_requests q ON q.id = a.request_id
                JOIN policies p ON p.policy_number = q.policy_number
                LEFT JOIN policy_ai_rule_candidates c ON c.request_id = q.id
                LEFT JOIN policy_ai_rule_calls call ON call.request_id = q.id
            )
            """;
    // runs의 a.state와 display_state가 함께 있으므로 PolicyAiRuns.Item 구성요소 이름에 맞춘 열만 고른다.
    private static final String ITEM = """
            SELECT request_id, policy_number, title, revision, current_revision, source_matches, latest_request, attempt,
                display_state AS state, started_at, finished_at, result_code, candidate_status, phase AS reservation_phase, response_stored
            """;
    private final JdbcClient jdbc;
    private final AiProperties properties;

    PolicyAiRunStore(JdbcClient jdbc, AiProperties properties) {
        this.jdbc = jdbc; this.properties = properties;
    }

    PolicyAiRuns.Page list(int page, int pageSize, PolicyAiRuns.Filter filter, String query) {
        var now = jdbc.sql("SELECT transaction_timestamp()").query(OffsetDateTime.class).single();
        var params = Map.<String, Object>of("now", now, "filter", filter.name(), "query", query.strip());
        var where = """
                FROM runs WHERE (:filter = 'ALL' OR display_state = :filter)
                AND (position(lower(:query) in lower(title)) > 0 OR position(:query in policy_number) > 0)
                """;
        var total = jdbc.sql(RUNS + "SELECT count(*) " + where).params(params).query(Long.class).single();
        var items = jdbc.sql(RUNS + ITEM + where + "ORDER BY started_at DESC, sequence DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", pageSize).param("offset", (page - 1) * pageSize).query(PolicyAiRuns.Item.class).list();
        return new PolicyAiRuns.Page(items, page, pageSize, total, (long) page * pageSize < total, now.toInstant(),
                properties.auto().enabled());
    }
}
