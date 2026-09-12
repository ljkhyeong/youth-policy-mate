package kr.youthpolicymate.admin;

import kr.youthpolicymate.ingestion.OntongPolicyCapture;
import kr.youthpolicymate.ingestion.PolicyAiRuleDraftStore;
import kr.youthpolicymate.policy.catalog.PolicyCatalogStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"app.admin.member-ids=10000000-0000-0000-0000-000000000001",
        "app.ai.auto.enabled=false", "app.reminders.enabled=false", "app.email.enabled=false", "app.ontong.schedule.enabled=false"})
@AutoConfigureMockMvc
class PolicyAiRunApiTest {
    private static final String ROOT = "/api/v1/admin/policy-ai-runs";
    private static final String ADMIN = "10000000-0000-0000-0000-000000000001";
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PolicyCatalogStore policies;
    @Autowired PolicyAiRuleDraftStore drafts;
    @MockitoSpyBean PolicyAiRunStore runs;

    @BeforeEach void setup() {
        jdbc.sql("""
                INSERT INTO members(id, provider, provider_subject, display_name) VALUES
                ('10000000-0000-0000-0000-000000000001', 'kakao', 'admin-fixture', '검증 관리자'),
                ('20000000-0000-0000-0000-000000000002', 'naver', 'member-fixture', '검증 회원')
                ON CONFLICT (id) DO NOTHING
                """).update();
        for (var table : List.of("policy_ai_rule_auto_runs", "policy_ai_rule_calls", "ai_request_reservations", "ai_budgets", "policy_ai_rule_candidates", "policy_ai_rule_requests",
                "policy_revisions", "policy_source_snapshots", "policies")) jdbc.sql("DELETE FROM " + table).update();
    }

