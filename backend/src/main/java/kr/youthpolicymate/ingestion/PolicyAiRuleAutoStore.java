package kr.youthpolicymate.ingestion;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.UUID;

import static kr.youthpolicymate.ingestion.AiDatabaseTime.dbTime;

@Repository
@Profile("!preview")
public class PolicyAiRuleAutoStore {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String CURRENT_REQUEST = """
            p.current_revision = q.revision AND p.content_hash = q.content_hash
            AND NOT EXISTS (SELECT 1 FROM policy_ai_rule_requests newer
                            WHERE newer.policy_number = q.policy_number AND newer.sequence > q.sequence)
            """;
    private final JdbcClient jdbc;
    private final PolicyAiRuleDraftStore drafts;

    public PolicyAiRuleAutoStore(JdbcClient jdbc, PolicyAiRuleDraftStore drafts) { this.jdbc = jdbc; this.drafts = drafts; }

    @Transactional
    public Claim claim(Limits limits, OpenAiRuleClient.Settings settings) {
        // 짧은 선택 트랜잭션만 직렬화한다. 외부 호출 중에는 이 잠금을 보유하지 않는다.
        if (!jdbc.sql("SELECT pg_try_advisory_xact_lock(794631028)").query(Boolean.class).single()) return new Claim("BUSY", null);
        var now = now();
        expire(now, limits.maximumAttempts());
        if (jdbc.sql("SELECT EXISTS(SELECT 1 FROM policy_ai_rule_auto_runs WHERE state = 'RUNNING')").query(Boolean.class).single())
            return new Claim("BUSY", null);
        var start = now.atZone(SEOUL).toLocalDate().atStartOfDay(SEOUL).toInstant();
        if (jdbc.sql("SELECT count(*) FROM policy_ai_rule_auto_runs WHERE started_at >= :start")
                .param("start", dbTime(start)).query(Long.class).single() >= limits.dailyLimit()) return new Claim("DAILY_LIMIT", null);
        if (jdbc.sql("SELECT EXISTS(SELECT 1 FROM policy_ai_rule_auto_runs WHERE started_at > :cutoff)")
                .param("cutoff", dbTime(now.minusSeconds(limits.intervalSeconds()))).query(Boolean.class).single()) return new Claim("WAITING", null);
        var retry = jdbc.sql("""
                SELECT q.id, a.attempt, call.request_id IS NOT NULL AS reserved FROM policy_ai_rule_requests q
                JOIN policies p ON p.policy_number = q.policy_number
                JOIN LATERAL (SELECT * FROM policy_ai_rule_auto_runs WHERE request_id = q.id ORDER BY attempt DESC LIMIT 1) a ON true
                LEFT JOIN policy_ai_rule_candidates result ON result.request_id = q.id
                LEFT JOIN policy_ai_rule_calls call ON call.request_id = q.id
                LEFT JOIN ai_request_reservations reservation ON reservation.reservation_id = call.reservation_id
                WHERE a.state IN ('RETRY_PENDING', 'INTERRUPTED') AND a.attempt < :maximum AND result.request_id IS NULL
                  AND (call.request_id IS NULL OR call.response_body IS NOT NULL OR reservation.phase = 'HELD') AND
                """ + CURRENT_REQUEST + " ORDER BY a.started_at, q.sequence LIMIT 1 FOR UPDATE OF p SKIP LOCKED")
                .param("maximum", limits.maximumAttempts())
                .query((rs, row) -> new Retry(rs.getObject("id", UUID.class), rs.getInt("attempt") + 1, rs.getBoolean("reserved"))).optional();
        if (retry.isEmpty() || !retry.get().reserved()) {
            var budget = jdbc.sql("SELECT limit_won, limit_won - confirmed_won - reserved_won AS remaining FROM ai_budgets WHERE budget_id = :id")
                    .param("id", "policy-ai-" + YearMonth.from(now.atZone(SEOUL)))
                    .query((rs, row) -> new Budget(rs.getBigDecimal("limit_won"), rs.getBigDecimal("remaining"))).optional();
            if (budget.isPresent()) {
                if (budget.get().limit().compareTo(settings.monthlyLimit()) != 0) return new Claim("BUDGET_CONFIGURATION_REQUIRED", null);
                if (budget.get().remaining().compareTo(settings.maximumWon(0)) <= 0) return new Claim("BUDGET_LIMIT", null);
            }
        }
        UUID requestId;
        int attempt;
        if (retry.isPresent()) { requestId = retry.get().requestId(); attempt = retry.get().attempt(); }
        else {
            var policy = jdbc.sql("""
                    SELECT p.policy_number, p.current_revision FROM policies p
                    WHERE p.current_revision > 0
                      AND NOT EXISTS (SELECT 1 FROM policy_ai_rule_requests q WHERE q.policy_number = p.policy_number
                                      AND (q.revision = p.current_revision OR q.content_hash = p.content_hash))
                      AND NOT EXISTS (SELECT 1 FROM policy_rule_versions v WHERE v.policy_number = p.policy_number AND v.definition->>'contentHash' = p.content_hash)
                    ORDER BY p.last_collected_at, p.policy_number LIMIT 1 FOR UPDATE OF p SKIP LOCKED
                    """).query((rs, row) -> new Policy(rs.getString("policy_number"), rs.getLong("current_revision"))).optional();
            if (policy.isEmpty()) return new Claim("EMPTY", null);
            requestId = drafts.prepare(new PolicyAiRuleDraftStore.Preparation(UUID.randomUUID(), policy.get().number(), policy.get().revision(),
                    OpenAiRuleClient.PROMPT_VERSION, "system:ai-rule-auto")).id();
            attempt = 1;
        }
        var run = new Run(UUID.randomUUID(), requestId, attempt, now, now.plus(Duration.ofMinutes(10)));
        jdbc.sql("""
                INSERT INTO policy_ai_rule_auto_runs(id, request_id, attempt, started_at, lease_until, state)
                VALUES (:id, :request, :attempt, :at, :until, 'RUNNING')
                """).param("id", run.id()).param("request", run.requestId()).param("attempt", run.attempt())
                .param("at", dbTime(run.startedAt())).param("until", dbTime(run.leaseUntil())).update();
        return new Claim("STARTED", run);
    }

