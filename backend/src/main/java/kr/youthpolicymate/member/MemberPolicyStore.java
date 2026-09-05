package kr.youthpolicymate.member;

import kr.youthpolicymate.policy.catalog.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@Profile("!preview")
public class MemberPolicyStore {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final PolicyCatalogStore policies;
    private final Clock clock;

    public MemberPolicyStore(JdbcClient jdbc, ObjectMapper mapper, PolicyCatalogStore policies, Clock clock) {
        this.jdbc = jdbc; this.mapper = mapper; this.policies = policies; this.clock = clock;
    }
    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))); }
    private void lock(UUID member) {
        if (jdbc.sql("SELECT id FROM members WHERE id = :id FOR UPDATE").param("id", member).query(UUID.class).optional().isEmpty()) {
            throw new org.springframework.security.access.AccessDeniedException("회원 확인이 필요합니다.");
        }
    }
    private void lockPolicy(String number) {
        if (jdbc.sql("SELECT policy_number FROM policies WHERE policy_number = :number AND current_revision > 0 FOR SHARE")
                .param("number", number).query(String.class).optional().isEmpty()) throw new PolicyNotFoundException();
    }

    public MemberResponses.Conditions conditions(UUID member) {
        return jdbc.sql("SELECT conditions FROM members WHERE id = :id").param("id", member)
                .query((rs, row) -> new MemberResponses.Conditions(rs.getString(1) == null ? null : mapper.readValue(rs.getString(1), BasicConditions.class)))
                .optional().orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("회원 확인이 필요합니다."));
    }
    @Transactional
    public void saveConditions(UUID member, BasicConditions conditions) {
        conditions.validate(today()); lock(member);
        jdbc.sql("UPDATE members SET conditions = CAST(:conditions AS jsonb), conditions_updated_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("id", member).param("conditions", mapper.writeValueAsString(conditions)).update();
    }
    @Transactional
    public void clearConditions(UUID member) {
        lock(member);
        jdbc.sql("UPDATE members SET conditions = NULL, conditions_updated_at = CURRENT_TIMESTAMP WHERE id = :id").param("id", member).update();
    }

    @Transactional
    public void save(UUID member, String number) {
        lock(member); lockPolicy(number);
        var policy = policies.find(number).orElseThrow(PolicyNotFoundException::new);
        var deadline = PolicyDeadline.from(policies.source(number).orElseThrow(PolicyNotFoundException::new));
        var generation = UUID.randomUUID();
        boolean exists = jdbc.sql("SELECT count(*) FROM saved_policies WHERE member_id = :member AND policy_number = :number")
                .param("member", member).param("number", number).query(Long.class).single() > 0;
        if (exists) return;
        jdbc.sql("""
                INSERT INTO saved_policies(member_id, policy_number, generation, saved_revision, current_revision, deadline_on, deadline_note)
                VALUES (:member, :number, :generation, :revision, :revision, :date, :note)
                """).param("member", member).param("number", number).param("generation", generation).param("revision", policy.revision())
                .param("date", deadline.date()).param("note", deadline.note()).update();
        plan(member, number, generation, policy.revision(), deadline);
    }
    private void plan(UUID member, String number, UUID generation, long revision, PolicyDeadline deadline) {
        if (deadline.date() == null) return;
        var today = today();
        for (int before : List.of(7,3,1)) {
            var due = deadline.date().minusDays(before);
            if (due.isBefore(today)) continue;
            jdbc.sql("""
                    INSERT INTO policy_reminders(id, member_id, policy_number, generation, policy_revision, days_before, due_on, state)
                    SELECT :id, :member, :number, :generation, :revision, :before, :due, 'PENDING'
                    WHERE NOT EXISTS (SELECT 1 FROM policy_reminders WHERE member_id = :member AND policy_number = :number
                        AND generation = :generation AND days_before = :before AND due_on = :due AND state = 'DELIVERED')
                    ON CONFLICT DO NOTHING
                    """).param("id", UUID.randomUUID()).param("member", member).param("number", number)
                    .param("generation", generation).param("revision", revision).param("before", before).param("due", due).update();
        }
    }
    @Transactional
    public void remove(UUID member, String number) {
        lock(member);
        cancel(member, number);
        jdbc.sql("DELETE FROM saved_policies WHERE member_id = :member AND policy_number = :number").param("member", member).param("number", number).update();
    }
    private void cancel(UUID member, String number) {
        jdbc.sql("""
                UPDATE member_email_outbox o SET state = 'CANCELED' FROM member_notifications n
                WHERE o.notification_id = n.id AND o.member_id = :member AND n.policy_number = :number AND o.state = 'PENDING'
                """).param("member", member).param("number", number).update();
        jdbc.sql("UPDATE policy_reminders SET state = 'CANCELED' WHERE member_id = :member AND policy_number = :number AND state = 'PENDING'")
                .param("member", member).param("number", number).update();
    }
    @Transactional
    public MemberResponses.SavedList saved(UUID member) {
        refresh(member);
        var items = jdbc.sql("""
                SELECT s.*, p.content FROM saved_policies s JOIN policies p ON p.policy_number = s.policy_number
                WHERE s.member_id = :member ORDER BY s.deadline_on NULLS LAST, s.saved_at DESC
                """).param("member", member).query((rs, row) -> {
                    var content = mapper.readValue(rs.getString("content"), PolicyContent.class);
                    return new MemberResponses.Saved(rs.getString("policy_number"), content.title(), rs.getLong("saved_revision"),
                            rs.getLong("current_revision"), new PolicyDeadline(rs.getObject("deadline_on", LocalDate.class), rs.getString("deadline_note")),
                            rs.getObject("saved_at", OffsetDateTime.class).toInstant(), content.applicationPeriod());
                }).list();
        return new MemberResponses.SavedList(items);
    }
    @Transactional
    public void refresh(UUID member) {
        lock(member);
        var numbers = jdbc.sql("SELECT policy_number FROM saved_policies WHERE member_id = :member ORDER BY policy_number")
                .param("member", member).query(String.class).list();
        for (var number : numbers) {
            lockPolicy(number);
            var policy = policies.find(number).orElseThrow(PolicyNotFoundException::new);
            var saved = jdbc.sql("SELECT current_revision, generation FROM saved_policies WHERE member_id = :member AND policy_number = :number")
                    .param("member", member).param("number", number).query((rs, row) -> new SavedVersion(rs.getLong(1), rs.getObject(2, UUID.class))).single();
            if (saved.revision() == policy.revision()) continue;
            var deadline = PolicyDeadline.from(policies.source(number).orElseThrow(PolicyNotFoundException::new));
            cancel(member, number);
            jdbc.sql("UPDATE saved_policies SET current_revision = :revision, deadline_on = :date, deadline_note = :note WHERE member_id = :member AND policy_number = :number")
                    .param("member", member).param("number", number).param("revision", policy.revision())
                    .param("date", deadline.date()).param("note", deadline.note()).update();
            plan(member, number, saved.generation(), policy.revision(), deadline);
            notify(member, number, saved.generation(), policy.revision(), "POLICY_CHANGED", policy.content().title(), "저장한 정책 내용이 바뀌었어요. 신청 조건과 기간을 다시 확인해주세요.");
        }
    }
    @Transactional
    public void deliver(UUID member) {
        refresh(member);
        var today = today();
        var due = jdbc.sql("""
                SELECT r.* , p.content->>'title' AS title FROM policy_reminders r
                JOIN saved_policies s ON s.member_id = r.member_id AND s.policy_number = r.policy_number
                    AND s.generation = r.generation AND s.current_revision = r.policy_revision
                JOIN policies p ON p.policy_number = s.policy_number AND p.current_revision = s.current_revision
                WHERE r.member_id = :member AND r.state = 'PENDING' AND r.due_on <= :today ORDER BY r.due_on
                """).param("member", member).param("today", today).query((rs, row) -> new Due(rs.getObject("id", UUID.class),
                        rs.getString("policy_number"), rs.getObject("generation", UUID.class), rs.getLong("policy_revision"),
                        rs.getInt("days_before"), rs.getObject("due_on", LocalDate.class), rs.getString("title"))).list();
        for (var reminder : due) {
            if (reminder.date().equals(today)) notify(member, reminder.number(), reminder.generation(), reminder.revision(),
                    "DEADLINE_" + reminder.before(), reminder.title(), "신청 마감 " + reminder.before() + "일 전이에요. 정확한 마감 시각은 공식 안내를 확인해주세요.");
            jdbc.sql("UPDATE policy_reminders SET state = :state, delivered_at = CASE WHEN :state = 'DELIVERED' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE id = :id")
                    .param("id", reminder.id()).param("state", reminder.date().equals(today) ? "DELIVERED" : "SKIPPED").update();
        }
    }
    private void notify(UUID member, String number, UUID generation, long revision, String kind, String title, String message) {
        UUID notification = UUID.randomUUID();
        int inserted = jdbc.sql("""
                INSERT INTO member_notifications(id, member_id, policy_number, generation, policy_revision, kind, title, message)
                VALUES (:id,:member,:number,:generation,:revision,:kind,:title,:message) ON CONFLICT DO NOTHING
                """).param("id",notification).param("member",member).param("number",number).param("generation",generation)
                .param("revision",revision).param("kind",kind).param("title",title).param("message",message).update();
        if (inserted == 0) return;
        var expires = kind.startsWith("DEADLINE_") ? today().plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime() : null;
        jdbc.sql("""
                INSERT INTO member_email_outbox(id, member_id, settings_version, kind, notification_id, state, created_at, expires_at)
                SELECT :id, member_id, version, 'POLICY', :notification, 'PENDING', :now, :expires
                FROM member_email_settings WHERE member_id = :member AND enabled AND verified_at IS NOT NULL
                ON CONFLICT DO NOTHING
                """).param("id", UUID.randomUUID()).param("member", member).param("notification", notification)
                .param("now", MemberEmailStore.at(clock.instant())).param("expires", expires).update();
    }
    public MemberResponses.Notifications notifications(UUID member) {
        return new MemberResponses.Notifications(jdbc.sql("SELECT * FROM member_notifications WHERE member_id = :member ORDER BY created_at DESC LIMIT 100")
                .param("member",member).query((rs,row) -> new MemberResponses.Notification(rs.getString("id"),rs.getString("policy_number"),
                        rs.getString("title"),rs.getString("message"),rs.getObject("created_at",OffsetDateTime.class).toInstant(),rs.getObject("read_at") != null)).list());
    }
    public void read(UUID member, UUID id) {
        jdbc.sql("UPDATE member_notifications SET read_at = COALESCE(read_at,CURRENT_TIMESTAMP) WHERE id = :id AND member_id = :member")
                .param("id",id).param("member",member).update();
    }
    private record SavedVersion(long revision, UUID generation) {}
    private record Due(UUID id, String number, UUID generation, long revision, int before, LocalDate date, String title) {}
}
