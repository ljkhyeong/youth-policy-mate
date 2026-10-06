package kr.youthpolicymate.admin;

import kr.youthpolicymate.ingestion.OntongPolicyCapture;
import kr.youthpolicymate.policy.catalog.PolicyCatalogStore;
import kr.youthpolicymate.policy.catalog.PolicyContent;
import kr.youthpolicymate.policy.catalog.PolicyCorrectionStore.Field;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Profile("!preview")
class PolicyCorrectionService {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final PolicyCatalogStore catalog;
    private final OntongPolicyCapture parser;
    // 보정 생성·해소는 잠근 개정이 요청 개정과 같고 반영 결과가 APPLIED(개정+1)일 때만 커밋되므로 결과 개정을 계산한다.
    private static final String ITEMS = """
            SELECT c.*, c.requested_revision + 1 AS applied_revision, c.resolved_expected_revision + 1 AS resolved_revision,
                   p.current_revision, source.raw_policy::text AS source_raw,
                   review.id AS review_id, review.raw_policy::text AS review_raw
            FROM policy_corrections c JOIN policies p ON p.policy_number = c.policy_number
            JOIN policy_revisions r ON r.policy_number = p.policy_number AND r.revision = p.current_revision
            JOIN policy_source_snapshots source ON source.id = c.source_snapshot_id
            JOIN policy_source_snapshots review ON review.id = COALESCE(c.resolved_snapshot_id, c.conflict_snapshot_id, r.source_snapshot_id)
            """;

    PolicyCorrectionService(JdbcClient jdbc, ObjectMapper mapper, PolicyCatalogStore catalog, OntongPolicyCapture parser) {
        this.jdbc = jdbc; this.mapper = mapper; this.catalog = catalog; this.parser = parser;
    }

    @Transactional
    public PolicyCorrections.Item create(PolicyCorrections.Request request, UUID actor) {
        var current = lock(request.policyNumber());
        var existing = item(request.requestId());
        if (existing.isPresent()) {
            var previous = existing.orElseThrow();
            if (!previous.policyNumber().equals(request.policyNumber()) || !previous.actorId().equals(actor)
                    || previous.requestedRevision() != request.expectedRevision() || previous.field() != request.field()
                    || !previous.value().equals(request.value().strip()) || !previous.reason().equals(request.reason().strip()))
                throw new PolicyCorrections.Changed();
            return previous;
        }
        if (current.revision() != request.expectedRevision() || jdbc.sql("SELECT EXISTS(SELECT 1 FROM policy_corrections WHERE policy_number = :number AND status <> 'RELEASED')")
                .param("number", request.policyNumber()).query(Boolean.class).single()) throw new PolicyCorrections.Changed();
        var content = mapper.readValue(current.content(), PolicyContent.class);
        var shown = request.field() == Field.TITLE ? content.title() : content.organization();
        if (shown.equals(request.value().strip())) throw new PolicyCorrections.Invalid();
        insert(request.requestId(), request.policyNumber(), request.field(), current.sourceId(), request.value().strip(),
                request.reason().strip(), actor, current.revision());
        apply(request.policyNumber(), current, current.sourceId());
        return item(request.requestId()).orElseThrow();
    }

