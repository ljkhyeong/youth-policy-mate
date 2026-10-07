package kr.youthpolicymate.admin;

import kr.youthpolicymate.member.ResendMemberEmailSender;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static kr.youthpolicymate.admin.EmailDeliveries.*;

@Service
@Profile("!preview")
public class EmailDeliveryStore {
    private static final String PERIOD = "created_at >= :since AND created_at <= :now";
    private static final String FILTER = PERIOD + " AND (:state = '' OR state = :state) AND (:kind = '' OR kind = :kind)";
    private final JdbcClient jdbc;
    private final Clock clock;
    private final ResendMemberEmailSender sender;

    public EmailDeliveryStore(JdbcClient jdbc, Clock clock, ResendMemberEmailSender sender) {
        this.jdbc = jdbc; this.clock = clock; this.sender = sender;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page list(int page, int pageSize, int days, State state, Kind kind) {
        Instant checkedAt = clock.instant();
        Instant since = checkedAt.minus(days, ChronoUnit.DAYS);
        var summary = jdbc.sql("""
                SELECT count(*) AS total,
                    count(*) FILTER (WHERE state IN ('FAILED','BOUNCED','COMPLAINED','SUPPRESSED')) AS failed,
                    count(*) FILTER (WHERE state = 'UNKNOWN') AS unknown
                FROM member_email_outbox WHERE
                """ + PERIOD).param("since", since.atOffset(ZoneOffset.UTC)).param("now", checkedAt.atOffset(ZoneOffset.UTC))
                .query(Summary.class).single();
        long total = filtered("SELECT count(*) FROM member_email_outbox WHERE " + FILTER, since, checkedAt, state, kind)
                .query(Long.class).single();
        // 열 이름이 응답 레코드 구성요소(id, kind, state, providerMessageId, …)와 대응한다.
        var items = filtered("""
                SELECT id, kind, state, provider_message_id, created_at, started_at, finished_at, provider_event_at
                FROM member_email_outbox WHERE
                """ + FILTER + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset", since, checkedAt, state, kind)
                .param("limit", pageSize).param("offset", (page - 1) * pageSize)
                .query(Item.class).list();
        return new Page(items, page, pageSize, total, (long) page * pageSize < total, since, checkedAt, sender.available(), summary);
    }

    public UUID providerMessageId(UUID id) {
        return jdbc.sql("SELECT provider_message_id FROM member_email_outbox WHERE id = :id AND provider_message_id IS NOT NULL")
                .param("id", id).query(UUID.class).optional().orElse(null);
    }

    private JdbcClient.StatementSpec filtered(String sql, Instant since, Instant now, State state, Kind kind) {
        return jdbc.sql(sql).param("since", since.atOffset(ZoneOffset.UTC)).param("now", now.atOffset(ZoneOffset.UTC))
                .param("state", state == null ? "" : state.name()).param("kind", kind == null ? "" : kind.name());
    }
}
