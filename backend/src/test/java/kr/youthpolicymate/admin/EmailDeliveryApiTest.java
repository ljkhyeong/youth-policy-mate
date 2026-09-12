package kr.youthpolicymate.admin;

import kr.youthpolicymate.member.MemberEmailSender;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"app.admin.member-ids=10000000-0000-0000-0000-000000000001",
        "app.email.enabled=false", "app.reminders.enabled=false", "app.ontong.schedule.enabled=false", "app.ai.auto.enabled=false"})
@AutoConfigureMockMvc
class EmailDeliveryApiTest {
    private static final String ROOT = "/api/v1/admin/email-deliveries";
    private static final String ADMIN = "10000000-0000-0000-0000-000000000001";
    private static final UUID MEMBER = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @MockitoBean Clock clock;
    @MockitoBean MemberEmailSender sender;
    @MockitoSpyBean EmailDeliveryStore deliveries;

    @BeforeEach void prepare() {
        when(clock.instant()).thenReturn(NOW);
        when(sender.provider()).thenReturn("resend");
        jdbc.sql("DELETE FROM members").update();
        jdbc.sql("INSERT INTO members(id, provider, provider_subject, display_name) VALUES (:id, 'kakao', 'private-subject', '비공개 회원')")
                .param("id", MEMBER).update();
        jdbc.sql("""
                INSERT INTO members(id, provider, provider_subject, display_name) VALUES
                ('10000000-0000-0000-0000-000000000001', 'kakao', 'admin-fixture', '검증 관리자'),
                ('20000000-0000-0000-0000-000000000002', 'naver', 'member-fixture', '검증 회원')
                ON CONFLICT (id) DO NOTHING
                """).update();
    }

    @Test @DisplayName("관리자 소셜 세션만 발송 현황을 조회하며 변경 요청은 허용하지 않는다")
    void protectsAccess() throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT).with(social(MEMBER.toString()))).andExpect(status().isForbidden());
        mvc.perform(get(ROOT).with(user(ADMIN).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post(ROOT).with(social(ADMIN)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get(ROOT).with(social(ADMIN))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.summary.total").value(0))
                .andExpect(jsonPath("$.sendingEnabled").value(false)).andExpect(jsonPath("$.provider").value("resend"));
    }

    @Test @DisplayName("기간 경계와 상태·종류를 적용하고 같은 요청 시각은 ID 역순으로 페이지를 나눈다")
    void filtersAndPaginates() throws Exception {
        row(1, "SENT", NOW.minusSeconds(60)); row(2, "UNKNOWN", NOW.minusSeconds(60)); row(3, "FAILED", NOW.minusSeconds(60));
        row(4, "PENDING", NOW.minusSeconds(7 * 86400));
        row(5, "SENT", NOW.minusSeconds(7 * 86400 + 1)); row(6, "SENT", NOW.plusSeconds(1));
        mvc.perform(get(ROOT).with(social(ADMIN)).param("pageSize", "2"))
                .andExpect(jsonPath("$.total").value(4)).andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items[0].id").value(id(3).toString())).andExpect(jsonPath("$.items[1].id").value(id(2).toString()))
                .andExpect(jsonPath("$.summary.total").value(4)).andExpect(jsonPath("$.summary.failed").value(1))
                .andExpect(jsonPath("$.summary.unknown").value(1));
        mvc.perform(get(ROOT).with(social(ADMIN)).param("page", "2").param("pageSize", "2"))
                .andExpect(jsonPath("$.items[0].id").value(id(1).toString())).andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get(ROOT).with(social(ADMIN)).param("state", "UNKNOWN").param("kind", "VERIFICATION"))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].state").value("UNKNOWN"))
                .andExpect(jsonPath("$.summary.total").value(4));
        mvc.perform(get(ROOT).with(social(ADMIN)).param("days", "1").param("kind", "POLICY"))
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.summary.total").value(3));
        mvc.perform(get(ROOT).with(social(ADMIN)).param("page", "10"))
                .andExpect(jsonPath("$.total").value(4)).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test @DisplayName("조회는 오래된 발송 중 상태도 변경하지 않고 회원·주소·인증 코드를 응답하지 않는다")
    void readsWithoutSendingOrLeaking() throws Exception {
        row(1, "SENDING", NOW.minusSeconds(3600));
        jdbc.sql("UPDATE member_email_outbox SET code_cipher = 'private-encrypted-code', started_at = created_at WHERE id = :id")
                .param("id", id(1)).update();
        String before = jdbc.sql("SELECT row_to_json(o)::text FROM member_email_outbox o").query(String.class).single();
        var response = mvc.perform(get(ROOT).with(social(ADMIN))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].state").value("SENDING"))
                .andExpect(jsonPath("$.items[0].finishedAt").isEmpty()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("private-encrypted-code", "private-subject", "비공개 회원", MEMBER.toString(), "memberId", "settingsVersion", "address", "codeCipher");
        assertThat(jdbc.sql("SELECT row_to_json(o)::text FROM member_email_outbox o").query(String.class).single()).isEqualTo(before);
        verify(sender, never()).send(any(), any(), any(), any(), any());
    }

    @Test @DisplayName("잘못된 조회 조건과 DB 실패를 빈 목록으로 응답하지 않는다")
    void reportsFailures() throws Exception {
        for (String query : List.of("days=0", "days=91", "page=0", "pageSize=51", "page=x", "state=bad", "kind=bad")) {
            mvc.perform(get(ROOT + "?" + query).with(social(ADMIN))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_EMAIL_DELIVERY_QUERY"));
        }
        doThrow(new DataAccessResourceFailureException("private-database-error")).when(deliveries).list(1, 20, 7, null, null);
        var response = mvc.perform(get(ROOT).with(social(ADMIN))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_UNAVAILABLE")).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("private-database-error", "items");
    }

    private static UUID id(int value) { return UUID.fromString("30000000-0000-0000-0000-" + String.format("%012d", value)); }
    private void row(int value, String state, Instant created) {
        jdbc.sql("""
                INSERT INTO member_email_outbox(id, member_id, settings_version, kind, state, created_at, provider)
                VALUES (:id, :member, :version, 'VERIFICATION', :state, :created, 'resend')
                """).param("id", id(value)).param("member", MEMBER).param("version", UUID.randomUUID())
                .param("state", state).param("created", created.atOffset(ZoneOffset.UTC)).update();
    }
    private static RequestPostProcessor social(String id) {
        return oauth2Login().oauth2User(new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_MEMBER")), Map.of("memberId", id), "memberId"));
    }
}
