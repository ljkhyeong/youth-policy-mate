package kr.youthpolicymate.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@ActiveProfiles("prod")
// 세션 쿠키 속성(server.servlet.session.cookie.*)은 내장 서버로 실행할 때 적용되므로 MOCK 대신 실제 포트로 기동한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"PUBLIC_APP_URL=https://policy.example.test/", "DB_HOST=unused", "DB_NAME=unused", "DB_USER=unused", "DB_PASSWORD=unused",
        "KAKAO_CLIENT_ID=test-client", "KAKAO_CLIENT_SECRET=test-secret", "app.email.enabled=false",
        "app.email.resend.read-api-key=",
        "app.reminders.enabled=false", "app.ontong.schedule.enabled=false", "app.ai.auto.enabled=false"})
@AutoConfigureMockMvc
class ProductionRuntimeTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MockMvc mvc;
    @Autowired AppUrls urls;
    @Autowired kr.youthpolicymate.member.ResendMemberEmailSender emailSender;

    @Test
    @DisplayName("운영에서 실제 Resend 모듈을 구성하되 발송은 명시적으로 켜기 전까지 비활성화한다")
    void preparesResendWithoutSending() {
        assertThat(emailSender.available()).isFalse();
    }

    @Test
    @DisplayName("운영 프로필은 HTTPS 로그인 주소·보안 쿠키와 DB를 포함한 준비 상태를 제공한다")
    void preparesProxyLoginAndProbes() throws Exception {
        assertThat(urls.frontend()).isEqualTo("https://policy.example.test");
        assertThat(urls.backend()).isEqualTo("https://policy.example.test");
        var login = mvc.perform(get("/oauth2/authorization/kakao").header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "policy.example.test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Set-Cookie", containsString("YPM_SESSION=")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax"))).andReturn().getResponse();
        assertThat(java.net.URLDecoder.decode(login.getHeader("Location"), java.nio.charset.StandardCharsets.UTF_8))
                .contains("redirect_uri=https://policy.example.test/login/oauth2/code/kakao");
        // 저장된 인가 요청이 없는 콜백은 로그인 실패로 처리되어 공개 웹 주소의 로그인 화면으로 돌아간다.
        var failure = mvc.perform(get("/login/oauth2/code/kakao").param("code", "unknown").param("state", "unknown"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getHeader("Location");
        assertThat(failure).startsWith("https://policy.example.test/").endsWith("/login?error=login");
        for (String probe : new String[]{"/actuator/health/liveness", "/actuator/health/readiness"}) {
            mvc.perform(get(probe)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist());
        }
        mvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
    }
}
