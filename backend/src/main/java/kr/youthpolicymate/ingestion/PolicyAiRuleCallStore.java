package kr.youthpolicymate.ingestion;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static kr.youthpolicymate.policy.SeoulTime.SEOUL;

/** 규칙 추출 호출 한 건의 비용 예약·발송·응답·정산을 policy_ai_rule_calls 한 행과 월 예산 행으로 관리한다. */
@Repository
public class PolicyAiRuleCallStore {
    // 자동 실행의 예산 확인도 같은 서울 기준 월 예산을 사용한다.
    static String monthlyBudgetId(Instant at) {
        return "policy-ai-" + YearMonth.from(at.atZone(SEOUL));
    }

    // 정산·무과금 확인은 외부 호출을 기록한 예약에만 적용한다.
    private static final String SENT = "phase IN ('DISPATCHED', 'OUTCOME_UNKNOWN')";
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final PolicyAiRuleDraftStore drafts;

    public PolicyAiRuleCallStore(JdbcClient jdbc, ObjectMapper mapper, PolicyAiRuleDraftStore drafts) {
        this.jdbc = jdbc; this.mapper = mapper; this.drafts = drafts;
    }

    // 기본 격리 수준을 유지한다. 동시 예약이 먼저 커밋하면 PostgreSQL이 최신 예산 행으로 조건을 다시 평가한다.
    @Transactional
    public Call reserve(PolicyAiRuleDraftStore.Prepared source, JsonNode body, long inputTokens, AiProperties settings, Instant at) {
        if (!drafts.lockCurrent(source)) throw new IllegalStateException("공고·요청이 변경됐거나 이미 결과가 있습니다.");
        var previous = find(source.id());
        if (previous.isPresent()) return previous.get();
        var month = YearMonth.from(at.atZone(SEOUL));
        var start = month.atDay(1).atStartOfDay(SEOUL).toInstant();
        var end = month.plusMonths(1).atDay(1).atStartOfDay(SEOUL).toInstant();
        var budgetId = monthlyBudgetId(at);
        jdbc.sql("""
                INSERT INTO ai_budgets(budget_id, starts_at, ends_at, limit_won, confirmed_won, reserved_won, created_at, updated_at)
                VALUES (:id, :start, :end, :limit, 0, 0, :at, :at) ON CONFLICT DO NOTHING
                """).param("id", budgetId).param("start", utc(start)).param("end", utc(end))
                .param("limit", settings.monthlyLimitWon()).param("at", utc(at)).update();
        // 한도는 처음 만든 뒤 바꾸지 않으므로 잠그지 않고 읽는다.
        var limit = jdbc.sql("SELECT limit_won FROM ai_budgets WHERE budget_id = :id").param("id", budgetId).query(BigDecimal.class).single();
        if (limit.compareTo(settings.monthlyLimitWon()) != 0)
            throw new IllegalStateException("이미 설정된 이번 달 AI 예산과 설정값이 다릅니다. 예산을 임의로 덮어쓰지 않습니다.");
        var until = settings.pricingValidUntil().isBefore(end) ? settings.pricingValidUntil() : end;
        if (!at.isBefore(until)) throw new IllegalStateException("AI 예산 예약 보류: COST_EXPIRED");
        var maximum = settings.maximumWon(inputTokens);
        int held = jdbc.sql("""
                UPDATE ai_budgets SET reserved_won = reserved_won + :maximum, updated_at = :at
                WHERE budget_id = :id AND starts_at <= :at AND :at < ends_at
                  AND limit_won - confirmed_won - reserved_won > 0 AND limit_won - confirmed_won - reserved_won >= :maximum
                """).param("maximum", maximum).param("at", utc(at)).param("id", budgetId).update();
        if (held != 1) throw new IllegalStateException("AI 예산 예약 보류: BUDGET_LIMIT");
        jdbc.sql("""
                INSERT INTO policy_ai_rule_calls(request_id, budget_id, request_body, input_tokens, input_won_per_million,
                    output_won_per_million, pricing_version, maximum_won, reserved_at, cost_valid_until, phase)
                VALUES (:id, :budget, CAST(:body AS jsonb), :tokens, :inputRate, :outputRate, :pricing, :maximum, :at, :until, 'HELD')
                """).param("id", source.id()).param("budget", budgetId).param("body", body.toString()).param("tokens", inputTokens)
                .param("inputRate", settings.inputWonPerMillion()).param("outputRate", settings.outputWonPerMillion())
                .param("pricing", OpenAiRuleClient.PROMPT_VERSION + "/" + settings.pricingVersion()).param("maximum", maximum)
                .param("at", utc(at)).param("until", utc(until)).update();
        return find(source.id()).orElseThrow();
    }

