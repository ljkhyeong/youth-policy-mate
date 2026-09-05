package kr.youthpolicymate.member;

import kr.youthpolicymate.ingestion.OntongPolicyCapture;
import kr.youthpolicymate.policy.catalog.PolicyCatalogStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"app.reminders.enabled=false", "app.email.enabled=false",
        "app.email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc
class MemberFlowTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MemberPolicyStore members;
    @Autowired MemberIdentityStore identities;
    @Autowired PolicyCatalogStore policies;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean Clock clock;
    @MockitoBean MemberEmailSender emailSender;
    @Autowired MemberEmailStore emails;
    @Autowired MemberEmailDelivery emailDelivery;
    @Autowired EmailCrypto crypto;
    private ObjectNode raw;
    private UUID first;
    private UUID second;
    private long capture;
    private static final String NUMBER = "20260903005400113371";
    private static final String INPUT = """
            {"birthDate":"2000-01-02","district":"은평구","employmentStatus":"NOT_EMPLOYED"}
            """;

    @BeforeEach
    void prepare() throws Exception {
        jdbc.sql("DELETE FROM member_notifications").update();
        jdbc.sql("DELETE FROM policy_reminders").update();
        jdbc.sql("DELETE FROM saved_policies").update();
        jdbc.sql("DELETE FROM members").update();
        jdbc.sql("DELETE FROM policy_revisions").update();
        jdbc.sql("DELETE FROM policy_source_snapshots").update();
        jdbc.sql("DELETE FROM policies").update();
        raw = (ObjectNode) new OntongPolicyCapture(mapper).parse(Files.readString(Path.of("src/test/resources/ontong/list-capture.json"))).items().getFirst();
        raw.put("aplyPrdSeCd", "0057001").put("aplyYmd", "20260901 ~ 20260912");
        raw.put("plcySprtCn", "학업 지원").put("plcyAplyMthdCn", "공식 신청처 접수").put("etcMttrCn", "");
        capture = 0;
        importPolicy();
        first = identities.login("kakao", "101", "첫 회원");
        second = identities.login("naver", "101", "둘째 회원");
        time("2026-09-04T15:00:00Z");
        when(emailSender.available()).thenReturn(true);
    }

    @Test
    @DisplayName("동일 제공자 식별자는 재사용하고 다른 제공자의 동일 값은 별도 회원이다")
    void keepsProviderIdentity() {
        assertThat(identities.login("kakao", "101", "이름 수정")).isEqualTo(first).isNotEqualTo(second);
        assertThat(count("members")).isEqualTo(2);
    }

    @Test
    @DisplayName("회원 API는 로그인과 CSRF를 요구하고 다른 회원의 조건과 저장을 노출하거나 삭제하지 않는다")
    void protectsOwnership() throws Exception {
        mvc.perform(get("/api/v1/me/policies")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/me/policies/" + NUMBER).with(oauth2Login().oauth2User(user(first))))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/me/conditions").with(oauth2Login().oauth2User(user(first))).with(csrf())
                .contentType("application/json").content(INPUT)).andExpect(status().isNoContent());
        members.save(first, NUMBER);
        mvc.perform(get("/api/v1/me/conditions").with(oauth2Login().oauth2User(user(second))))
                .andExpect(status().isOk()).andExpect(content().json("{\"conditions\":null}"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/me/policies").with(oauth2Login().oauth2User(user(second))))
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(delete("/api/v1/me/policies/" + NUMBER).with(oauth2Login().oauth2User(user(second))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(members.saved(first).items()).hasSize(1);
        mvc.perform(delete("/api/v1/me/conditions").with(oauth2Login().oauth2User(user(first))).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(members.conditions(first).conditions()).isNull();
    }

    @Test
    @DisplayName("공개 조건 확인은 실제 원문과 검토 필요를 반환하며 생년월일과 회원 조건을 저장하지 않는다")
    void checksWithoutSaving() throws Exception {
        var response = mvc.perform(post("/api/v1/policies/checks").contentType("application/json").content(INPUT))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.items[0].revision").value(1))
                .andExpect(jsonPath("$.items[0].checks[0].evidence").isNotEmpty()).andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("2000-01-02", "apiKeyNm");
        assertThat(members.conditions(first).conditions()).isNull();
        mvc.perform(post("/api/v1/policies/checks").contentType("application/json").content(INPUT.replace("2000-01-02", "2099-01-01")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("동시 저장은 한 번만 예약하고 해제 후 다시 저장하면 새 예약만 전달한다")
    void savesAndCancelsOnce() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> members.save(first, NUMBER));
            var b = pool.submit(() -> members.save(first, NUMBER));
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        assertThat(count("saved_policies")).isEqualTo(1);
        assertThat(reminders("PENDING")).isEqualTo(3);
        members.remove(first, NUMBER);
        members.deliver(first);
        assertThat(reminders("CANCELED")).isEqualTo(3);
        assertThat(members.notifications(first).items()).isEmpty();
        members.save(first, NUMBER);
        members.deliver(first); members.deliver(first);
        assertThat(reminders("DELIVERED")).isEqualTo(1);
        assertThat(members.notifications(first).items()).hasSize(1);
        var id = UUID.fromString(members.notifications(first).items().getFirst().id());
        members.read(second, id);
        assertThat(members.notifications(first).items().getFirst().read()).isFalse();
        members.read(first, id);
        assertThat(members.notifications(first).items().getFirst().read()).isTrue();
    }

    @Test
    @DisplayName("마감 개정은 이전 예약을 취소하고 미확인 마감으로 바뀌면 새 마감 알림을 보류한다")
    void refreshesDeadlineRevision() {
        members.save(first, NUMBER);
        raw.put("aplyYmd", "20260901 ~ 20260920"); importPolicy();
        var saved = members.saved(first).items().getFirst();
        assertThat(saved.savedRevision()).isEqualTo(1);
        assertThat(saved.currentRevision()).isEqualTo(2);
        assertThat(saved.deadline().date()).hasToString("2026-09-20");
        assertThat(reminders("CANCELED")).isEqualTo(3);
        assertThat(reminders("PENDING")).isEqualTo(3);
        members.refresh(first);
        assertThat(members.notifications(first).items()).hasSize(1);
        raw.put("aplyYmd", "상시"); importPolicy();
        members.deliver(first);
        assertThat(members.saved(first).items().getFirst().deadline().date()).isNull();
        assertThat(reminders("PENDING")).isZero();
        assertThat(reminders("CANCELED")).isEqualTo(6);
    }

    @Test
    @DisplayName("서울 자정이 지난 예약은 몰아서 전달하지 않고 당일 예약만 전달한다")
    void skipsPastSeoulDay() {
        members.save(first, NUMBER);
        time("2026-09-05T15:00:00Z");
        members.deliver(first);
        assertThat(reminders("SKIPPED")).isEqualTo(1);
        assertThat(members.notifications(first).items()).isEmpty();
        time("2026-09-08T15:00:00Z");
        members.deliver(first);
        assertThat(reminders("DELIVERED")).isEqualTo(1);
        assertThat(members.notifications(first).items()).hasSize(1);
    }

    @Test
    @DisplayName("마감일이 그대로인 내용 변경은 이미 전달한 당일 마감 알림을 다시 보내지 않는다")
    void doesNotRepeatDeliveredDeadline() {
        members.save(first, NUMBER); members.deliver(first);
        raw.put("plcySprtCn", "지원 안내 수정"); importPolicy();
        members.deliver(first);
        assertThat(reminders("DELIVERED")).isEqualTo(1);
        assertThat(members.notifications(first).items()).hasSize(2);
    }

    @Test
    @DisplayName("저장 트랜잭션이 실패하면 관심 정책과 발송 예약이 모두 남지 않는다")
    void rollsBackTogether() {
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            members.save(first, NUMBER);
            transaction.setRollbackOnly();
        });
        assertThat(count("saved_policies")).isZero();
        assertThat(count("policy_reminders")).isZero();
    }

    @Test
    @DisplayName("주소 확인과 수신 동의는 별개이며 동의 이전 알림을 소급 발송하지 않는다")
    void verifiesThenOptsInWithoutBackfill() {
        members.save(first, NUMBER);
        emails.request(first, "first@example.test");
        String code = pendingCode(first);
        assertThat(emails.settings(first).verified()).isFalse();
        assertThat(emails.confirm(second, code)).isFalse();
        assertThat(emails.confirm(first, code)).isTrue();
        assertThat(emails.settings(first).verified()).isTrue();
        assertThat(emails.settings(first).enabled()).isFalse();
        members.deliver(first);
        emails.consent(first, true);
        assertThat(policyMailIds()).isEmpty();
        time("2026-09-08T15:00:00Z"); members.deliver(first); members.deliver(first);
        assertThat(policyMailIds()).hasSize(1);
        UUID mail = policyMailIds().getFirst(); emailDelivery.deliver(mail);
        assertThat(mailState(mail)).isEqualTo("SENT");
        var body = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(emailSender).send(org.mockito.ArgumentMatchers.eq("first@example.test"), any(), body.capture());
        assertThat(body.getValue()).contains("마감 3일 전", "/policies/" + NUMBER, "이메일 수신 해제:");
        assertThat(jdbc.sql("SELECT address_cipher FROM member_email_settings WHERE member_id = :member").param("member", first).query(String.class).single())
                .doesNotContain("first@example.test");
        assertThat(jdbc.sql("SELECT count(*) FROM member_email_outbox WHERE code_cipher IS NOT NULL").query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("확인 코드 실패 횟수는 오류 응답 뒤에도 남고 다섯 번 실패한 코드는 사용할 수 없다")
    void commitsFailedCodeAttempts() throws Exception {
        emails.request(first, "first@example.test"); String code = pendingCode(first);
        String wrong = code.equals("00000000") ? "11111111" : "00000000";
        for (int i = 0; i < 5; i++) mvc.perform(post("/api/v1/me/email-verification/confirm")
                .with(oauth2Login().oauth2User(user(first))).with(csrf()).contentType("application/json")
                .content("{\"code\":\"" + wrong + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_CODE_INVALID"));
        assertThat(jdbc.sql("SELECT attempts FROM member_email_settings WHERE member_id = :member").param("member", first).query(Integer.class).single()).isEqualTo(5);
        assertThat(emails.confirm(first, code)).isFalse();
        assertThat(emails.settings(first).verificationExpiresAt()).isNull();
    }

    @Test
    @DisplayName("확인 코드는 만료 순간부터 거절하고 재요청 간격과 시간당 제한은 주소 삭제 뒤에도 유지한다")
    void expiresAndLimitsRequests() {
        emails.request(first, "first@example.test"); String old = pendingCode(first);
        assertThatThrownBy(() -> emails.request(first, "second@example.test")).isInstanceOf(MemberEmailStore.EmailException.class);
        time("2026-09-04T15:10:00Z"); assertThat(emails.confirm(first, old)).isFalse();
        emails.request(first, "second@example.test");
        assertThat(emails.confirm(first, old)).isFalse();
        assertThat(emails.settings(first).address()).isEqualTo("second@example.test");
        time("2026-09-04T15:11:00Z"); emails.request(first, "third@example.test");
        emails.remove(first); time("2026-09-04T15:12:00Z");
        assertThatThrownBy(() -> emails.request(first, "fourth@example.test")).isInstanceOf(MemberEmailStore.EmailException.class);
        assertThat(emails.settings(first).addressRegistered()).isFalse();
    }

    @Test
    @DisplayName("이메일 API는 계정과 CSRF를 확인하며 미설정 상태에서도 수신 해제와 삭제가 가능하다")
    void protectsEmailOwnershipAndDisabledSettings() throws Exception {
        mvc.perform(get("/api/v1/me/email-settings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/me/email-verification").with(oauth2Login().oauth2User(user(first)))
                .contentType("application/json").content("{\"address\":\"first@example.test\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/me/email-settings").with(oauth2Login().oauth2User(user(first))).with(csrf())
                .contentType("application/json").content("{\"enabled\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        verifiedEmail(); emails.consent(first, true);
        mvc.perform(get("/api/v1/me/email-settings").with(oauth2Login().oauth2User(user(second))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.addressRegistered").value(false)).andExpect(content().json("{\"address\":null}"));
        when(emailSender.available()).thenReturn(false);
        emails.consent(first, false); emails.remove(first);
        assertThat(emails.settings(first).available()).isFalse();
        assertThatThrownBy(() -> emails.request(first, "first@example.test")).isInstanceOf(MemberEmailStore.EmailException.class);
    }

    @Test
    @DisplayName("동시 발송은 한 번만 배정하고 외부 전송은 DB 트랜잭션 밖에서 실행한다")
    void claimsEmailOnceOutsideTransaction() throws Exception {
        emails.request(first, "first@example.test");
        UUID id = jdbc.sql("SELECT id FROM member_email_outbox WHERE member_id = :member").param("member", first).query(UUID.class).single();
        doAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(call.getArgument(0, String.class)).isEqualTo("first@example.test");
            assertThat(call.getArgument(2, String.class)).contains("확인 코드:");
            return null;
        }).when(emailSender).send(any(), any(), any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> emailDelivery.deliver(id)); var b = pool.submit(() -> emailDelivery.deliver(id));
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        org.mockito.Mockito.verify(emailSender, org.mockito.Mockito.times(1)).send(any(), any(), any());
        assertThat(mailState(id)).isEqualTo("SENT");
        assertThat(emails.settings(first).verificationDelivery()).isEqualTo("SENT");
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                assertThatThrownBy(() -> emailDelivery.deliver(id)).isInstanceOf(IllegalStateException.class));
    }

    @Test
    @DisplayName("결과 미확인과 중단된 발송은 다시 보내지 않고 암호화한 코드도 지운다")
    void doesNotRetryUnknownDelivery() {
        emails.request(first, "first@example.test");
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("인공 전송 중단")).when(emailSender).send(any(), any(), any());
        emailDelivery.deliverPending(); emailDelivery.deliverPending();
        assertThat(emails.settings(first).verificationDelivery()).isEqualTo("UNKNOWN");
        org.mockito.Mockito.verify(emailSender, org.mockito.Mockito.times(1)).send(any(), any(), any());
        time("2026-09-04T15:01:00Z"); emails.request(first, "first@example.test");
        jdbc.sql("UPDATE member_email_outbox SET state = 'SENDING', started_at = :now WHERE state = 'PENDING'")
                .param("now", MemberEmailStore.at(clock.instant())).update();
        time("2026-09-04T15:04:00Z"); emailDelivery.deliverPending();
        assertThat(jdbc.sql("SELECT count(*) FROM member_email_outbox WHERE state = 'UNKNOWN'").query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM member_email_outbox WHERE code_cipher IS NOT NULL").query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("수신 해제·주소 변경·저장 해제·개정 변경은 이전 미발송 정책 메일을 취소한다")
    void cancelsPolicyEmailBeforeDispatch() {
        verifiedEmail(); emails.consent(first, true); members.save(first, NUMBER); members.deliver(first);
        UUID original = policyMailIds().getFirst();
        emails.consent(first, false); emails.consent(first, true); emailDelivery.deliver(original);
        assertThat(mailState(original)).isEqualTo("CANCELED");
        raw.put("plcySprtCn", "변경 안내 1"); importPolicy(); members.refresh(first);
        UUID change = pendingPolicyMail(); members.remove(first, NUMBER); emailDelivery.deliver(change);
        assertThat(mailState(change)).isEqualTo("CANCELED");
        members.save(first, NUMBER); raw.put("plcySprtCn", "변경 안내 2"); importPolicy(); members.refresh(first);
        UUID stale = pendingPolicyMail(); raw.put("plcySprtCn", "변경 안내 3"); importPolicy(); emailDelivery.deliver(stale);
        assertThat(mailState(stale)).isEqualTo("CANCELED");
        UUID current = pendingPolicyMail(); time("2026-09-04T15:01:00Z"); emails.request(first, "new@example.test"); emailDelivery.deliver(current);
        assertThat(mailState(current)).isEqualTo("CANCELED");
        assertThat(emails.settings(first).verified()).isFalse(); assertThat(emails.settings(first).enabled()).isFalse();
        org.mockito.Mockito.verify(emailSender, org.mockito.Mockito.never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("서비스 내 알림과 이메일 요청은 함께 롤백하고 지난 서울 날짜의 마감 메일은 보내지 않는다")
    void rollsBackEmailAndSkipsExpiredDeadline() {
        verifiedEmail(); emails.consent(first, true); members.save(first, NUMBER);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> { members.deliver(first); tx.setRollbackOnly(); });
        assertThat(members.notifications(first).items()).isEmpty(); assertThat(policyMailIds()).isEmpty();
        members.deliver(first); UUID id = policyMailIds().getFirst();
        time("2026-09-05T15:00:00Z"); emailDelivery.deliver(id);
        assertThat(mailState(id)).isEqualTo("CANCELED");
        org.mockito.Mockito.verify(emailSender, org.mockito.Mockito.never()).send(any(), any(), any());
    }

    private void verifiedEmail() {
        emails.request(first, "first@example.test"); assertThat(emails.confirm(first, pendingCode(first))).isTrue();
    }
    private String pendingCode(UUID member) {
        return jdbc.sql("SELECT settings_version, code_cipher FROM member_email_outbox WHERE member_id = :member AND state = 'PENDING' AND kind = 'VERIFICATION'")
                .param("member", member).query((rs, row) -> crypto.decrypt(MemberEmailStore.context(member, rs.getObject(1, UUID.class), "code"), rs.getString(2))).single();
    }
    private List<UUID> policyMailIds() { return jdbc.sql("SELECT id FROM member_email_outbox WHERE kind = 'POLICY'").query(UUID.class).list(); }
    private UUID pendingPolicyMail() { return jdbc.sql("SELECT id FROM member_email_outbox WHERE kind = 'POLICY' AND state = 'PENDING'").query(UUID.class).single(); }
    private String mailState(UUID id) { return jdbc.sql("SELECT state FROM member_email_outbox WHERE id = :id").param("id", id).query(String.class).single(); }

    private void importPolicy() {
        var item = new OntongPolicyCapture(mapper).item(raw);
        capture++;
        policies.importPolicy(item.number(), item.content(), item.rawPolicy(), Instant.parse("2026-09-04T00:00:00Z").plusSeconds(capture),
                "member-test-" + capture, item.contentHash());
    }
    private void time(String now) {
        var fixed = Clock.fixed(Instant.parse(now), ZoneId.of("UTC"));
        when(clock.instant()).thenReturn(fixed.instant());
        doAnswer(call -> fixed.withZone(call.getArgument(0))).when(clock).withZone(any());
    }
    private long count(String table) { return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single(); }
    private long reminders(String state) { return jdbc.sql("SELECT count(*) FROM policy_reminders WHERE state = :state").param("state", state).query(Long.class).single(); }
    private DefaultOAuth2User user(UUID id) {
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_MEMBER")),
                Map.of("memberId", id.toString(), "displayName", "테스트 회원", "suggestedBirthDate", ""), "memberId");
    }
}
