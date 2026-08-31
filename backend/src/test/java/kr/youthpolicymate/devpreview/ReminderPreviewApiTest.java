package kr.youthpolicymate.devpreview;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("preview")
class ReminderPreviewApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    @DisplayName("인공 자료임을 표시하고 서버가 계산한 날짜형 후보와 기준을 직렬화한다")
    void returnsCalculatedCandidates() throws Exception {
        mockMvc.perform(get("/api/dev/reminder-examples"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.dataKind").value("SYNTHETIC"))
                .andExpect(jsonPath("$.examples.length()").value(7))
                .andExpect(jsonPath("$.examples[0].result.applicationPeriod.endsOnInclusive").value("2026-09-07"))
                .andExpect(jsonPath("$.examples[0].result.dates[0].date").value("2026-08-31"))
                .andExpect(jsonPath("$.examples[0].result.dates[0].daysBeforeDeadline").value(7))
                .andExpect(jsonPath("$.examples[0].result.dates[0].status").value("TODAY_REQUIRES_SEND_TIME_CHECK"))
                .andExpect(jsonPath("$.examples[0].result.basis.evaluatedAt").value("2026-08-30T15:30:00Z"))
                .andExpect(jsonPath("$.examples[0].result.basis.policyRevision").value("sample-revision-1"));
    }

    @Test
    @DisplayName("원래 마감 순간과 시간대를 서울 마감 날짜와 구분해 전송한다")
    void retainsExactDeadline() throws Exception {
        mockMvc.perform(get("/api/dev/reminder-examples"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examples[1].result.applicationPeriod.closesAtExclusive").value("2026-09-06T18:00:00Z"))
                .andExpect(jsonPath("$.examples[1].result.applicationPeriod.closingTimeZone").value("UTC"))
                .andExpect(jsonPath("$.examples[1].result.deadlineOnSeoul").value("2026-09-07"))
                .andExpect(jsonPath("$.examples[1].result.recruitment.status").value("OPEN"));
    }

    @Test
    @DisplayName("빈 후보의 사유와 명시적인 null을 보존하며 모집 마감과 지난 후보를 구분한다")
    void retainsAbsentDatesAndReasons() throws Exception {
        var response = mockMvc.perform(get("/api/dev/reminder-examples")).andExpect(status().isOk()).andReturn();
        var examples = mapper.readTree(response.getResponse().getContentAsByteArray()).path("examples");
        var unresolved = examples.get(2).path("result");
        assertThat(unresolved.has("deadlineOnSeoul")).isTrue();
        assertThat(unresolved.path("deadlineOnSeoul").isNull()).isTrue();
        assertThat(unresolved.path("applicationPeriod").path("reason").asString()).contains("서로 다릅니다");
        assertThat(unresolved.path("outcome").asString()).isEqualTo("NO_CONFIRMED_DEADLINE");
        for (int i = 2; i < 7; i++) assertThat(examples.get(i).path("result").path("dates").size()).isZero();
        assertThat(examples.get(5).path("result").path("outcome").asString()).isEqualTo("RECRUITMENT_CLOSED");
        assertThat(examples.get(6).path("result").path("outcome").asString()).isEqualTo("NO_REMAINING_DATES");
    }

    @Test
    @DisplayName("미리보기에서도 쓰기 요청과 허용하지 않은 API는 차단한다")
    void deniesOtherRequests() throws Exception {
        mockMvc.perform(post("/api/dev/reminder-examples")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dev/other")).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("실제 생성 OpenAPI와 저장된 계약이 일치한다")
    void matchesGeneratedContract() throws Exception {
        var response = mockMvc.perform(get("/dev/openapi")).andExpect(status().isOk()).andReturn();
        var actual = mapper.readTree(response.getResponse().getContentAsByteArray());
        assertThat(actual.path("openapi").asString()).startsWith("3.1.");
        assertThat(actual.path("paths").size()).isEqualTo(1);
        var path = Path.of(System.getProperty("preview.contract.path"));
        if (Boolean.getBoolean("preview.contract.update")) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
        }
        assertThat(actual).isEqualTo(mapper.readTree(Files.readString(path)));
    }
}
