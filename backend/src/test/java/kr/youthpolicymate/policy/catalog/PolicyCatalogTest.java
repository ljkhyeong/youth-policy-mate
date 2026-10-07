package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.ingestion.OntongFixtures;
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
import static kr.youthpolicymate.policy.catalog.PolicyRuleFixtures.*;

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
    @Autowired PolicyRuleStore rules;
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
        jdbc.sql("UPDATE policy_rule_heads SET version_id = 'ba390000-0000-4000-8000-000000000001' WHERE policy_number = '20260527005400113224'").update();
        jdbc.sql("DELETE FROM policy_rule_versions WHERE created_by = 'rule-test'").update();
        jdbc.sql("DELETE FROM policy_revisions").update();
        jdbc.sql("DELETE FROM policy_corrections").update();
        jdbc.sql("DELETE FROM policy_source_snapshots").update();
        jdbc.sql("DELETE FROM policies").update();
        parser = new OntongPolicyCapture(mapper);
        item = (ObjectNode) parser.parseResponse(OntongFixtures.listBody(mapper), AT).items().getFirst();
    }

    @Test @DisplayName("새 연도 기준을 데이터로 적용하면 서버 재시작 없이 질문·정렬·자동 답변이 함께 바뀐다")
    void publishesNextYearWithoutCodeChange() throws Exception {
        saveReviewed(EXAM_FEE, "응시료 지원", hash(EXAM_FEE));
        var old = questions.questions(EXAM_FEE);
        var json = (ObjectNode) mapper.readTree(mapper.writeValueAsString(exam())
                .replace("1991", "1992").replace("1990", "1991").replace("2026년", "2027년"));
        json.put("ruleVersion", "exam-2027-test").put("validFrom", "2026-12-31T15:00:00Z").put("validUntil", "2027-12-31T15:00:00Z");
        json.put("scope", "2027년 검증용 공고");
        ((ObjectNode) json.get("birthBinding")).put("minimumInclusive", "1992-01-01");
        var next = mapper.treeToValue(json, PolicyRuleDefinition.class);
        var id = rules.draft(next, "rule-test", "연도 변경 동작 검증");
        assertThatThrownBy(() -> rules.publish(id, old.ruleVersion(), "rule-test")).isInstanceOf(IllegalStateException.class).hasMessageContaining("기간");
        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse("2027-01-01T00:00:00Z"));
        assertThat(questions.questions(EXAM_FEE).available()).isFalse();
        assertThat(rules.status()).anySatisfy(state -> { assertThat(state.ruleVersion()).isEqualTo(old.ruleVersion()); assertThat(state.state()).contains("만료"); });
        rules.publish(id, old.ruleVersion(), "rule-test");
        assertThat(questions.questions(EXAM_FEE).scope()).isEqualTo("2027년 검증용 공고");
        assertThatThrownBy(() -> questions.evaluate(EXAM_FEE, new PolicyQuestions.Request(1, old.ruleVersion(), List.of())))
                .isInstanceOf(PolicyQuestionService.PolicyChangedException.class);
        var birth = java.time.LocalDate.parse("1991-12-31");
        var prefill = questions.prefill(EXAM_FEE, new PolicyQuestions.PrefillRequest(1, next.ruleVersion(), birth));
        var evaluated = questions.evaluate(EXAM_FEE, new PolicyQuestions.Request(1, next.ruleVersion(), prefill.answers()));
        var compared = checks.check(new BasicConditions(birth, "강남구", BasicConditions.EmploymentStatus.OTHER), 1, "", PolicyCheckResponse.Sort.AGE_MATCH, null);
        assertThat(evaluated.checks().getFirst().outcome()).isEqualTo(kr.youthpolicymate.eligibility.ConditionOutcome.NOT_MET);
        assertThat(compared.items().getFirst().checks().getFirst().outcome()).isEqualTo(evaluated.checks().getFirst().outcome());
        assertThat(compared.items().getFirst().ruleVersion()).isEqualTo(next.ruleVersion());
        assertThat(store.list("", 1, 20, true, null, clock.instant()).total()).isOne();
        assertThatThrownBy(() -> rules.publish(id, next.ruleVersion(), "rule-test")).hasMessageContaining("이미 적용");
        assertThatThrownBy(() -> jdbc.sql("UPDATE policy_rule_versions SET definition = '{}'::jsonb WHERE id = :id").param("id", id).update())
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test @DisplayName("같은 직전 버전을 대상으로 동시에 적용하면 하나만 성공하고 원문 변경 시 적용·제출을 차단한다")
    void protectsRulePublication() throws Exception {
        saveReviewed(EXAM_FEE, "응시료 지원", hash(EXAM_FEE));
        var json = (ObjectNode) mapper.valueToTree(exam());
        var ids = new java.util.ArrayList<java.util.UUID>();
        for (int i = 0; i < 2; i++) {
            json.put("ruleVersion", "concurrent-" + i);
            ids.add(rules.draft(mapper.treeToValue(json, PolicyRuleDefinition.class), "rule-test", "동시 적용 검증"));
        }
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = ids.stream().map(id -> executor.submit(() -> {
                gate.await();
                try { rules.publish(id, version(EXAM_FEE), "rule-test"); return true; }
                catch (IllegalStateException changed) { return false; }
            })).toList();
            gate.countDown();
            int successes = 0;
            for (var future : futures) if (future.get(10, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isOne();
        }
        var current = questions.questions(EXAM_FEE);
        jdbc.sql("UPDATE policies SET content_hash = repeat('a', 64) WHERE policy_number = :number").param("number", EXAM_FEE).update();
        assertThat(questions.questions(EXAM_FEE).available()).isFalse();
        assertThat(rules.status()).anySatisfy(state -> { assertThat(state.ruleVersion()).isEqualTo(current.ruleVersion()); assertThat(state.state()).contains("원문 변경"); });
        assertThatThrownBy(() -> questions.prefill(EXAM_FEE, new PolicyQuestions.PrefillRequest(1, current.ruleVersion(), java.time.LocalDate.parse("2000-01-01"))))
                .isInstanceOf(PolicyQuestionService.PolicyChangedException.class);
        assertThatThrownBy(() -> rules.publish(ids.getFirst(), current.ruleVersion(), "rule-test")).hasMessageContaining("원문이 바뀌");
        assertThat(store.list("", 1, 20, true, null, AT).total()).isZero();
    }

    @Test @DisplayName("출생일 답변 API는 로그인 없이 사용하고 미래 날짜·오래된 버전을 거부하며 저장하지 않는다")
    void prefillsBirthWithVersionGuard() throws Exception {
        saveReviewed(EXAM_FEE, "응시료 지원", hash(EXAM_FEE));
        var path = "/api/v1/policies/" + EXAM_FEE + "/question-prefill";
        var body = mapper.createObjectNode().put("revision", 1).put("ruleVersion", version(EXAM_FEE)).put("birthDate", "1991-01-01");
        mvc.perform(post(path).contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.answers[0].value").value("ON_OR_AFTER_1991_01_01"));
        body.put("birthDate", "2027-01-01");
        mvc.perform(post(path).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isBadRequest());
        body.put("birthDate", "2000-01-01").put("ruleVersion", "old");
        mvc.perform(post(path).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("질문과 답변 평가는 각각 SELECT 한 번으로 현재 개정과 해시를 확인한다")
    void loadsQuestionVersionWithOneQuery() {
        saveReviewed(EXAM_FEE, "응시료 지원", hash(EXAM_FEE));

        org.mockito.Mockito.clearInvocations(jdbc);
        var questionnaire = questions.questions(EXAM_FEE);
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(1)).sql(org.mockito.ArgumentMatchers.anyString());
        assertThat(questionnaire.available()).isTrue();
        assertThat(questionnaire.revision()).isOne();

        org.mockito.Mockito.clearInvocations(jdbc);
        var evaluation = questions.evaluate(EXAM_FEE,
                new PolicyQuestions.Request(questionnaire.revision(), questionnaire.ruleVersion(), List.of()));
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(1)).sql(org.mockito.ArgumentMatchers.anyString());
        assertThat(evaluation.revision()).isOne();
    }

    @Test @DisplayName("12개 검토 정책이 DB 규칙으로 질문을 제공하고 생년월일로 답할 수 있는 10개 연령 항목을 연결한다")
    void servesAllDataRules() {
        int prefills = 0;
        var birth = java.time.LocalDate.parse("1992-02-29");
        for (var definition : DEFINITIONS.values()) {
            saveReviewed(definition.policyNumber(), definition.scope(), definition.contentHash());
            var questionnaire = questions.questions(definition.policyNumber());
            assertThat(questionnaire.available()).isTrue();
            assertThat(questionnaire.ruleVersion()).isEqualTo(definition.versionAt(AT));
            var filled = questions.prefill(definition.policyNumber(), new PolicyQuestions.PrefillRequest(1, questionnaire.ruleVersion(), birth));
            if (!filled.answers().isEmpty()) {
                prefills++;
                var response = questions.evaluate(definition.policyNumber(), new PolicyQuestions.Request(1, questionnaire.ruleVersion(), filled.answers()));
                var expected = definition.compareBirth(birth, AT).age();
                assertThat(response.checks().getFirst().outcome()).isEqualTo(expected.outcome());
            }
        }
        assertThat(prefills).isEqualTo(10);
        assertThat(store.list("", 1, 20, true, null, AT).total()).isEqualTo(12);
        // 기본 조건의 연령 항목도 같은 규칙 데이터로 비교하고, 연령 연결이 없는 규칙은 판정하지 않는다.
        var compared = checks.check(new BasicConditions(birth, null, null), 1, "", PolicyCheckResponse.Sort.RECENT, null).items();
        assertThat(compared).hasSize(DEFINITIONS.size()).allSatisfy(checked -> {
            var definition = rule(checked.policyNumber());
            var expected = definition.compareBirth(birth, AT);
            assertThat(checked.questionnaireAvailable()).isTrue();
            assertThat(checked.ruleVersion()).isEqualTo(expected == null ? "" : definition.versionAt(AT));
            assertThat(checked.checks().getFirst().label()).isEqualTo("연령");
            assertThat(checked.checks().getFirst().outcome()).isEqualTo(expected == null
                    ? kr.youthpolicymate.eligibility.ConditionOutcome.UNKNOWN : expected.age().outcome());
            if (expected == null) return;
            // 미확인이면 규칙 항목 설명을, 충족·불충족이면 공통 안내를 쓰고 기간·연령 안내를 뒤에 붙인다.
            assertThat(checked.explanation()).startsWith(expected.age().outcome() == kr.youthpolicymate.eligibility.ConditionOutcome.UNKNOWN
                    ? expected.age().explanation() : "입력한 생년월일은").endsWith(expected.periodNotice());
        });
    }

    @Test @org.springframework.transaction.annotation.Transactional
    @DisplayName("월별 정책의 연령 기준을 데이터로 변경하면 새 질문 버전과 연령 답변에 즉시 반영한다")
    void publishesAgeAndMonthlyRuleWithoutRestart() {
        saveReviewed(K_PASS, "K-패스", hash(K_PASS));
        var original = rule(K_PASS);
        var json = (ObjectNode) mapper.valueToTree(original);
        json.put("ruleVersion", "k-pass-age-test");
        ((ObjectNode) json.get("ageBinding")).put("minimumInclusive", 20);
        var next = mapper.treeToValue(json, PolicyRuleDefinition.class);
        var id = rules.draft(next, "rule-test", "공통 연령 비교의 데이터 변경 검증");
        rules.publish(id, original.ruleVersion(), "rule-test");
        var birth = java.time.LocalDate.parse("2007-01-01");
        var current = questions.questions(K_PASS);
        assertThat(current.ruleVersion()).isEqualTo("k-pass-age-test-2026-09");
        assertThatThrownBy(() -> questions.evaluate(K_PASS, new PolicyQuestions.Request(1, original.versionAt(AT), List.of())))
                .isInstanceOf(PolicyQuestionService.PolicyChangedException.class);
        var answers = questions.prefill(K_PASS, new PolicyQuestions.PrefillRequest(1, current.ruleVersion(), birth)).answers();
        assertThat(questions.evaluate(K_PASS, new PolicyQuestions.Request(1, current.ruleVersion(), answers)).checks().getFirst().outcome())
                .isEqualTo(kr.youthpolicymate.eligibility.ConditionOutcome.NOT_MET);
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
        assertThat(importAt(normalized.number(), normalized.content(), normalized.rawPolicy(), AT,
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
    @DisplayName("충돌 안내는 검토한 정책 내용에만 붙이고 원문을 유지한다")
    void exposesReviewedSourceNoticeWithoutChangingOriginal() throws Exception {
        var number = YOUTH_TOMORROW_SAVINGS;
        item.put("addAplyQlfcCndCn", "가구 소득인정액 기준 중위소득 100% 이하");
        item.put("earnEtcCn", "가구 소득인정액 기준 중위소득 50% 이하");
        saveReviewed(number, "청년내일저축계좌", hash(YOUTH_TOMORROW_SAVINGS));
        var original = store.source(number).orElseThrow();
        var content = store.find(number).orElseThrow().content();

        mvc.perform(get("/api/v1/policies/" + number)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceNotices.length()").value(2))
                .andExpect(jsonPath("$.sourceNotices[0].title").value("소득 기준 확인 필요"))
                .andExpect(jsonPath("$.sourceNotices[0].sourceUrl")
                        .value("https://www.bokjiro.go.kr/ssis-tbu/cms/pc/customer/notice/1309680_1141.html"))
                .andExpect(jsonPath("$.sourceNotices[1].title").value("출생일 기준 확인 필요"));
        assertThat(store.source(number).orElseThrow()).isEqualTo(original);
        assertThat(store.find(number).orElseThrow().content()).isEqualTo(content);
        assertThat(content.sections()).extracting(PolicyContent.TextSection::text)
                .contains("가구 소득인정액 기준 중위소득 100% 이하", "가구 소득인정액 기준 중위소득 50% 이하");

        item.put("plcyNo", number).put("earnEtcCn", "소득 기준이 변경된 안내");
        save("changed-income", AT.plusSeconds(1));
        mvc.perform(get("/api/v1/policies/" + number)).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.sourceNotices").isEmpty());

        saveReviewed(NUMBER, "다른 정책", hash(YOUTH_TOMORROW_SAVINGS));
        mvc.perform(get("/api/v1/policies/" + NUMBER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceNotices").isEmpty());

        saveReviewed(FUTURE_YOUTH_JOBS, "미래 청년 일자리", hash(FUTURE_YOUTH_JOBS));
        mvc.perform(get("/api/v1/policies/" + FUTURE_YOUTH_JOBS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceNotices.length()").value(3))
                .andExpect(jsonPath("$.sourceNotices[0].sourceUrl").value(rule(FUTURE_YOUTH_JOBS).sourceUrl()));
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
        item.put("plcyNo", WORK_STUDY);
        var normalized = parser.item(item);
        importAt(normalized.number(), normalized.content(), normalized.rawPolicy(), AT, "reviewed", hash(WORK_STUDY));
        var path = "/api/v1/policies/" + WORK_STUDY;
        mvc.perform(get(path + "/questions")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.questions.length()").value(6));
        var request = new PolicyQuestions.Request(1, version(WORK_STUDY), List.of());
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.commonCriteriaStatus").value("NEEDS_REVIEW"));
        for (var stale : List.of(new PolicyQuestions.Request(2, version(WORK_STUDY), List.of()),
                new PolicyQuestions.Request(1, "old-rules", List.of()))) {
            mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(stale)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POLICY_CHANGED"));
        }
        importAt(normalized.number(), normalized.content(), normalized.rawPolicy(), AT.plusSeconds(1), "changed", "changed-content");
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
        var path = "/api/v1/policies/" + WORK_STUDY;
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
        item.put("plcyNo", EXAM_FEE);
        var normalized = parser.item(item);
        importAt(normalized.number(), normalized.content(), normalized.rawPolicy(), AT, "exam-reviewed", hash(EXAM_FEE));
        String path = "/api/v1/policies/" + EXAM_FEE;
        mvc.perform(get(path + "/questions")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.questions.length()").value(3))
                .andExpect(jsonPath("$.questions[0].id").value("birthRange"));
        var input = new PolicyQuestions.Request(1, version(EXAM_FEE), List.of(
                new PolicyQuestions.Answer("birthRange", "ON_OR_AFTER_1991_01_01"),
                new PolicyQuestions.Answer("exam", "HRDK_TECHNICAL"), new PolicyQuestions.Answer("remainingUses", "ONE")));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.policyNumber").value(EXAM_FEE))
                .andExpect(jsonPath("$.ruleVersion").value(version(EXAM_FEE)))
                .andExpect(jsonPath("$.commonCriteriaStatus").value("ELIGIBLE"))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"));
        var otherRule = new PolicyQuestions.Request(1, version(WORK_STUDY), input.answers());
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(otherRule))).andExpect(status().isConflict());
        var otherAnswer = new PolicyQuestions.Request(1, version(EXAM_FEE), List.of(new PolicyQuestions.Answer("nationality", "YES")));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(otherAnswer))).andExpect(status().isBadRequest());
        importAt(normalized.number(), normalized.content(), normalized.rawPolicy(), AT.plusSeconds(1), "exam-updated", "changed-content");
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(mapper.writeValueAsString(input))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("서울 기준 새해가 되면 이전 연도의 응시료 질문과 제출을 중단한다")
    void stopsExamFeeQuestionsAcrossYearBoundary() throws Exception {
        item.put("plcyNo", EXAM_FEE);
        var normalized = parser.item(item);
        importAt(normalized.number(), normalized.content(), normalized.rawPolicy(), AT, "exam-reviewed", hash(EXAM_FEE));
        String path = "/api/v1/policies/" + EXAM_FEE;
        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse("2026-12-31T14:59:59Z"));
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        // 비교 시각을 한 번만 읽는다. 질문 검사와 결과가 자정 양쪽으로 나뉘지 않아야 한다.
        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse("2026-12-31T14:59:59Z"), Instant.parse("2026-12-31T15:00:00Z"));
        String body = mapper.writeValueAsString(new PolicyQuestions.Request(1, version(EXAM_FEE), List.of()));
        mvc.perform(post(path + "/evaluation").contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluatedAt").value("2026-12-31T14:59:59Z"));
        mvc.perform(get(path + "/questions")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("적용 기간")));
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
        saveReviewed(WORK_STUDY, "국가근로 지원", hash(WORK_STUDY));
        saveReviewed(EXAM_FEE, "응시료 지원", hash(EXAM_FEE));
        mvc.perform(get("/api/v1/policies").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].policyNumber").value(NUMBER))
                .andExpect(jsonPath("$.items[0].questionnaireAvailable").value(false));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items[0].policyNumber").value(EXAM_FEE))
                .andExpect(jsonPath("$.items[0].questionnaireAvailable").value(true));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true").param("pageSize", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items[0].policyNumber").value(WORK_STUDY));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true").param("pageSize", "1").param("page", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true").param("q", "국가근로"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].policyNumber").value(WORK_STUDY));
        mvc.perform(get("/api/v1/policies").param("questionsOnly", "true").param("q", "없는검색어"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("조건 확인은 규칙 조회 한 번과 SELECT 두 번으로 정렬·페이지·현재 개정의 원문을 반환한다")
    void loadsConditionPageWithThreeQueries() {
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
            org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(3)).sql(org.mockito.ArgumentMatchers.anyString());
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

    @Test @DisplayName("온통청년 표기 조건은 상세에 참고로만 보여주고 검토 기준이 없는 연령은 표기 범위와 만 나이만 함께 보여준다")
    void showsStatedSourceConditionsWithoutJudging() throws Exception {
        item.put("plcyNo", "971").put("sprtTrgtAgeLmtYn", "N").put("sprtTrgtMinAge", "19").put("sprtTrgtMaxAge", "34")
                .put("earnCndSeCd", "0043002").put("earnMaxAmt", "3500").put("jobCd", "0013003").put("schoolCd", "0049010");
        save("stated", AT);
        mvc.perform(get("/api/v1/policies/971")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceConditions[*].label").value(org.hamcrest.Matchers.contains("연령", "소득", "취업 상태")))
                .andExpect(jsonPath("$.sourceConditions[*].value").value(org.hamcrest.Matchers.contains("만 19~34세", "연소득 3,500만 원 이하", "미취업자")));
        var body = mapper.writeValueAsString(new BasicConditions(java.time.LocalDate.of(1995, 3, 1), null, null));
        mvc.perform(post("/api/v1/policies/checks").contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].explanation").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("검토한 연령 기준은 아직 없어요"), org.hamcrest.Matchers.containsString("만 19~34세"),
                        org.hamcrest.Matchers.containsString("만 31세"))))
                .andExpect(jsonPath("$.items[0].status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.items[0].checks[0].providedValue").value("만 31세 (2026-09-05 · 서울)"))
                .andExpect(jsonPath("$.items[0].checks[0].explanation").value(org.hamcrest.Matchers.containsString("만 19~34세")))
                .andExpect(jsonPath("$.items[0].checks[0].outcome").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[0].checks[2].explanation").value(org.hamcrest.Matchers.containsString("소득 연소득 3,500만 원 이하")));
        // 만 나이는 서울 날짜로 계산하고, 생년월일이 없어도 표기 범위와 기준일·예외 확인을 함께 안내한다.
        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse("2026-02-28T14:59:59Z"), Instant.parse("2026-02-28T15:00:00Z"));
        mvc.perform(post("/api/v1/policies/checks").contentType("application/json").content(body))
                .andExpect(jsonPath("$.items[0].checks[0].providedValue").value("만 30세 (2026-02-28 · 서울)"));
        mvc.perform(post("/api/v1/policies/checks").contentType("application/json").content(body))
                .andExpect(jsonPath("$.items[0].checks[0].providedValue").value("만 31세 (2026-03-01 · 서울)"));
        org.mockito.Mockito.when(clock.instant()).thenReturn(AT);
        mvc.perform(post("/api/v1/policies/checks").contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.items[0].checks[0].providedValue").value("미입력"))
                .andExpect(jsonPath("$.items[0].checks[0].explanation").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("만 19~34세"), org.hamcrest.Matchers.containsString("기준일과 예외"))))
                .andExpect(jsonPath("$.items[0].checks[0].outcome").value("UNKNOWN"));
        // 검토한 연령 비교가 있으면 표기 범위 대신 그 결과를 사용한다.
        saveReviewed(EXAM_FEE, "응시료 지원", hash(EXAM_FEE));
        mvc.perform(post("/api/v1/policies/checks").contentType("application/json").content(body).param("q", "응시료"))
                .andExpect(jsonPath("$.items[0].ruleVersion").value(org.hamcrest.Matchers.not("")))
                .andExpect(jsonPath("$.items[0].checks[0].explanation").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("온통청년 표기"))));
        // 제한 없음·무관 표기는 조건이 없다는 근거로 보여주지 않는다.
        item.put("plcyNo", "972").put("sprtTrgtAgeLmtYn", "Y").put("sprtTrgtMinAge", "19").put("sprtTrgtMaxAge", "39")
                .put("earnCndSeCd", "0043001").put("jobCd", "0013010").put("schoolCd", "0049010");
        save("unrestricted", AT.plusSeconds(1));
        mvc.perform(get("/api/v1/policies/972")).andExpect(status().isOk()).andExpect(jsonPath("$.sourceConditions").isEmpty());
    }

    @Test
    @DisplayName("기본 조건 결과의 질문 제공 여부는 자격 상태와 분리하고 같은 비교 시각을 사용한다")
    void exposesQuestionsInConditionChecks() throws Exception {
        saveReviewed(EXAM_FEE, "응시료 지원", hash(EXAM_FEE));
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
    @DisplayName("서울 월말의 한 요청은 같은 달로 비교하고 다음 요청은 이전 월 답변을 거절한다")
    void fencesKPassAnswersAtMonthBoundary() throws Exception {
        saveReviewed(K_PASS, "K-패스", hash(K_PASS));
        var path = "/api/v1/policies/" + K_PASS;
        var before = Instant.parse("2026-09-30T14:59:59Z");
        var after = Instant.parse("2026-09-30T15:00:00Z");
        org.mockito.Mockito.when(clock.instant()).thenReturn(before, after);
        var body = mapper.writeValueAsString(new PolicyQuestions.Request(1, rule(K_PASS).versionAt(before), List.of()));
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
    @DisplayName("조건 없이 탐색하고 생년월일만 추가해 연령을 비교한다")
    void progressivelyComparesOptionalConditions() throws Exception {
        saveReviewed(EXAM_FEE, "응시료 지원", hash(EXAM_FEE));
        saveReviewed(K_PASS, "K-패스", hash(K_PASS));
        for (String body : List.of("{}", "{\"birthDate\":null,\"district\":null,\"employmentStatus\":null}")) {
            mvc.perform(post("/api/v1/policies/checks").contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.items[*].checks[*].outcome").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("UNKNOWN"))))
                    .andExpect(jsonPath("$.items[0].checks[0].providedValue").value("미입력"));
        }
        mvc.perform(post("/api/v1/policies/checks").contentType("application/json").content("{\"birthDate\":\"1990-12-31\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].policyNumber").value(K_PASS))
                .andExpect(jsonPath("$.items[0].checks[0].outcome").value("MET"))
                .andExpect(jsonPath("$.items[1].checks[0].outcome").value("NOT_MET"));
        for (String body : List.of("{\"birthDate\":\"9999-01-01\"}", "{\"district\":\"부산\"}", "{\"employmentStatus\":\"UNKNOWN\"}")) {
            mvc.perform(post("/api/v1/policies/checks").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test @DisplayName("전체 정책을 연령 충족·미확인·불충족 순으로 정렬한 뒤 검색과 페이지를 적용한다")
    void ranksConditionResultsBeforePagination() throws Exception {
        saveReviewed(EXAM_FEE, "응시료 지원", hash(EXAM_FEE));
        saveReviewed(K_PASS, "K-패스", hash(K_PASS));
        for (int index = 1; index <= 21; index++) {
            item.put("plcyNo", "900000000000000000%02d".formatted(index)).put("plcyNm", "미검토 정책 " + index);
            save("condition-order-" + index, AT.plusSeconds(1));
        }
        var input = new BasicConditions(java.time.LocalDate.parse("1990-12-31"), "강남구", BasicConditions.EmploymentStatus.NOT_EMPLOYED);
        org.mockito.Mockito.clearInvocations(jdbc);
        var first = checks.check(input, 1, "", PolicyCheckResponse.Sort.AGE_MATCH, null);
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(3)).sql(org.mockito.ArgumentMatchers.anyString());
        assertThat(first.total()).isEqualTo(23);
        assertThat(first.items().getFirst().policyNumber()).isEqualTo(K_PASS);
        assertThat(first.items().getFirst().checks().getFirst().outcome()).isEqualTo(kr.youthpolicymate.eligibility.ConditionOutcome.MET);
        assertThat(first.items().getFirst().ruleVersion()).isEqualTo(rule(K_PASS).versionAt(AT));
        var second = checks.check(input, 2, "", PolicyCheckResponse.Sort.AGE_MATCH, null);
        assertThat(second.items()).hasSize(3);
        assertThat(second.items().getLast().policyNumber()).isEqualTo(EXAM_FEE);
        assertThat(second.items().getLast().checks().getFirst().outcome()).isEqualTo(kr.youthpolicymate.eligibility.ConditionOutcome.NOT_MET);
        assertThat(first.items()).noneMatch(value -> second.items().stream().anyMatch(next -> value.policyNumber().equals(next.policyNumber())));
        var search = checks.check(input, 1, "응시료", PolicyCheckResponse.Sort.AGE_MATCH, null);
        assertThat(search.total()).isEqualTo(1);
        assertThat(search.items().getFirst().policyNumber()).isEqualTo(EXAM_FEE);
        assertThat(checks.check(input, 1, "%_", PolicyCheckResponse.Sort.AGE_MATCH, null).items()).isEmpty();
        assertThat(checks.check(input, 1, "", PolicyCheckResponse.Sort.RECENT, null).items().getFirst().title()).isEqualTo("미검토 정책 1");
        var body = mapper.writeValueAsString(input);
        mvc.perform(post("/api/v1/policies/checks").param("q", " 응시료 ").param("sort", "RECENT").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].checks[0].outcome").value("NOT_MET"));
        mvc.perform(post("/api/v1/policies/checks").param("q", "가".repeat(81)).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/policies/checks").param("sort", "SCORE").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        var current = store.find(K_PASS).orElseThrow();
        importAt(K_PASS, current.content(), "{}", AT.plusSeconds(2), "changed-basic", "changed-basic-hash");
        var changed = checks.check(input, 1, "K-패스", PolicyCheckResponse.Sort.AGE_MATCH, null).items().getFirst();
        assertThat(changed.checks().getFirst().outcome()).isEqualTo(kr.youthpolicymate.eligibility.ConditionOutcome.UNKNOWN);
        assertThat(changed.ruleVersion()).isEmpty();
        assertThat(changed.questionnaireAvailable()).isFalse();
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
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(3)).sql(org.mockito.ArgumentMatchers.anyString());
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
        saveReviewed(WORK_STUDY, "필터 지원 장학금", hash(WORK_STUDY));
        item.put("plcyNo", "991").put("aplyYmd", "20260801 ~ 20260831"); save("closed", AT.plusSeconds(30));
        item.put("plcyNo", "992").put("aplyPrdSeCd", "0057002").put("aplyYmd", ""); save("rolling", AT.plusSeconds(31));
        item.put("plcyNo", "993").put("aplyYmd", "20260901 ~ 20260912"); save("unknown", AT.plusSeconds(32));
        item.put("plcyNo", "994").put("aplyPrdSeCd", "0057001").put("aplyYmd", "20261001 ~ 20261012"); save("before", AT.plusSeconds(33));

        var seen = new java.util.HashSet<String>();
        for (int page = 1; page <= 3; page++) {
            org.mockito.Mockito.clearInvocations(jdbc);
            var result = store.list("필터 지원", page, 10, false, kr.youthpolicymate.policy.RecruitmentStatus.OPEN, AT);
            org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(3)).sql(org.mockito.ArgumentMatchers.anyString());
            assertThat(result.total()).isEqualTo(24);
            assertThat(result.hasNext()).isEqualTo(page < 3);
            assertThat(result.items()).hasSize(page < 3 ? 10 : 4).allSatisfy(policy -> {
                assertThat(policy.recruitment().status()).isEqualTo(kr.youthpolicymate.policy.RecruitmentStatus.OPEN);
                assertThat(seen.add(policy.policyNumber())).isTrue();
            });
        }
        mvc.perform(get("/api/v1/policies").param("recruitmentStatus", "OPEN").param("q", "장학금").param("questionsOnly", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].policyNumber").value(WORK_STUDY));
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
        saveReviewed(MOVING_FEE, "이사비", hash(MOVING_FEE));
        for (var now : List.of(rule(MOVING_FEE).periodNotice().opensAt().minusNanos(1000), rule(MOVING_FEE).periodNotice().opensAt(),
                rule(MOVING_FEE).periodNotice().closesAt().minusNanos(1000), rule(MOVING_FEE).periodNotice().closesAt())) {
            var expected = PolicyRecruitment.from(MOVING_FEE, 1, hash(MOVING_FEE), item, now).status();
            assertThat(store.list("이사비", 1, 20, true, expected, now).items()).singleElement()
                    .satisfies(policy -> assertThat(policy.recruitment().status()).isEqualTo(expected));
        }
        var current = store.find(MOVING_FEE).orElseThrow();
        importAt(MOVING_FEE, current.content(), "{}", AT.plusSeconds(1), "changed-window", "changed-window");
        assertThat(store.list("이사비", 1, 20, false, kr.youthpolicymate.policy.RecruitmentStatus.UNKNOWN, AT).total()).isOne();
        assertThat(store.list("이사비", 1, 20, false, kr.youthpolicymate.policy.RecruitmentStatus.CLOSED, AT).total()).isZero();
    }

    @Test @DisplayName("공개 목록은 분야로 좁히고 접수 중인 정책을 마감 임박순으로 먼저 보여준다")
    void ordersByAvailabilityAndFiltersCategory() throws Exception {
        item.put("lclsfNm", "일자리").put("aplyPrdSeCd", "0057001");
        item.put("plcyNo", "981").put("aplyYmd", "20260801 ~ 20260831"); save("closed", AT.plusSeconds(1));
        item.put("plcyNo", "982").put("aplyYmd", "20260901 ~ 20260930"); save("open-late", AT.plusSeconds(2));
        item.put("plcyNo", "983").put("aplyPrdSeCd", "0057002").put("aplyYmd", ""); save("rolling", AT.plusSeconds(3));
        item.put("plcyNo", "984").put("aplyPrdSeCd", "0057001").put("aplyYmd", "20260901 ~ 20260912"); save("open-soon", AT.plusSeconds(4));
        item.put("plcyNo", "985").put("aplyYmd", "20261001 ~ 20261012"); save("before", AT.plusSeconds(5));
        item.put("plcyNo", "986").put("lclsfNm", "금융･복지･문화").put("aplyYmd", "20260901 ~ 20260910"); save("finance", AT.plusSeconds(6));
        item.put("plcyNo", "987").put("lclsfNm", "금융·복지·문화").put("aplyYmd", "20260801 ~ 20260802"); save("finance-dot", AT.plusSeconds(7));
        item.put("plcyNo", "988").put("lclsfNm", "일자리").put("aplyYmd", "20261001 ~ 20261031"); save("before-later", AT.plusSeconds(8));

        // 마감 임박순은 접수 중에만 적용하고 접수 전은 최근 수집순이다.
        var numbers = store.list("", 1, 20, false, null, java.util.Set.of(), AT).items().stream().map(PolicySummary::policyNumber).toList();
        assertThat(numbers).containsSubsequence("986", "984", "982", "988", "985", "983", "981");
        assertThat(store.list("", 1, 20, false, null, java.util.Set.of(PolicyCategory.JOB), AT).total()).isEqualTo(6);
        assertThat(store.list("", 1, 20, false, kr.youthpolicymate.policy.RecruitmentStatus.OPEN, java.util.Set.of(PolicyCategory.JOB), AT).items())
                .extracting(PolicySummary::policyNumber).containsExactly("984", "982");
        mvc.perform(get("/api/v1/policies").param("category", "FINANCE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items[0].policyNumber").value("986"))
                .andExpect(jsonPath("$.items[0].recruitment.deadlineOnSeoul").value("2026-09-10"))
                .andExpect(jsonPath("$.items[0].recruitment.daysUntilDeadline").value(5))
                .andExpect(jsonPath("$.items[1].recruitment.status").value("CLOSED"));
        mvc.perform(get("/api/v1/policies").param("category", "JOB").param("recruitmentStatus", "ROLLING")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].recruitment.deadlineOnSeoul").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].recruitment.daysUntilDeadline").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/v1/policies").param("category", "INVALID")).andExpect(status().isBadRequest());
    }

    @Test @DisplayName("복수 분류 정책은 목록 분야 필터와 분야별 수에 분야마다 포함하고 맞지 않는 분류는 전체 수에만 넣는다")
    void countsPoliciesByCategory() throws Exception {
        item.put("plcyNo", "991").put("lclsfNm", "일자리"); save("job", AT.plusSeconds(1));
        item.put("plcyNo", "992"); save("job-2", AT.plusSeconds(2));
        item.put("plcyNo", "993").put("lclsfNm", "금융･복지･문화"); save("finance", AT.plusSeconds(3));
        item.put("plcyNo", "994").put("lclsfNm", "금융·복지·문화"); save("finance-dot", AT.plusSeconds(4));
        item.put("plcyNo", "995").put("lclsfNm", "일자리,주거"); save("multi", AT.plusSeconds(5));
        item.put("plcyNo", "996").put("lclsfNm", "교육･직업훈련 , 금융･복지･문화"); save("multi-spaced", AT.plusSeconds(6));
        item.put("plcyNo", "999").put("lclsfNm", "주거,\u00a0참여･기반\t,\u3000기타"); save("multi-unicode-space", AT.plusSeconds(9));
        item.put("plcyNo", "997").put("lclsfNm", "기타"); save("unknown", AT.plusSeconds(7));
        item.put("plcyNo", "998").put("lclsfNm", ""); save("empty", AT.plusSeconds(8));

        mvc.perform(get("/api/v1/policies/category-counts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(9))
                .andExpect(jsonPath("$.items[*].category").value(org.hamcrest.Matchers.contains("JOB", "HOUSING", "EDUCATION", "FINANCE", "PARTICIPATION")))
                .andExpect(jsonPath("$.items[*].count").value(org.hamcrest.Matchers.contains(3, 2, 1, 3, 1)));
        // 홈 상황 항목의 수와 그 분야 하나만 고른 목록의 전체 건수가 같아야 한다.
        for (var count : store.categoryCounts().items()) {
            assertThat(store.list("", 1, 20, false, null, java.util.Set.of(count.category()), AT).total()).as(count.category().name()).isEqualTo(count.count());
        }
        mvc.perform(get("/api/v1/policies").param("category", "HOUSING")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].policyNumber").value(org.hamcrest.Matchers.contains("999", "995")))
                .andExpect(jsonPath("$.items[1].category").value("일자리,주거"));
        // 여러 분야는 하나라도 해당하면 포함하고, 두 분야에 모두 속한 995는 한 번만 센다.
        mvc.perform(get("/api/v1/policies").param("category", "JOB", "HOUSING")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[*].policyNumber").value(org.hamcrest.Matchers.containsInAnyOrder("991", "992", "995", "999")));
    }

    private void saveReviewed(String number, String title, String hash) {
        // 인공 본문과 검토 해시로 조회·질문 연결만 검사한다. 공식 조건의 정확성 검사가 아니다.
        var source = item.deepCopy();
        source.put("plcyNo", number).put("plcyNm", title);
        var normalized = parser.item(source);
        importAt(number, normalized.content(), normalized.rawPolicy(), AT, "reviewed-" + number, hash);
    }

    private PolicyCatalogStore.ImportResult save(String captureHash, Instant at) {
        var normalized = parser.item(item);
        return importAt(normalized.number(), normalized.content(), normalized.rawPolicy(), at, captureHash, normalized.contentHash());
    }

    // 수신 시각 순서에 기대는 검사가 있으므로 수집 요청 순번도 시각에서 단조 증가하게 만든다.
    private PolicyCatalogStore.ImportResult importAt(String number, PolicyContent content, String raw, Instant at,
                                                     String captureHash, String contentHash) {
        return store.importPolicy(number, content, raw, at, captureHash, contentHash, at.getEpochSecond());
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
