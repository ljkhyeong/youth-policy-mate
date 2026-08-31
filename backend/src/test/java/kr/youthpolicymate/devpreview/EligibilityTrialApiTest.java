package kr.youthpolicymate.devpreview;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static kr.youthpolicymate.devpreview.EligibilityTrialRequest.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("preview")
class EligibilityTrialApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    @DisplayName("인공 질문 개정·정의·기준일과 서버가 지원하는 답변 선택지를 제공한다")
    void describesSyntheticQuestions() throws Exception {
        mockMvc.perform(get("/api/dev/eligibility-trial"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.dataKind").value("SYNTHETIC"))
                .andExpect(jsonPath("$.questionSets.length()").value(2))
                .andExpect(jsonPath("$.questionSets[1].policyRevision").value("sample-revision-2"))
                .andExpect(jsonPath("$.questionSets[1].employmentDescription").value("유급 근로계약을 맺고 일하고 있는 상태"))
                .andExpect(jsonPath("$.employmentChoices[0].value").value("UNANSWERED"))
                .andExpect(jsonPath("$.incomeChoices[5].value").value("BETWEEN_20M_25M"));
    }

    @Test
    @DisplayName("소득 답변 구간을 좁히면 같은 질문 기준으로 다시 계산한다")
    void reevaluatesChangedAnswer() throws Exception {
        var partial = request(QuestionSetId.INITIAL, QuestionSetId.INITIAL, EmploymentChoice.DOES_NOT_APPLY, IncomeChoice.BETWEEN_20M_30M);
        var narrowed = request(QuestionSetId.INITIAL, QuestionSetId.INITIAL, EmploymentChoice.DOES_NOT_APPLY, IncomeChoice.BETWEEN_20M_25M);
        mockMvc.perform(post("/api/dev/eligibility-trial").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(partial)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.example.result.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.example.result.conditions[3].comparedValue").value("20000000원 초과 · 30000000원 이하"));
        mockMvc.perform(post("/api/dev/eligibility-trial").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(narrowed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.example.result.status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.example.result.conditions[2].comparedValue").value("해당하지 않음"))
                .andExpect(jsonPath("$.example.result.conditions[3].comparedValue").value("20000000원 초과 · 25000000원 이하"));
    }

    @Test
    @DisplayName("이전 개정에서 작성한 답변을 새 정의·기준일·소득 기간에 다시 붙이지 않는다")
    void rejectsAnswerReuseAcrossQuestions() throws Exception {
        var old = request(QuestionSetId.REVISED, QuestionSetId.INITIAL, EmploymentChoice.DOES_NOT_APPLY, IncomeChoice.ZERO);
        var response = mockMvc.perform(post("/api/dev/eligibility-trial").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(old)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionSet").value("REVISED"))
                .andExpect(jsonPath("$.example.result.status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.example.result.basis.policyRevision").value("sample-revision-2"))
                .andExpect(jsonPath("$.example.result.conditions[2].referenceDate").value("2026-08-30"))
                .andExpect(jsonPath("$.example.result.conditions[3].referenceDate").value("2024-12-31"))
                .andReturn();
        var conditions = mapper.readTree(response.getResponse().getContentAsByteArray()).path("example").path("result").path("conditions");
        for (int index : new int[]{2, 3}) {
            assertThat(conditions.get(index).path("uncertainty").asString()).isEqualTo("MISSING_USER_INPUT");
            assertThat(conditions.get(index).path("comparedValue").isNull()).isTrue();
        }
    }

    @Test
    @DisplayName("선택 안 함과 모름 답변은 각각 빈 비교값과 모름으로 유지한다")
    void distinguishesUnansweredAndUnknown() throws Exception {
        var request = request(QuestionSetId.INITIAL, QuestionSetId.INITIAL, EmploymentChoice.UNANSWERED, IncomeChoice.UNKNOWN);
        var response = mockMvc.perform(post("/api/dev/eligibility-trial").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.example.result.status").value("NEEDS_REVIEW")).andReturn();
        var conditions = mapper.readTree(response.getResponse().getContentAsByteArray()).path("example").path("result").path("conditions");
        assertThat(conditions.get(2).path("comparedValue").isNull()).isTrue();
        assertThat(conditions.get(3).path("comparedValue").asString()).isEqualTo("모름");
    }

    @Test
    @DisplayName("누락·알 수 없는 코드는 400으로 거절하고 다른 요청의 차단은 유지한다")
    void rejectsInvalidRequestsWithoutEchoingInput() throws Exception {
        for (String body : new String[]{"{}", "{\"questionSet\":\"PRIVATE_VALUE\"}"}) {
            var response = mockMvc.perform(post("/api/dev/eligibility-trial").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SYNTHETIC_ANSWER")).andReturn();
            assertThat(response.getResponse().getContentAsString()).doesNotContain("PRIVATE_VALUE");
        }
        mockMvc.perform(patch("/api/dev/eligibility-trial").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/dev/eligibility-examples").with(csrf())).andExpect(status().isForbidden());
    }

    private EligibilityTrialRequest request(QuestionSetId target, QuestionSetId answeredOn, EmploymentChoice employment, IncomeChoice income) {
        return new EligibilityTrialRequest(target, answeredOn, employment, answeredOn, income);
    }
}
