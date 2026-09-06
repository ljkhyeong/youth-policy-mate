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
        "springdoc.api-docs.version=OPENAPI_3_1", "springdoc.packages-to-scan=kr.youthpolicymate.policy.catalog,kr.youthpolicymate.member",
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
        assertThat(actual.path("paths").size()).isEqualTo(15);
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
            var response = checks.check(input, page);
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
