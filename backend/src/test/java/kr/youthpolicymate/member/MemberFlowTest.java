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
        "app.email.provider=resend", "app.email.resend.webhook-secret=whsec_dGVzdC13ZWJob29rLXNlY3JldA==",
        "KAKAO_CLIENT_ID=test-kakao", "KAKAO_CLIENT_SECRET=test-secret",
        "NAVER_CLIENT_ID=test-naver", "NAVER_CLIENT_SECRET=test-secret",
        "app.email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc
class MemberFlowTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired MemberPolicyStore members;
    @Autowired MemberIdentityStore identities;
    @Autowired PolicyCatalogStore policies;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean JdbcClient jdbc;
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
        when(emailSender.provider()).thenReturn("resend");
    }

    private static final String WEBHOOK_SECRET = "whsec_dGVzdC13ZWJob29rLXNlY3JldA==";

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webhook(
            UUID outbox, UUID message, String type, String occurred) throws Exception {
        String body = mapper.writeValueAsString(Map.of("type", type, "created_at", occurred,
                "data", Map.of("email_id", message.toString(), "tags", Map.of("outbox_id", outbox.toString()))));
        String id = "msg-test-" + outbox;
        long timestamp = Instant.now().getEpochSecond();
        return post("/api/v1/webhooks/resend").contentType("application/json").content(body)
                .header("svix-id", id).header("svix-timestamp", timestamp)
                .header("svix-signature", new com.svix.Webhook(WEBHOOK_SECRET).sign(id, timestamp, body));
    }

    private UUID verificationMail() {
        return jdbc.sql("SELECT id FROM member_email_outbox WHERE member_id = :member ORDER BY created_at DESC LIMIT 1")
                .param("member", first).query(UUID.class).single();
    }

    @Test
    @DisplayName("서명된 웹훅은 API 응답보다 먼저 와도 전달 결과를 보존하고 중복·순서 역전을 무시한다")
    void keepsEarlyWebhookResult() throws Exception {
        emails.request(first, "first@example.test");
        UUID mail = verificationMail(); UUID message = UUID.randomUUID();
        doAnswer(call -> {
            mvc.perform(webhook(mail, message, "email.delivered", "2026-09-07T12:00:02Z")).andExpect(status().isNoContent());
            return message.toString();
        }).when(emailSender).send(any(), any(), any(), any());
        emailDelivery.deliver(mail);
        assertThat(mailState(mail)).isEqualTo("DELIVERED");
        mvc.perform(webhook(mail, message, "email.delivered", "2026-09-07T12:00:02Z")).andExpect(status().isNoContent());
        mvc.perform(webhook(mail, message, "email.sent", "2026-09-07T12:00:03Z")).andExpect(status().isNoContent());
        assertThat(emails.settings(first).verificationDelivery()).isEqualTo("DELIVERED");
        mvc.perform(webhook(mail, UUID.randomUUID(), "email.bounced", "2026-09-07T12:00:04Z")).andExpect(status().isNoContent());
        assertThat(mailState(mail)).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("접수 결과 미확인은 웹훅으로 확정하며 반송 시 동의·코드·미발송 요청을 해제한다")
    void reconcilesUnknownAndStopsBouncedAddress() throws Exception {
        emails.request(first, "first@example.test");
        UUID mail = verificationMail(); UUID message = UUID.randomUUID();
        var code = pendingCode(first);
        assertThat(emails.confirm(first, code)).isTrue();
        emails.consent(first, true);
        jdbc.sql("UPDATE member_email_outbox SET state = 'UNKNOWN', provider = 'resend' WHERE id = :id").param("id", mail).update();
        UUID pending = UUID.randomUUID();
        jdbc.sql("INSERT INTO member_email_outbox(id, member_id, settings_version, kind, state, created_at) SELECT :pending, member_id, settings_version, 'VERIFICATION', 'PENDING', created_at FROM member_email_outbox WHERE id = :id")
                .param("pending", pending).param("id", mail).update();
        mvc.perform(webhook(mail, message, "email.sent", "2026-09-07T12:00:02Z")).andExpect(status().isNoContent());
        assertThat(mailState(mail)).isEqualTo("SENT");
        // 지연 도착한 반송도 이미 접수된 주소의 추가 발송을 중단한다.
        mvc.perform(webhook(mail, message, "email.bounced", "2026-09-07T12:00:01Z")).andExpect(status().isNoContent());
        assertThat(mailState(mail)).isEqualTo("BOUNCED");
        assertThat(mailState(pending)).isEqualTo("CANCELED");
        assertThat(emails.settings(first).deliveryIssue()).isEqualTo("BOUNCED");
        assertThat(emails.settings(first).enabled()).isFalse();
        assertThat(emails.settings(first).verified()).isFalse();
        assertThatThrownBy(() -> emails.consent(first, true)).isInstanceOf(MemberEmailStore.EmailException.class);
        mvc.perform(webhook(mail, message, "email.delivered", "2026-09-07T12:00:09Z")).andExpect(status().isNoContent());
        assertThat(mailState(mail)).isEqualTo("BOUNCED");
    }

    @Test
    @DisplayName("이전 이메일의 신고는 새 주소·동의를 바꾸지 않고 잘못된 서명과 오래된 요청은 거절한다")
    void isolatesSettingsAndVerifiesSignature() throws Exception {
        emails.request(first, "first@example.test"); UUID mail = verificationMail(); UUID message = UUID.randomUUID();
        emailDelivery.deliver(mail);
        time("2026-09-08T15:00:00Z");
        emails.request(first, "new@example.test");
        assertThat(emails.confirm(first, pendingCode(first))).isTrue(); emails.consent(first, true);
        mvc.perform(webhook(mail, message, "email.complained", "2026-09-08T15:00:01Z")).andExpect(status().isNoContent());
        assertThat(emails.settings(first).address()).isEqualTo("new@example.test");
        assertThat(emails.settings(first).enabled()).isTrue();
        assertThat(emails.settings(first).deliveryIssue()).isNull();
        mvc.perform(webhook(mail, message, "email.delivered", "2026-09-08T15:00:02Z").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/webhooks/resend").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        String oldId = "old-message"; long oldTime = Instant.now().minusSeconds(601).getEpochSecond();
        mvc.perform(post("/api/v1/webhooks/resend").contentType("application/json").content("{}")
                .header("svix-id", oldId).header("svix-timestamp", oldTime)
                .header("svix-signature", new com.svix.Webhook(WEBHOOK_SECRET).sign(oldId, oldTime, "{}")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/webhooks/resend").contentType("application/json").content("x".repeat(65537)))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(put("/api/v1/me/email-settings").with(oauth2Login().oauth2User(user(first))).contentType("application/json").content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("설정한 카카오·네이버 순서로 로그인 제공자를 반환하고 OAuth 인증 주소로 이동한다")
    void exposesConfiguredLoginProviders() throws Exception {
        mvc.perform(get("/api/v1/session")).andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.providers.length()").value(2))
                .andExpect(jsonPath("$.providers[0].id").value("kakao"))
                .andExpect(jsonPath("$.providers[1].id").value("naver"));
        mvc.perform(get("/oauth2/authorization/kakao")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://kauth.kakao.com/oauth/authorize?")));
        mvc.perform(get("/oauth2/authorization/naver")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://nid.naver.com/oauth2.0/authorize?")));
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
    @DisplayName("변경 비교는 본인의 저장 당시부터 최신 개정까지 조회하며 저장 기준과 알림을 변경하지 않는다")
    void comparesSavedPolicyWithLatestRevision() throws Exception {
        var original = policies.find(NUMBER).orElseThrow().content();
        members.save(first, NUMBER);
        raw.put("plcyNm", "중간 공고").put("aplyYmd", "20260901 ~ 20260920");
        importPolicy();
        members.save(second, NUMBER);
        raw.put("plcyNm", "최신 공고").put("aplyYmd", "20260901 ~ 20260930").put("apiKeyNm", "not-public-test-value");
        importPolicy();

        mvc.perform(get("/api/v1/me/policies/" + NUMBER + "/changes")).andExpect(status().isUnauthorized());
        var result = mvc.perform(get("/api/v1/me/policies/" + NUMBER + "/changes").with(oauth2Login().oauth2User(user(first))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.policyNumber").value(NUMBER)).andExpect(jsonPath("$.savedAt").isNotEmpty())
                .andExpect(jsonPath("$.saved.revision").value(1)).andExpect(jsonPath("$.current.revision").value(3))
                .andExpect(jsonPath("$.saved.content.title").value(original.title()))
                .andExpect(jsonPath("$.current.content.title").value("최신 공고"))
                .andExpect(jsonPath("$.saved.content.applicationPeriod").value(original.applicationPeriod()))
                .andExpect(jsonPath("$.current.content.applicationPeriod").value("20260901 ~ 20260930"))
                .andExpect(jsonPath("$.saved.sourceCapturedAt").isNotEmpty()).andExpect(jsonPath("$.current.sourceCapturedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain("apiKeyNm", "not-public-test-value", first.toString(), second.toString(), "rawPolicy");
        assertThat(members.changes(second, NUMBER).saved().revision()).isEqualTo(2);
        assertThat(members.changes(first, NUMBER).saved().revision()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT current_revision FROM saved_policies WHERE member_id = :member").param("member", first)
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT DISTINCT policy_revision FROM policy_reminders WHERE member_id = :member").param("member", first)
                .query(Long.class).single()).isEqualTo(1);
        assertThat(count("member_notifications")).isZero();
        assertThat(count("member_email_outbox")).isZero();
        members.remove(first, NUMBER);
        mvc.perform(get("/api/v1/me/policies/" + NUMBER + "/changes").with(oauth2Login().oauth2User(user(first))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
        assertThat(members.changes(second, NUMBER).saved().revision()).isEqualTo(2);
        members.save(first, NUMBER);
        var savedAgain = members.changes(first, NUMBER);
        assertThat(savedAgain.saved().revision()).isEqualTo(3);
        assertThat(savedAgain.saved().content()).isEqualTo(savedAgain.current().content());
        assertThat(savedAgain.saved().sourceCapturedAt()).isEqualTo(savedAgain.current().sourceCapturedAt());
    }

    @Test
    @DisplayName("알림은 100건 이후에도 조회하고 같은 시각의 순서와 전체 미읽음 수를 유지한다")
    void pagesNotificationsAndCountsUnread() throws Exception {
        insertNotifications(first, 105, 0);
        insertNotifications(second, 3, 1000);
        var last = members.notifications(first, 6, 20, MemberResponses.NotificationFilter.ALL);
        assertThat(last.total()).isEqualTo(105);
        assertThat(last.unreadCount()).isEqualTo(53);
        assertThat(last.hasNext()).isFalse();
        assertThat(last.items()).extracting(MemberResponses.Notification::id).containsExactly(
                notificationId(5), notificationId(4), notificationId(3), notificationId(2), notificationId(1));
        var unread = members.notifications(first, 2, 20, MemberResponses.NotificationFilter.UNREAD);
        assertThat(unread.total()).isEqualTo(53);
        assertThat(unread.unreadCount()).isEqualTo(53);
        assertThat(unread.hasNext()).isTrue();
        assertThat(unread.items()).hasSize(20).allMatch(item -> !item.read());
        assertThat(unread.items().getFirst().id()).isEqualTo(notificationId(65));
        assertThat(members.notifications(first, Integer.MAX_VALUE, 50, MemberResponses.NotificationFilter.ALL).items()).isEmpty();
        mvc.perform(get("/api/v1/me/notifications").param("page", "6").with(oauth2Login().oauth2User(user(first))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items.length()").value(5)).andExpect(jsonPath("$.page").value(6))
                .andExpect(jsonPath("$.pageSize").value(20)).andExpect(jsonPath("$.total").value(105))
                .andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.unreadCount").value(53));
        mvc.perform(get("/api/v1/me/notifications").param("filter", "UNREAD").with(oauth2Login().oauth2User(user(second))))
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.unreadCount").value(2));
    }

    @Test
    @DisplayName("알림 조회는 잘못된 페이지와 필터를 거절하고 읽음 처리는 본인·CSRF·최초 읽은 시각을 지킨다")
    void protectsNotificationRequests() throws Exception {
        mvc.perform(get("/api/v1/me/notifications")).andExpect(status().isUnauthorized());
        for (var query : List.of("page=0", "page=-1", "pageSize=0", "pageSize=51", "filter=INVALID")) {
            mvc.perform(get("/api/v1/me/notifications?" + query).with(oauth2Login().oauth2User(user(first))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MEMBER_INPUT"));
        }
        mvc.perform(get("/api/v1/me/notifications").with(oauth2Login().oauth2User(user(first))))
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.unreadCount").value(0)).andExpect(jsonPath("$.hasNext").value(false));
        insertNotifications(first, 1, 0);
        var path = "/api/v1/me/notifications/" + notificationId(1) + "/read";
        mvc.perform(post(path).with(oauth2Login().oauth2User(user(first)))).andExpect(status().isForbidden());
        mvc.perform(post(path).with(oauth2Login().oauth2User(user(second))).with(csrf())).andExpect(status().isNoContent());
        assertThat(notifications(first).unreadCount()).isEqualTo(1);
        mvc.perform(post(path).with(oauth2Login().oauth2User(user(first))).with(csrf())).andExpect(status().isNoContent());
        assertThat(notifications(first).unreadCount()).isZero();
        jdbc.sql("UPDATE member_notifications SET read_at = '2026-09-05T01:00:00Z' WHERE member_id = :member")
                .param("member", first).update();
        mvc.perform(post(path).with(oauth2Login().oauth2User(user(first))).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.sql("SELECT read_at FROM member_notifications WHERE member_id = :member").param("member", first)
                .query((rs, row) -> rs.getTimestamp("read_at").toInstant()).single()).isEqualTo(Instant.parse("2026-09-05T01:00:00Z"));
    }

    private void insertNotifications(UUID member, int count, int offset) {
        jdbc.sql("""
                INSERT INTO member_notifications (id, member_id, policy_number, generation, policy_revision, kind, title, message, created_at, read_at)
                SELECT CAST('00000000-0000-0000-0000-' || lpad(CAST(i + :offset AS text), 12, '0') AS uuid),
                       :member, :policy, gen_random_uuid(), i, 'POLICY_CHANGED', '정책 변경', '신청 기간이 바뀌었습니다.',
                       TIMESTAMPTZ '2026-09-04T15:00:00Z',
                       CASE WHEN i % 2 = 0 THEN TIMESTAMPTZ '2026-09-05T00:00:00Z' END
                FROM generate_series(1, :count) AS i
                """).param("member", member).param("policy", NUMBER).param("count", count).param("offset", offset).update();
    }

    private static String notificationId(int number) { return "00000000-0000-0000-0000-%012d".formatted(number); }

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
        assertThat(notifications(first).items()).isEmpty();
        members.save(first, NUMBER);
        members.deliver(first); members.deliver(first);
        assertThat(reminders("DELIVERED")).isEqualTo(1);
        assertThat(notifications(first).items()).hasSize(1);
        var id = UUID.fromString(notifications(first).items().getFirst().id());
        members.read(second, id);
        assertThat(notifications(first).items().getFirst().read()).isFalse();
        members.read(first, id);
        assertThat(notifications(first).items().getFirst().read()).isTrue();
    }

    @Test
    @DisplayName("변경 없는 관심 정책 20건과 빈 목록을 각각 SELECT 세 번으로 조회한다")
    void loadsSavedPoliciesWithThreeQueries() {
        for (int index = 1; index <= 20; index++) {
            var number = "2026090600540011%04d".formatted(index);
            raw.put("plcyNo", number).put("plcyNm", "관심 정책 " + index)
                    .put("aplyYmd", "20260901 ~ 202610%02d".formatted(index));
            importPolicy();
            members.save(first, number);
        }

        org.mockito.Mockito.clearInvocations(jdbc);
        var saved = members.saved(first).items();
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(3)).sql(org.mockito.ArgumentMatchers.anyString());
        assertThat(saved).hasSize(20);
        assertThat(saved).extracting(MemberResponses.Saved::title)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 20)
                        .mapToObj(index -> "관심 정책 " + index).toList());
        assertThat(saved.getFirst().deadline().date()).hasToString("2026-10-01");
        assertThat(saved.getFirst().applicationPeriod()).isEqualTo("20260901 ~ 20261001");
        assertThat(saved).allSatisfy(policy -> {
            assertThat(policy.savedRevision()).isOne();
            assertThat(policy.currentRevision()).isOne();
        });
        assertThat(notifications(first).items()).isEmpty();

        org.mockito.Mockito.clearInvocations(jdbc);
        assertThat(members.saved(second).items()).isEmpty();
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.times(3)).sql(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("내 일정은 가까운 마감부터 조회하고 상시·미확인·종료를 구분한다")
    void groupsSavedRecruitmentWithoutInventingDeadlines() throws Exception {
        var fixtures = List.of(
                List.of("마감된 날짜", "0057001", "20260901 ~ 20260904"),
                List.of("먼 마감", "0057001", "20260918 ~ 20260920"),
                List.of("가까운 마감", "0057001", "20260901 ~ 20260906"),
                List.of("상시 접수", "0057002", ""),
                List.of("기간 미확인", "0057001", "추후 안내"),
                List.of("접수 종료", "0057003", ""));
        for (int index = 0; index < fixtures.size(); index++) {
            var fixture = fixtures.get(index);
            var number = "999900000000000000%02d".formatted(index);
            raw.put("plcyNo", number).put("plcyNm", fixture.get(0)).put("aplyPrdSeCd", fixture.get(1)).put("aplyYmd", fixture.get(2));
            importPolicy(); members.save(first, number);
        }
        var saved = members.saved(first).items();
        assertThat(saved.subList(0, 2)).extracting(MemberResponses.Saved::title).containsExactly("가까운 마감", "먼 마감");
        assertThat(saved.subList(4, 6)).allSatisfy(policy -> assertThat(policy.recruitment().status()).isEqualTo(kr.youthpolicymate.policy.RecruitmentStatus.CLOSED));
        assertThat(saved.stream().filter(policy -> List.of("상시 접수", "기간 미확인", "접수 종료").contains(policy.title())))
                .allSatisfy(policy -> assertThat(policy.deadline().date()).isNull());
        assertThat(saved).extracting(policy -> policy.recruitment().status().name())
                .containsExactly("OPEN", "BEFORE_OPENING", "UNKNOWN", "ROLLING", "CLOSED", "CLOSED");
        mvc.perform(get("/api/v1/me/policies").with(oauth2Login().oauth2User(user(first))))
                .andExpect(jsonPath("$.items[0].recruitment.status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].recruitment.evaluatedAt").value("2026-09-04T15:00:00Z"));
    }

    @Test
    @DisplayName("개정이 그대로여도 서울 날짜가 바뀌면 내 일정과 공개 화면의 마감 상태가 함께 바뀐다")
    void refreshesRecruitmentAcrossSeoulMidnight() throws Exception {
        members.save(first, NUMBER);
        time("2026-09-12T14:59:59Z");
        assertThat(members.saved(first).items().getFirst().recruitment().status()).isEqualTo(kr.youthpolicymate.policy.RecruitmentStatus.OPEN);
        time("2026-09-12T15:00:00Z");
        var saved = members.saved(first).items().getFirst();
        assertThat(saved.recruitment().status()).isEqualTo(kr.youthpolicymate.policy.RecruitmentStatus.CLOSED);
        assertThat(saved.deadline().date()).hasToString("2026-09-12");
        assertThat(saved.currentRevision()).isOne();
        mvc.perform(get("/api/v1/policies/" + NUMBER)).andExpect(jsonPath("$.recruitment.status").value("CLOSED"));
    }

    @Test
    @DisplayName("관심 정책 조회가 끝날 때까지 수집의 정책 갱신을 막는다")
    void keepsPolicyLockedDuringSavedListRead() {
        members.save(first, NUMBER);
        var transaction = new TransactionTemplate(transactions);
        try (var pool = Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(status -> {
                assertThat(members.saved(first).items()).hasSize(1);
                var update = pool.submit(() -> transaction.executeWithoutResult(other -> {
                    jdbc.sql("SET LOCAL lock_timeout = '100ms'").update();
                    jdbc.sql("UPDATE policies SET last_collected_at = last_collected_at WHERE policy_number = :number")
                            .param("number", NUMBER).update();
                }));
                assertThatThrownBy(() -> update.get(5, TimeUnit.SECONDS))
                        .hasRootCauseInstanceOf(java.sql.SQLException.class)
                        .rootCause().extracting(cause -> ((java.sql.SQLException) cause).getSQLState())
                        .isEqualTo("55P03");
            });
        }
        raw.put("aplyYmd", "20260901 ~ 20260920");
        importPolicy();
        assertThat(members.saved(first).items().getFirst().currentRevision()).isEqualTo(2);
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
        assertThat(notifications(first).items()).hasSize(1);
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
        assertThat(notifications(first).items()).isEmpty();
        time("2026-09-08T15:00:00Z");
        members.deliver(first);
        assertThat(reminders("DELIVERED")).isEqualTo(1);
        assertThat(notifications(first).items()).hasSize(1);
    }

    @Test
    @DisplayName("마감일이 그대로인 내용 변경은 이미 전달한 당일 마감 알림을 다시 보내지 않는다")
    void doesNotRepeatDeliveredDeadline() {
        members.save(first, NUMBER); members.deliver(first);
        raw.put("plcySprtCn", "지원 안내 수정"); importPolicy();
        members.deliver(first);
        assertThat(reminders("DELIVERED")).isEqualTo(1);
        assertThat(notifications(first).items()).hasSize(2);
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
        org.mockito.Mockito.verify(emailSender).send(any(), org.mockito.ArgumentMatchers.eq("first@example.test"), any(), body.capture());
        assertThat(body.getValue()).contains("마감 3일 전", "/policies/" + NUMBER, "이메일 수신 해제:");
        assertThat(jdbc.sql("SELECT address_cipher FROM member_email_settings WHERE member_id = :member").param("member", first).query(String.class).single())
                .doesNotContain("first@example.test");
        assertThat(jdbc.sql("SELECT count(*) FROM member_email_outbox WHERE code_cipher IS NOT NULL").query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("이메일 주소 제약은 HTTP와 서비스 호출에 같게 적용하고 잘못된 주소는 저장하지 않는다")
    void validatesEmailAddresses() throws Exception {
        for (String address : java.util.Arrays.asList(null, "", " ", "not-an-address", ".first@example.test",
                "first..last@example.test", "x".repeat(255) + "@example.test")) {
            mvc.perform(post("/api/v1/me/email-verification").with(oauth2Login().oauth2User(user(first)))
                    .with(csrf()).contentType("application/json").content(mapper.writeValueAsString(new MemberEmailAddress(address))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MEMBER_INPUT"));
            assertThatIllegalArgumentException().isThrownBy(() -> emails.request(first, address));
        }
        assertThat(count("member_email_outbox")).isZero();
        emails.request(first, "first.last+tag@example.test");
        assertThat(emails.settings(first).address()).isEqualTo("first.last+tag@example.test");
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
            assertThat(call.getArgument(1, String.class)).isEqualTo("first@example.test");
            assertThat(call.getArgument(3, String.class)).contains("확인 코드:");
            return null;
        }).when(emailSender).send(any(), any(), any(), any());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> emailDelivery.deliver(id)); var b = pool.submit(() -> emailDelivery.deliver(id));
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        org.mockito.Mockito.verify(emailSender, org.mockito.Mockito.times(1)).send(any(), any(), any(), any());
        assertThat(mailState(id)).isEqualTo("SENT");
        assertThat(emails.settings(first).verificationDelivery()).isEqualTo("SENT");
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
                assertThatThrownBy(() -> emailDelivery.deliver(id)).isInstanceOf(IllegalStateException.class));
    }

    @Test
    @DisplayName("결과 미확인과 중단된 발송은 다시 보내지 않고 암호화한 코드도 지운다")
    void doesNotRetryUnknownDelivery() {
        emails.request(first, "first@example.test");
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("인공 전송 중단")).when(emailSender).send(any(), any(), any(), any());
        emailDelivery.deliverPending(); emailDelivery.deliverPending();
        assertThat(emails.settings(first).verificationDelivery()).isEqualTo("UNKNOWN");
        org.mockito.Mockito.verify(emailSender, org.mockito.Mockito.times(1)).send(any(), any(), any(), any());
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
        org.mockito.Mockito.verify(emailSender, org.mockito.Mockito.never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("서비스 내 알림과 이메일 요청은 함께 롤백하고 지난 서울 날짜의 마감 메일은 보내지 않는다")
    void rollsBackEmailAndSkipsExpiredDeadline() {
        verifiedEmail(); emails.consent(first, true); members.save(first, NUMBER);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> { members.deliver(first); tx.setRollbackOnly(); });
        assertThat(notifications(first).items()).isEmpty(); assertThat(policyMailIds()).isEmpty();
        members.deliver(first); UUID id = policyMailIds().getFirst();
        time("2026-09-05T15:00:00Z"); emailDelivery.deliver(id);
        assertThat(mailState(id)).isEqualTo("CANCELED");
        org.mockito.Mockito.verify(emailSender, org.mockito.Mockito.never()).send(any(), any(), any(), any());
    }

    private void verifiedEmail() {
        emails.request(first, "first@example.test"); assertThat(emails.confirm(first, pendingCode(first))).isTrue();
    }
    private String pendingCode(UUID member) {
        return jdbc.sql("SELECT settings_version, code_cipher FROM member_email_outbox WHERE member_id = :member AND state = 'PENDING' AND kind = 'VERIFICATION'")
                .param("member", member).query((rs, row) -> crypto.decrypt(MemberEmailStore.context(member, rs.getObject(1, UUID.class), "code"), rs.getString(2))).single();
    }
    private MemberResponses.Notifications notifications(UUID member) { return members.notifications(member, 1, 20, MemberResponses.NotificationFilter.ALL); }
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
