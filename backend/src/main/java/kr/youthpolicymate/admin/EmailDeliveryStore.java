package kr.youthpolicymate.admin;

import kr.youthpolicymate.member.MemberEmailSender;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
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
    private final MemberEmailSender sender;

    public EmailDeliveryStore(JdbcClient jdbc, Clock clock, MemberEmailSender sender) {
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
                .query((rs, row) -> new Summary(rs.getLong("total"), rs.getLong("failed"), rs.getLong("unknown"))).single();
        long total = filtered("SELECT count(*) FROM member_email_outbox WHERE " + FILTER, since, checkedAt, state, kind)
                .query(Long.class).single();
        var items = filtered("""
                SELECT id, kind, state, provider, provider_message_id, created_at, started_at, finished_at, provider_event_at
                FROM member_email_outbox WHERE
                """ + FILTER + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset", since, checkedAt, state, kind)
                .param("limit", pageSize).param("offset", (page - 1) * pageSize)
                .query((rs, row) -> new Item(rs.getObject("id", UUID.class), Kind.valueOf(rs.getString("kind")), State.valueOf(rs.getString("state")),
                        rs.getString("provider"), rs.getObject("provider_message_id", UUID.class), instant(rs, "created_at"),
                        instant(rs, "started_at"), instant(rs, "finished_at"), instant(rs, "provider_event_at"))).list();
        return new Page(items, page, pageSize, total, (long) page * pageSize < total, since, checkedAt,
                sender.available(), sender.provider(), summary);
    }

    public UUID providerMessageId(UUID id) {
        return jdbc.sql("SELECT provider_message_id FROM member_email_outbox WHERE id = :id AND provider = 'resend' AND provider_message_id IS NOT NULL")
                .param("id", id).query(UUID.class).optional().orElse(null);
    }

    private JdbcClient.StatementSpec filtered(String sql, Instant since, Instant now, State state, Kind kind) {
        return jdbc.sql(sql).param("since", since.atOffset(ZoneOffset.UTC)).param("now", now.atOffset(ZoneOffset.UTC))
                .param("state", state == null ? "" : state.name()).param("kind", kind == null ? "" : kind.name());
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