    // HELD 행을 DISPATCHED로 바꾼 한 실행만 true를 받아 외부 API를 호출한다. 공고가 바뀌었거나 요금 기간이 끝났으면 예약을 해제한다.
    @Transactional
    public boolean dispatch(PolicyAiRuleDraftStore.Prepared source, Call call, Instant at) {
        if (drafts.lockCurrent(source) && at.isBefore(call.validUntil()))
            return jdbc.sql("UPDATE policy_ai_rule_calls SET phase = 'DISPATCHED', dispatched_at = :at WHERE request_id = :id AND phase = 'HELD'")
                    .param("at", utc(at)).param("id", source.id()).update() == 1;
        complete(source.id(), "CANCELLED", "phase = 'HELD'", null, null, at);
        return false;
    }

    // 연결 유실·응답 한도 초과는 결과를 알 수 없으므로 예약액을 유지한다.
    public void markOutcomeUnknown(UUID id, Instant at) {
        jdbc.sql("UPDATE policy_ai_rule_calls SET phase = 'OUTCOME_UNKNOWN', outcome_unknown_at = :at WHERE request_id = :id AND phase = 'DISPATCHED'")
                .param("at", utc(at)).param("id", id).update();
    }

    @Transactional
    public void response(UUID id, OpenAiRuleClient.Response response, Instant at) {
        int saved = jdbc.sql("""
                UPDATE policy_ai_rule_calls SET response_status = :status, response_body = :body, received_at = :at
                WHERE request_id = :id AND response_body IS NULL
                """).param("status", response.status()).param("body", response.body()).param("at", utc(at)).param("id", id).update();
        if (saved != 1) throw new IllegalStateException("AI 응답이 이미 저장됐거나 호출 기록이 없습니다.");
    }

    /** 운영자가 확인한 실제 청구액을 정산한다. 같은 확인 ID·시각·금액의 재실행은 REPLAYED다. */
    @Transactional
    public Completion settle(UUID id, String confirmationId, Instant confirmedAt, BigDecimal actualWon) {
        return complete(id, "SETTLED", SENT, confirmationId, actualWon, confirmedAt);
    }

    /** 운영자가 무과금을 확인한 발송 건의 예약만 해제한다. */
    @Transactional
    public Completion releaseNoCharge(UUID id, String confirmationId, Instant confirmedAt) {
        return complete(id, "RELEASED_NO_CHARGE", SENT, confirmationId, null, confirmedAt);
    }

    public Optional<Call> find(UUID id) {
        return jdbc.sql("SELECT * FROM policy_ai_rule_calls WHERE request_id = :id").param("id", id)
                .query((rs, row) -> new Call(mapper.readTree(rs.getString("request_body")),
                        rs.getObject("cost_valid_until", OffsetDateTime.class).toInstant(), rs.getBigDecimal("maximum_won"),
                        rs.getString("phase"), rs.getString("response_body") == null ? null
                        : new OpenAiRuleClient.Response(rs.getInt("response_status"), rs.getString("response_body")))).optional();
    }

    // 호출 행의 단계 전환과 예산 행의 예약 해제·확정을 한 문장으로 처리한다. 다른 종료가 먼저 커밋되면 갱신하지 않는다.
    private Completion complete(UUID id, String phase, String from, String confirmationId, BigDecimal actualWon, Instant at) {
        var maximum = jdbc.sql("""
                WITH done AS (
                    UPDATE policy_ai_rule_calls SET phase = :phase, completion_id = :confirmation, completed_at = :at, actual_won = :actual
                    WHERE request_id = :id AND\s""" + from + """
                    RETURNING budget_id, maximum_won)
                UPDATE ai_budgets b SET reserved_won = b.reserved_won - d.maximum_won, confirmed_won = b.confirmed_won + :confirmed, updated_at = :at
                FROM done d WHERE b.budget_id = d.budget_id RETURNING d.maximum_won
                """).param("phase", phase).param("confirmation", confirmationId, Types.VARCHAR).param("at", utc(at))
                .param("actual", actualWon, Types.NUMERIC).param("confirmed", actualWon == null ? BigDecimal.ZERO : actualWon)
                .param("id", id).query(BigDecimal.class).optional();
        if (maximum.isPresent())
            return actualWon != null && actualWon.compareTo(maximum.get()) > 0 ? Completion.OVER_RESERVATION : Completion.APPLIED;
        return jdbc.sql("""
                SELECT (phase, completion_id, completed_at, actual_won) IS NOT DISTINCT FROM (:phase, :confirmation, :at, :actual)
                FROM policy_ai_rule_calls WHERE request_id = :id
                """).param("phase", phase).param("confirmation", confirmationId, Types.VARCHAR).param("at", utc(at))
                .param("actual", actualWon, Types.NUMERIC).param("id", id).query(Boolean.class).optional()
                .map(same -> same ? Completion.REPLAYED : Completion.CONFLICT)
                .orElseThrow(() -> new IllegalArgumentException("AI 호출 기록을 찾을 수 없습니다."));
    }

    private static OffsetDateTime utc(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }

    public enum Completion { APPLIED, OVER_RESERVATION, REPLAYED, CONFLICT }

    record Call(JsonNode body, Instant validUntil, BigDecimal maximumWon, String phase, OpenAiRuleClient.Response response) {}
}
