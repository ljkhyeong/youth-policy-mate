package kr.youthpolicymate.admin;

import kr.youthpolicymate.ingestion.OntongPolicyCapture;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"app.admin.member-ids=10000000-0000-0000-0000-000000000001",
        "app.reminders.enabled=false", "app.email.enabled=false", "app.ontong.schedule.enabled=false"})
@AutoConfigureMockMvc
class CollectionExceptionApiTest {
    private static final String ROOT = "/api/v1/admin/collection-exceptions";
    private static final String ADMIN = "10000000-0000-0000-0000-000000000001";
    private static final String MEMBER = "20000000-0000-0000-0000-000000000002";
    private static final Instant AT = Instant.parse("2026-09-07T00:00:00Z");
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MockMvc mvc;
    @MockitoSpyBean JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PolicyCatalogStore policies;

    @BeforeEach
    void clear() {
        jdbc.sql("DELETE FROM ontong_collection_item_attempts").update();
        jdbc.sql("DELETE FROM ontong_collection_items").update();
        jdbc.sql("DELETE FROM ontong_collection_pages").update();
        jdbc.sql("DELETE FROM policy_revisions").update();
        jdbc.sql("DELETE FROM policy_source_snapshots").update();
        jdbc.sql("DELETE FROM policies").update();
    }

