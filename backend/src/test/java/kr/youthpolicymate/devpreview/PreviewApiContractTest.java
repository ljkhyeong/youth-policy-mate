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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("preview")
class PreviewApiContractTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    @DisplayName("실제 생성 OpenAPI의 경로·null enum과 저장된 계약이 일치한다")
    void matchesGeneratedContract() throws Exception {
        var response = mockMvc.perform(get("/dev/openapi")).andExpect(status().isOk()).andReturn();
        var actual = mapper.readTree(response.getResponse().getContentAsByteArray());
        assertThat(actual.path("openapi").asString()).startsWith("3.1.");
        assertThat(actual.path("paths").size()).isEqualTo(3);
        assertThat(actual.path("paths").has("/api/dev/eligibility-examples")).isTrue();
        var uncertainty = actual.path("components").path("schemas").path("EligibilityCondition").path("properties").path("uncertainty");
        assertThat(uncertainty.path("enum").valueStream().anyMatch(value -> value.isNull())).isTrue();
        var path = Path.of(System.getProperty("preview.contract.path"));
        if (Boolean.getBoolean("preview.contract.update")) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
        }
        assertThat(actual).isEqualTo(mapper.readTree(Files.readString(path)));
    }
}
