package kr.youthpolicymate.admin;

import kr.youthpolicymate.ingestion.OntongApiClient;
import kr.youthpolicymate.ingestion.OntongCollectionService;
import kr.youthpolicymate.ingestion.OntongCollectionStore;
import kr.youthpolicymate.ingestion.OntongPolicyCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"app.admin.member-ids=10000000-0000-0000-0000-000000000001",
        "app.reminders.enabled=false", "app.email.enabled=false", "app.ontong.schedule.enabled=false"})
@AutoConfigureMockMvc
class CollectionReplayApiTest {
    private static final String ROOT = "/api/v1/admin/collection-exceptions";
    private static final String ADMIN = "10000000-0000-0000-0000-000000000001";
    private static final Instant AT = Instant.parse("2026-09-07T00:00:00Z");
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired OntongCollectionStore store;
    @Autowired OntongCollectionService collection;
    @MockitoBean OntongApiClient client;

    @BeforeEach void clear() {
        jdbc.sql("TRUNCATE ontong_collection_pages, policies CASCADE").update();
    }
    @AfterEach void noExternalRequests() { verifyNoInteractions(client); }

    @Test
    @DisplayName("선택한 실패 항목만 반영하고 관리자 사유·개정·이력을 한 번 기록한다")
    void replaysOneItemAndReturnsSameResult() throws Exception {
        var run = prepared("정책 제목");
        var raw = store.page(run).rawBody();
        var body = request(UUID.randomUUID(), "저장 오류 조치 후 재처리");
        var response = mvc.perform(post(path(run)).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.policyRevision").value(1)).andExpect(jsonPath("$.attempt").value(2))
                .andExpect(jsonPath("$.actorId").value(ADMIN)).andReturn().getResponse().getContentAsString();
        mvc.perform(post(path(run)).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(content().json(response));
        mvc.perform(get(ROOT + "/replays").with(social(ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].reason").value("저장 오류 조치 후 재처리"));
        mvc.perform(get(ROOT + "/replays?page=2&pageSize=1").with(social(ADMIN)))
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.hasNext").value(false));
        assertThat(store.pending(run)).containsExactly(1);
        assertThat(store.page(run).rawBody()).isEqualTo(raw);
        assertThat(jdbc.sql("SELECT count(*) FROM policy_revisions").query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM ontong_collection_item_attempts").query(Long.class).single()).isEqualTo(3);
        mvc.perform(post(path(run)).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(request(UUID.randomUUID(), "새 요청")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COLLECTION_REPLAY_CHANGED"));
    }

    @Test
    @DisplayName("검증 실패는 실패 결과로 기록하고 사유를 바꾼 요청 ID 재사용은 거절한다")
    void recordsValidationFailure() throws Exception {
        var run = prepared("");
        var id = UUID.randomUUID();
        mvc.perform(post(path(run)).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request(id, "규칙 수정 후 확인")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("INVALID_ITEM"))
                .andExpect(jsonPath("$.policyRevision").doesNotExist());
        mvc.perform(post(path(run)).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request(id, "다른 사유")))
                .andExpect(status().isConflict());
        assertThat(store.pending(run)).containsExactly(0, 1);
        assertThat(jdbc.sql("SELECT count(*) FROM policies").query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("이전 수집의 재처리는 최신 정책을 덮어쓰지 않는다")
    void preservesNewerPolicy() throws Exception {
        var old = prepared("이전 제목");
        var recent = prepared("현재 제목");
        collection.applyStoredItem(recent, 0);
        mvc.perform(post(path(old)).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(request(UUID.randomUUID(), "이전 실패 확인")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("STALE"))
                .andExpect(jsonPath("$.policyRevision").doesNotExist());
        assertThat(jdbc.sql("SELECT content->>'title' FROM policies WHERE policy_number = '123'").query(String.class).single()).isEqualTo("현재 제목");
    }

    @Test
    @DisplayName("관리자 기록 저장에 실패하면 정책·시도도 롤백하고 같은 요청을 재시도할 수 있다")
    void rollsBackTogether() throws Exception {
        var run = prepared("정책 제목");
        var body = request(UUID.randomUUID(), "롤백 검증");
        jdbc.sql("ALTER TABLE admin_collection_replays ADD CONSTRAINT reject_replay CHECK (reason <> '롤백 검증')").update();
        try {
            mvc.perform(post(path(run)).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("COLLECTION_UNAVAILABLE"));
            assertThat(jdbc.sql("SELECT count(*) FROM policies").query(Long.class).single()).isZero();
            assertThat(jdbc.sql("SELECT count(*) FROM ontong_collection_item_attempts").query(Long.class).single()).isEqualTo(2);
        } finally { jdbc.sql("ALTER TABLE admin_collection_replays DROP CONSTRAINT reject_replay").update(); }
        mvc.perform(post(path(run)).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attempt").value(2));
    }

    @Test
    @DisplayName("같은 요청의 동시 재전송도 한 번만 반영한다")
    void serializesConcurrentRetries() throws Exception {
        var run = prepared("정책 제목");
        var body = request(UUID.randomUUID(), "동시 재전송 확인");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                start.await();
                return mvc.perform(post(path(run)).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            })).toList();
            start.countDown();
            for (var task : tasks) assertThat(task.get()).isEqualTo(200);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM admin_collection_replays").query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM policy_revisions").query(Long.class).single()).isOne();
    }

    @Test
    @DisplayName("관리자 권한·CSRF·입력 검증에 실패하면 재처리하지 않는다")
    void enforcesWriteBoundary() throws Exception {
        var run = prepared("정책 제목");
        var body = request(UUID.randomUUID(), "권한 검증");
        mvc.perform(post(path(run)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(path(run)).with(social(UUID.randomUUID().toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path(run)).with(social(ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        for (var invalid : List.of(request(UUID.randomUUID(), " "), request(UUID.randomUUID(), "가".repeat(501)), "{}", "{")) {
            mvc.perform(post(path(run)).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get(ROOT + "/replays")).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT + "/replays").with(social(UUID.randomUUID().toString()))).andExpect(status().isForbidden());
        assertThat(jdbc.sql("SELECT count(*) FROM admin_collection_replays").query(Long.class).single()).isZero();
    }

    private UUID prepared(String title) {
        var raw = mapper.writeValueAsString(Map.of("resultCode", 200, "result", Map.of(
                "pagging", Map.of("pageNum", 1, "pageSize", 10, "totCount", 2),
                "youthPolicyList", List.of(Map.of("plcyNo", "123", "plcyNm", title, "plcyAprvSttsCd", "0044002"),
                        Map.of("plcyNo", "456", "plcyNm", "다른 항목", "plcyAprvSttsCd", "0044002")))));
        var run = UUID.randomUUID();
        store.begin(run, 1);
        store.received(run, new OntongApiClient.Response(AT, raw));
        store.prepare(run, new OntongPolicyCapture(mapper).parseResponse(raw, AT));
        store.itemFailed(run, 0);
        store.itemFailed(run, 1);
        return run;
    }

    private String request(UUID id, String reason) { return mapper.writeValueAsString(new CollectionReplays.Request(id, 1, reason)); }
    private String path(UUID run) { return ROOT + "/" + run + "/0/replays"; }
    private static RequestPostProcessor social(String id) {
        return oauth2Login().oauth2User(new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_MEMBER")), Map.of("memberId", id), "memberId"));
    }
}