    @Test
    @DisplayName("비회원·일반 회원·다른 인증 방식은 차단하고 등록된 관리자만 조회한다")
    void enforcesAdminBoundary() throws Exception {
        for (var path : List.of(ROOT, ROOT + "/pages", ROOT + "/" + UUID.randomUUID() + "/0")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
            mvc.perform(get(path).with(social(MEMBER))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(user(ADMIN).roles("MEMBER", "ADMIN"))).andExpect(status().isForbidden());
        }
        mvc.perform(get(ROOT).with(social(ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post(ROOT).with(social(ADMIN)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/policies")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("현재 실패만 안정된 순서로 페이지 조회하고 재처리 성공 항목은 제외한다")
    void listsCurrentFailures() throws Exception {
        var old = page(1);
        item(old, 0, "INVALID_ITEM", "{}");
        var recent = page(2);
        item(recent, 0, "APPLIED", "{}");
        item(recent, 1, "STORE_FAILED", "{\"plcyNo\":\" 123 \"}");
        item(recent, 2, "INVALID_ITEM", "null");
        item(recent, 3, "PENDING", "{}");

        mvc.perform(get(ROOT).with(social(ADMIN)).param("pageSize", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].runId").value(recent.toString()))
                .andExpect(jsonPath("$.items[0].itemIndex").value(1))
                .andExpect(jsonPath("$.items[0].policyNumber").value("123"))
                .andExpect(jsonPath("$.items[0].outcome").value("STORE_FAILED"))
                .andExpect(jsonPath("$.items[1].itemIndex").value(2))
                .andExpect(jsonPath("$.items[0].rawPolicyJson").doesNotExist())
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get(ROOT).with(social(ADMIN)).param("page", "2").param("pageSize", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].runId").value(old.toString()))
                .andExpect(jsonPath("$.hasNext").value(false));
        jdbc.sql("UPDATE ontong_collection_items SET outcome = 'UNCHANGED' WHERE run_id = :id")
                .param("id", recent).update();
        mvc.perform(get(ROOT).with(social(ADMIN))).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get(ROOT + "/" + recent + "/1").with(social(ADMIN)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COLLECTION_EXCEPTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("실패 원본과 같은 번호의 공개 내용만 비교하고 원본·개정·처리 이력은 변경하지 않는다")
    void readsRawAndCurrentPolicyWithoutMutation() throws Exception {
        var parser = new OntongPolicyCapture(mapper);
        var capture = parser.parse(Files.readString(Path.of("src/test/resources/ontong/list-capture.json")));
        var source = capture.items().getFirst().deepCopy();
        var policy = parser.item(source);
        policies.importPolicy(policy.number(), policy.content(), policy.rawPolicy(), AT, "admin-fixture", policy.contentHash());
        ((tools.jackson.databind.node.ObjectNode) source).put("plcyNm", "");
        var run = page(1);
        item(run, 0, "INVALID_ITEM", source.toString());
        var result = mvc.perform(get(ROOT + "/" + run + "/0").with(social(ADMIN)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.currentPolicy.policyNumber").value(policy.number()))
                .andExpect(jsonPath("$.currentPolicy.revision").value(1))
                .andExpect(jsonPath("$.currentPolicy.content.title").value(policy.content().title()))
                .andExpect(jsonPath("$.item.attempts").value(1)).andReturn();
        var body = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(mapper.readTree(body.path("rawPolicyJson").asString())).isEqualTo(source);

        int index = 1;
        for (var invalid : List.of("null", "17", "{\"plcyNo\":17}", "{\"plcyNo\":\"wrong\"}",
                "{\"plcyNm\":" + mapper.writeValueAsString(policy.content().title()) + "}")) {
            item(run, index, "INVALID_ITEM", invalid);
            var invalidResult = mvc.perform(get(ROOT + "/" + run + "/" + index).with(social(ADMIN)))
                    .andExpect(status().isOk()).andReturn();
            var invalidBody = mapper.readTree(invalidResult.getResponse().getContentAsString());
            assertThat(invalidBody.get("currentPolicy").isNull()).isTrue();
            assertThat(invalidBody.path("item").get("policyNumber").isNull()).isTrue();
            index++;
        }
        assertThat(jdbc.sql("SELECT count(*) FROM policy_revisions").query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT sum(attempts) FROM ontong_collection_items").query(Long.class).single()).isEqualTo(index);
        assertThat(jdbc.sql("SELECT count(*) FROM ontong_collection_item_attempts").query(Long.class).single()).isZero();
        assertThat(mapper.readTree(jdbc.sql("SELECT raw_policy::text FROM ontong_collection_items WHERE item_index=0")
                .query(String.class).single())).isEqualTo(source);
        assertThat(policies.find(policy.number()).orElseThrow().content()).isEqualTo(policy.content());
    }

    @Test
    @DisplayName("잘못된 조회 조건과 없는 항목을 구분하고 DB 오류 내용을 노출하지 않는다")
    void returnsSafeErrors() throws Exception {
        for (var query : List.of("?page=0", "?page=1001", "?pageSize=0", "?pageSize=51", "?page=abc",
                "/invalid/0", "/" + UUID.randomUUID() + "/10")) {
            mvc.perform(get(ROOT + query).with(social(ADMIN))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_COLLECTION_QUERY"))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        mvc.perform(get(ROOT + "/" + UUID.randomUUID() + "/0").with(social(ADMIN)))
                .andExpect(status().isNotFound());
        doThrow(new DataAccessResourceFailureException("sensitive-db-detail")).when(jdbc).sql(startsWith("SELECT "));
        mvc.perform(get(ROOT).with(social(ADMIN))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COLLECTION_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("sensitive-db-detail"))));
    }

    private UUID page(int pageNumber) {
        var run = UUID.randomUUID();
        jdbc.sql("INSERT INTO ontong_collection_pages(run_id, page_number, state) VALUES (:run, :page, 'READY')")
                .param("run", run).param("page", pageNumber).update();
        return run;
    }

    @Test
    @DisplayName("페이지 실패를 요청 순서로 조회하고 복구된 페이지와 원본 응답은 제외한다")
    void listsPageFailuresWithoutExposingResponse() throws Exception {
        var invalid = failedPage(1, "INVALID_RESPONSE", "INVALID_LIST_RESPONSE", "private-response-body");
        var failed = failedPage(2, "FETCH_FAILED", "HTTP_429", null);
        jdbc.sql("UPDATE ontong_collection_pages SET dispatch_started_at = :at WHERE run_id = :id")
                .param("at", AT.atOffset(java.time.ZoneOffset.UTC)).param("id", failed).update();
        page(3);
        var first = mvc.perform(get(ROOT + "/pages").with(social(ADMIN)).param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items[0].runId").value(failed.toString()))
                .andExpect(jsonPath("$.items[0].reason").value("HTTP_ERROR"))
                .andExpect(jsonPath("$.items[0].httpStatus").value(429))
                .andExpect(jsonPath("$.items[0].dispatchedAt").value(AT.toString()))
                .andExpect(jsonPath("$.items[0].responseStored").value(false)).andReturn();
        assertThat(mapper.readTree(first.getResponse().getContentAsString()).at("/items/0/receivedAt").isNull()).isTrue();
        var second = mvc.perform(get(ROOT + "/pages").with(social(ADMIN)).param("pageSize", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items[0].runId").value(invalid.toString()))
                .andExpect(jsonPath("$.items[0].state").value("INVALID_RESPONSE"))
                .andExpect(jsonPath("$.items[0].reason").value("INVALID_LIST_RESPONSE"))
                .andExpect(jsonPath("$.items[0].responseStored").value(true))
                .andExpect(jsonPath("$.items[0].receivedAt").value(AT.toString())).andReturn();
        assertThat(second.getResponse().getContentAsString()).doesNotContain("private-response-body", "rawBody", "failureCode");
        assertThat(jdbc.sql("SELECT raw_body FROM ontong_collection_pages WHERE run_id = :id").param("id", invalid)
                .query(String.class).single()).isEqualTo("private-response-body");
        assertThat(jdbc.sql("SELECT count(*) FROM ontong_collection_item_attempts").query(Long.class).single()).isZero();
        jdbc.sql("UPDATE ontong_collection_pages SET state = 'READY', failure_code = NULL WHERE run_id = :id")
                .param("id", invalid).update();
        mvc.perform(get(ROOT + "/pages").with(social(ADMIN))).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("알 수 없는 오류 문자열과 잘못된 HTTP 코드는 노출하지 않고 조회 입력을 검증한다")
    void sanitizesPageFailures() throws Exception {
        for (var code : java.util.Arrays.asList("private-error-with-key", "HTTP_999", "HTTP_401?private-key", null)) {
            failedPage(1, "FETCH_FAILED", code, null);
        }
        var result = mvc.perform(get(ROOT + "/pages").with(social(ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4)).andReturn();
        var body = mapper.readTree(result.getResponse().getContentAsString());
        for (var item : body.path("items")) {
            assertThat(item.path("reason").asString()).isEqualTo("UNKNOWN");
            assertThat(item.get("httpStatus").isNull()).isTrue();
            assertThat(item.get("dispatchedAt").isNull()).isTrue();
        }
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private-", "HTTP_999");
        mvc.perform(get(ROOT + "/pages").with(social(ADMIN)).param("pageSize", "51"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_COLLECTION_QUERY"));
        mvc.perform(post(ROOT + "/pages").with(social(ADMIN)).with(csrf())).andExpect(status().isForbidden());
        doThrow(new DataAccessResourceFailureException("private-database-error")).when(jdbc).sql(startsWith("SELECT "));
        mvc.perform(get(ROOT + "/pages").with(social(ADMIN))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COLLECTION_UNAVAILABLE"));
    }

    private UUID failedPage(int number, String state, String code, String raw) {
        var run = page(number);
        jdbc.sql("UPDATE ontong_collection_pages SET state = :state, failure_code = :code, raw_body = :raw, received_at = :received WHERE run_id = :id")
                .param("state", state).param("code", code).param("raw", raw)
                .param("received", raw == null ? null : AT.atOffset(java.time.ZoneOffset.UTC)).param("id", run).update();
        return run;
    }

    private void item(UUID run, int index, String outcome, String raw) {
        jdbc.sql("""
                INSERT INTO ontong_collection_items(run_id, item_index, raw_policy, outcome, attempts, updated_at)
                VALUES (:run, :index, CAST(:raw AS jsonb), :outcome, 1, :at)
                """).param("run", run).param("index", index).param("raw", raw).param("outcome", outcome)
                .param("at", AT.atOffset(java.time.ZoneOffset.UTC)).update();
    }

    private static RequestPostProcessor social(String memberId) {
        return oauth2Login().oauth2User(new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_MEMBER")),
                Map.of("memberId", memberId), "memberId"));
    }
}
