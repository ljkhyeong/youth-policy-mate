package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.policy.catalog.PolicyCatalogStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@Testcontainers
@SpringBootTest(properties = {"app.reminders.enabled=false", "app.email.enabled=false", "app.ontong.schedule.enabled=false"})
class PolicyAiRuleGenerationTest {
    private static final String NUMBER = "99980000000000000002";
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PolicyCatalogStore policies;
    @Autowired PolicyAiRuleDraftStore drafts;
    @Autowired PolicyAiRuleCallStore calls;
    @Autowired AiBudgetReservationStore reservations;
    @Autowired AiBudgetReservationLifecycleStore lifecycle;
    @Autowired PolicyAiRuleAutoStore automation;
    @Autowired kr.youthpolicymate.policy.catalog.PolicyRuleStore rules;
    MockRestServiceServer server;
    MockEnvironment environment;
    OpenAiRuleClient client;
    PolicyAiRuleGenerationService service;
    PolicyAiRuleAutoRunner automatic;

    @BeforeEach void setup() throws Exception {
        jdbc.sql("DELETE FROM policy_ai_rule_auto_runs").update();
        jdbc.sql("DELETE FROM policy_ai_rule_calls").update();
        jdbc.sql("DELETE FROM policy_ai_rule_candidates").update();
        jdbc.sql("DELETE FROM policy_ai_rule_requests").update();
        jdbc.sql("DELETE FROM ai_request_reservations").update();
        jdbc.sql("DELETE FROM ai_budgets").update();
        jdbc.sql("DELETE FROM policy_rule_heads WHERE policy_number = :number").param("number", NUMBER).update();
        jdbc.sql("DELETE FROM policy_rule_versions WHERE policy_number = :number").param("number", NUMBER).update();
        jdbc.sql("DELETE FROM policy_revisions").update();
        jdbc.sql("DELETE FROM policy_source_snapshots").update();
        jdbc.sql("DELETE FROM policies").update();
        source("AI 호출 검증 공고", Instant.now().minusSeconds(60));
        environment = new MockEnvironment().withProperty("AI_ENABLED", "true").withProperty("OPENAI_API_KEY", "test-key")
                .withProperty("OPENAI_MODEL", "test-model").withProperty("AI_MONTHLY_LIMIT_WON", "100")
                .withProperty("AI_INPUT_WON_PER_MILLION", "1000").withProperty("AI_OUTPUT_WON_PER_MILLION", "2000")
                .withProperty("AI_MAX_OUTPUT_TOKENS", "1000").withProperty("AI_PRICING_VERSION", "test-pricing-v1")
                .withProperty("AI_PRICING_VALID_UNTIL", Instant.now().plusSeconds(86400).toString());
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenAiRuleClient(environment, mapper, builder.build());
        service = new PolicyAiRuleGenerationService(drafts, calls, reservations, lifecycle, client, Clock.systemUTC());
        environment.withProperty("AI_AUTO_DAILY_LIMIT", "10").withProperty("AI_AUTO_INTERVAL_SECONDS", "60").withProperty("AI_AUTO_MAX_ATTEMPTS", "3");
        automatic = new PolicyAiRuleAutoRunner(automation, service, client, environment, Clock.systemUTC());
    }

    @Test @DisplayName("수집된 신규·변경 공고를 자동 추출하고 내용이 같은 재수집에는 다시 호출하지 않는다")
    void automaticallyProcessesChangedPolicies() throws Exception {
        countTokens(); automaticResponse(); countTokens(); automaticResponse();
        var first = automatic.tick();
        assertThat(first.state()).isEqualTo("COMPLETED");
        assertThat(first.candidateStatus()).isEqualTo("DRAFT_CREATED");
        assertThat(automatic.tick().state()).isEqualTo("WAITING");
        elapse();
        source("AI 호출 검증 공고", Instant.now());
        assertThat(automatic.tick().state()).isEqualTo("EMPTY");
        source("내용이 바뀐 자동 추출 공고", Instant.now().plusSeconds(1));
        var changed = automatic.tick();
        assertThat(changed.state()).isEqualTo("COMPLETED");
        assertThat(changed.requestId()).isNotEqualTo(first.requestId());
        assertThat(drafts.prepared(changed.requestId()).revision()).isEqualTo(2);
        assertThat(heads()).isZero();
        server.verify();
    }

