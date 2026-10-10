package kr.youthpolicymate.member;

import kr.youthpolicymate.config.ApiException;
import kr.youthpolicymate.policy.RecruitmentStatus;
import kr.youthpolicymate.policy.catalog.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Comparator;
import java.util.UUID;

import static kr.youthpolicymate.policy.SeoulTime.SEOUL;

@Service
public class MemberPolicyStore {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final PolicyCatalogStore policies;
    private final Clock clock;

    public MemberPolicyStore(JdbcClient jdbc, ObjectMapper mapper, PolicyCatalogStore policies, Clock clock) {
        this.jdbc = jdbc; this.mapper = mapper; this.policies = policies; this.clock = clock;
    }
    private LocalDate today() { return LocalDate.ofInstant(clock.instant(), SEOUL); }
    private void lock(UUID member) { MemberIdentityStore.lock(jdbc, member); }
    private void lockPolicy(String number) {
        if (jdbc.sql("SELECT policy_number FROM policies WHERE policy_number = :number AND current_revision > 0 FOR SHARE")
                .param("number", number).query(String.class).optional().isEmpty()) throw ApiException.notFound();
    }

    public MemberResponses.Conditions conditions(UUID member) {
        return jdbc.sql("SELECT conditions FROM members WHERE id = :id").param("id", member)
                .query((rs, row) -> new MemberResponses.Conditions(rs.getString(1) == null ? null : mapper.readValue(rs.getString(1), BasicConditions.class)))
                .optional().orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("회원 확인이 필요합니다."));
    }
    @Transactional
    public void saveConditions(UUID member, BasicConditions conditions) {
        BasicConditions.checkBirthDate(conditions.birthDate(), today()); lock(member);
        jdbc.sql("UPDATE members SET conditions = CAST(:conditions AS jsonb) WHERE id = :id")
                .param("id", member).param("conditions", mapper.writeValueAsString(conditions)).update();
    }
    @Transactional
    public void clearConditions(UUID member) {
        lock(member);
        jdbc.sql("UPDATE members SET conditions = NULL WHERE id = :id").param("id", member).update();
    }

    @Transactional
    public void save(UUID member, String number) {
        lock(member); lockPolicy(number);
        var policy = policies.find(number).orElseThrow(ApiException::notFound);
        var generation = UUID.randomUUID();
        // 이미 저장한 정책은 기존 저장 식별자와 예약을 그대로 둔다.
        if (jdbc.sql("""
                INSERT INTO saved_policies(member_id, policy_number, generation, saved_revision, current_revision)
                VALUES (:member, :number, :generation, :revision, :revision)
                ON CONFLICT (member_id, policy_number) DO NOTHING
                """).param("member", member).param("number", number).param("generation", generation).param("revision", policy.revision())
                .update() == 0) return;
        plan(member, number, generation, policy.revision(), policy.recruitment().deadlineOnSeoul());
    }
    // 화면 접수 상태와 같은 마감일로 예약한다. 마감일이 없는 상시·마감·기간 미확인은 예약하지 않는다.
    private void plan(UUID member, String number, UUID generation, long revision, LocalDate deadline) {
        if (deadline == null) return;
        var today = today();
        for (int before : List.of(7,3,1)) {
            var due = deadline.minusDays(before);
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
        var now = clock.instant();
        var items = jdbc.sql("""
                SELECT s.*, p.content_hash, p.content->>'title' AS title, p.content->>'applicationPeriod' AS application_period,
                    source.raw_policy
                FROM saved_policies s JOIN policies p ON p.policy_number = s.policy_number
                JOIN policy_revisions revision ON revision.policy_number = p.policy_number AND revision.revision = p.current_revision
                JOIN policy_source_snapshots source ON source.id = revision.source_snapshot_id
                WHERE s.member_id = :member
                """).param("member", member).query((rs, row) -> {
                    var recruitment = PolicyRecruitment.from(rs.getString("policy_number"), rs.getString("content_hash"),
                            mapper.readTree(rs.getString("raw_policy")), now);
                    return new MemberResponses.Saved(rs.getString("policy_number"), rs.getString("title"), rs.getLong("saved_revision"),
                            rs.getLong("current_revision"), PolicyDeadline.from(recruitment),
                            rs.getObject("saved_at", OffsetDateTime.class).toInstant(), rs.getString("application_period"), recruitment);
                }).list().stream()
                // 마감된 정책은 뒤로 보내고, 가까운 마감일(없으면 뒤)·최근 저장·정책번호 순으로 놓는다.
                .sorted(Comparator.comparing((MemberResponses.Saved item) -> item.recruitment().status() == RecruitmentStatus.CLOSED)
                        .thenComparing(item -> item.deadline().date(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MemberResponses.Saved::savedAt, Comparator.reverseOrder())
                        .thenComparing(MemberResponses.Saved::policyNumber))
                .toList();
        return new MemberResponses.SavedList(items);
    }
    public MemberResponses.SavedChanges changes(UUID member, String number) {
        return jdbc.sql("""
                SELECT s.saved_at, saved.revision AS saved_revision, saved.content AS saved_content,
                       saved_source.captured_at AS saved_captured_at, latest.revision AS current_revision,
                       latest.content AS current_content, current_source.captured_at AS current_captured_at
                FROM saved_policies s
                JOIN policies p ON p.policy_number = s.policy_number AND p.current_revision > 0
                JOIN policy_revisions saved ON saved.policy_number = s.policy_number AND saved.revision = s.saved_revision
                JOIN policy_source_snapshots saved_source ON saved_source.id = saved.source_snapshot_id
                JOIN policy_revisions latest ON latest.policy_number = p.policy_number AND latest.revision = p.current_revision
                JOIN policy_source_snapshots current_source ON current_source.id = latest.source_snapshot_id
                WHERE s.member_id = :member AND s.policy_number = :number
                """).param("member", member).param("number", number).query((rs, row) -> new MemberResponses.SavedChanges(
                        number, rs.getObject("saved_at", OffsetDateTime.class).toInstant(),
                        new MemberResponses.SavedVersion(rs.getLong("saved_revision"), rs.getObject("saved_captured_at", OffsetDateTime.class).toInstant(),
                                mapper.readValue(rs.getString("saved_content"), PolicyContent.class)),
                        new MemberResponses.SavedVersion(rs.getLong("current_revision"), rs.getObject("current_captured_at", OffsetDateTime.class).toInstant(),
                                mapper.readValue(rs.getString("current_content"), PolicyContent.class))))
                .optional().orElseThrow(ApiException::notFound);
    }
    @Transactional
    public void refresh(UUID member) {
        lock(member);
        var versions = jdbc.sql("""
                SELECT p.policy_number, p.current_revision, p.content->>'title' AS title,
                    s.current_revision AS saved_revision, s.generation
                FROM saved_policies s JOIN policies p ON p.policy_number = s.policy_number
                WHERE s.member_id = :member ORDER BY p.policy_number FOR SHARE OF p
                """).param("member", member).query(SavedVersion.class).list();
        for (var saved : versions) {
            if (saved.currentRevision() <= 0) throw ApiException.notFound();
            if (saved.savedRevision() == saved.currentRevision()) continue;
            var number = saved.policyNumber();
            var deadline = policies.find(number).orElseThrow(ApiException::notFound).recruitment().deadlineOnSeoul();
            cancel(member, number);
            jdbc.sql("UPDATE saved_policies SET current_revision = :revision WHERE member_id = :member AND policy_number = :number")
                    .param("member", member).param("number", number).param("revision", saved.currentRevision()).update();
            plan(member, number, saved.generation(), saved.currentRevision(), deadline);
            notify(member, number, saved.generation(), saved.currentRevision(), "POLICY_CHANGED", saved.title(), "저장한 정책 내용이 바뀌었어요. 신청 조건과 기간을 다시 확인해주세요.");
        }
    }
    @Transactional
    public void deliver(UUID member) {
        refresh(member);
        var today = today();
        var due = jdbc.sql("""
                SELECT r.id, r.policy_number, r.generation, r.policy_revision, r.days_before, r.due_on, p.content->>'title' AS title
                FROM policy_reminders r
                JOIN saved_policies s ON s.member_id = r.member_id AND s.policy_number = r.policy_number
                    AND s.generation = r.generation AND s.current_revision = r.policy_revision
                JOIN policies p ON p.policy_number = s.policy_number AND p.current_revision = s.current_revision
                WHERE r.member_id = :member AND r.state = 'PENDING' AND r.due_on <= :today ORDER BY r.due_on
                """).param("member", member).param("today", today).query(Due.class).list();
        for (var reminder : due) {
            // 개정 없이 마감일 해석이 바뀌었으면 화면 마감일과 다른 이전 날짜 기준 알림을 보내지 않는다.
            var deadline = policies.find(reminder.policyNumber()).map(policy -> policy.recruitment().deadlineOnSeoul()).orElse(null);
            boolean deliver = reminder.dueOn().equals(today) && reminder.dueOn().plusDays(reminder.daysBefore()).equals(deadline);
            if (deliver) notify(member, reminder.policyNumber(), reminder.generation(), reminder.policyRevision(),
                    "DEADLINE_" + reminder.daysBefore(), reminder.title(), "신청 마감 " + reminder.daysBefore() + "일 전이에요. 정확한 마감 시각은 공식 안내를 확인해주세요.");
            jdbc.sql("UPDATE policy_reminders SET state = :state WHERE id = :id")
                    .param("id", reminder.id()).param("state", deliver ? "DELIVERED" : "SKIPPED").update();
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
        var expires = kind.startsWith("DEADLINE_") ? today().plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime() : null;
        jdbc.sql("""
                INSERT INTO member_email_outbox(id, member_id, settings_version, kind, notification_id, state, created_at, expires_at)
                SELECT :id, member_id, version, 'POLICY', :notification, 'PENDING', :now, :expires
                FROM member_email_settings WHERE member_id = :member AND enabled AND verified_at IS NOT NULL
                ON CONFLICT DO NOTHING
                """).param("id", UUID.randomUUID()).param("member", member).param("notification", notification)
                .param("now", MemberEmailStore.at(clock.instant())).param("expires", expires).update();
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public MemberResponses.Notifications notifications(UUID member, int page, int pageSize, MemberResponses.NotificationFilter filter) {
        var counts = jdbc.sql("""
                SELECT count(*) AS total, count(*) FILTER (WHERE read_at IS NULL) AS unread
                FROM member_notifications WHERE member_id = :member
                """).param("member", member).query(NotificationCounts.class).single();
        var total = filter == MemberResponses.NotificationFilter.UNREAD ? counts.unread() : counts.total();
        var items = jdbc.sql("""
                SELECT id, policy_number, title, message, created_at, read_at IS NOT NULL AS read
                FROM member_notifications WHERE member_id = :member AND (:filter = 'ALL' OR read_at IS NULL)
                ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset
                """).param("member", member).param("filter", filter.name()).param("limit", pageSize).param("offset", (long) (page - 1) * pageSize)
                .query(MemberResponses.Notification.class).list();
        return new MemberResponses.Notifications(items, page, pageSize, total, (long) page * pageSize < total, counts.unread());
    }
    public void read(UUID member, UUID id) {
        jdbc.sql("UPDATE member_notifications SET read_at = COALESCE(read_at,CURRENT_TIMESTAMP) WHERE id = :id AND member_id = :member")
                .param("id",id).param("member",member).update();
    }
    public void readAll(UUID member) {
        jdbc.sql("UPDATE member_notifications SET read_at = CURRENT_TIMESTAMP WHERE member_id = :member AND read_at IS NULL")
                .param("member", member).update();
    }
    // 아래 record는 JdbcClient.query(Class)가 구성요소 이름(snake_case 열)으로 채운다.
    private record SavedVersion(String policyNumber, long currentRevision, long savedRevision, UUID generation, String title) {}
    private record Due(UUID id, String policyNumber, UUID generation, long policyRevision, int daysBefore, LocalDate dueOn, String title) {}
    private record NotificationCounts(long total, long unread) {}
}
