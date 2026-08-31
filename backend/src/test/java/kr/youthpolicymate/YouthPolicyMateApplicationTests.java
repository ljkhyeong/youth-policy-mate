package kr.youthpolicymate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import kr.youthpolicymate.devpreview.ReminderPreviewController;
import kr.youthpolicymate.devpreview.EligibilityPreviewController;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class YouthPolicyMateApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    @DisplayName("실제 PostgreSQL에 연결하고 상태 확인 응답에는 상세 정보를 노출하지 않는다")
    void connectsToPostgresAndReportsHealthWithoutDetails() throws Exception {
        assertThat(jdbcClient.sql("select current_database()").query(String.class).single())
                .isEqualTo(postgres.getDatabaseName());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("상태 확인 이외의 경로는 허용하지 않는다")
    void deniesOtherPaths() throws Exception {
        assertThat(applicationContext.getBeansOfType(ReminderPreviewController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(EligibilityPreviewController.class)).isEmpty();
        for (String path : new String[]{"/actuator/env", "/api/dev/reminder-examples", "/api/dev/eligibility-examples", "/dev/openapi"}) {
            mockMvc.perform(get(path)).andExpect(status().isForbidden());
        }
    }
}
