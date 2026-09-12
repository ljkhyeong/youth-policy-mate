package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.policy.catalog.PolicyCatalogStore;
import kr.youthpolicymate.policy.catalog.PolicyRuleDefinition;
import kr.youthpolicymate.policy.catalog.PolicyRuleStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static kr.youthpolicymate.ingestion.PolicyAiRuleDraftStore.Status.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"app.admin.member-ids=10000000-0000-0000-0000-000000000001",
        "app.reminders.enabled=false", "app.email.enabled=false", "app.ontong.schedule.enabled=false"})
@AutoConfigureMockMvc
class PolicyAiRuleDraftStoreTest {
    private static final String NUMBER = "99980000000000000001";
    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PolicyCatalogStore policies;
    @Autowired PolicyRuleStore rules;
    @Autowired PolicyAiRuleDraftStore drafts;
    @Autowired MockMvc mvc;
    @MockitoBean Clock clock;

    @BeforeEach void setup() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        jdbc.sql("DELETE FROM policy_ai_rule_candidates").update();
        jdbc.sql("DELETE FROM policy_ai_rule_requests").update();
        jdbc.sql("DELETE FROM policy_rule_heads WHERE policy_number = :number").param("number", NUMBER).update();
        jdbc.sql("DELETE FROM policy_rule_versions WHERE policy_number = :number").param("number", NUMBER).update();
        jdbc.sql("DELETE FROM policy_revisions").update();
        jdbc.sql("DELETE FROM policy_source_snapshots").update();
        jdbc.sql("DELETE FROM policies").update();
        source("AI 초안 검증용 공고", 0);
    }

    @Test @DisplayName("수집 원문에 묶인 결과를 초안으로 저장하고 기존 관리자 검토 화면에 제공한다")
    void createsReviewableDraft() throws Exception {
        var request = prepare(1);
        assertThat(request.content().title()).isEqualTo("AI 초안 검증용 공고");
        assertThat(request.rawPolicy().path("plcyNo").asString()).isEqualTo(NUMBER);
        assertThat(request.sourceSnapshotId()).isPositive();
        assertThat(mapper.valueToTree(request).has("requestedBy")).isFalse();
        var body = body(request);
        var result = drafts.complete(request.id(), body);
        assertThat(result.status()).isEqualTo(DRAFT_CREATED);
        assertThat(result.bodySha256()).isEqualTo(OntongPolicyCapture.hash(body));
        assertThat(drafts.result(request.id())).contains(result);
        assertThat(drafts.complete(request.id(), body)).isEqualTo(result);
        assertThat(ruleHeadCount()).isZero();
        assertThat(count("policy_ai_rule_candidates")).isEqualTo(1);
        assertThat(rules.definition(result.versionId())).isEqualTo(mapper.readValue(body, PolicyRuleDefinition.class));
        var admin = oauth2Login().oauth2User(new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_MEMBER")),
                Map.of("memberId", "10000000-0000-0000-0000-000000000001"), "memberId"));
        mvc.perform(get("/api/v1/admin/policy-rule-reviews/" + NUMBER).with(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.item.draftCount").value(1)).andExpect(jsonPath("$.versions[0].state").value("DRAFT"))
                .andExpect(jsonPath("$.versions[0].createdBy").value("AI/test-extraction-v1"));
        mvc.perform(get("/api/v1/policies/" + NUMBER + "/questions")).andExpect(jsonPath("$.available").value(false));
        rules.publish(result.versionId(), "none", "검증 관리자");
        var newer = prepare(1);
        var next = drafts.complete(newer.id(), body(newer));
        rules.publish(next.versionId(), request.ruleVersion(), "검증 관리자");
        assertThat(drafts.complete(request.id(), body)).isEqualTo(result);
        assertThat(jdbc.sql("SELECT version_id FROM policy_rule_heads WHERE policy_number = :number").param("number", NUMBER)
                .query(UUID.class).single()).isEqualTo(next.versionId());
    }

    @Test @DisplayName("새 공고나 새 요청 뒤에 도착한 결과는 본문만 보관하고 초안을 만들지 않는다")
    void rejectsStaleResults() throws Exception {
        var old = prepare(1);
        source("내용이 바뀐 공고", 1);
        assertThat(drafts.complete(old.id(), body(old)).status()).isEqualTo(SOURCE_CHANGED);
        assertThatThrownBy(() -> drafts.prepare(input(old.id(), 1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> prepare(1)).isInstanceOf(IllegalStateException.class);
        var earlier = prepare(2);
        var latest = prepare(2);
        assertThatThrownBy(() -> drafts.prepare(input(earlier.id(), 2))).isInstanceOf(IllegalStateException.class);
        assertThat(drafts.complete(earlier.id(), body(earlier)).status()).isEqualTo(REQUEST_SUPERSEDED);
        assertThat(drafts.complete(latest.id(), body(latest)).status()).isEqualTo(DRAFT_CREATED);
        assertThat(count("policy_ai_rule_candidates")).isEqualTo(3);
        assertThat(jdbc.sql("SELECT count(*) FROM policy_rule_versions WHERE policy_number = :number").param("number", NUMBER)
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test @DisplayName("잘못된 형식·원문 참조를 보관하고 결과 변경과 용량 초과를 차단한다")
    void retainsInvalidOutput() {
        var malformed = prepare(1);
        assertThat(drafts.complete(malformed.id(), "규칙을 만들 수 없습니다.").status()).isEqualTo(INVALID_DEFINITION);
        assertThatThrownBy(() -> drafts.complete(malformed.id(), "다른 결과")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.sql("SELECT body FROM policy_ai_rule_candidates WHERE request_id = :id").param("id", malformed.id())
                .query(String.class).single()).isEqualTo("규칙을 만들 수 없습니다.");
        for (String invalid : List.of("null", "{}")) {
            var request = prepare(1);
            assertThat(drafts.complete(request.id(), invalid).status()).isEqualTo(INVALID_DEFINITION);
        }
        var unknown = prepare(1);
        assertThat(drafts.complete(unknown.id(), definition(unknown).put("unrecognized", "원문 지시").toString()).status()).isEqualTo(INVALID_DEFINITION);
        var trailing = prepare(1);
        assertThat(drafts.complete(trailing.id(), body(trailing) + " {}").status()).isEqualTo(INVALID_DEFINITION);
        var wrongPeriod = prepare(1);
        assertThat(drafts.complete(wrongPeriod.id(), definition(wrongPeriod).put("validUntil", NOW.minusSeconds(100).toString()).toString()).status()).isEqualTo(INVALID_DEFINITION);
        for (String key : List.of("policyNumber", "contentHash", "ruleVersion")) {
            var wrong = prepare(1);
            var value = key.equals("policyNumber") ? "99980000000000000002" : key.equals("contentHash") ? "b".repeat(64) : "unknown-version";
            assertThat(drafts.complete(wrong.id(), definition(wrong).put(key, value).toString()).status()).isEqualTo(INVALID_REFERENCE);
        }
        var large = prepare(1);
        assertThatThrownBy(() -> drafts.complete(large.id(), "가".repeat(50000))).isInstanceOf(IllegalArgumentException.class);
        assertThat(drafts.result(large.id())).isEmpty();
        assertThat(ruleHeadCount()).isZero();
    }

    @Test @DisplayName("같은 요청과 결과의 동시 재시도는 요청 한 건과 초안 한 건으로 끝난다")
    void serializesRetries() throws Exception {
        var input = input(UUID.randomUUID(), 1);
        var prepared = concurrent(() -> drafts.prepare(input));
        assertThat(prepared.get(0)).isEqualTo(prepared.get(1));
        assertThat(count("policy_ai_rule_requests")).isEqualTo(1);
        var request = prepared.getFirst();
        var body = body(request);
        var results = concurrent(() -> drafts.complete(request.id(), body));
        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(count("policy_ai_rule_candidates")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM policy_rule_versions WHERE policy_number = :number").param("number", NUMBER)
                .query(Long.class).single()).isEqualTo(1);
        assertThatThrownBy(() -> drafts.prepare(new PolicyAiRuleDraftStore.Preparation(input.id(), NUMBER, 1, "other-generator", input.requestedBy())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("후보 기록 실패는 초안도 취소하고 같은 결과로 재시도할 수 있다")
    void rollsBackDraftWithCandidate() {
        var request = prepare(1);
        var body = body(request);
        jdbc.sql("CREATE TRIGGER fail_ai_candidate BEFORE INSERT ON policy_ai_rule_candidates FOR EACH ROW EXECUTE FUNCTION reject_policy_ai_rule_update()").update();
        try { assertThatThrownBy(() -> drafts.complete(request.id(), body)).isInstanceOf(DataAccessException.class); }
        finally { jdbc.sql("DROP TRIGGER fail_ai_candidate ON policy_ai_rule_candidates").update(); }
        assertThat(drafts.result(request.id())).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM policy_rule_versions WHERE policy_number = :number").param("number", NUMBER)
                .query(Long.class).single()).isZero();
        assertThat(drafts.complete(request.id(), body).status()).isEqualTo(DRAFT_CREATED);
        assertThatThrownBy(() -> jdbc.sql("UPDATE policy_ai_rule_requests SET generation_version = 'changed'").update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("UPDATE policy_ai_rule_candidates SET body = 'changed'").update()).isInstanceOf(DataAccessException.class);
    }

    @Test @DisplayName("이미 등록된 같은 규칙 버전은 덮어쓰지 않는다")
    void rejectsVersionCollision() {
        var request = prepare(1);
        var body = body(request);
        var original = rules.draft(mapper.readValue(body, PolicyRuleDefinition.class), "검증 관리자", "수동 초안");
        assertThat(drafts.complete(request.id(), body).status()).isEqualTo(VERSION_CONFLICT);
        assertThat(rules.definition(original).ruleVersion()).isEqualTo(request.ruleVersion());
    }

    private PolicyAiRuleDraftStore.Preparation input(UUID id, long revision) {
        return new PolicyAiRuleDraftStore.Preparation(id, NUMBER, revision, "test-extraction-v1", "검증 작업자");
    }
    private PolicyAiRuleDraftStore.Prepared prepare(long revision) { return drafts.prepare(input(UUID.randomUUID(), revision)); }
    private ObjectNode definition(PolicyAiRuleDraftStore.Prepared request) {
        var json = (ObjectNode) mapper.readTree(jdbc.sql("SELECT definition::text FROM policy_rule_versions WHERE id = 'ba390000-0000-4000-8000-000000000001'").query(String.class).single());
        return json.put("policyNumber", request.policyNumber()).put("contentHash", request.contentHash()).put("ruleVersion", request.ruleVersion())
                .put("validFrom", NOW.minusSeconds(1).toString()).put("validUntil", NOW.plusSeconds(3600).toString());
    }
    private String body(PolicyAiRuleDraftStore.Prepared request) { return definition(request).toString(); }
    private long count(String table) { return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single(); }
    private long ruleHeadCount() {
        return jdbc.sql("SELECT count(*) FROM policy_rule_heads WHERE policy_number = :number").param("number", NUMBER).query(Long.class).single();
    }
    private void source(String title, int seconds) throws Exception {
        var parser = new OntongPolicyCapture(mapper);
        var json = (ObjectNode) parser.parse(Files.readString(Path.of("src/test/resources/ontong/list-capture.json"))).items().getFirst().deepCopy();
        var item = parser.item(json.put("plcyNo", NUMBER).put("plcyNm", title));
        policies.importPolicy(item.number(), item.content(), item.rawPolicy(), NOW.plusSeconds(seconds), UUID.randomUUID().toString(), item.contentHash());
    }
    private <T> List<T> concurrent(Callable<T> action) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<T> task = () -> { ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("동시 실행 대기 시간 초과"); return action.call(); };
            var first = executor.submit(task); var second = executor.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }
}
