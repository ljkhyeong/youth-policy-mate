package kr.youthpolicymate.admin;

import kr.youthpolicymate.policy.catalog.PolicyRuleDefinition;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!preview")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class PolicyRuleReviewStore {
    // 상태 계산을 공통 쿼리로 두어 필터·전체 건수·페이지 순서가 같은 기준을 사용한다.
    private static final String REVIEW = """
            WITH review AS (
                SELECT p.policy_number, p.current_revision, p.content_hash, p.content->>'title' AS title, p.last_collected_at,
                    CASE WHEN v.id IS NULL THEN 'MISSING'
                         WHEN v.definition->>'contentHash' <> p.content_hash THEN 'SOURCE_CHANGED'
                         WHEN CAST(v.definition->>'validUntil' AS timestamptz) <= :now THEN 'EXPIRED'
                         WHEN CAST(v.definition->>'validFrom' AS timestamptz) > :now THEN 'SCHEDULED'
                         ELSE 'ACTIVE' END AS status,
                    (SELECT count(*) FROM policy_rule_versions d WHERE d.policy_number = p.policy_number
                        AND d.published_at IS NULL) AS draft_count
                FROM policies p LEFT JOIN policy_rule_heads h ON h.policy_number = p.policy_number
                LEFT JOIN policy_rule_versions v ON v.id = h.version_id WHERE p.current_revision > 0
            )
            """;
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final CollectionExceptionStore sources;

    PolicyRuleReviewStore(JdbcClient jdbc, ObjectMapper mapper, Clock clock, CollectionExceptionStore sources) {
        this.jdbc = jdbc; this.mapper = mapper; this.clock = clock; this.sources = sources;
    }

    PolicyRuleReviews.Page list(int page, int pageSize, PolicyRuleReviews.Filter filter, String query) {
        var now = clock.instant();
        var parameters = java.util.Map.<String, Object>of("now", now.atOffset(ZoneOffset.UTC), "query", query.strip(), "filter", filter.name());
        var where = """
                FROM review WHERE (position(lower(:query) in lower(title)) > 0 OR position(:query in policy_number) > 0)
                AND (:filter = 'ALL' OR status = :filter OR (:filter = 'REVIEW' AND status IN ('SOURCE_CHANGED', 'EXPIRED', 'MISSING')))
                """;
        var total = jdbc.sql(REVIEW + "SELECT count(*) " + where).params(parameters).query(Long.class).single();
        var items = jdbc.sql(REVIEW + "SELECT * " + where + """
                ORDER BY CASE status WHEN 'SOURCE_CHANGED' THEN 0 WHEN 'EXPIRED' THEN 1 WHEN 'MISSING' THEN 2
                         WHEN 'SCHEDULED' THEN 3 ELSE 4 END, last_collected_at DESC, policy_number
                LIMIT :limit OFFSET :offset
                """).params(parameters).param("limit", pageSize).param("offset", (page - 1) * pageSize)
                .query((rs, row) -> item(rs)).list();
        return new PolicyRuleReviews.Page(items, page, pageSize, total, (long) page * pageSize < total, now);
    }

    Optional<PolicyRuleReviews.Detail> detail(String number) {
        var now = clock.instant();
        return jdbc.sql(REVIEW + "SELECT * FROM review WHERE policy_number = :number")
                .param("now", now.atOffset(ZoneOffset.UTC)).param("number", number)
                .query((rs, row) -> new ReviewSource(item(rs), rs.getString("content_hash"))).optional()
                .map(source -> {
                    var item = source.item();
                    var policy = sources.currentPolicy(number).orElseThrow();
                    var raw = jdbc.sql("""
                            SELECT s.raw_policy::text FROM policy_revisions r JOIN policy_source_snapshots s ON s.id = r.source_snapshot_id
                            WHERE r.policy_number = :number AND r.revision = :revision
                            """).param("number", number).param("revision", item.revision()).query(String.class).single();
                    var versions = jdbc.sql("""
                            SELECT v.*, p.content_hash, h.version_id, a.reason AS publish_reason FROM policy_rule_versions v
                            JOIN policies p ON p.policy_number = v.policy_number
                            LEFT JOIN policy_rule_heads h ON h.policy_number = v.policy_number
                            LEFT JOIN admin_policy_rule_actions a ON a.version_id = v.id AND a.action = 'PUBLISH'
                            WHERE v.policy_number = :number
                            ORDER BY (v.id = h.version_id) DESC NULLS LAST, v.created_at DESC, v.id LIMIT 21
                            """).param("number", number).query((rs, row) -> {
                        var definition = mapper.readValue(rs.getString("definition"), PolicyRuleDefinition.class);
                        var state = rs.getObject("id").equals(rs.getObject("version_id")) ? PolicyRuleReviews.VersionState.CURRENT
                                : rs.getObject("published_at") == null ? PolicyRuleReviews.VersionState.DRAFT : PolicyRuleReviews.VersionState.PREVIOUS;
                        boolean matches = definition.contentHash().equals(rs.getString("content_hash"));
                        var publishedAt = rs.getObject("published_at", OffsetDateTime.class);
                        return new PolicyRuleReviews.Version(rs.getObject("id", UUID.class), definition.ruleVersion(), state,
                                matches, definition.validFrom(), definition.validUntil(),
                                definition.scope(), definition.reason(), definition.sourceUrl(), definition.questions(), definition.remainingChecks(),
                                rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getString("reason"),
                                state == PolicyRuleReviews.VersionState.DRAFT && matches && definition.appliesAt(now), rs.getString("created_by"),
                                publishedAt == null ? null : publishedAt.toInstant(), rs.getString("published_by"), rs.getString("publish_reason"));
                    }).list();
                    return new PolicyRuleReviews.Detail(item, policy, raw, source.hash(), versions, now);
                });
    }

    private static PolicyRuleReviews.Item item(ResultSet rs) throws SQLException {
        return new PolicyRuleReviews.Item(rs.getString("policy_number"), rs.getString("title"), rs.getLong("current_revision"),
                PolicyRuleReviews.Status.valueOf(rs.getString("status")), rs.getObject("last_collected_at", OffsetDateTime.class).toInstant(),
                rs.getLong("draft_count"));
    }
    private record ReviewSource(PolicyRuleReviews.Item item, String hash) {}
}
