package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.ingestion.OntongPolicyCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.api-docs.path=/contract/policy",
        "springdoc.api-docs.version=OPENAPI_3_1", "springdoc.packages-to-scan=kr.youthpolicymate.policy.catalog",
        "springdoc.writer-with-order-by-keys=true"})
@AutoConfigureMockMvc
@Import(PolicyCatalogTest.ContractSecurity.class)
class PolicyCatalogTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired PolicyCatalogStore store;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    private OntongPolicyCapture parser;
    private ObjectNode item;
    private static final Instant AT = Instant.parse("2026-09-05T01:00:00Z");
    private static final String NUMBER = "20260903005400113371";

    @BeforeEach
    void prepare() throws Exception {
        jdbc.sql("DELETE FROM policy_revisions").update();
        jdbc.sql("DELETE FROM policy_source_snapshots").update();
        jdbc.sql("DELETE FROM policies").update();
        parser = new OntongPolicyCapture(mapper);
        item = (ObjectNode) parser.parse(Files.readString(Path.of("src/test/resources/ontong/list-capture.json"))).items().getFirst();
    }

    @Test
    @DisplayName("실제 PostgreSQL에 원본과 개정을 저장하고 같은 캡처 재전달과 조회수 변경은 개정을 늘리지 않는다")
    void storesWithoutDuplicateRevision() {
        assertThat(save("first", AT)).isEqualTo(PolicyCatalogStore.ImportResult.APPLIED);
        assertThat(save("first", AT)).isEqualTo(PolicyCatalogStore.ImportResult.REPLAYED);
        item.put("inqCnt", "999");
        assertThat(save("next", AT.plusSeconds(1))).isEqualTo(PolicyCatalogStore.ImportResult.UNCHANGED);
        assertThat(store.find(NUMBER).orElseThrow().revision()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM policy_source_snapshots").query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM policy_revisions").query(Long.class).single()).isOne();
    }

    @Test
    @DisplayName("현재 원본의 표시 규칙 변경은 원본 중복 없이 새 개정에 반영한다")
    void reappliesCurrentCaptureWithNewNormalization() {
        save("same-capture", AT);
        var normalized = parser.item(item);
        assertThat(store.importPolicy(normalized.number(), normalized.content(), normalized.rawPolicy(), AT,
                "same-capture", "next-normalization-version")).isEqualTo(PolicyCatalogStore.ImportResult.APPLIED);
        assertThat(store.find(NUMBER).orElseThrow().revision()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM policy_source_snapshots").query(Long.class).single()).isOne();
    }

    @Test
    @DisplayName("늦게 반입한 오래된 캡처는 현재 내용을 유지하고 A에서 B를 거쳐 A로 돌아오면 새 개정을 만든다")
    void preservesChronologyAndReversions() {
        save("a", AT);
        var title = item.path("plcyNm").asString();
        item.put("plcyNm", "변경된 정책 이름");
        save("b", AT.plusSeconds(20));
        item.put("plcyNm", title);
        assertThat(save("old", AT.plusSeconds(10))).isEqualTo(PolicyCatalogStore.ImportResult.STALE);
        assertThat(store.find(NUMBER).orElseThrow().content().title()).isEqualTo("변경된 정책 이름");
        save("a-again", AT.plusSeconds(30));
        assertThat(store.find(NUMBER).orElseThrow().revision()).isEqualTo(3);
    }

    @Test
    @DisplayName("동시에 같은 캡처를 적재해도 정책과 개정은 한 건만 남는다")
    void serializesSameCapture() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return save("same", AT); });
            var second = executor.submit(() -> { start.await(); return save("same", AT); });
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(PolicyCatalogStore.ImportResult.APPLIED, PolicyCatalogStore.ImportResult.REPLAYED);
        }
        assertThat(store.find(NUMBER).orElseThrow().revision()).isOne();
    }

    @Test
    @DisplayName("비회원이 검색·페이지·상세·404를 구분하고 원본 담당자 필드는 공개 응답에서 제외한다")
    void exposesPublicReads() throws Exception {
        save("first", AT);
        mvc.perform(get("/api/v1/policies").param("q", "AI학업").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].policyNumber").value(NUMBER)).andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get("/api/v1/policies").param("q", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/policies").param("page", "2").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(1));
        var response = mvc.perform(get("/api/v1/policies/" + NUMBER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.content.applicationPeriod").value("20260701 ~ 20261117")).andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("raw_policy", "PicNm", "apiKeyNm");
        mvc.perform(get("/api/v1/policies/0")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
    }

    @Test
    @DisplayName("잘못된 검색은 400으로 거절하고 쓰기·관리·개발 경로는 계속 차단한다")
    void rejectsInvalidAndPrivateRequests() throws Exception {
        mvc.perform(get("/api/v1/policies").param("page", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/policies").param("q", "가".repeat(81))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/policies").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
        mvc.perform(get("/api/dev/eligibility-examples")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("공개 정책의 실제 생성 OpenAPI와 저장한 계약이 일치한다")
    void matchesGeneratedContract() throws Exception {
        var response = mvc.perform(get("/contract/policy")).andExpect(status().isOk()).andReturn();
        var actual = mapper.readTree(response.getResponse().getContentAsByteArray());
        assertThat(actual.path("paths").size()).isEqualTo(2);
        var path = Path.of(System.getProperty("policy.contract.path"));
        if (Boolean.getBoolean("policy.contract.update")) {
            Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
        }
        assertThat(actual).isEqualTo(mapper.readTree(Files.readString(path)));
    }

    private PolicyCatalogStore.ImportResult save(String captureHash, Instant at) {
        var normalized = parser.item(item);
        return store.importPolicy(normalized.number(), normalized.content(), normalized.rawPolicy(), at, captureHash, normalized.contentHash());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContractSecurity {
        @Bean @Order(-1)
        SecurityFilterChain contractChain(HttpSecurity http) throws Exception {
            return http.securityMatcher("/contract/policy").authorizeHttpRequests(requests -> requests
                    .requestMatchers(HttpMethod.GET, "/contract/policy").permitAll().anyRequest().denyAll()).build();
        }
    }
}