    @Test @DisplayName("관리자가 준비한 요청과 같은 원문 해시의 기존 규칙은 자동으로 중복 추출하지 않는다")
    void skipsManualWork() {
        var manual = prepare();
        assertThat(automatic.tick().state()).isEqualTo("EMPTY");
        rules.draft(mapper.readValue(definition(manual), kr.youthpolicymate.policy.catalog.PolicyRuleDefinition.class), "검증 관리자", "수동 검토");
        jdbc.sql("DELETE FROM policy_ai_rule_requests WHERE id = :id").param("id", manual.id()).update();
        assertThat(automatic.tick().state()).isEqualTo("EMPTY");
        assertThat(automation.recent()).isEmpty();
        server.verify();
    }

    @Test @DisplayName("자동 설정 누락·일일 한도 소진을 차단하고 한국 날짜가 바뀌면 일일 한도를 다시 계산한다")
    void enforcesAutomaticDailyLimit() throws Exception {
        environment.withProperty("AI_AUTO_DAILY_LIMIT", "0");
        assertThat(automatic.tick().state()).isEqualTo("CONFIGURATION_REQUIRED");
        assertThat(automation.recent()).isEmpty();
        environment.withProperty("AI_AUTO_DAILY_LIMIT", "1");
        countTokens(); automaticResponse(); countTokens(); automaticResponse();
        assertThat(automatic.tick().state()).isEqualTo("COMPLETED");
        source("다음 자동 추출 공고", Instant.now());
        assertThat(automatic.tick().state()).isEqualTo("DAILY_LIMIT");
        jdbc.sql("UPDATE policy_ai_rule_auto_runs SET started_at = (date_trunc('day', clock_timestamp() AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'Asia/Seoul') - interval '1 second'").update();
        assertThat(automatic.tick().state()).isEqualTo("COMPLETED");
        server.verify();
    }

    @Test @DisplayName("호출 전 실패는 동일 요청으로 제한된 횟수만 재개한다")
    void boundsAutomaticRetries() {
        environment.withProperty("AI_AUTO_MAX_ATTEMPTS", "2");
        server.expect(requestTo("https://api.openai.com/v1/responses/input_tokens")).andRespond(withServerError());
        server.expect(requestTo("https://api.openai.com/v1/responses/input_tokens")).andRespond(withServerError());
        var first = automatic.tick();
        assertThat(first.state()).isEqualTo("RETRY_PENDING");
        elapse();
        var second = automatic.tick();
        assertThat(second.state()).isEqualTo("REVIEW_REQUIRED");
        assertThat(second.requestId()).isEqualTo(first.requestId());
        assertThat(automation.recent()).extracting(PolicyAiRuleAutoStore.Summary::attempt).containsExactly(2, 1);
        elapse();
        assertThat(automatic.tick().state()).isEqualTo("EMPTY");
        assertThat(calls.find(first.requestId())).isEmpty();
        server.verify();
    }

