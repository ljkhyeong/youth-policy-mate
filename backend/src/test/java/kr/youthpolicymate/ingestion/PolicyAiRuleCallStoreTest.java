package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.PolicyAiRuleCallStore.Completion;
import kr.youthpolicymate.policy.catalog.PolicyCatalogStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = {"app.reminders.enabled=false", "app.email.enabled=false", "app.ontong.schedule.enabled=false"})
class PolicyAiRuleCallStoreTest {
    private static final String FIRST = "99970000000000000001";
    private static final String SECOND = "99970000000000000002";
    private static final String THIRD = "99970000000000000003";
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PolicyCatalogStore policies;
    @Autowired PolicyAiRuleDraftStore drafts;
    @Autowired PolicyAiRuleCallStore calls;
    private long sequence;

    @BeforeEach void setup() {
        for (var table : List.of("policy_ai_rule_auto_runs", "policy_ai_rule_calls", "ai_budgets", "policy_ai_rule_candidates",
                "policy_ai_rule_requests", "policy_revisions", "policy_source_snapshots", "policies")) jdbc.sql("DELETE FROM " + table).update();
    }

    @Test @DisplayName("다른 공고의 동시 예약은 최신 잔액으로 다시 검사해 남은 한도 안의 요청만 확보한다")
    void reservesWithinLatestBalance() throws Exception {
        var settings = settings("100", Instant.now().plusSeconds(3600));
        var earlier = prepare(THIRD);
        var call = reserve(earlier, 10, settings, Instant.now());
        assertThat(call.phase()).isEqualTo("HELD");
        assertThat(call.maximumWon()).isEqualByComparingTo("20");
        assertThat(reserve(earlier, 90, settings, Instant.now()).maximumWon()).isEqualByComparingTo("20");
        var sources = List.of(prepare(FIRST), prepare(SECOND));
        var start = new CountDownLatch(1);
        var results = new ArrayList<String>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = sources.stream().map(source -> pool.submit(() -> {
                start.await();
                return reserve(source, 50, settings, Instant.now()).phase();
            })).toList();
            start.countDown();
            for (var task : tasks) {
                try { results.add(task.get(10, TimeUnit.SECONDS)); }
                catch (ExecutionException exception) { results.add(exception.getCause().getMessage()); }
            }
        }
        assertThat(results).containsExactlyInAnyOrder("HELD", "AI 예산 예약 보류: BUDGET_LIMIT");
        assertThat(amount("reserved_won")).isEqualByComparingTo("80");
        assertThat(jdbc.sql("SELECT count(*) FROM policy_ai_rule_calls").query(Long.class).single()).isEqualTo(2);
    }

    @Test @DisplayName("요금 만료·예산 기간 밖·한도 부족·다른 월 한도 설정에는 예약하지 않는다")
    void rejectsExpiredCostAndInactiveBudget() {
        var source = prepare(FIRST);
        var at = Instant.now();
        assertThatThrownBy(() -> reserve(source, 50, settings("100", at), at)).hasMessage("AI 예산 예약 보류: COST_EXPIRED");
        var settings = settings("100", at.plusSeconds(3600));
        assertThatThrownBy(() -> reserve(source, 91, settings, at)).hasMessage("AI 예산 예약 보류: BUDGET_LIMIT");
        assertThat(jdbc.sql("SELECT count(*) FROM ai_budgets").query(Long.class).single()).isZero();
        jdbc.sql("""
                INSERT INTO ai_budgets(budget_id, starts_at, ends_at, limit_won, created_at, updated_at)
                VALUES (:id, :start, :start + interval '1 day', 100, now(), now())
                """).param("id", PolicyAiRuleCallStore.monthlyBudgetId(at)).param("start", at.plusSeconds(1).atOffset(ZoneOffset.UTC)).update();
        assertThatThrownBy(() -> reserve(source, 10, settings, at)).hasMessage("AI 예산 예약 보류: BUDGET_LIMIT");
        assertThatThrownBy(() -> reserve(source, 10, settings("90", at.plusSeconds(3600)), at)).hasMessageContaining("덮어쓰지 않습니다");
        assertThat(amount("reserved_won")).isZero();
        assertThat(calls.find(source.id())).isEmpty();
    }

    @Test @DisplayName("한 번만 발송하고 결과 미확인은 예약액을 유지하며 확인한 청구를 정산·재생·충돌로 구분한다")
    void dispatchesOnceAndSettles() {
        var source = prepare(FIRST);
        var call = reserve(source, 50, settings("100", Instant.now().plusSeconds(3600)), Instant.now());
        calls.markOutcomeUnknown(source.id(), Instant.now());
        assertThat(phase(source)).isEqualTo("HELD");
        assertThat(calls.dispatch(source, call, Instant.now())).isTrue();
        assertThat(calls.dispatch(source, call, Instant.now())).isFalse();
        calls.markOutcomeUnknown(source.id(), Instant.now());
        assertThat(phase(source)).isEqualTo("OUTCOME_UNKNOWN");
        assertThat(amount("reserved_won")).isEqualByComparingTo("60");

        var confirmedAt = Instant.now();
        assertThat(calls.settle(source.id(), "charge-a", confirmedAt, new BigDecimal("7.50"))).isEqualTo(Completion.APPLIED);
        assertThat(calls.settle(source.id(), "charge-a", confirmedAt, new BigDecimal("7.5"))).isEqualTo(Completion.REPLAYED);
        assertThat(calls.settle(source.id(), "charge-a", confirmedAt, new BigDecimal("8"))).isEqualTo(Completion.CONFLICT);
        assertThat(calls.releaseNoCharge(source.id(), "no-charge-a", Instant.now())).isEqualTo(Completion.CONFLICT);
        assertThat(amount("confirmed_won")).isEqualByComparingTo("7.5");
        assertThat(amount("reserved_won")).isZero();

        var over = prepare(SECOND);
        var overCall = reserve(over, 10, settings("100", Instant.now().plusSeconds(3600)), Instant.now());
        calls.dispatch(over, overCall, Instant.now());
        assertThat(calls.settle(over.id(), "charge-b", Instant.now(), new BigDecimal("25"))).isEqualTo(Completion.OVER_RESERVATION);
        assertThat(amount("confirmed_won")).isEqualByComparingTo("32.5");
        assertThat(amount("reserved_won")).isZero();
    }

    @Test @DisplayName("발송 전에 새 요청이 생기거나 요금 기간이 끝나면 호출하지 않고 예약액을 해제한다")
    void cancelsBeforeDispatch() {
        var settings = settings("100", Instant.now().plusSeconds(3600));
        var source = prepare(FIRST);
        var call = reserve(source, 50, settings, Instant.now());
        drafts.prepare(new PolicyAiRuleDraftStore.Preparation(UUID.randomUUID(), FIRST, 1, "test-extraction-v1", "검증 작업자"));
        assertThat(calls.dispatch(source, call, Instant.now())).isFalse();
        assertThat(phase(source)).isEqualTo("CANCELLED");
        assertThat(calls.settle(source.id(), "charge-a", Instant.now(), BigDecimal.ONE)).isEqualTo(Completion.CONFLICT);

        var expired = prepare(SECOND);
        var expiredCall = reserve(expired, 10, settings, Instant.now());
        assertThat(calls.dispatch(expired, expiredCall, expiredCall.validUntil())).isFalse();
        assertThat(phase(expired)).isEqualTo("CANCELLED");
        assertThat(amount("reserved_won")).isZero();
        assertThat(amount("confirmed_won")).isZero();
    }

    @Test @DisplayName("무과금 확인은 발송한 예약에만 적용하고 같은 확인의 재실행은 재생으로 처리한다")
    void releasesOnlyAfterDispatch() {
        var source = prepare(FIRST);
        var call = reserve(source, 50, settings("100", Instant.now().plusSeconds(3600)), Instant.now());
        assertThat(calls.releaseNoCharge(source.id(), "no-charge-a", Instant.now())).isEqualTo(Completion.CONFLICT);
        assertThat(amount("reserved_won")).isEqualByComparingTo("60");
        calls.dispatch(source, call, Instant.now());
        var confirmedAt = Instant.now();
        assertThat(calls.releaseNoCharge(source.id(), "no-charge-a", confirmedAt)).isEqualTo(Completion.APPLIED);
        assertThat(calls.releaseNoCharge(source.id(), "no-charge-a", confirmedAt)).isEqualTo(Completion.REPLAYED);
        assertThat(amount("reserved_won")).isZero();
        assertThat(amount("confirmed_won")).isZero();
        assertThatThrownBy(() -> calls.releaseNoCharge(UUID.randomUUID(), "no-charge-b", Instant.now()))
                .hasMessage("AI 호출 기록을 찾을 수 없습니다.");
    }

    @Test @DisplayName("동시에 도착한 정산과 무과금 확인 중 하나만 예약액을 해제한다")
    void serializesConcurrentCompletions() throws Exception {
        var source = prepare(FIRST);
        calls.dispatch(source, reserve(source, 50, settings("100", Instant.now().plusSeconds(3600)), Instant.now()), Instant.now());
        var start = new CountDownLatch(1);
        var confirmedAt = Instant.now();
        List<Completion> results;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var settlement = pool.submit(() -> { start.await(); return calls.settle(source.id(), "charge-a", confirmedAt, new BigDecimal("7")); });
            var noCharge = pool.submit(() -> { start.await(); return calls.releaseNoCharge(source.id(), "no-charge-a", confirmedAt); });
            start.countDown();
            results = List.of(settlement.get(10, TimeUnit.SECONDS), noCharge.get(10, TimeUnit.SECONDS));
        }
        assertThat(results).containsExactlyInAnyOrder(Completion.APPLIED, Completion.CONFLICT);
        assertThat(amount("reserved_won")).isZero();
        assertThat(amount("confirmed_won")).isEqualByComparingTo(phase(source).equals("SETTLED") ? "7" : "0");
    }

    @Test @DisplayName("DB 제약은 단계별 필수 기록·역전된 시각·음수 청구·음수 예산을 저장하지 않는다")
    void enforcesConstraints() {
        var source = prepare(FIRST);
        var reservedAt = Instant.now();
        var call = reserve(source, 50, settings("100", reservedAt.plusSeconds(3600)), reservedAt);
        assertThatThrownBy(() -> jdbc.sql("UPDATE policy_ai_rule_calls SET phase = 'DISPATCHED'").update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> calls.dispatch(source, call, reservedAt.minusSeconds(1))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(phase(source)).isEqualTo("HELD");
        calls.dispatch(source, call, Instant.now());
        assertThatThrownBy(() -> calls.settle(source.id(), "charge-a", Instant.now(), new BigDecimal("-0.01")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> calls.settle(source.id(), "charge-a", reservedAt.minusSeconds(1), BigDecimal.ONE))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(phase(source)).isEqualTo("DISPATCHED");
        assertThat(amount("reserved_won")).isEqualByComparingTo("60");
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO ai_budgets(budget_id, starts_at, ends_at, limit_won, created_at, updated_at)
                VALUES ('negative', now(), now() + interval '1 day', -1, now(), now())
                """).update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    // 입력 토큰과 출력 상한 토큰이 각각 1원이라 최대 예약액은 입력 토큰 수 + 10원이다.
    private static AiProperties settings(String monthlyLimit, Instant validUntil) {
        return new AiProperties(true, "test-key", "test-model", new BigDecimal(monthlyLimit), new BigDecimal("1000000"),
                new BigDecimal("1000000"), "test-pricing-v1", validUntil, 10, new AiProperties.Auto(false, 10, 60L, 3));
    }
    private PolicyAiRuleCallStore.Call reserve(PolicyAiRuleDraftStore.Prepared source, long tokens, AiProperties settings, Instant at) {
        return calls.reserve(source, mapper.createObjectNode().put("model", "test-model"), tokens, settings, at);
    }
    private String phase(PolicyAiRuleDraftStore.Prepared source) { return calls.find(source.id()).orElseThrow().phase(); }
    private BigDecimal amount(String column) {
        return jdbc.sql("SELECT coalesce(sum(" + column + "), 0) FROM ai_budgets").query(BigDecimal.class).single();
    }
    private PolicyAiRuleDraftStore.Prepared prepare(String number) {
        var parser = new OntongPolicyCapture(mapper);
        var json = (ObjectNode) parser.parseResponse(OntongFixtures.listBody(mapper), Instant.now()).items().getFirst().deepCopy();
        var item = parser.item(json.put("plcyNo", number).put("plcyNm", "AI 예약 검증 공고"));
        policies.importPolicy(item.number(), item.content(), item.rawPolicy(), Instant.now(), UUID.randomUUID().toString(),
                item.contentHash(), ++sequence);
        return drafts.prepare(new PolicyAiRuleDraftStore.Preparation(UUID.randomUUID(), number, 1, "test-extraction-v1", "검증 작업자"));
    }
}