    @Transactional
    public String finish(Run run, PolicyAiRuleGenerationService.Status result, boolean failed, int maximumAttempts) {
        String state;
        String code = result == null ? "STATUS_UNAVAILABLE" : result.candidateStatus();
        if (code != null && code.equals("DRAFT_CREATED")) state = "COMPLETED";
        else if (!isCurrent(run.requestId())) state = "SUPERSEDED";
        else if (result != null && result.candidateStatus() != null) state = "REVIEW_REQUIRED";
        else if (result != null && !result.responseStored() && result.reservationPhase() != null && !result.reservationPhase().equals("HELD"))
            state = "REVIEW_REQUIRED";
        else state = run.attempt() >= maximumAttempts ? "REVIEW_REQUIRED" : "RETRY_PENDING";
        if (code == null) code = failed ? "GENERATION_FAILED" : result.reservationPhase();
        int updated = jdbc.sql("""
                UPDATE policy_ai_rule_auto_runs SET state = :state, result_code = :code, finished_at = clock_timestamp()
                WHERE id = :id AND state = 'RUNNING'
                """).param("id", run.id()).param("state", state).param("code", code).update();
        return updated == 1 ? state : "LEASE_EXPIRED";
    }

    private boolean isCurrent(UUID id) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM policy_ai_rule_requests q JOIN policies p ON p.policy_number = q.policy_number WHERE q.id = :id AND "
                + CURRENT_REQUEST + ")").param("id", id).query(Boolean.class).single();
    }

    private void expire(Instant now, int maximum) {
        jdbc.sql("""
                UPDATE policy_ai_rule_auto_runs a SET finished_at = :at, result_code = 'WORKER_INTERRUPTED', state = CASE
                    WHEN EXISTS (SELECT 1 FROM policy_ai_rule_candidates c WHERE c.request_id = a.request_id AND c.status = 'DRAFT_CREATED') THEN 'COMPLETED'
                    WHEN EXISTS (SELECT 1 FROM policy_ai_rule_candidates c WHERE c.request_id = a.request_id) THEN 'REVIEW_REQUIRED'
                    WHEN NOT EXISTS (SELECT 1 FROM policy_ai_rule_requests q JOIN policies p ON p.policy_number = q.policy_number WHERE q.id = a.request_id AND
                """ + CURRENT_REQUEST + """
                    ) THEN 'SUPERSEDED'
                    WHEN a.attempt >= :maximum OR EXISTS (
                        SELECT 1 FROM policy_ai_rule_calls c JOIN ai_request_reservations r ON r.reservation_id = c.reservation_id
                        WHERE c.request_id = a.request_id AND c.response_body IS NULL AND r.phase <> 'HELD'
                    ) THEN 'REVIEW_REQUIRED' ELSE 'INTERRUPTED' END
                WHERE a.state = 'RUNNING' AND a.lease_until <= :at
                """).param("at", dbTime(now)).param("maximum", maximum).update();
    }

    public List<Summary> recent() {
        return jdbc.sql("""
                SELECT a.*, q.policy_number, q.revision, c.status AS candidate_status, r.phase
                FROM policy_ai_rule_auto_runs a JOIN policy_ai_rule_requests q ON q.id = a.request_id
                LEFT JOIN policy_ai_rule_candidates c ON c.request_id = a.request_id
                LEFT JOIN ai_request_reservations r ON r.reservation_id = a.request_id::text
                ORDER BY a.started_at DESC, a.id LIMIT 20
                """).query((rs, row) -> new Summary(rs.getObject("id", UUID.class), rs.getObject("request_id", UUID.class),
                        rs.getString("policy_number"), rs.getLong("revision"), rs.getInt("attempt"), rs.getString("state"),
                        rs.getString("result_code"), rs.getString("candidate_status"), rs.getString("phase"),
                        rs.getObject("started_at", OffsetDateTime.class).toInstant())).list();
    }

    private Instant now() { return jdbc.sql("SELECT clock_timestamp()").query(OffsetDateTime.class).single().toInstant(); }
    public record Limits(int dailyLimit, long intervalSeconds, int maximumAttempts) {
        public Limits {
            if (dailyLimit < 1 || dailyLimit > 100 || intervalSeconds < 60 || maximumAttempts < 1 || maximumAttempts > 5)
                throw new IllegalArgumentException("AI 자동 처리의 일일 한도(1~100건)·간격(60초 이상)·최대 시도(1~5회)를 설정해주세요.");
        }
    }
    public record Run(UUID id, UUID requestId, int attempt, Instant startedAt, Instant leaseUntil) {}
    public record Claim(String reason, Run run) {}
    public record Summary(UUID runId, UUID requestId, String policyNumber, long revision, int attempt, String state,
                          String resultCode, String candidateStatus, String reservationPhase, Instant startedAt) {}
    private record Retry(UUID requestId, int attempt, boolean reserved) {}
    private record Budget(java.math.BigDecimal limit, java.math.BigDecimal remaining) {}
    private record Policy(String number, long revision) {}
}
