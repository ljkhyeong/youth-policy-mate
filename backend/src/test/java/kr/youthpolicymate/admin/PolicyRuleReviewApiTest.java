package kr.youthpolicymate.admin;

import kr.youthpolicymate.ingestion.OntongPolicyCapture;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"app.admin.member-ids=10000000-0000-0000-0000-000000000001",
        "app.reminders.enabled=false", "app.email.enabled=false", "app.ontong.schedule.enabled=false"})
@AutoConfigureMockMvc
class PolicyRuleReviewApiTest {
    private static final String ROOT = "/api/v1/admin/policy-rule-reviews";
    private static final String ADMIN = "10000000-0000-0000-0000-000000000001";
    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PolicyCatalogStore policies;
    @Autowired PolicyRuleStore rules;
    @MockitoBean Clock clock;
    @MockitoSpyBean PolicyRuleReviewStore reviews;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        jdbc.sql("DELETE FROM policy_revisions").update();
        jdbc.sql("DELETE FROM policy_source_snapshots").update();
        jdbc.sql("DELETE FROM policies").update();
        jdbc.sql("DELETE FROM policy_rule_heads WHERE policy_number LIKE '9999%'").update();
        jdbc.sql("DELETE FROM policy_rule_versions WHERE policy_number LIKE '9999%'").update();
    }

    @Test @DisplayName("관리자 소셜 세션만 조회하고 등록·적용 요청은 열지 않는다")
    void protectsAccess() throws Exception {
        for (var path : List.of(ROOT, ROOT + "/99990000000000000001")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", containsString("no-store")));
            mvc.perform(get(path).with(social("20000000-0000-0000-0000-000000000002"))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isForbidden());
            mvc.perform(post(path).with(social(ADMIN)).with(csrf())).andExpect(status().isForbidden());
        }
        mvc.perform(get(ROOT).with(social(ADMIN))).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test @DisplayName("원문 변경·만료·미등록 순서로 검색과 필터를 전체 결과에 적용한다")
    void filtersBeforePagination() throws Exception {
        var active = source(1, "현재 조건", 0); publish(active, NOW.minusSeconds(1), NOW.plusSeconds(100));
        var changed = source(2, "이전 조건", 0); publish(changed, NOW.minusSeconds(1), NOW.plusSeconds(100));
        source(2, "변경된 조건", 1);
        var expired = source(3, "만료 조건", 0); publish(expired, NOW.minusSeconds(10), NOW.plusSeconds(1));
        source(4, "지원 100% 조건", 0);
        var scheduled = source(5, "적용 전 조건", 0); publish(scheduled, NOW, NOW.plusSeconds(200));
        var pending = source(6, "초안 조건", 0); draft(pending, NOW, NOW.plusSeconds(200), "draft");
        // 적용된 규칙의 시작·종료 경계를 현재 시각 변경으로 확인한다.
        when(clock.instant()).thenReturn(NOW.minusSeconds(1));
        mvc.perform(get(ROOT).param("filter", "SCHEDULED").with(social(ADMIN)))
                .andExpect(jsonPath("$.items[0].policyNumber").value(scheduled.number())).andExpect(jsonPath("$.total").value(1));
        when(clock.instant()).thenReturn(NOW.plusSeconds(1));
        mvc.perform(get(ROOT).param("pageSize", "2").with(social(ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4)).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items[0].status").value("SOURCE_CHANGED"))
                .andExpect(jsonPath("$.items[1].status").value("EXPIRED"));
        mvc.perform(get(ROOT).param("pageSize", "2").param("page", "2").with(social(ADMIN)))
                .andExpect(jsonPath("$.items[0].policyNumber").value("99990000000000000004"))
                .andExpect(jsonPath("$.items[1].draftCount").value(1)).andExpect(jsonPath("$.items[1].status").value("MISSING"))
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get(ROOT).param("filter", "ALL").with(social(ADMIN))).andExpect(jsonPath("$.total").value(6));
        mvc.perform(get(ROOT).param("filter", "ACTIVE").with(social(ADMIN))).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get(ROOT).param("query", " % ").with(social(ADMIN))).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get(ROOT).param("query", changed.number()).with(social(ADMIN))).andExpect(jsonPath("$.total").value(1));
    }

    @Test @DisplayName("현재·직전 개정과 질문 근거·초안을 조회하되 원본이나 적용 버전을 변경하지 않는다")
    void comparesRevisionsAndRulesWithoutWriting() throws Exception {
        var original = source(1, "최초 제목", 0);
        var head = publish(original, NOW.minusSeconds(1), NOW.plusSeconds(100));
        source(1, "직전 제목", 1);
        var current = source(1, "<script>최신 제목</script>", 2);
        draft(current, NOW, NOW.plusSeconds(100), "draft");
        var result = mvc.perform(get(ROOT + "/" + original.number()).with(social(ADMIN)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.item.status").value("SOURCE_CHANGED"))
                .andExpect(jsonPath("$.currentPolicy.revision").value(3))
                .andExpect(jsonPath("$.currentPolicy.previousRevision.revision").value(2))
                .andExpect(jsonPath("$.currentPolicy.previousRevision.content.title").value("직전 제목"))
                .andExpect(jsonPath("$.versions[0].id").value(head.toString()))
                .andExpect(jsonPath("$.versions[0].state").value("CURRENT"))
                .andExpect(jsonPath("$.versions[0].sourceMatches").value(false))
                .andExpect(jsonPath("$.versions[1].state").value("DRAFT"))
                .andExpect(jsonPath("$.versions[1].sourceMatches").value(true)).andReturn();
        var body = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(mapper.readTree(body.path("rawPolicyJson").asString()).path("plcyNm").asString()).isEqualTo(current.content().title());
        assertThat(body.at("/versions/0/questions").size()).isPositive();
        assertThat(jdbc.sql("SELECT version_id FROM policy_rule_heads WHERE policy_number = :number").param("number", original.number()).query(UUID.class).single()).isEqualTo(head);
        assertThat(jdbc.sql("SELECT count(*) FROM policy_revisions").query(Long.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("SELECT count(*) FROM policy_source_snapshots").query(Long.class).single()).isEqualTo(3);
    }

    @Test @DisplayName("초안·이전 개정이 없는 정책과 입력 오류·장애를 구분한다")
    void handlesMissingAndFailures() throws Exception {
        var policy = source(1, "새 정책", 0);
        mvc.perform(get(ROOT + "/" + policy.number()).with(social(ADMIN))).andExpect(jsonPath("$.versions").isEmpty())
                .andExpect(jsonPath("$.currentPolicy.previousRevision").isEmpty());
        for (var query : List.of("?page=0", "?pageSize=51", "?filter=WRONG", "?query=" + "a".repeat(101), "/invalid")) {
            mvc.perform(get(ROOT + query).with(social(ADMIN))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_POLICY_REVIEW_QUERY"));
        }
        mvc.perform(get(ROOT + "/123").with(social(ADMIN))).andExpect(status().isNotFound());
        doThrow(new DataAccessResourceFailureException("private-error")).when(reviews).list(1, 20, PolicyRuleReviews.Filter.REVIEW, "");
        mvc.perform(get(ROOT).with(social(ADMIN))).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("private-error"))));
    }

    private OntongPolicyCapture.Item source(int index, String title, int seconds) throws Exception {
        var parser = new OntongPolicyCapture(mapper);
        var source = (ObjectNode) parser.parse(Files.readString(Path.of("src/test/resources/ontong/list-capture.json"))).items().getFirst().deepCopy();
        source.put("plcyNo", "999900000000000000%02d".formatted(index)); source.put("plcyNm", title);
        var item = parser.item(source);
        policies.importPolicy(item.number(), item.content(), item.rawPolicy(), NOW.plusSeconds(seconds), UUID.randomUUID().toString(), item.contentHash());
        return item;
    }
    private UUID draft(OntongPolicyCapture.Item item, Instant from, Instant until, String version) {
        var definition = (ObjectNode) mapper.readTree(jdbc.sql("SELECT definition::text FROM policy_rule_versions WHERE id = 'ba390000-0000-4000-8000-000000000001'").query(String.class).single());
        definition.put("policyNumber", item.number()).put("contentHash", item.contentHash()).put("ruleVersion", version)
                .put("validFrom", from.toString()).put("validUntil", until.toString());
        return rules.draft(mapper.treeToValue(definition, PolicyRuleDefinition.class), "검증 작업자", "원문 확인");
    }
    private UUID publish(OntongPolicyCapture.Item item, Instant from, Instant until) {
        var id = draft(item, from, until, "review-v1"); rules.publish(id, "none", "검증 작업자"); return id;
    }
    private static RequestPostProcessor social(String id) {
        return oauth2Login().oauth2User(new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_MEMBER")), Map.of("memberId", id), "memberId"));
    }
}
