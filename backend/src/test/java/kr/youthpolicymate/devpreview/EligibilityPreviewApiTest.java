package kr.youthpolicymate.devpreview;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("preview")
class EligibilityPreviewApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    @DisplayName("계산한 자격과 모집 상태를 분리하고 조건별 기준·근거를 전송한다")
    void returnsCalculatedDecisionAndEvidence() throws Exception {
        mockMvc.perform(get("/api/dev/eligibility-examples"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.dataKind").value("SYNTHETIC"))
                .andExpect(jsonPath("$.examples.length()").value(4))
                .andExpect(jsonPath("$.examples[0].result.status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.examples[0].recruitment.status").value("CLOSED"))
                .andExpect(jsonPath("$.examples[0].result.basis.ruleVersion").value("sample-rule-1"))
                .andExpect(jsonPath("$.examples[0].result.basis.evaluatedAt").value("2026-08-30T15:30:00Z"))
                .andExpect(jsonPath("$.examples[0].result.conditions[0].comparedValue").value("만 27세"))
                .andExpect(jsonPath("$.examples[0].result.conditions[0].referenceDate").value("2026-08-01"))
                .andExpect(jsonPath("$.examples[0].result.conditions[3].referenceDate").value("2025-12-31"))
                .andExpect(jsonPath("$.examples[0].result.conditions[3].evidence.location").value("인공 자료 4항 · 소득"));
    }

    @Test
    @DisplayName("미해석 예외·입력 부족·불충족의 구분과 명시적인 null을 보존한다")
    void retainsUnknownCausesAndNulls() throws Exception {
        var response = mockMvc.perform(get("/api/dev/eligibility-examples")).andExpect(status().isOk()).andReturn();
        var examples = mapper.readTree(response.getResponse().getContentAsByteArray()).path("examples");
        var partial = examples.get(1).path("result");
        assertThat(partial.path("status").asString()).isEqualTo("NEEDS_REVIEW");
        assertThat(partial.path("conditions").get(3).path("uncertainty").asString()).isEqualTo("MISSING_USER_INPUT");
        assertThat(partial.path("conditions").get(3).path("comparedValue").asString()).contains("30000000원 이하");
        var unresolved = examples.get(2).path("result");
        assertThat(unresolved.path("status").asString()).isEqualTo("NEEDS_REVIEW");
        assertThat(unresolved.path("basis").path("policyRevision").asString()).isEqualTo("sample-revision-2");
        assertThat(unresolved.path("policyReview").path("completion").asString()).isEqualTo("INCOMPLETE");
        assertThat(unresolved.path("policyReview").path("pendingIssues").get(0).path("evidence").path("excerpt").asString())
                .contains("연령 상한의 예외");
        assertThat(unresolved.path("conditions").get(0).path("outcome").asString()).isEqualTo("NOT_MET");
        var employment = unresolved.path("conditions").get(2);
        assertThat(employment.path("uncertainty").asString()).isEqualTo("UNRESOLVED_POLICY");
        for (String field : new String[]{"comparedValue", "referenceDate"}) {
            assertThat(employment.has(field)).isTrue();
            assertThat(employment.path(field).isNull()).isTrue();
        }
        assertThat(employment.path("evidence").has("excerpt")).isTrue();
        assertThat(employment.path("evidence").path("excerpt").isNull()).isTrue();
        var failed = examples.get(3).path("result");
        assertThat(failed.path("status").asString()).isEqualTo("INELIGIBLE");
        assertThat(failed.path("conditions").get(3).path("uncertainty").asString()).isEqualTo("MISSING_USER_INPUT");
        var confirmed = failed.path("conditions").get(0);
        assertThat(confirmed.has("uncertainty")).isTrue();
        assertThat(confirmed.path("uncertainty").isNull()).isTrue();
    }

    @Test
    @DisplayName("자격 예시 API에 답변을 보내거나 저장할 수 없다")
    void deniesWrites() throws Exception {
        mockMvc.perform(post("/api/dev/eligibility-examples")).andExpect(status().isForbidden());
    }
}
