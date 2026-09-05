package kr.youthpolicymate.policy.catalog;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
@Profile("!preview")
public class PolicyCatalogStore {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public PolicyCatalogStore(JdbcClient jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public String contentHash(String number) {
        return jdbc.sql("SELECT content_hash FROM policies WHERE policy_number = :number")
                .param("number", number).query(String.class).optional().orElse("");
    }

    @Transactional
    public ImportResult importPolicy(String number, PolicyContent content, String rawPolicy,
                                     Instant capturedAt, String captureHash, String contentHash) {
        return apply(number, content, rawPolicy, capturedAt, captureHash, contentHash, 0);
    }

    @Transactional
    public ImportResult importCollectedPolicy(String number, PolicyContent content, String rawPolicy,
                                              Instant capturedAt, String captureHash, String contentHash, long requestSequence) {
        if (requestSequence <= 0) throw new IllegalArgumentException("수집 요청 순번이 필요합니다.");
        return apply(number, content, rawPolicy, capturedAt, captureHash, contentHash, requestSequence);
    }

    private ImportResult apply(String number, PolicyContent content, String rawPolicy,
                               Instant capturedAt, String captureHash, String contentHash, long requestSequence) {
        jdbc.sql("INSERT INTO policies(policy_number) VALUES (:number) ON CONFLICT DO NOTHING")
                .param("number", number).update();
        var current = jdbc.sql("SELECT current_revision, content_hash, last_collected_at, last_request_sequence FROM policies WHERE policy_number = :number FOR UPDATE")
                .param("number", number).query((rs, row) -> new Current(rs.getLong(1), rs.getString(2),
                        rs.getObject(3, OffsetDateTime.class), rs.getLong(4))).single();
        var snapshot = jdbc.sql("""
                INSERT INTO policy_source_snapshots(policy_number, capture_hash, captured_at, raw_policy)
                VALUES (:number, :hash, :at, CAST(:raw AS jsonb))
                ON CONFLICT DO NOTHING RETURNING id
                """).param("number", number).param("hash", captureHash)
                .param("at", capturedAt.atOffset(ZoneOffset.UTC)).param("raw", rawPolicy).query(Long.class).optional();
        if (snapshot.isEmpty() && contentHash.equals(current.hash()) && requestSequence <= current.requestSequence()) {
            return ImportResult.REPLAYED;
        }
        // 직접 수집을 시작한 정책은 요청 발급 순서로 비교한다. 순번 없는 과거 캡처는 덮어쓰지 않는다.
        if (current.requestSequence() > 0 && (requestSequence < current.requestSequence()
                || (requestSequence == current.requestSequence() && snapshot.isPresent()))) return ImportResult.STALE;
        // 로컬 캡처의 수신 시각 순서다. 원천 서버의 개정 순서를 보장하는 값으로 사용하지 않는다.
        if (current.requestSequence() == 0 && current.collectedAt() != null && (capturedAt.isBefore(current.collectedAt().toInstant())
                || (capturedAt.equals(current.collectedAt().toInstant()) && snapshot.isPresent()))) return ImportResult.STALE;
        // 현재 캡처의 표시 규칙만 바뀐 경우 기존 원본을 참조하는 새 개정을 만든다.
        var snapshotId = snapshot.orElseGet(() -> jdbc.sql("SELECT id FROM policy_source_snapshots WHERE policy_number = :number AND capture_hash = :hash")
                .param("number", number).param("hash", captureHash).query(Long.class).single());
        var changed = !contentHash.equals(current.hash());
        var revision = current.revision() + (changed ? 1 : 0);
        var json = mapper.writeValueAsString(content);
        if (changed) {
            jdbc.sql("""
                    INSERT INTO policy_revisions(policy_number, revision, source_snapshot_id, content)
                    VALUES (:number, :revision, :snapshot, CAST(:content AS jsonb))
                    """).param("number", number).param("revision", revision).param("snapshot", snapshotId)
                    .param("content", json).update();
        }
        jdbc.sql("""
                UPDATE policies SET current_revision = :revision, content_hash = :hash,
                    content = CAST(:content AS jsonb), last_collected_at = :at,
                    last_request_sequence = :sequence WHERE policy_number = :number
                """).param("number", number).param("revision", revision).param("hash", contentHash)
                .param("content", json).param("at", capturedAt.atOffset(ZoneOffset.UTC)).param("sequence", requestSequence).update();
        return changed ? ImportResult.APPLIED : ImportResult.UNCHANGED;
    }

    @Transactional(readOnly = true)
    public PolicyListResponse list(String query, int page, int pageSize) {
        // strpos는 검색어의 %·_를 패턴으로 해석하지 않고 바인딩한 문자열 그대로 검색한다.
        var where = " WHERE current_revision > 0 AND (:query = '' OR strpos(lower((content->>'title') || ' ' || (content->>'description')), lower(:query)) > 0)";
        var total = jdbc.sql("SELECT count(*) FROM policies" + where).param("query", query).query(Long.class).single();
        var items = jdbc.sql("SELECT policy_number, content, last_collected_at FROM policies" + where
                        + " ORDER BY last_collected_at DESC, policy_number LIMIT :limit OFFSET :offset")
                .param("query", query).param("limit", pageSize).param("offset", (page - 1) * pageSize)
                .query((rs, row) -> {
                    var content = mapper.readValue(rs.getString("content"), PolicyContent.class);
                    return new PolicySummary(rs.getString("policy_number"), content.title(), content.description(),
                            content.category(), content.organization(), content.applicationPeriod(),
                            rs.getObject("last_collected_at", OffsetDateTime.class).toInstant());
                }).list();
        return new PolicyListResponse(items, page, pageSize, total, (long) page * pageSize < total);
    }

    public Optional<PolicyDetailResponse> find(String number) {
        return jdbc.sql("SELECT current_revision, content, last_collected_at FROM policies WHERE policy_number = :number AND current_revision > 0")
                .param("number", number).query((rs, row) -> new PolicyDetailResponse(number, rs.getLong("current_revision"),
                        mapper.readValue(rs.getString("content"), PolicyContent.class),
                        "https://www.youthcenter.go.kr/youthPolicy/ythPlcyTotalSearch/ythPlcyDetail/" + number + "?isNew=N",
                        rs.getObject("last_collected_at", OffsetDateTime.class).toInstant())).optional();
    }

    public Optional<tools.jackson.databind.JsonNode> source(String number) {
        return jdbc.sql("""
                SELECT s.raw_policy FROM policies p
                JOIN policy_revisions r ON r.policy_number = p.policy_number AND r.revision = p.current_revision
                JOIN policy_source_snapshots s ON s.id = r.source_snapshot_id WHERE p.policy_number = :number
                """).param("number", number).query((rs, row) -> mapper.readTree(rs.getString(1))).optional();
    }

    public enum ImportResult { APPLIED, UNCHANGED, REPLAYED, STALE }
    private record Current(long revision, String hash, OffsetDateTime collectedAt, long requestSequence) {}
}
