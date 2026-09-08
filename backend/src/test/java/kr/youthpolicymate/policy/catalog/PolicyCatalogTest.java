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
        "springdoc.api-docs.version=OPENAPI_3_1", "springdoc.packages-to-scan=kr.youthpolicymate.policy.catalog,kr.youthpolicymate.member,kr.youthpolicymate.admin",
        "springdoc.writer-with-order-by-keys=true"})
@AutoConfigureMockMvc
@Import(PolicyCatalogTest.ContractSecurity.class)
class PolicyCatalogTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired PolicyCatalogStore store;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean JdbcClient jdbc;
    @Autowired PolicyCheckService checks;
    @Autowired PolicyQuestionService questions;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean java.time.Clock clock;
    private OntongPolicyCapture parser;
    private ObjectNode item;
    private static final Instant AT = Instant.parse("2026-09-05T01:00:00Z");
    private static final String NUMBER = "20260903005400113371";

    @BeforeEach
    void prepare() throws Exception {
        org.mockito.Mockito.when(clock.instant()).thenReturn(AT);
        jdbc.sql("DELETE FROM policy_revisions").update();
        jdbc.sql("DELETE FROM policy_corrections").update();
        jdbc.sql("DELETE FROM policy_source_snapshots").update();
        jdbc.sql("DELETE FROM policies").update();
        parser = new OntongPolicyCapture(mapper);
        item = (ObjectNode) parser.parse(Files.readString(Path.of("src/test/resources/ontong/list-capture.json"))).items().getFirst();
    }

    @Test
    @DisplayName("질문과 답변 평가는 각각 SELECT 한 번으로 현재 개정과 해시를 확인한다")
    void loadsQuestionVersionWithOneQuery() {
        saveReviewed(ExamFeeRules.NUMBER, "응시료 지원", ExamFeeRules.CONTENT_HASH);

        org.mockito.Mockito.clearInvocations(jdbc);
        var questionnaire = questions.questions(ExamFeeRules.NUMBER);
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(1)).sql(org.mockito.ArgumentMatchers.anyString());
        assertThat(questionnaire.available()).isTrue();
        assertThat(questionnaire.revision()).isOne();

        org.mockito.Mockito.clearInvocations(jdbc);
        var evaluation = questions.evaluate(ExamFeeRules.NUMBER,
                new PolicyQuestions.Request(questionnaire.revision(), questionnaire.ruleVersion(), List.of()));
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(1)).sql(org.mockito.ArgumentMatchers.anyString());
        assertThat(evaluation.revision()).isOne();
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
        var content = parser.item(item).content();
        mvc.perform(get("/api/v1/policies").param("q", "AI학업").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].policyNumber").value(NUMBER))
                .andExpect(jsonPath("$.items[0].title").value(content.title()))
                .andExpect(jsonPath("$.items[0].description").value(content.description()))
                .andExpect(jsonPath("$.items[0].category").value(content.category()))
                .andExpect(jsonPath("$.items[0].organization").value(content.organization()))
                .andExpect(jsonPath("$.items[0].applicationPeriod").value(content.applicationPeriod()))
                .andExpect(jsonPath("$.items[0].collectedAt").value(AT.toString()))
                .andExpect(jsonPath("$.hasNext").value(false));
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
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "invalid")).andExpect(status().isBadRequest());
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
        assertThat(actual.at("/paths/~1api~1v1~1admin~1collection-exceptions/get/security/0/memberSession").isArray()).isTrue();
        assertThat(actual.at("/paths/~1api~1v1~1admin~1collection-exceptions~1pages/get/security/0/memberSession").isArray()).isTrue();
        assertThat(actual.at("/paths/~1api~1v1~1admin~1collection-exceptions~1{runId}~1{itemIndex}/get/security/0/memberSession").isArray()).isTrue();
        assertThat(actual.at("/components/schemas/CollectionExceptionDetail/properties/currentPolicy/anyOf/1/type").asString()).isEqualTo("null");
        assertThat(actual.at("/components/schemas/CollectionExceptionCurrentPolicy/properties/previousRevision/anyOf/1/type").asString()).isEqualTo("null");
        assertThat(actual.at("/paths/~1api~1v1~1admin~1collection-exceptions~1{runId}~1{itemIndex}~1replays/post/security/0/memberSession").isArray()).isTrue();
        for (var path : java.util.List.of("~1api~1v1~1admin~1policy-corrections", "~1api~1v1~1admin~1policy-corrections~1{id}~1resolutions")) {
            assertThat(actual.at("/paths/" + path + "/post/security/0/memberSession").isArray()).isTrue();
            assertThat(actual.at("/paths/" + path + "/post/responses/200/content/*~1*/schema/$ref").asString()).isEqualTo("#/components/schemas/PolicyCorrectionItem");
        }
        assertThat(actual.at("/components/schemas/PolicySummary/required").valueStream().map(value -> value.asString()))
                .contains("questionnaireAvailable");
        assertThat(actual.at("/components/schemas/PolicyCheckItem/required").valueStream().map(value -> value.asString()))
                .contains("questionnaireAvailable");
        assertThat(actual.at("/components/schemas/MemberEmailSettings/properties/verificationDelivery/enum").valueStream().anyMatch(value -> value.isNull())).isTrue();
        assertThat(actual.at("/components/schemas/MemberConditions/properties/conditions/anyOf/1/type").asString()).isEqualTo("null");
        assertThat(actual.at("/paths/~1api~1v1~1session/get/parameters").isMissingNode()).isTrue();
        var path = Path.of(System.getProperty("policy.contract.path"));
        if (Boolean.getBoolean("policy.contract.update")) {
            Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
        }
        assertThat(actual).isEqualTo(mapper.readTree(Files.readString(path)));
    }

    @Test
    @DisplayName("검토한 원문에만 비회원 질문을 제공하고 최신 개정·규칙으로 제출한 답변만 비교한다")
    void evaluatesOnlyReviewedRevision() throws Exception {
        save("unreviewed", AT);
        mvc.perform(get("/api/v1/policies/" + NUMBER + "/questions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        // 규칙 활성화와 DB 개정 검사를 위한 인공 자료다. 정책 내용 자체의 검토 자료가 아니다.
        item.put("plcyNo", WorkStudyRules.NUMBER);
        var normalized = parser.item(item);
        store.importPolicy(normalized.number(), normalized.content(), normalized.rawPolicy(), AT, "reviewed", WorkStudyRules.CONTENT_HASH);
        var path = "/api/v1/policies/" + WorkStudyRules.NUMBER;
        mvc.perform(get(path + "/questions")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.questions.length()").value(6));
        var request = new PolicyQuestions.Request(1, WorkStudyRules.VERSION, List.of());
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.commonCriteriaStatus").value("NEEDS_REVIEW"));
        for (var stale : List.of(new PolicyQuestions.Request(2, WorkStudyRules.VERSION, List.of()),
                new PolicyQuestions.Request(1, "old-rules", List.of()))) {
            mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(stale)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_CHANGED"));
        }
        store.importPolicy(normalized.number(), normalized.content(), normalized.rawPolicy(), AT.plusSeconds(1), "changed", "changed-content");
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/policies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].questionnaireAvailable").value(false));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("잘못된 추가 답변과 없는 정책을 안전한 오류 응답으로 구분한다")
    void rejectsInvalidEvaluation() throws Exception {
        var path = "/api/v1/policies/" + WorkStudyRules.NUMBER;
        mvc.perform(get(path + "/questions")).andExpect(status().isNotFound());
        for (String json : List.of("{}", "{\"revision\":1,\"ruleVersion\":\"v1\",\"answers\":null}",
                "{\"revision\":1,\"ruleVersion\":\"v1\",\"answers\":[{\"questionId\":\"\",\"value\":\"YES\"}]}")) {
            mvc.perform(post(path + "/evaluation").contentType("application/json").content(json)).andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("응시료 규칙은 검토한 정책에만 연결하고 국가근로 질문과 다른 답변을 비교한다")
    void evaluatesReviewedExamFee() throws Exception {
        // 정책 내용은 인공 자료다. 해시 등록·개정 검사·규칙 연결만 검증한다.
        item.put("plcyNo", ExamFeeRules.NUMBER);
        var normalized = parser.item(item);
        store.importPolicy(normalized.number(), normalized.content(), normalized.rawPolicy(), AT, "exam-reviewed", ExamFeeRules.CONTENT_HASH);
        String path = "/api/v1/policies/" + ExamFeeRules.NUMBER;
        mvc.perform(get(path + "/questions")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.questions.length()").value(3))
                .andExpect(jsonPath("$.questions[0].id").value("birthRange"));
        var input = new PolicyQuestions.Request(1, ExamFeeRules.VERSION, List.of(
                new PolicyQuestions.Answer("birthRange", "ON_OR_AFTER_1991_01_01"),
                new PolicyQuestions.Answer("exam", "HRDK_TECHNICAL"), new PolicyQuestions.Answer("remainingUses", "ONE")));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.policyNumber").value(ExamFeeRules.NUMBER))
                .andExpect(jsonPath("$.ruleVersion").value(ExamFeeRules.VERSION))
                .andExpect(jsonPath("$.commonCriteriaStatus").value("ELIGIBLE"))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"));
        var otherRule = new PolicyQuestions.Request(1, WorkStudyRules.VERSION, input.answers());
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(otherRule))).andExpect(status().isConflict());
        var otherAnswer = new PolicyQuestions.Request(1, ExamFeeRules.VERSION, List.of(new PolicyQuestions.Answer("nationality", "YES")));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(otherAnswer))).andExpect(status().isBadRequest());
        store.importPolicy(normalized.number(), normalized.content(), normalized.rawPolicy(), AT.plusSeconds(1), "exam-updated", "changed-content");
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(input))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("서울 기준 새해가 되면 이전 연도의 응시료 질문과 제출을 중단한다")
    void stopsExamFeeQuestionsAcrossYearBoundary() throws Exception {
        item.put("plcyNo", ExamFeeRules.NUMBER);
        var normalized = parser.item(item);
        store.importPolicy(normalized.number(), normalized.content(), normalized.rawPolicy(), AT, "exam-reviewed", ExamFeeRules.CONTENT_HASH);
        String path = "/api/v1/policies/" + ExamFeeRules.NUMBER;
        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse("2026-12-31T14:59:59Z"));
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        // 비교 시각을 한 번만 읽는다. 질문 검사와 결과가 자정 양쪽으로 나뉘지 않아야 한다.
        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse("2026-12-31T14:59:59Z"), Instant.parse("2026-12-31T15:00:00Z"));
        String body = mapper.writeValueAsString(new PolicyQuestions.Request(1, ExamFeeRules.VERSION, List.of()));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatedAt").value("2026-12-31T14:59:59Z"));
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("올해")));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/policies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].questionnaireAvailable").value(false));
    }

    @Test
    @DisplayName("질문 필터는 검색·건수·페이지에 먼저 적용하고 전체 목록에도 제공 여부를 표시한다")
    void filtersReviewedQuestionsBeforePagination() throws Exception {
        save("unreviewed", AT.plusSeconds(5));
        saveReviewed(WorkStudyRules.NUMBER, "국가근로 지원", WorkStudyRules.CONTENT_HASH);
        saveReviewed(ExamFeeRules.NUMBER, "응시료 지원", ExamFeeRules.CONTENT_HASH);
        mvc.perform(get("/api/v1/policies").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].policyNumber").value(NUMBER))
                .andExpect(jsonPath("$.items[0].questionnaireAvailable").value(false));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items[0].policyNumber").value(ExamFeeRules.NUMBER))
                .andExpect(jsonPath("$.items[0].questionnaireAvailable").value(true));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true").param("pageSize", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items[0].policyNumber").value(WorkStudyRules.NUMBER));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true").param("pageSize", "1").param("page", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true").param("q", "국가근로"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].policyNumber").value(WorkStudyRules.NUMBER));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true").param("q", "없는검색어"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("조건 확인은 SELECT 두 번으로 정렬·페이지·현재 개정의 원문을 반환한다")
    void loadsConditionPageWithTwoQueries() {
        var numbers = new java.util.ArrayList<String>();
        for (int index = 1; index <= 21; index++) {
            var number = "900000000000000000%02d".formatted(index);
            numbers.add(number);
            item.put("plcyNo", number).put("plcyNm", "조회 테스트 정책 " + index);
            item.put("addAplyQlfcCndCn", "이전 조건 " + index);
            save("page-" + index, AT);
        }
        item.put("plcyNm", "개정한 정책").put("addAplyQlfcCndCn", "최신 조건");
        save("current-revision", AT.plusSeconds(1));
        var expected = new java.util.ArrayList<>(numbers);
        expected.addFirst(expected.removeLast());
        var input = new BasicConditions(java.time.LocalDate.of(2000, 1, 2), "강남구", BasicConditions.EmploymentStatus.NOT_EMPLOYED);

        for (int page = 1; page <= 3; page++) {
            org.mockito.Mockito.clearInvocations(jdbc);
            var response = checks.check(input, page, "", PolicyCheckResponse.Sort.AGE_MATCH, null);
            org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(2)).sql(org.mockito.ArgumentMatchers.anyString());
            assertThat(response.page()).isEqualTo(page);
            assertThat(response.total()).isEqualTo(21);
            assertThat(response.hasNext()).isEqualTo(page == 1);
            int from = Math.min((page - 1) * 20, expected.size());
            int to = Math.min(page * 20, expected.size());
            assertThat(response.items()).extracting(PolicyCheckResponse.Item::policyNumber).containsExactlyElementsOf(expected.subList(from, to));
            assertThat(response.items()).allSatisfy(value ->
                    assertThat(value.status()).isEqualTo(kr.youthpolicymate.eligibility.EligibilityStatus.NEEDS_REVIEW));
            if (page == 1) {
                var current = response.items().getFirst();
                assertThat(current.revision()).isEqualTo(2);
                assertThat(current.title()).isEqualTo("개정한 정책");
                assertThat(current.checks().getFirst().evidence()).isEqualTo("최신 조건");
            }
        }
    }

    @Test
    @DisplayName("기본 조건 결과의 질문 제공 여부는 자격 상태와 분리하고 같은 비교 시각을 사용한다")
    void exposesQuestionsInConditionChecks() throws Exception {
        saveReviewed(ExamFeeRules.NUMBER, "응시료 지원", ExamFeeRules.CONTENT_HASH);
        var input = new BasicConditions(java.time.LocalDate.of(2000, 1, 2), "강남구", BasicConditions.EmploymentStatus.NOT_EMPLOYED);
        var body = mapper.writeValueAsString(input);
        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse("2026-12-31T14:59:59Z"), Instant.parse("2026-12-31T15:00:00Z"));
        mvc.perform(post("/api/v1/policies/checks").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.evaluatedAt").value("2026-12-31T14:59:59Z"))
                .andExpect(jsonPath("$.items[0].questionnaireAvailable").value(true))
                .andExpect(jsonPath("$.items[0].status").value("NEEDS_REVIEW"));
        mvc.perform(post("/api/v1/policies/checks").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].questionnaireAvailable").value(false))
                .andExpect(jsonPath("$.items[0].checks[0].outcome").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[0].ruleVersion").value(""))
                .andExpect(jsonPath("$.items[0].status").value("NEEDS_REVIEW"));
    }

    @Test
    @DisplayName("검토한 K-패스 원문에 질문·목록 표시를 연결하고 내용이 바뀌면 이전 답변을 중단한다")
    void providesReviewedKPassQuestions() throws Exception {
        saveReviewed(KPassRules.NUMBER, "K-패스", KPassRules.CONTENT_HASH);
        var path = "/api/v1/policies/" + KPassRules.NUMBER;
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.questions.length()").value(4))
                .andExpect(jsonPath("$.ruleVersion").value("k-pass-2026-v1-2026-09"));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true").param("q", "K-패스"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].questionnaireAvailable").value(true));
        var request = new PolicyQuestions.Request(1, KPassRules.versionAt(AT), List.of(new PolicyQuestions.Answer("age", "ADULT"),
                new PolicyQuestions.Answer("registration", "REGISTERED"), new PolicyQuestions.Answer("residence", "CONFIRMED"),
                new PolicyQuestions.Answer("monthlyRides", "FIRST_MONTH_1_TO_14")));
        var body = mapper.writeValueAsString(request);
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.commonCriteriaStatus").value("ELIGIBLE"))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(
                new PolicyQuestions.Request(2, request.ruleVersion(), request.answers())))).andExpect(status().isConflict());
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(
                new PolicyQuestions.Request(1, request.ruleVersion(), List.of(new PolicyQuestions.Answer("monthlyRides", "TEN"))))))
                .andExpect(status().isBadRequest());
        var current = store.find(KPassRules.NUMBER).orElseThrow();
        store.importPolicy(KPassRules.NUMBER, current.content(), "{}", AT.plusSeconds(1), "changed-k-pass", "changed-hash");
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_CHANGED"));
    }

    @Test
    @DisplayName("서울 월말의 한 요청은 같은 달로 비교하고 다음 요청은 이전 월 답변을 거절한다")
    void fencesKPassAnswersAtMonthBoundary() throws Exception {
        saveReviewed(KPassRules.NUMBER, "K-패스", KPassRules.CONTENT_HASH);
        var path = "/api/v1/policies/" + KPassRules.NUMBER;
        var before = Instant.parse("2026-09-30T14:59:59Z");
        var after = Instant.parse("2026-09-30T15:00:00Z");
        org.mockito.Mockito.when(clock.instant()).thenReturn(before, after);
        var body = mapper.writeValueAsString(new PolicyQuestions.Request(1, KPassRules.versionAt(before), List.of()));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evaluatedAt").value(before.toString()))
                .andExpect(jsonPath("$.ruleVersion").value("k-pass-2026-v1-2026-09"));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_CHANGED"));
        mvc.perform(get(path + "/questions")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleVersion").value("k-pass-2026-v1-2026-10"))
                .andExpect(jsonPath("$.scope").value(org.hamcrest.Matchers.startsWith("2026년 10월")));
    }

    @Test
    @DisplayName("검토한 연도가 지나면 K-패스 질문·목록 표시와 이전 답변 제출을 중단한다")
    void stopsKPassQuestionsAfterReviewedYear() throws Exception {
        saveReviewed(KPassRules.NUMBER, "K-패스", KPassRules.CONTENT_HASH);
        var path = "/api/v1/policies/" + KPassRules.NUMBER;
        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse("2026-12-31T15:00:00Z"));
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("올해")));
        mvc.perform(get("/api/v1/policies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].questionnaireAvailable").value(false));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(
                new PolicyQuestions.Request(1, KPassRules.versionAt(AT), List.of())))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("청년주택드림청약통장 질문·목록 표시를 연결하고 개정·규칙·원문 변경 시 이전 답변을 거절한다")
    void providesReviewedYouthHousingSavingsQuestions() throws Exception {
        saveReviewed(YouthHousingSavingsRules.NUMBER, "청년주택드림청약통장", YouthHousingSavingsRules.CONTENT_HASH);
        var path = "/api/v1/policies/" + YouthHousingSavingsRules.NUMBER;
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.questions.length()").value(4))
                .andExpect(jsonPath("$.ruleVersion").value(YouthHousingSavingsRules.VERSION));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true").param("q", "청년주택드림"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].questionnaireAvailable").value(true));
        var request = new PolicyQuestions.Request(1, YouthHousingSavingsRules.VERSION, List.of(new PolicyQuestions.Answer("age", "AGE_19_TO_34"),
                new PolicyQuestions.Answer("homeOwnership", "NO_HOME"), new PolicyQuestions.Answer("incomeBasis", "PREVIOUS_YEAR"),
                new PolicyQuestions.Answer("incomeAmount", "UP_TO_50M")));
        var body = mapper.writeValueAsString(request);
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.commonCriteriaStatus").value("ELIGIBLE"))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW")).andExpect(jsonPath("$.checks.length()").value(3));
        for (var stale : List.of(new PolicyQuestions.Request(2, request.ruleVersion(), request.answers()),
                new PolicyQuestions.Request(1, "youth-housing-2026-v0", request.answers()))) {
            mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(stale)))
                    .andExpect(status().isConflict());
        }
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(
                new PolicyQuestions.Request(1, request.ruleVersion(), List.of(new PolicyQuestions.Answer("incomeAmount", "ZERO"))))))
                .andExpect(status().isBadRequest());
        var current = store.find(YouthHousingSavingsRules.NUMBER).orElseThrow();
        store.importPolicy(YouthHousingSavingsRules.NUMBER, current.content(), "{}", AT.plusSeconds(1), "changed-housing", "changed-hash");
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_CHANGED"));
    }

    @Test
    @DisplayName("서울 연말의 한 요청은 같은 연도로 비교하고 새해에는 가입 질문·목록 표시·제출을 중단한다")
    void stopsYouthHousingSavingsQuestionsAtYearBoundary() throws Exception {
        saveReviewed(YouthHousingSavingsRules.NUMBER, "청년주택드림청약통장", YouthHousingSavingsRules.CONTENT_HASH);
        var path = "/api/v1/policies/" + YouthHousingSavingsRules.NUMBER;
        var before = Instant.parse("2026-12-31T14:59:59Z");
        org.mockito.Mockito.when(clock.instant()).thenReturn(before, Instant.parse("2026-12-31T15:00:00Z"));
        var body = mapper.writeValueAsString(new PolicyQuestions.Request(1, YouthHousingSavingsRules.VERSION, List.of()));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evaluatedAt").value(before.toString()));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_CHANGED"));
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("올해")));
        mvc.perform(get("/api/v1/policies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].questionnaireAvailable").value(false));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("서울청년정책네트워크 질문과 마감 안내를 제공하고 개정·규칙·원문 변경 시 이전 답변을 거절한다")
    void providesReviewedSeoulYouthNetworkQuestions() throws Exception {
        saveReviewed(SeoulYouthNetworkRules.NUMBER, "2026년 서울청년정책네트워크 하반기 모집", SeoulYouthNetworkRules.CONTENT_HASH);
        var path = "/api/v1/policies/" + SeoulYouthNetworkRules.NUMBER;
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.questions.length()").value(4))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("접수가 마감")))
                .andExpect(jsonPath("$.ruleVersion").value(SeoulYouthNetworkRules.VERSION));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true").param("q", "서울청년정책네트워크"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].questionnaireAvailable").value(true));
        var request = new PolicyQuestions.Request(1, SeoulYouthNetworkRules.VERSION, List.of(new PolicyQuestions.Answer("birthRange", "BASE_RANGE"),
                new PolicyQuestions.Answer("seoulConnection", "UNIVERSITY"), new PolicyQuestions.Answer("consecutiveTerms", "NOT_APPLICABLE"),
                new PolicyQuestions.Answer("priorDisqualification", "NONE")));
        var body = mapper.writeValueAsString(request);
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.commonCriteriaStatus").value("ELIGIBLE"))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.explanation").value(org.hamcrest.Matchers.containsString("접수가 마감")));
        for (var stale : List.of(new PolicyQuestions.Request(2, request.ruleVersion(), request.answers()),
                new PolicyQuestions.Request(1, "seoul-network-2026-h1-v1", request.answers()))) {
            mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(stale)))
                    .andExpect(status().isConflict());
        }
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(
                new PolicyQuestions.Request(1, request.ruleVersion(), List.of(new PolicyQuestions.Answer("birthRange", "ADULT"))))))
                .andExpect(status().isBadRequest());
        var current = store.find(SeoulYouthNetworkRules.NUMBER).orElseThrow();
        store.importPolicy(SeoulYouthNetworkRules.NUMBER, current.content(), "{}", AT.plusSeconds(1), "changed-network", "changed-hash");
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_CHANGED"));
    }

    @Test
    @DisplayName("서울 연말의 한 요청은 같은 연도로 비교하고 새해에는 이전 모집의 질문·목록 표시·제출을 중단한다")
    void stopsSeoulYouthNetworkQuestionsAtYearBoundary() throws Exception {
        saveReviewed(SeoulYouthNetworkRules.NUMBER, "서울청년정책네트워크", SeoulYouthNetworkRules.CONTENT_HASH);
        var path = "/api/v1/policies/" + SeoulYouthNetworkRules.NUMBER;
        var before = Instant.parse("2026-12-31T14:59:59Z");
        org.mockito.Mockito.when(clock.instant()).thenReturn(before, Instant.parse("2026-12-31T15:00:00Z"));
        var body = mapper.writeValueAsString(new PolicyQuestions.Request(1, SeoulYouthNetworkRules.VERSION, List.of()));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evaluatedAt").value(before.toString()));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_CHANGED"));
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("새 모집")));
        mvc.perform(get("/api/v1/policies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].questionnaireAvailable").value(false));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test @DisplayName("전체 정책을 연령 충족·미확인·불충족 순으로 정렬한 뒤 검색과 페이지를 적용한다")
    void ranksConditionResultsBeforePagination() throws Exception {
        saveReviewed(ExamFeeRules.NUMBER, "응시료 지원", ExamFeeRules.CONTENT_HASH);
        saveReviewed(KPassRules.NUMBER, "K-패스", KPassRules.CONTENT_HASH);
        for (int index = 1; index <= 21; index++) {
            item.put("plcyNo", "900000000000000000%02d".formatted(index)).put("plcyNm", "미검토 정책 " + index);
            save("condition-order-" + index, AT.plusSeconds(1));
        }
        var input = new BasicConditions(java.time.LocalDate.parse("1990-12-31"), "강남구", BasicConditions.EmploymentStatus.NOT_EMPLOYED);
        org.mockito.Mockito.clearInvocations(jdbc);
        var first = checks.check(input, 1, "", PolicyCheckResponse.Sort.AGE_MATCH, null);
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(2)).sql(org.mockito.ArgumentMatchers.anyString());
        assertThat(first.total()).isEqualTo(23);
        assertThat(first.items().getFirst().policyNumber()).isEqualTo(KPassRules.NUMBER);
        assertThat(first.items().getFirst().checks().getFirst().outcome()).isEqualTo(kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.MET);
        assertThat(first.items().getFirst().ruleVersion()).isEqualTo(KPassRules.versionAt(AT));
        var second = checks.check(input, 2, "", PolicyCheckResponse.Sort.AGE_MATCH, null);
        assertThat(second.items()).hasSize(3);
        assertThat(second.items().getLast().policyNumber()).isEqualTo(ExamFeeRules.NUMBER);
        assertThat(second.items().getLast().checks().getFirst().outcome()).isEqualTo(kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.NOT_MET);
        assertThat(first.items()).noneMatch(value -> second.items().stream().anyMatch(next -> value.policyNumber().equals(next.policyNumber())));
        var search = checks.check(input, 1, "응시료", PolicyCheckResponse.Sort.AGE_MATCH, null);
        assertThat(search.total()).isEqualTo(1);
        assertThat(search.items().getFirst().policyNumber()).isEqualTo(ExamFeeRules.NUMBER);
        assertThat(checks.check(input, 1, "%_", PolicyCheckResponse.Sort.AGE_MATCH, null).items()).isEmpty();
        assertThat(checks.check(input, 1, "", PolicyCheckResponse.Sort.RECENT, null).items().getFirst().title()).isEqualTo("미검토 정책 1");
        var body = mapper.writeValueAsString(input);
        mvc.perform(post("/api/v1/policies/checks").param("q", " 응시료 ").param("sort", "RECENT").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].checks[0].outcome").value("NOT_MET"));
        mvc.perform(post("/api/v1/policies/checks").param("q", "가".repeat(81)).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/policies/checks").param("sort", "SCORE").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        var current = store.find(KPassRules.NUMBER).orElseThrow();
        store.importPolicy(KPassRules.NUMBER, current.content(), "{}", AT.plusSeconds(2), "changed-basic", "changed-basic-hash");
        var changed = checks.check(input, 1, "K-패스", PolicyCheckResponse.Sort.AGE_MATCH, null).items().getFirst();
        assertThat(changed.checks().getFirst().outcome()).isEqualTo(kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN);
        assertThat(changed.ruleVersion()).isEmpty();
        assertThat(changed.questionnaireAvailable()).isFalse();
    }

    @Test @DisplayName("상반기 이사비 질문을 공개 목록에 연결하고 원문·개정·규칙·연도가 바뀌면 비교를 중단한다")
    void providesMovingFeeQuestionsWithVersionGuards() throws Exception {
        saveReviewed(MovingFeeRules.NUMBER, "이사비 지원", MovingFeeRules.CONTENT_HASH);
        var path = "/api/v1/policies/" + MovingFeeRules.NUMBER;
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.questions.length()").value(9)).andExpect(jsonPath("$.questions[8].id").value("requestedCost"))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("상반기 접수는")));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true").param("q", "이사비"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        var request = new PolicyQuestions.Request(1, MovingFeeRules.VERSION, List.of(new PolicyQuestions.Answer("birthRange", "IN_RANGE"),
                new PolicyQuestions.Answer("move", "COMPLETED"), new PolicyQuestions.Answer("contract", "ALL"),
                new PolicyQuestions.Answer("homeOwnership", "NO_HOME"), new PolicyQuestions.Answer("housingCost", "WITHIN_LIMIT"),
                new PolicyQuestions.Answer("income", "WITHIN_LIMIT"), new PolicyQuestions.Answer("seoulSupport", "NONE"),
                new PolicyQuestions.Answer("otherSupport", "BROKERAGE_ONLY"), new PolicyQuestions.Answer("requestedCost", "MOVING")));
        var body = mapper.writeValueAsString(request);
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.checks[0].outcome").value("MET"))
                .andExpect(jsonPath("$.checks[5].outcome").value("MET"))
                .andExpect(jsonPath("$.checks[6].outcome").value("MET"))
                .andExpect(jsonPath("$.commonCriteriaStatus").value("ELIGIBLE"))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"));
        for (var stale : List.of(new PolicyQuestions.Request(2, request.ruleVersion(), request.answers()),
                new PolicyQuestions.Request(1, "moving-fee-2026-h1-v1", request.answers()),
                new PolicyQuestions.Request(1, "moving-fee-2026-h1-v2", request.answers()),
                new PolicyQuestions.Request(1, "moving-fee-2026-h2-v1", request.answers()))) {
            mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(stale))).andExpect(status().isConflict());
        }
        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse("2026-12-31T15:00:00Z"));
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body)).andExpect(status().isConflict());
        org.mockito.Mockito.when(clock.instant()).thenReturn(AT);
        var current = store.find(MovingFeeRules.NUMBER).orElseThrow();
        store.importPolicy(MovingFeeRules.NUMBER, current.content(), "{}", AT.plusSeconds(1), "moving-change", "moving-new-hash");
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test @DisplayName("목록·상세·개인 조건에 같은 원문의 접수 상태를 제공하고 목록 조회 횟수를 유지한다")
    void exposesRecruitmentAcrossPolicyViews() throws Exception {
        item.put("aplyPrdSeCd", "0057001").put("aplyYmd", "20260901 ~ 20260912");
        for (var field : List.of("plcySprtCn", "plcyAplyMthdCn", "etcMttrCn", "addAplyQlfcCndCn", "srngMthdCn", "plcyExplnCn")) item.put(field, "안내");
        save("recruitment", AT);
        var number = item.path("plcyNo").asString();
        var body = mapper.writeValueAsString(new BasicConditions(java.time.LocalDate.parse("2000-01-01"), "강남구", BasicConditions.EmploymentStatus.NOT_EMPLOYED));
        org.mockito.Mockito.clearInvocations(jdbc);
        mvc.perform(get("/api/v1/policies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].recruitment.status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].recruitment.evaluatedAt").value(AT.toString()));
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(2)).sql(org.mockito.ArgumentMatchers.anyString());
        mvc.perform(get("/api/v1/policies/" + number)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recruitment.status").value("OPEN"));
        mvc.perform(post("/api/v1/policies/checks").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].recruitment.status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].status").value("NEEDS_REVIEW"));
        item.put("etcMttrCn", "예산 소진까지 신청");
        save("recruitment-change", AT.plusSeconds(1));
        mvc.perform(get("/api/v1/policies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].recruitment.status").value("UNKNOWN"));
        mvc.perform(get("/api/v1/policies/" + number)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recruitment.status").value("UNKNOWN"));
        mvc.perform(post("/api/v1/policies/checks").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].recruitment.status").value("UNKNOWN"));
    }

    @Test @DisplayName("접수 필터를 전체 검색에 적용한 뒤 페이지를 나누고 질문 필터·연령 정렬과 함께 사용한다")
    void filtersRecruitmentBeforePagination() throws Exception {
        for (var field : List.of("plcySprtCn", "plcyAplyMthdCn", "etcMttrCn", "addAplyQlfcCndCn", "srngMthdCn", "plcyExplnCn")) item.put(field, "안내");
        item.put("aplyPrdSeCd", "0057001").put("aplyYmd", "20260901 ~ 20260912");
        for (int i = 1; i <= 23; i++) {
            item.put("plcyNo", "90" + i).put("plcyNm", "필터 지원 " + i);
            save("open-" + i, AT.plusSeconds(i));
        }
        saveReviewed(WorkStudyRules.NUMBER, "필터 지원 장학금", WorkStudyRules.CONTENT_HASH);
        item.put("plcyNo", "991").put("aplyYmd", "20260801 ~ 20260831"); save("closed", AT.plusSeconds(30));
        item.put("plcyNo", "992").put("aplyPrdSeCd", "0057002").put("aplyYmd", ""); save("rolling", AT.plusSeconds(31));
        item.put("plcyNo", "993").put("aplyYmd", "20260901 ~ 20260912"); save("unknown", AT.plusSeconds(32));
        item.put("plcyNo", "994").put("aplyPrdSeCd", "0057001").put("aplyYmd", "20261001 ~ 20261012"); save("before", AT.plusSeconds(33));

        var seen = new java.util.HashSet<String>();
        for (int page = 1; page <= 3; page++) {
            org.mockito.Mockito.clearInvocations(jdbc);
            var result = store.list("필터 지원", page, 10, false, kr.youthpolicymate.policy.RecruitmentStatus.OPEN, AT);
            org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(2)).sql(org.mockito.ArgumentMatchers.anyString());
            assertThat(result.total()).isEqualTo(24);
            assertThat(result.hasNext()).isEqualTo(page < 3);
            assertThat(result.items()).hasSize(page < 3 ? 10 : 4).allSatisfy(policy -> {
                assertThat(policy.recruitment().status()).isEqualTo(kr.youthpolicymate.policy.RecruitmentStatus.OPEN);
                assertThat(seen.add(policy.policyNumber())).isTrue();
            });
        }
        mvc.perform(get("/api/v1/policies").param("recruitmentStatus", "OPEN").param("q", "장학금").param("questionsOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].policyNumber").value(WorkStudyRules.NUMBER));
        for (var state : List.of("CLOSED", "ROLLING", "UNKNOWN", "BEFORE_OPENING")) {
            mvc.perform(get("/api/v1/policies").param("recruitmentStatus", state)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].recruitment.status").value(state));
        }
        mvc.perform(get("/api/v1/policies").param("recruitmentStatus", "UNTIL_EXHAUSTED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        var body = mapper.writeValueAsString(new BasicConditions(java.time.LocalDate.parse("2000-01-01"), "강남구", BasicConditions.EmploymentStatus.NOT_EMPLOYED));
        mvc.perform(post("/api/v1/policies/checks").param("recruitmentStatus", "OPEN").param("page", "2")
                        .param("q", "필터 지원").param("sort", "RECENT").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(24)).andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.items[0].recruitment.status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].status").value("NEEDS_REVIEW"));
        mvc.perform(get("/api/v1/policies").param("recruitmentStatus", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/policies/checks").param("recruitmentStatus", "INVALID")
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }

    @Test @DisplayName("필터와 표시가 서울 자정·정확한 접수 시각 경계에서 함께 바뀌며 원문 변경을 반영한다")
    void keepsFilterAndDisplayConsistentAtBoundaries() {
        for (var field : List.of("plcySprtCn", "plcyAplyMthdCn", "etcMttrCn", "addAplyQlfcCndCn", "srngMthdCn", "plcyExplnCn")) item.put(field, "안내");
        item.put("aplyPrdSeCd", "0057001").put("aplyYmd", "20260906 ~ 20260907");
        save("dates", AT);
        for (var time : List.of("2026-09-05T14:59:59.999999Z", "2026-09-05T15:00:00Z", "2026-09-07T14:59:59.999999Z", "2026-09-07T15:00:00Z")) {
            var now = Instant.parse(time);
            var expected = PolicyRecruitment.from(NUMBER, 1, "", item, now).status();
            assertThat(store.list("", 1, 20, false, expected, now).items()).singleElement()
                    .satisfies(policy -> assertThat(policy.recruitment().status()).isEqualTo(expected));
        }
        saveReviewed(MovingFeeRules.NUMBER, "이사비", MovingFeeRules.CONTENT_HASH);
        for (var now : List.of(MovingFeeRules.OPEN_AT.minusNanos(1000), MovingFeeRules.OPEN_AT,
                MovingFeeRules.CLOSE_AT.minusNanos(1000), MovingFeeRules.CLOSE_AT)) {
            var expected = PolicyRecruitment.from(MovingFeeRules.NUMBER, 1, MovingFeeRules.CONTENT_HASH, item, now).status();
            assertThat(store.list("이사비", 1, 20, true, expected, now).items()).singleElement()
                    .satisfies(policy -> assertThat(policy.recruitment().status()).isEqualTo(expected));
        }
        var current = store.find(MovingFeeRules.NUMBER).orElseThrow();
        store.importPolicy(MovingFeeRules.NUMBER, current.content(), "{}", AT.plusSeconds(1), "changed-window", "changed-window");
        assertThat(store.list("이사비", 1, 20, false, kr.youthpolicymate.policy.RecruitmentStatus.UNKNOWN, AT).total()).isOne();
        assertThat(store.list("이사비", 1, 20, false, kr.youthpolicymate.policy.RecruitmentStatus.CLOSED, AT).total()).isZero();
    }

    @Test @DisplayName("기존 정책을 삭제하거나 개정을 늘리지 않고 접수 검색 기간을 이전한다")
    void backfillsRecruitmentForExistingPolicies() throws Exception {
        var config = org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas("recruitment_backfill");
        config.target("17").load().migrate();
        try (var connection = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setSchema("recruitment_backfill");
            var isolated = JdbcClient.create(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
            var normalized = parser.item(item);
            isolated.sql("INSERT INTO policies(policy_number, current_revision, content_hash, content, last_collected_at) VALUES (:number, 1, :hash, CAST(:content AS jsonb), :at)")
                    .param("number", MovingFeeRules.NUMBER).param("hash", MovingFeeRules.CONTENT_HASH)
                    .param("content", mapper.writeValueAsString(normalized.content())).param("at", AT.atOffset(java.time.ZoneOffset.UTC)).update();
            var id = isolated.sql("INSERT INTO policy_source_snapshots(policy_number, capture_hash, captured_at, raw_policy) VALUES (:number, 'old', :at, CAST(:raw AS jsonb)) RETURNING id")
                    .param("number", MovingFeeRules.NUMBER).param("at", AT.atOffset(java.time.ZoneOffset.UTC)).param("raw", normalized.rawPolicy()).query(Long.class).single();
            isolated.sql("INSERT INTO policy_revisions(policy_number, revision, source_snapshot_id, content) SELECT policy_number, 1, :id, content FROM policies")
                    .param("id", id).update();
            config.target("latest").load().migrate();
            var migrated = new PolicyCatalogStore(isolated, mapper, java.time.Clock.fixed(AT, java.time.ZoneOffset.UTC), new PolicyCorrectionStore(isolated, mapper));
            assertThat(migrated.list("", 1, 20, false, kr.youthpolicymate.policy.RecruitmentStatus.CLOSED, AT).items()).singleElement()
                    .satisfies(policy -> assertThat(policy.recruitment().status()).isEqualTo(kr.youthpolicymate.policy.RecruitmentStatus.CLOSED));
            assertThat(migrated.find(MovingFeeRules.NUMBER).orElseThrow().revision()).isOne();
        }
    }

    @Test @DisplayName("청약통장 연령을 검색·정렬·접수 필터에 연결하고 원문·검토 연도 변경 시 중단한다")
    void comparesHousingAgeInBasicConditions() throws Exception {
        item.put("aplyPrdSeCd", "0057002").put("aplyYmd", "");
        saveReviewed(YouthHousingSavingsRules.NUMBER, "청년주택드림청약통장", YouthHousingSavingsRules.CONTENT_HASH);
        item.put("plcyNo", "99881").put("plcyNm", "연령 미검토 정책");
        save("housing-age-order", AT.plusSeconds(1));
        var input = new BasicConditions(java.time.LocalDate.parse("2000-01-01"), "강남구", BasicConditions.EmploymentStatus.NOT_EMPLOYED);
        org.mockito.Mockito.clearInvocations(jdbc);
        var result = checks.check(input, 1, "", PolicyCheckResponse.Sort.AGE_MATCH, kr.youthpolicymate.policy.RecruitmentStatus.ROLLING);
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(2)).sql(org.mockito.ArgumentMatchers.anyString());
        var housing = result.items().getFirst();
        assertThat(housing.policyNumber()).isEqualTo(YouthHousingSavingsRules.NUMBER);
        assertThat(housing.ruleVersion()).isEqualTo(YouthHousingSavingsRules.VERSION);
        assertThat(housing.sourceUrl()).isEqualTo(YouthHousingSavingsRules.SOURCE);
        assertThat(housing.explanation()).contains("오늘(서울 날짜) 가입", "실제 가입일");
        assertThat(housing.checks()).extracting(PolicyCheckResponse.Check::outcome).containsExactly(
                kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.MET,
                kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN,
                kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN,
                kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN);
        mvc.perform(post("/api/v1/policies/checks").param("q", "청약").param("recruitmentStatus", "ROLLING")
                        .contentType("application/json").content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.items[0].checks[0].providedValue").value("만 26세 (2026-09-05 · 서울)"));
        var military = new BasicConditions(java.time.LocalDate.parse("1990-01-01"), input.district(), input.employmentStatus());
        assertThat(checks.check(military, 1, "청약", PolicyCheckResponse.Sort.AGE_MATCH, null).items().getFirst().explanation())
                .contains("병역기간 차감", "실제 가입일");

        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse("2026-12-31T15:00:00Z"));
        var expired = checks.check(input, 1, "청약", PolicyCheckResponse.Sort.AGE_MATCH, null).items().getFirst();
        assertThat(expired.ruleVersion()).isEmpty();
        assertThat(expired.checks().getFirst().outcome()).isEqualTo(kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN);
        org.mockito.Mockito.when(clock.instant()).thenReturn(AT);
        var current = store.find(YouthHousingSavingsRules.NUMBER).orElseThrow();
        store.importPolicy(YouthHousingSavingsRules.NUMBER, current.content(), "{}", AT.plusSeconds(2), "housing-age-changed", "changed-housing-age");
        var changed = checks.check(input, 1, "청약", PolicyCheckResponse.Sort.AGE_MATCH, null).items().getFirst();
        assertThat(changed.ruleVersion()).isEmpty();
        assertThat(changed.checks().getFirst().outcome()).isEqualTo(kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN);
        assertThat(changed.questionnaireAvailable()).isFalse();
    }

    private void saveReviewed(String number, String title, String hash) {
        // 인공 본문과 검토 해시로 조회·질문 연결만 검사한다. 공식 조건의 정확성 검사가 아니다.
        var source = item.deepCopy();
        source.put("plcyNo", number).put("plcyNm", title);
        var normalized = parser.item(source);
        store.importPolicy(number, normalized.content(), normalized.rawPolicy(), AT, "reviewed-" + number, hash);
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