    @Test @DisplayName("여러 작업자가 동시에 선택해도 한 건만 배정하고 중단 후 같은 요청으로 재개한다")
    void claimsOnceAndResumes() throws Exception {
        var start = new CountDownLatch(1);
        var limits = new PolicyAiRuleAutoStore.Limits(10, 60, 3);
        var settings = client.settings(Instant.now());
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<Future<PolicyAiRuleAutoStore.Claim>>();
            for (int i = 0; i < 3; i++) tasks.add(pool.submit(() -> { start.await(); return automation.claim(limits, settings); }));
            start.countDown();
            var claims = new ArrayList<PolicyAiRuleAutoStore.Claim>();
            for (var task : tasks) claims.add(task.get(10, TimeUnit.SECONDS));
            assertThat(claims.stream().filter(c -> c.run() != null)).hasSize(1);
        }
        var original = automation.recent().getFirst();
        assertThat(automation.claim(limits, settings).reason()).isEqualTo("BUSY");
        elapse();
        var resumed = automation.claim(limits, settings).run();
        assertThat(resumed.requestId()).isEqualTo(original.requestId());
        assertThat(resumed.attempt()).isEqualTo(2);
        assertThat(automation.recent()).extracting(PolicyAiRuleAutoStore.Summary::state).containsExactly("RUNNING", "INTERRUPTED");
        assertThat(automation.finish(new PolicyAiRuleAutoStore.Run(original.runId(), original.requestId(), 1, original.startedAt(), original.startedAt().plusSeconds(600)),
                service.status(original.requestId()), true, 3)).isEqualTo("LEASE_EXPIRED");
        server.verify();
    }

    @Test @DisplayName("발송 후 중단된 작업은 자동 재호출하지 않고 운영자 확인으로 남긴다")
    void doesNotRedispatchInterruptedWork() {
        var run = automation.claim(new PolicyAiRuleAutoStore.Limits(10, 60, 3), client.settings(Instant.now())).run();
        var request = drafts.prepared(run.requestId());
        var call = reserve(request);
        calls.dispatch(request, call, new AiBudgetReservationState.Dispatch("auto-interrupted", Instant.now()));
        elapse();
        assertThat(automatic.tick().state()).isEqualTo("EMPTY");
        assertThat(automation.recent().getFirst().state()).isEqualTo("REVIEW_REQUIRED");
        assertThat(service.status(request.id()).reservationPhase()).isEqualTo("DISPATCHED");
        server.verify();
    }

    @Test @DisplayName("비용 예약 직후 중단된 작업은 남은 예산이 없어도 같은 예약으로 실행한다")
    void resumesHeldReservationWithExhaustedBudget() {
        var run = automation.claim(new PolicyAiRuleAutoStore.Limits(10, 60, 3), client.settings(Instant.now())).run();
        reserve(drafts.prepared(run.requestId()));
        jdbc.sql("UPDATE ai_budgets SET confirmed_won = limit_won - reserved_won").update();
        elapse();
        automaticResponse();
        var result = automatic.tick();
        assertThat(result.state()).isEqualTo("COMPLETED");
        assertThat(result.requestId()).isEqualTo(run.requestId());
        assertThat(automation.recent().getFirst().attempt()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT reserved_won FROM ai_budgets").query(java.math.BigDecimal.class).single()).isEqualByComparingTo("2.1");
        server.verify();
    }

    @Test @DisplayName("예산이 모두 예약됐어도 저장한 응답의 초안 재처리는 외부 호출 없이 완료한다")
    void resumesResponseWithExhaustedBudget() {
        countTokens(); automaticResponse();
        jdbc.sql("CREATE FUNCTION fail_auto_candidate_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '검증 실패'; END; $$").update();
        jdbc.sql("CREATE TRIGGER fail_auto_candidate_test BEFORE INSERT ON policy_ai_rule_candidates FOR EACH ROW EXECUTE FUNCTION fail_auto_candidate_test()").update();
        PolicyAiRuleAutoRunner.Tick first;
        try {
            first = automatic.tick();
            assertThat(first.state()).isEqualTo("RETRY_PENDING");
            assertThat(service.status(first.requestId()).responseStored()).isTrue();
        } finally {
            jdbc.sql("DROP TRIGGER fail_auto_candidate_test ON policy_ai_rule_candidates").update();
            jdbc.sql("DROP FUNCTION fail_auto_candidate_test()").update();
        }
        jdbc.sql("UPDATE ai_budgets SET confirmed_won = limit_won - reserved_won").update();
        elapse();
        assertThat(automatic.tick().state()).isEqualTo("COMPLETED");
        assertThat(automation.recent().getFirst().requestId()).isEqualTo(first.requestId());
        assertThat(service.status(first.requestId()).candidateStatus()).isEqualTo("DRAFT_CREATED");
        elapse();
        assertThat(automatic.tick().state()).isEqualTo("BUDGET_LIMIT");
        server.verify();
    }

    private void automaticResponse() {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(http -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var request = drafts.prepared(automation.recent().getFirst().requestId());
            return withSuccess(response(definition(request)), MediaType.APPLICATION_JSON).createResponse(http);
        });
    }
    private void elapse() {
        jdbc.sql("UPDATE policy_ai_rule_auto_runs SET started_at = started_at - interval '11 minutes', lease_until = lease_until - interval '11 minutes'").update();
    }

    @Test @DisplayName("예산을 예약한 뒤 한 번 호출하고 원 응답·검토 초안을 보관하며 청구 확인을 기다린다")
    void generatesOnce() {
        var request = prepare();
        countTokens();
        server.expect(requestTo("https://api.openai.com/v1/responses")).andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.store").value(false)).andExpect(jsonPath("$.max_output_tokens").value(1000))
                .andExpect(jsonPath("$.text.format.type").value("json_object"))
                .andExpect(http -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    assertThat(lifecycle.find(request.id().toString()).orElseThrow().phase().name()).isEqualTo("DISPATCHED");
                }).andRespond(withSuccess(response(definition(request)), MediaType.APPLICATION_JSON));
        var result = service.generate(request.id());
        assertThat(result.candidateStatus()).isEqualTo("DRAFT_CREATED");
        assertThat(result.reservationPhase()).isEqualTo("DISPATCHED");
        assertThat(result.reservedMaximumWon()).isEqualByComparingTo("2.1");
        assertThat(result.responseStored()).isTrue();
        assertThat(service.generate(request.id())).isEqualTo(result);
        var call = calls.find(request.id()).orElseThrow();
        assertThat(call.body().path("instructions").asString()).contains("PolicyRuleDefinition", "신뢰하지 않는 자료");
        assertThat(call.body().path("input").asString()).doesNotContain("검증 작업자", "requestedBy", "memberId");
        assertThat(call.response().body()).contains("response-test", "usage");
        assertThat(calls.balance(call.budgetId()).reservedWon()).isEqualByComparingTo("2.1");
        assertThat(calls.balance(call.budgetId()).confirmedWon()).isZero();
        assertThat(heads()).isZero();
        assertThatThrownBy(() -> jdbc.sql("UPDATE policy_ai_rule_calls SET request_body = '{}'::jsonb").update())
                .isInstanceOf(DataAccessException.class);
        var confirmedAt = Instant.now();
        assertThat(service.settle(request.id(), "test-receipt-1", confirmedAt, new java.math.BigDecimal("1.25")).decision().name())
                .isEqualTo("SETTLED");
        assertThat(service.settle(request.id(), "test-receipt-1", confirmedAt, new java.math.BigDecimal("1.25")).decision().name())
                .isEqualTo("REPLAYED");
        assertThat(calls.balance(call.budgetId()).reservedWon()).isZero();
        assertThat(calls.balance(call.budgetId()).confirmedWon()).isEqualByComparingTo("1.25");
        server.verify();
    }

    @Test @DisplayName("설정 누락·요금 만료·오래된 요청은 호출 전에 차단하고 예산 초과는 생성 요청을 보내지 않는다")
    void rejectsBeforeGeneration() {
        var request = prepare();
        environment.withProperty("AI_ENABLED", "false");
        assertThatThrownBy(() -> service.generate(request.id())).hasMessageContaining("비활성화");
        environment.withProperty("AI_ENABLED", "true").withProperty("OPENAI_API_KEY", "");
        assertThatThrownBy(() -> service.generate(request.id())).hasMessageContaining("OPENAI_API_KEY");
        environment.withProperty("OPENAI_API_KEY", "test-key");
        environment.withProperty("AI_ENABLED", "true").withProperty("AI_PRICING_VALID_UNTIL", "2020-01-01T00:00:00Z");
        assertThatThrownBy(() -> service.generate(request.id())).hasMessageContaining("유효기간");
        environment.withProperty("AI_PRICING_VALID_UNTIL", Instant.now().plusSeconds(86400).toString());
        var current = prepare();
        assertThatThrownBy(() -> service.generate(request.id())).hasMessageContaining("변경");
        environment.withProperty("AI_MONTHLY_LIMIT_WON", "1");
        countTokens();
        assertThatThrownBy(() -> service.generate(current.id())).hasMessageContaining("BUDGET_LIMIT");
        assertThat(calls.find(current.id())).isEmpty();
        assertThat(lifecycle.find(current.id().toString())).isEmpty();
        server.verify();
    }

    @Test @DisplayName("토큰 확인 중 공고가 바뀌면 예약하지 않고 발송 전 새 요청이 생기면 예약을 취소한다")
    void rechecksSource() {
        var request = prepare();
        server.expect(requestTo("https://api.openai.com/v1/responses/input_tokens")).andRespond(http -> {
            prepare();
            return withSuccess("{\"input_tokens\":100}", MediaType.APPLICATION_JSON).createResponse(http);
        });
        assertThatThrownBy(() -> service.generate(request.id())).hasMessageContaining("변경");
        var current = prepare();
        var call = reserve(current);
        prepare();
        assertThat(service.generate(current.id()).reservationPhase()).isEqualTo("CANCELLED");
        assertThat(calls.balance(call.budgetId()).reservedWon()).isZero();
        server.verify();
    }

    @Test @DisplayName("동시에 같은 예약을 실행해도 생성 API를 한 번만 호출한다")
    void concurrentGeneration() throws Exception {
        var request = prepare();
        reserve(request);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(http -> {
            entered.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("검증 대기 초과"); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IOException(exception); }
            return withSuccess(response(definition(request)), MediaType.APPLICATION_JSON).createResponse(http);
        });
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> service.generate(request.id()));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(service.generate(request.id()).reservationPhase()).isEqualTo("DISPATCHED");
            } finally { release.countDown(); }
            assertThat(first.get(10, TimeUnit.SECONDS).candidateStatus()).isEqualTo("DRAFT_CREATED");
        }
        server.verify();
    }

    @Test @DisplayName("연결 유실은 예약액을 유지하고 동일 요청을 자동으로 다시 보내지 않는다")
    void holdsUnknownOutcome() {
        var request = prepare();
        countTokens();
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(http -> { throw new IOException("연결 유실"); });
        var result = service.generate(request.id());
        assertThat(result.reservationPhase()).isEqualTo("OUTCOME_UNKNOWN");
        assertThat(result.responseStored()).isFalse();
        assertThat(service.generate(request.id())).isEqualTo(result);
        var confirmedAt = Instant.now();
        assertThat(service.noCharge(request.id(), "test-no-charge", confirmedAt).decision().name()).isEqualTo("RELEASED_NO_CHARGE");
        assertThat(service.noCharge(request.id(), "test-no-charge", confirmedAt).decision().name()).isEqualTo("REPLAYED");
        assertThat(service.generate(request.id()).reservationPhase()).isEqualTo("RELEASED_NO_CHARGE");
        server.verify();
    }

    @Test @DisplayName("초안 저장 실패 후 저장한 응답만 재처리하며 공고 변경 후의 결과는 적용하지 않는다")
    void resumesSavedResponse() throws Exception {
        var request = prepare();
        countTokens();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(response(definition(request)), MediaType.APPLICATION_JSON));
        jdbc.sql("CREATE FUNCTION fail_ai_candidate_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '검증 실패'; END; $$").update();
        jdbc.sql("CREATE TRIGGER fail_ai_candidate_test BEFORE INSERT ON policy_ai_rule_candidates FOR EACH ROW EXECUTE FUNCTION fail_ai_candidate_test()").update();
        try {
            assertThatThrownBy(() -> service.generate(request.id())).isInstanceOf(DataAccessException.class);
            assertThat(service.status(request.id()).responseStored()).isTrue();
            assertThat(drafts.result(request.id())).isEmpty();
        } finally {
            jdbc.sql("DROP TRIGGER fail_ai_candidate_test ON policy_ai_rule_candidates").update();
            jdbc.sql("DROP FUNCTION fail_ai_candidate_test()").update();
        }
        source("수정된 AI 호출 공고", Instant.now());
        environment.withProperty("AI_ENABLED", "false");
        var result = service.generate(request.id());
        assertThat(result.candidateStatus()).isEqualTo("SOURCE_CHANGED");
        assertThat(result.reservationPhase()).isEqualTo("DISPATCHED");
        assertThat(heads()).isZero();
        server.verify();
    }

    @Test @DisplayName("공급자 오류·미완료·거절·잘못된 응답은 규칙으로 사용하지 않는다")
    void rejectsProviderFailures() {
        var request = prepare();
        countTokens();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withServerError().body("{\"error\":{\"message\":\"test failure\"}}"));
        assertThat(service.generate(request.id()).candidateStatus()).isEqualTo("INVALID_DEFINITION");
        assertThat(service.status(request.id()).reservationPhase()).isEqualTo("DISPATCHED");
        assertThat(service.generate(request.id()).responseStored()).isTrue();
        assertThat(client.candidate(new OpenAiRuleClient.Response(200, "{\"status\":\"incomplete\",\"output\":[]}")))
                .isEqualTo("AI 응답 미완료");
        assertThat(client.candidate(new OpenAiRuleClient.Response(200,
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\"}]}]}")))
                .isEqualTo("AI 추출 거절");
        assertThat(client.candidate(new OpenAiRuleClient.Response(200, "broken JSON"))).isEqualTo("AI 응답 형식 오류");
        server.verify();
    }

    @Test @DisplayName("입력 토큰 수를 확인하지 못하면 비용을 0원으로 간주하지 않는다")
    void requiresTokenCount() {
        var request = prepare();
        server.expect(requestTo("https://api.openai.com/v1/responses/input_tokens"))
                .andRespond(withSuccess("{\"input_tokens\":0}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.openai.com/v1/responses/input_tokens")).andRespond(withServerError());
        assertThatThrownBy(() -> service.generate(request.id())).hasMessageContaining("토큰 수");
        assertThatThrownBy(() -> service.generate(request.id())).hasMessageContaining("HTTP 500");
        assertThat(calls.find(request.id())).isEmpty();
        assertThat(lifecycle.find(request.id().toString())).isEmpty();
        server.verify();
    }

    private void countTokens() {
        server.expect(requestTo("https://api.openai.com/v1/responses/input_tokens"))
                .andExpect(http -> assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse())
                .andRespond(withSuccess("{\"input_tokens\":100,\"object\":\"response.input_tokens\"}", MediaType.APPLICATION_JSON));
    }
    private PolicyAiRuleCallStore.Call reserve(PolicyAiRuleDraftStore.Prepared request) {
        var settings = client.settings(Instant.now());
        return calls.reserve(request, client.request(request, settings), 100, settings, Instant.now());
    }
    private PolicyAiRuleDraftStore.Prepared prepare() {
        return drafts.prepare(new PolicyAiRuleDraftStore.Preparation(UUID.randomUUID(), NUMBER, 1, "test-extraction-v1", "검증 작업자"));
    }
    private String definition(PolicyAiRuleDraftStore.Prepared request) {
        var json = (ObjectNode) mapper.readTree(jdbc.sql("SELECT definition::text FROM policy_rule_versions WHERE id = 'ba390000-0000-4000-8000-000000000001'").query(String.class).single());
        return json.put("policyNumber", request.policyNumber()).put("contentHash", request.contentHash()).put("ruleVersion", request.ruleVersion())
                .put("validFrom", Instant.now().minusSeconds(30).toString()).put("validUntil", Instant.now().plusSeconds(3600).toString()).toString();
    }
    private String response(String text) {
        return mapper.writeValueAsString(Map.of("id", "response-test", "status", "completed", "usage", Map.of("input_tokens", 100, "output_tokens", 20),
                "output", List.of(Map.of("type", "reasoning"), Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", text))))));
    }
    private long heads() { return jdbc.sql("SELECT count(*) FROM policy_rule_heads WHERE policy_number = :number").param("number", NUMBER).query(Long.class).single(); }
    private void source(String title, Instant at) throws Exception {
        var parser = new OntongPolicyCapture(mapper);
        var json = (ObjectNode) parser.parse(Files.readString(Path.of("src/test/resources/ontong/list-capture.json"))).items().getFirst().deepCopy();
        var item = parser.item(json.put("plcyNo", NUMBER).put("plcyNm", title));
        policies.importPolicy(item.number(), item.content(), item.rawPolicy(), at, UUID.randomUUID().toString(), item.contentHash());
    }
}
