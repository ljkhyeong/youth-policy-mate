package kr.youthpolicymate.admin;

import kr.youthpolicymate.ingestion.OntongApiClient;
import kr.youthpolicymate.ingestion.OntongCollectionService;
import kr.youthpolicymate.ingestion.OntongCollectionStore;
import kr.youthpolicymate.ingestion.OntongPolicyCapture;
import kr.youthpolicymate.policy.catalog.PolicyCatalogStore;
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
import tools.jackson.databind.JsonNode;

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
class PolicyCorrectionApiTest {
    private static final String ROOT = "/api/v1/admin/policy-corrections";
    private static final String ADMIN = "10000000-0000-0000-0000-000000000001";
    private static final Instant AT = Instant.parse("2026-09-08T00:00:00Z");
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PolicyCatalogStore catalog;
    @Autowired OntongCollectionStore store;
    @Autowired OntongCollectionService collection;
    @MockitoBean OntongApiClient client;

    @BeforeEach void clear() { jdbc.sql("TRUNCATE ontong_collection_pages, policies CASCADE").update(); }
    @AfterEach void noExternalRequests() { verifyNoInteractions(client); }

    @Test @DisplayName("보정은 원본을 유지하고 새 개정과 작업 이력을 한 번만 기록한다")
    void preservesSourceAndRetries() throws Exception {
        ingest("원본 제목", "원본 설명", 1);
        var body = request(UUID.randomUUID(), "TITLE", "보정 제목");
        var correction = postJson(ROOT, body);
        postJson(ROOT, body);
        assertThat(title()).isEqualTo("보정 제목");
        assertThat(revision()).isEqualTo(2);
        assertThat(count("policy_corrections")).isOne();
        assertThat(count("policy_source_snapshots")).isOne();
        assertThat(jdbc.sql("SELECT raw_policy->>'plcyNm' FROM policy_source_snapshots").query(String.class).single()).isEqualTo("원본 제목");
        mvc.perform(get(ROOT + "/policies/123").with(social(ADMIN))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.correctionId").value(correction.path("id").asString()))
                .andExpect(jsonPath("$.previousRevision.content.title").value("원본 제목"));
        mvc.perform(post(ROOT).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("보정 제목", "다른 제목"))).andExpect(status().isConflict());
        mvc.perform(get(ROOT + "?page=2&pageSize=1").with(social(ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test @DisplayName("보정 대상이 같으면 다른 원본 변경을 반영하고 중복 수집으로 개정을 늘리지 않는다")
    void retainsCorrectionWhileApplyingOtherChanges() throws Exception {
        ingest("원본 제목", "원본 설명", 1);
        postJson(ROOT, request(UUID.randomUUID(), "TITLE", "보정 제목"));
        assertThat(ingest("원본 제목", "새 설명", 2)).isEqualTo(PolicyCatalogStore.ImportResult.APPLIED);
        assertThat(title()).isEqualTo("보정 제목");
        assertThat(jdbc.sql("SELECT content->>'description' FROM policies").query(String.class).single()).isEqualTo("새 설명");
        assertThat(ingest("원본 제목", "새 설명", 2)).isEqualTo(PolicyCatalogStore.ImportResult.UNCHANGED);
        assertThat(revision()).isEqualTo(3);
        assertThat(active().path("status").asString()).isEqualTo("ACTIVE");
    }

    @Test @DisplayName("충돌은 공개 내용을 유지하며 오래된 수집이 검토할 최신 원본을 덮어쓰지 않는다")
    void preservesPublicContentAndNewestConflict() throws Exception {
        ingest("원본 제목", "원본 설명", 1);
        postJson(ROOT, request(UUID.randomUUID(), "TITLE", "보정 제목"));
        assertThat(ingest("새 원본 제목", "새 설명", 3)).isEqualTo(PolicyCatalogStore.ImportResult.CORRECTION_CONFLICT);
        var conflict = active();
        assertThat(ingest("이전 수집 제목", "이전 설명", 2)).isEqualTo(PolicyCatalogStore.ImportResult.STALE);
        assertThat(active().path("reviewSnapshotId")).isEqualTo(conflict.path("reviewSnapshotId"));
        assertThat(title()).isEqualTo("보정 제목");
        assertThat(revision()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT last_request_sequence FROM policies").query(Long.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("SELECT last_collected_at FROM policies").query(java.time.OffsetDateTime.class).single().toInstant()).isEqualTo(AT.plusSeconds(1));
        assertThat(count("policy_source_snapshots")).isEqualTo(3);
        assertThat(conflict.path("reviewContent").path("description").asString()).isEqualTo("새 설명");
    }

    @Test @DisplayName("확인한 새 원본 기준으로 보정을 유지하고 다음 수집과 해제도 같은 원본을 따른다")
    void keepsThenReleases() throws Exception {
        ingest("원본 제목", "원본 설명", 1);
        postJson(ROOT, request(UUID.randomUUID(), "TITLE", "보정 제목"));
        ingest("새 원본 제목", "새 설명", 2);
        var conflict = active();
        var body = resolution(conflict, "KEEP");
        var path = ROOT + "/" + conflict.path("id").asString() + "/resolutions";
        postJson(path, body);
        postJson(path, body);
        assertThat(revision()).isEqualTo(3);
        assertThat(count("policy_corrections")).isEqualTo(2);
        assertThat(active().path("sourceValue").asString()).isEqualTo("새 원본 제목");
        assertThat(title()).isEqualTo("보정 제목");
        assertThat(ingest("새 원본 제목", "그다음 설명", 3)).isEqualTo(PolicyCatalogStore.ImportResult.APPLIED);
        var current = active();
        postJson(ROOT + "/" + current.path("id").asString() + "/resolutions", resolution(current, "USE_SOURCE"));
        assertThat(title()).isEqualTo("새 원본 제목");
        assertThat(revision()).isEqualTo(5);
        assertThat(ingest("다음 원본 제목", "최신 설명", 4)).isEqualTo(PolicyCatalogStore.ImportResult.APPLIED);
        assertThat(title()).isEqualTo("다음 원본 제목");
        assertThat(jdbc.sql("SELECT count(*) FROM policy_corrections WHERE status <> 'RELEASED'").query(Long.class).single()).isZero();
    }

    @Test @DisplayName("충돌 해소는 원본을 적용하고 항목 재처리로 실패 목록을 정리한다")
    void resolvesCollectionConflictAndReplay() throws Exception {
        var first = prepared("원본 제목");
        collection.applyStoredItem(first, 0);
        postJson(ROOT, request(UUID.randomUUID(), "TITLE", "보정 제목"));
        var newer = prepared("새 원본 제목");
        collection.applyStoredItem(newer, 0);
        assertThat(store.pending(newer)).containsExactly(0);
        mvc.perform(get("/api/v1/admin/collection-exceptions").with(social(ADMIN)))
                .andExpect(jsonPath("$.items[0].outcome").value("CORRECTION_CONFLICT"));
        var replayPath = "/api/v1/admin/collection-exceptions/" + newer + "/0/replays";
        postJson(replayPath, mapper.writeValueAsString(new CollectionReplays.Request(UUID.randomUUID(), 1, "충돌 확인")));
        var conflict = active();
        postJson(ROOT + "/" + conflict.path("id").asString() + "/resolutions", resolution(conflict, "USE_SOURCE"));
        assertThat(title()).isEqualTo("새 원본 제목");
        postJson(replayPath, mapper.writeValueAsString(new CollectionReplays.Request(UUID.randomUUID(), 2, "보정 해제 후 재처리")));
        assertThat(store.pending(newer)).isEmpty();
        assertThat(revision()).isEqualTo(3);
    }

    @Test @DisplayName("조회 뒤 충돌 원본이나 공개 개정이 바뀌면 이전 화면의 요청은 거절한다")
    void rejectsOutdatedReview() throws Exception {
        ingest("원본 제목", "원본 설명", 1);
        postJson(ROOT, request(UUID.randomUUID(), "TITLE", "보정 제목"));
        var initial = active();
        ingest("새 원본 제목", "새 설명", 2);
        var conflict = active();
        ingest("더 새 원본 제목", "더 새 설명", 3);
        for (var reviewed : List.of(initial, conflict)) {
            mvc.perform(post(ROOT + "/" + reviewed.path("id").asString() + "/resolutions").with(social(ADMIN)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(resolution(reviewed, "USE_SOURCE"))).andExpect(status().isConflict());
        }
        assertThat(revision()).isEqualTo(2);
        assertThat(active().path("reviewValue").asString()).isEqualTo("더 새 원본 제목");
    }

    @Test @DisplayName("개정 저장이 실패하면 보정과 해제 이력도 롤백한다")
    void rollsBackAuditAndPublication() throws Exception {
        ingest("원본 제목", "원본 설명", 1);
        var create = request(UUID.randomUUID(), "ORGANIZATION", "보정 기관");
        jdbc.sql("ALTER TABLE policy_revisions ADD CONSTRAINT reject_new_revision CHECK (revision < 2)").update();
        try {
            mvc.perform(post(ROOT).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(create))
                    .andExpect(status().isServiceUnavailable());
            assertThat(count("policy_corrections")).isZero();
            assertThat(revision()).isOne();
        } finally { jdbc.sql("ALTER TABLE policy_revisions DROP CONSTRAINT reject_new_revision").update(); }
        postJson(ROOT, create);
        assertThat(jdbc.sql("SELECT content->>'organization' FROM policies").query(String.class).single()).isEqualTo("보정 기관");
        var current = active();
        var release = resolution(current, "USE_SOURCE");
        var path = ROOT + "/" + current.path("id").asString() + "/resolutions";
        jdbc.sql("ALTER TABLE policy_revisions ADD CONSTRAINT reject_new_revision CHECK (revision < 3)").update();
        try {
            mvc.perform(post(path).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(release))
                    .andExpect(status().isServiceUnavailable());
            assertThat(active().path("status").asString()).isEqualTo("ACTIVE");
            assertThat(revision()).isEqualTo(2);
        } finally { jdbc.sql("ALTER TABLE policy_revisions DROP CONSTRAINT reject_new_revision").update(); }
        postJson(path, release);
        assertThat(jdbc.sql("SELECT content->>'organization' FROM policies").query(String.class).single()).isEqualTo("원본 기관");
    }

    @Test @DisplayName("동시 보정 재요청은 하나의 개정만 만든다")
    void serializesSameRequest() throws Exception {
        ingest("원본 제목", "원본 설명", 1);
        var body = request(UUID.randomUUID(), "TITLE", "보정 제목");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                start.await();
                return mvc.perform(post(ROOT).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            })).toList();
            start.countDown();
            for (var task : tasks) assertThat(task.get()).isEqualTo(200);
        }
        assertThat(count("policy_corrections")).isOne();
        assertThat(revision()).isEqualTo(2);
    }

    @Test @DisplayName("권한·CSRF·허용 필드·공개 정책·개정을 확인한 뒤에만 보정한다")
    void enforcesWriteBoundary() throws Exception {
        var body = request(UUID.randomUUID(), "TITLE", "보정 제목");
        mvc.perform(post(ROOT).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        ingest("원본 제목", "원본 설명", 1);
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT + "/policies/123").with(social(UUID.randomUUID().toString()))).andExpect(status().isForbidden());
        mvc.perform(post(ROOT).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT).with(social(ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        for (var invalid : List.of(request(UUID.randomUUID(), "DEADLINE", "값"), request(UUID.randomUUID(), "TITLE", " "),
                request(UUID.randomUUID(), "TITLE", "가".repeat(501)), request(UUID.randomUUID(), "TITLE", "원본 제목"), "{}")) {
            mvc.perform(post(ROOT).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        assertThat(count("policy_corrections")).isZero();
        postJson(ROOT, body);
        mvc.perform(post(ROOT).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(request(UUID.randomUUID(), "ORGANIZATION", "보정 기관"))).andExpect(status().isConflict());
    }

    @Test @DisplayName("순번 없는 캡처도 더 오래된 충돌 원본을 적용하지 않는다")
    void ordersLocalCaptures() throws Exception {
        local("원본 제목", 1);
        postJson(ROOT, request(UUID.randomUUID(), "TITLE", "보정 제목"));
        assertThat(local("새 원본 제목", 3)).isEqualTo(PolicyCatalogStore.ImportResult.CORRECTION_CONFLICT);
        assertThat(local("이전 원본 제목", 2)).isEqualTo(PolicyCatalogStore.ImportResult.STALE);
        var conflict = active();
        postJson(ROOT + "/" + conflict.path("id").asString() + "/resolutions", resolution(conflict, "USE_SOURCE"));
        assertThat(title()).isEqualTo("새 원본 제목");
    }

    private String request(UUID id, String field, String value) {
        return mapper.writeValueAsString(Map.of("requestId", id, "policyNumber", "123", "expectedRevision", 1,
                "field", field, "value", value, "reason", "공식 안내 확인"));
    }
    private String resolution(JsonNode item, String action) {
        return mapper.writeValueAsString(Map.of("requestId", UUID.randomUUID(), "expectedRevision", item.path("currentRevision").asLong(),
                "reviewSnapshotId", item.path("reviewSnapshotId").asLong(), "action", action, "reason", "새 원본 검토"));
    }
    private JsonNode postJson(String path, String body) throws Exception {
        return mapper.readTree(mvc.perform(post(path).with(social(ADMIN)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString());
    }
    private JsonNode active() throws Exception {
        var items = mapper.readTree(mvc.perform(get(ROOT).with(social(ADMIN))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("items");
        for (var item : items) if (!item.path("status").asString().equals("RELEASED")) return item;
        throw new AssertionError("진행 중 보정 없음");
    }
    private PolicyCatalogStore.ImportResult ingest(String title, String description, long sequence) {
        var parsed = new OntongPolicyCapture(mapper).item(raw(title, description));
        return catalog.importCollectedPolicy("123", parsed.content(), parsed.rawPolicy(), AT.plusSeconds(sequence), "capture-" + sequence, parsed.contentHash(), sequence);
    }
    private PolicyCatalogStore.ImportResult local(String title, int second) {
        var parsed = new OntongPolicyCapture(mapper).item(raw(title, "설명"));
        return catalog.importPolicy("123", parsed.content(), parsed.rawPolicy(), AT.plusSeconds(second), "local-" + second, parsed.contentHash());
    }
    private JsonNode raw(String title, String description) {
        return mapper.valueToTree(Map.of("plcyNo", "123", "plcyNm", title, "plcyExplnCn", description,
                "sprvsnInstCdNm", "원본 기관", "plcyAprvSttsCd", "0044002"));
    }
    private UUID prepared(String title) {
        var raw = mapper.writeValueAsString(Map.of("resultCode", 200, "result", Map.of(
                "pagging", Map.of("pageNum", 1, "pageSize", 10, "totCount", 1), "youthPolicyList", List.of(raw(title, "설명")))));
        var run = UUID.randomUUID(); store.begin(run, 1);
        store.received(run, new OntongApiClient.Response(AT, raw));
        store.prepare(run, new OntongPolicyCapture(mapper).parseResponse(raw, AT));
        return run;
    }
    private String title() { return jdbc.sql("SELECT content->>'title' FROM policies").query(String.class).single(); }
    private long revision() { return jdbc.sql("SELECT current_revision FROM policies").query(Long.class).single(); }
    private long count(String table) { return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single(); }
    private static RequestPostProcessor social(String id) {
        return oauth2Login().oauth2User(new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_MEMBER")), Map.of("memberId", id), "memberId"));
    }
}