    @Transactional
    public PolicyCorrections.Item resolve(UUID id, PolicyCorrections.Resolution request, UUID actor) {
        var number = jdbc.sql("SELECT policy_number FROM policy_corrections WHERE id = :id").param("id", id)
                .query(String.class).optional().orElseThrow(PolicyCorrections.Changed::new);
        var current = lock(number);
        var correction = item(id).orElseThrow();
        if (correction.status() == PolicyCorrections.Status.RELEASED) {
            boolean repeated = jdbc.sql("""
                    SELECT EXISTS(SELECT 1 FROM policy_corrections WHERE id = :id AND resolved_request_id = :request
                        AND resolved_by = :actor AND resolution = :action AND resolved_reason = :reason
                        AND resolved_snapshot_id = :snapshot AND resolved_expected_revision = :revision)
                    """).param("id", id).param("request", request.requestId()).param("actor", actor)
                    .param("action", request.action().name()).param("reason", request.reason().strip())
                    .param("snapshot", request.reviewSnapshotId()).param("revision", request.expectedRevision()).query(Boolean.class).single();
            if (!repeated) throw new PolicyCorrections.Changed();
            return correction;
        }
        if (current.revision() != request.expectedRevision() || correction.reviewSnapshotId() != request.reviewSnapshotId())
            throw new PolicyCorrections.Changed();
        if (request.action() == PolicyCorrections.Action.KEEP && correction.status() != PolicyCorrections.Status.CONFLICT)
            throw new PolicyCorrections.Invalid();
        jdbc.sql("""
                UPDATE policy_corrections SET status = 'RELEASED', resolved_request_id = :request, resolution = :action,
                    resolved_by = :actor, resolved_reason = :reason, resolved_snapshot_id = :snapshot,
                    resolved_expected_revision = :revision, resolved_at = statement_timestamp()
                WHERE id = :id
                """).param("request", request.requestId()).param("action", request.action().name()).param("actor", actor)
                .param("reason", request.reason().strip()).param("snapshot", request.reviewSnapshotId())
                .param("revision", request.expectedRevision()).param("id", id).update();
        if (request.action() == PolicyCorrections.Action.KEEP) {
            insert(request.requestId(), number, correction.field(), correction.reviewSnapshotId(), correction.value(),
                    request.reason().strip(), actor, current.revision());
        }
        apply(number, current, correction.reviewSnapshotId());
        return item(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public PolicyCorrections.Page list(int page, int pageSize) {
        var items = jdbc.sql(ITEMS + " ORDER BY c.created_at DESC, c.id LIMIT :limit OFFSET :offset")
                .param("limit", pageSize + 1).param("offset", (page - 1) * pageSize).query((rs, row) -> map(rs)).list();
        boolean hasNext = items.size() > pageSize;
        return new PolicyCorrections.Page(hasNext ? items.subList(0, pageSize) : items, page, pageSize, hasNext);
    }

    private void insert(UUID id, String number, Field field, long source, String value, String reason, UUID actor, long revision) {
        jdbc.sql("""
                INSERT INTO policy_corrections(id, policy_number, field, source_snapshot_id, value, reason, actor_id,
                    requested_revision, status)
                VALUES (:id, :number, :field, :source, :value, :reason, :actor, :revision, 'ACTIVE')
                """).param("id", id).param("number", number).param("field", field.name()).param("source", source).param("value", value)
                .param("reason", reason).param("actor", actor).param("revision", revision).update();
    }

    private void apply(String number, Current current, long snapshotId) {
        var source = jdbc.sql("SELECT raw_policy::text AS raw, capture_hash, captured_at FROM policy_source_snapshots WHERE id = :id AND policy_number = :number")
                .param("id", snapshotId).param("number", number).query((rs, row) -> new Source(rs.getString("raw"), rs.getString("capture_hash"),
                        rs.getObject("captured_at", OffsetDateTime.class).toInstant())).single();
        var parsed = parser.item(mapper.readTree(source.raw()));
        var at = source.at().isAfter(current.collectedAt()) ? source.at() : current.collectedAt();
        var result = catalog.importPolicy(number, parsed.content(), parsed.rawPolicy(), at, source.hash(), parsed.contentHash(), current.sequence());
        if (result != PolicyCatalogStore.ImportResult.APPLIED) throw new PolicyCorrections.Changed();
    }

    private Current lock(String number) {
        // 대기 중 개정이 바뀌어도 조인한 이전 개정에 묶이지 않도록 정책을 먼저 잠근다.
        jdbc.sql("SELECT policy_number FROM policies WHERE policy_number = :number AND current_revision > 0 FOR UPDATE")
                .param("number", number).query(String.class).optional().orElseThrow(PolicyCorrections.Changed::new);
        return jdbc.sql("""
                SELECT p.current_revision, p.last_collected_at, p.last_request_sequence, p.content::text AS content, r.source_snapshot_id
                FROM policies p JOIN policy_revisions r ON r.policy_number = p.policy_number AND r.revision = p.current_revision
                WHERE p.policy_number = :number
                """).param("number", number).query((rs, row) -> new Current(rs.getLong("current_revision"),
                        rs.getObject("last_collected_at", OffsetDateTime.class).toInstant(), rs.getLong("last_request_sequence"),
                        rs.getString("content"), rs.getLong("source_snapshot_id"))).optional().orElseThrow(PolicyCorrections.Changed::new);
    }

    private Optional<PolicyCorrections.Item> item(UUID id) {
        return jdbc.sql(ITEMS + " WHERE c.id = :id").param("id", id).query((rs, row) -> map(rs)).optional();
    }

    private PolicyCorrections.Item map(ResultSet rs) throws SQLException {
        var field = Field.valueOf(rs.getString("field"));
        var review = mapper.readTree(rs.getString("review_raw"));
        var resolved = rs.getObject("resolved_at", OffsetDateTime.class);
        return new PolicyCorrections.Item(rs.getObject("id", UUID.class), rs.getString("policy_number"), field,
                rs.getString("value"), rs.getString("reason"), rs.getObject("actor_id", UUID.class), rs.getLong("requested_revision"),
                rs.getLong("applied_revision"), rs.getObject("created_at", OffsetDateTime.class).toInstant(), PolicyCorrections.Status.valueOf(rs.getString("status")),
                field.sourceValue(mapper.readTree(rs.getString("source_raw"))), rs.getLong("review_id"),
                field.sourceValue(review), rs.getLong("current_revision"), parser.item(review).content(),
                rs.getString("resolution"), rs.getObject("resolved_by", UUID.class), rs.getString("resolved_reason"),
                rs.getObject("resolved_revision", Long.class), resolved == null ? null : resolved.toInstant());
    }

    private record Current(long revision, Instant collectedAt, long sequence, String content, long sourceId) {}
    private record Source(String raw, String hash, Instant at) {}
}
