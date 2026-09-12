package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.policy.PolicyObservation;
import kr.youthpolicymate.policy.PolicyRevisionState.AppliedRevision;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static kr.youthpolicymate.ingestion.AiDatabaseTime.dbTime;

@Repository
@Profile("!preview")
public class PolicyAiRuleCallStore {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final PolicyAiRuleDraftStore drafts;
    private final AiBudgetReservationStore reservations;
    private final AiBudgetReservationLifecycleStore lifecycle;

    public PolicyAiRuleCallStore(JdbcClient jdbc, ObjectMapper mapper, PolicyAiRuleDraftStore drafts,
                                 AiBudgetReservationStore reservations, AiBudgetReservationLifecycleStore lifecycle) {
        this.jdbc = jdbc; this.mapper = mapper; this.drafts = drafts; this.reservations = reservations; this.lifecycle = lifecycle;
    }

    @Transactional
    public Call reserve(PolicyAiRuleDraftStore.Prepared source, JsonNode body, long inputTokens,
                        OpenAiRuleClient.Settings settings, Instant at) {
        if (!drafts.lockCurrent(source)) throw new IllegalStateException("공고·요청이 변경됐거나 이미 결과가 있습니다.");
        var previous = find(source.id());
        if (previous.isPresent()) return previous.get();
        var month = YearMonth.from(at.atZone(ZoneId.of("Asia/Seoul")));
        var start = month.atDay(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        var end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        var budgetId = "policy-ai-" + month;
        jdbc.sql("""
                INSERT INTO ai_budgets(budget_id, starts_at, ends_at, limit_won, confirmed_won, reserved_won, created_at, updated_at)
                VALUES (:id, :start, :end, :limit, 0, 0, :at, :at) ON CONFLICT DO NOTHING
                """).param("id", budgetId).param("start", dbTime(start)).param("end", dbTime(end))
                .param("limit", settings.monthlyLimit()).param("at", dbTime(at)).update();
        var balance = balance(budgetId);
        if (balance.limitWon().compareTo(settings.monthlyLimit()) != 0)
            throw new IllegalStateException("이미 설정된 이번 달 AI 예산과 설정값이 다릅니다. 예산을 임의로 덮어쓰지 않습니다.");
        var until = settings.validUntil().isBefore(end) ? settings.validUntil() : end;
        var cost = new AiRequestBudget.CostCeiling(request(source), OpenAiRuleClient.PROMPT_VERSION + "/" + settings.pricingVersion(),
                until, settings.maximumWon(inputTokens));
        var attempt = reservations.reserve(source.id().toString(), new PolicyAiRequestAdmission.ReservationRequired(balance, cost), at);
        if (attempt.decision() != AiBudgetReservationStore.Decision.RESERVED)
            throw new IllegalStateException("AI 예산 예약 보류: " + attempt.decision());
        jdbc.sql("""
                INSERT INTO policy_ai_rule_calls(request_id, reservation_id, request_body, input_tokens, input_won_per_million, output_won_per_million)
                VALUES (:id, :reservation, CAST(:body AS jsonb), :tokens, :inputRate, :outputRate)
                """).param("id", source.id()).param("reservation", source.id().toString()).param("body", body.toString())
                .param("tokens", inputTokens).param("inputRate", settings.inputRate()).param("outputRate", settings.outputRate()).update();
        return find(source.id()).orElseThrow();
    }

    @Transactional
    public AiBudgetReservationLifecycleStore.Transition dispatch(PolicyAiRuleDraftStore.Prepared source, Call call,
                                                                 AiBudgetReservationState.Dispatch dispatch) {
        if (!drafts.lockCurrent(source) || !dispatch.dispatchedAt().isBefore(call.validUntil()))
            return lifecycle.cancelBeforeDispatch(source.id().toString(),
                    new AiBudgetReservationState.Cancellation("rule-stale-" + source.id(), dispatch.dispatchedAt()));
        return lifecycle.dispatch(source.id().toString(), dispatch);
    }

    @Transactional
    public void response(UUID id, OpenAiRuleClient.Response response, Instant at) {
        int saved = jdbc.sql("""
                UPDATE policy_ai_rule_calls SET response_status = :status, response_body = :body, received_at = :at
                WHERE request_id = :id AND response_body IS NULL
                """).param("status", response.status()).param("body", response.body()).param("at", dbTime(at)).param("id", id).update();
        if (saved != 1) throw new IllegalStateException("AI 응답이 이미 저장됐거나 호출 기록이 없습니다.");
    }

    public Optional<Call> find(UUID id) {
        return jdbc.sql("""
                SELECT c.*, r.budget_id, r.pricing_version, r.cost_valid_until, r.maximum_won, r.reserved_at
                FROM policy_ai_rule_calls c JOIN ai_request_reservations r ON r.reservation_id = c.reservation_id
                WHERE c.request_id = :id
                """).param("id", id).query((rs, row) -> new Call(rs.getObject("request_id", UUID.class), rs.getString("budget_id"),
                        mapper.readTree(rs.getString("request_body")), rs.getLong("input_tokens"), rs.getString("pricing_version"),
                        rs.getObject("cost_valid_until", OffsetDateTime.class).toInstant(), rs.getBigDecimal("maximum_won"),
                        rs.getObject("reserved_at", OffsetDateTime.class).toInstant(), rs.getString("response_body") == null ? null
                        : new OpenAiRuleClient.Response(rs.getInt("response_status"), rs.getString("response_body")))).optional();
    }

    public AiRequestBudget.Balance balance(String budgetId) {
        return jdbc.sql("SELECT * FROM ai_budgets WHERE budget_id = :id").param("id", budgetId).query((rs, row) ->
                new AiRequestBudget.Balance(rs.getString("budget_id"), rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("ends_at", OffsetDateTime.class).toInstant(), rs.getBigDecimal("limit_won"),
                        rs.getBigDecimal("confirmed_won"), rs.getBigDecimal("reserved_won"))).single();
    }

    static PolicyAiResult.Request request(PolicyAiRuleDraftStore.Prepared source) {
        // 카탈로그 개정 관찰의 순번이다. 외부 수집 요청 순번과 다른 이름 공간을 사용한다.
        var observation = new PolicyObservation(source.policyNumber(), source.revision(), source.sourceCapturedAt(),
                new PolicyObservation.Readable(new PolicyObservation.SnapshotReference("ontong-catalog-revision",
                        Long.toString(source.sourceSnapshotId()), OntongPolicyCapture.hash(source.rawPolicy().toString())),
                        new PolicyObservation.ContentFingerprint("policy-content-v1", source.contentHash())));
        return new PolicyAiResult.Request(new AppliedRevision(source.revision(), observation), PolicyAiResult.Kind.CONDITION_EXTRACTION,
                source.generationVersion(), source.sequence(), source.preparedAt());
    }

    record Call(UUID requestId, String budgetId, JsonNode body, long inputTokens, String pricingVersion, Instant validUntil,
                BigDecimal maximumWon, Instant reservedAt, OpenAiRuleClient.Response response) {
        AiRequestBudget.CostCeiling cost(PolicyAiRuleDraftStore.Prepared source) {
            return new AiRequestBudget.CostCeiling(request(source), pricingVersion, validUntil, maximumWon);
        }
    }
}