    @Test @DisplayName("관리자 소셜 세션만 조회하며 변경 요청은 허용하지 않는다")
    void protectsAccess() throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", containsString("no-store")));
        mvc.perform(get(ROOT).with(social("20000000-0000-0000-0000-000000000002"))).andExpect(status().isForbidden());
        mvc.perform(get(ROOT).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post(ROOT).with(social(ADMIN)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get(ROOT).with(social(ADMIN))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.automationEnabled").value(false));
    }

    @Test @DisplayName("요청별 마지막 시도만 검색·필터한 뒤 페이지를 나눈다")
    void filtersLatestAttempts() throws Exception {
        var first = request(source(1, "지원 100%"));
        attempt(first, 1, "RETRY_PENDING", -240);
        attempt(first, 2, "REVIEW_REQUIRED", -180);
        var second = request(source(2, "지원 정책"));
        attempt(second, 1, "RUNNING", -120);
        request(source(3, "자동 시도가 없는 수동 요청"));
        mvc.perform(get(ROOT).param("pageSize", "1").with(social(ADMIN)))
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items[0].requestId").value(second.toString()));
        mvc.perform(get(ROOT).param("pageSize", "1").param("page", "2").with(social(ADMIN)))
                .andExpect(jsonPath("$.items[0].attempt").value(2)).andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get(ROOT).param("filter", "RETRY_PENDING").with(social(ADMIN))).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get(ROOT).param("filter", "REVIEW_REQUIRED").param("query", " % ").with(social(ADMIN)))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].requestId").value(first.toString()));
        mvc.perform(get(ROOT).param("query", "99990000000000000002").with(social(ADMIN))).andExpect(jsonPath("$.total").value(1));
    }

    @Test @DisplayName("시간 초과·늦은 결과·원문 변경·후속 요청을 구분하고 조회는 실행 기록을 변경하지 않는다")
    void showsCurrentResultsWithoutMutatingHistory() throws Exception {
        var policy = source(1, "이전 공고");
        var id = request(policy);
        attempt(id, 1, "RUNNING", -1200);
        drafts.complete(id, "private-invalid-candidate");
        source(1, "변경 공고");
        request(policy);
        var before = jdbc.sql("SELECT row_to_json(a)::text FROM policy_ai_rule_auto_runs a").query(String.class).single();
        mvc.perform(get(ROOT).param("filter", "LEASE_EXPIRED").with(social(ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].state").value("LEASE_EXPIRED"))
                .andExpect(jsonPath("$.items[0].candidateStatus").value("INVALID_DEFINITION"))
                .andExpect(jsonPath("$.items[0].sourceMatches").value(false))
                .andExpect(jsonPath("$.items[0].latestRequest").value(false))
                .andExpect(jsonPath("$.items[0].revision").value(1)).andExpect(jsonPath("$.items[0].currentRevision").value(2))
                .andExpect(jsonPath("$.items[0].finishedAt").isEmpty()).andExpect(jsonPath("$.items[0].reservationPhase").isEmpty())
                .andExpect(jsonPath("$.items[0].responseStored").value(false))
                .andExpect(content().string(not(containsString("private-invalid-candidate"))));
        assertThat(jdbc.sql("SELECT row_to_json(a)::text FROM policy_ai_rule_auto_runs a").query(String.class).single()).isEqualTo(before);
        assertThat(jdbc.sql("SELECT count(*) FROM policy_ai_rule_calls").query(Long.class).single()).isZero();
    }

    @Test @DisplayName("잘못된 조회 조건과 저장소 장애를 빈 목록과 구분한다")
    void handlesInvalidAndUnavailable() throws Exception {
        for (var query : List.of("?page=0", "?pageSize=51", "?filter=WRONG", "?query=" + "a".repeat(101)))
            mvc.perform(get(ROOT + query).with(social(ADMIN))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_POLICY_AI_RUN_QUERY"));
        doThrow(new DataAccessResourceFailureException("private-error")).when(runs).list(1, 20, PolicyAiRuns.Filter.ALL, "");
        mvc.perform(get(ROOT).with(social(ADMIN))).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("private-error"))));
    }

    @Test @DisplayName("현재 비용 상태와 응답 보관 여부만 공개하며 원 요청·응답·작업자는 노출하지 않는다")
    void exposesOnlyOperationalMetadata() throws Exception {
        var id = request(source(1, "비용 확인 정책"));
        attempt(id, 1, "REVIEW_REQUIRED", -120);
        jdbc.sql("""
                INSERT INTO ai_budgets(budget_id, starts_at, ends_at, limit_won, reserved_won, created_at, updated_at)
                VALUES ('test-budget', now() - interval '1 day', now() + interval '1 day', 100, 1, now(), now())
                """).update();
        jdbc.sql("""
                INSERT INTO ai_request_reservations(reservation_id, budget_id, policy_id, source_revision_number,
                    source_collection_sequence, source_observed_at, source_name, source_snapshot_id, source_body_sha256,
                    comparison_version, source_content_sha256, ai_kind, generation_version, request_sequence,
                    request_prepared_at, pricing_version, cost_valid_until, maximum_won, phase, reserved_at, created_at, updated_at)
                SELECT id::text, 'test-budget', policy_number, revision, revision, now(), 'test', 'test', content_hash,
                    'test', content_hash, 'CONDITION_EXTRACTION', generation_version, sequence,
                    now(), 'test', now() + interval '1 hour', 1, 'HELD', now(), now(), now()
                FROM policy_ai_rule_requests WHERE id = :id
                """).param("id", id).update();
        jdbc.sql("""
                INSERT INTO policy_ai_rule_calls(request_id, reservation_id, request_body, input_tokens, input_won_per_million,
                    output_won_per_million, response_status, response_body, received_at)
                VALUES (:id, :reservation, '{"input":"private-prompt"}', 1, 1, 1, 200, 'private-response', now())
                """).param("id", id).param("reservation", id.toString()).update();
        mvc.perform(get(ROOT).with(social(ADMIN))).andExpect(jsonPath("$.items[0].reservationPhase").value("HELD"))
                .andExpect(jsonPath("$.items[0].responseStored").value(true))
                .andExpect(content().string(not(containsString("private-"))))
                .andExpect(content().string(not(containsString("검증 작업자"))));
        jdbc.sql("UPDATE ai_request_reservations SET phase = 'DISPATCHED', dispatch_id = 'test', dispatched_at = now()").update();
        mvc.perform(get(ROOT).with(social(ADMIN))).andExpect(jsonPath("$.items[0].reservationPhase").value("DISPATCHED"));
        assertThat(jdbc.sql("SELECT reserved_won FROM ai_budgets").query(java.math.BigDecimal.class).single()).isEqualByComparingTo("1");
    }

    private String source(int index, String title) throws Exception {
        var parser = new OntongPolicyCapture(mapper);
        var raw = (ObjectNode) parser.parse(Files.readString(Path.of("src/test/resources/ontong/list-capture.json"))).items().getFirst().deepCopy();
        raw.put("plcyNo", "999900000000000000%02d".formatted(index)).put("plcyNm", title);
        var item = parser.item(raw);
        policies.importPolicy(item.number(), item.content(), item.rawPolicy(), Instant.now(), UUID.randomUUID().toString(), item.contentHash());
        return item.number();
    }
    private UUID request(String number) {
        var revision = jdbc.sql("SELECT current_revision FROM policies WHERE policy_number = :number").param("number", number).query(Long.class).single();
        return drafts.prepare(new PolicyAiRuleDraftStore.Preparation(UUID.randomUUID(), number, revision, "test", "검증 작업자")).id();
    }
    private void attempt(UUID request, int attempt, String state, int seconds) {
        jdbc.sql("""
                INSERT INTO policy_ai_rule_auto_runs(id, request_id, attempt, started_at, lease_until, finished_at, state)
                VALUES (:id, :request, :attempt, now() + :seconds * interval '1 second',
                    now() + (:seconds + 600) * interval '1 second',
                    CASE WHEN :state = 'RUNNING' THEN NULL ELSE now() + (:seconds + 1) * interval '1 second' END, :state)
                """).param("id", UUID.randomUUID()).param("request", request).param("attempt", attempt).param("state", state).param("seconds", seconds).update();
    }
    private static RequestPostProcessor social(String id) {
        return oauth2Login().oauth2User(new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_MEMBER")), Map.of("memberId", id), "memberId"));
    }
}
