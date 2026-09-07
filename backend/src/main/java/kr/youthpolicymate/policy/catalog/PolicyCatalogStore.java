package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.policy.RecruitmentStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.Objects;
import kr.youthpolicymate.ingestion.OntongPolicyCapture;

@Repository
@Profile("!preview")
public class PolicyCatalogStore {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final java.time.Clock clock;
    private final PolicyCorrectionStore corrections;
    private static final String SOURCE_JOIN = """
            LEFT JOIN policy_revisions r ON r.policy_number = p.policy_number AND r.revision = p.current_revision
            LEFT JOIN policy_source_snapshots s ON s.id = r.source_snapshot_id
            """;
    // 목록에는 모집기간 해석에 쓰는 필드만 가져온다.
    private static final String PERIOD_SOURCE = """
            jsonb_build_object('aplyPrdSeCd', s.raw_policy->'aplyPrdSeCd', 'aplyYmd', s.raw_policy->'aplyYmd',
                'plcySprtCn', s.raw_policy->'plcySprtCn', 'plcyAplyMthdCn', s.raw_policy->'plcyAplyMthdCn',
                'etcMttrCn', s.raw_policy->'etcMttrCn', 'addAplyQlfcCndCn', s.raw_policy->'addAplyQlfcCndCn',
                'srngMthdCn', s.raw_policy->'srngMthdCn', 'plcyExplnCn', s.raw_policy->'plcyExplnCn') AS raw_policy
            """;

    public PolicyCatalogStore(JdbcClient jdbc, ObjectMapper mapper, java.time.Clock clock, PolicyCorrectionStore corrections) {
        this.jdbc = jdbc; this.mapper = mapper; this.clock = clock; this.corrections = corrections;
    }

    Optional<QuestionVersion> questionVersion(String number) {
        return jdbc.sql("SELECT current_revision, content_hash FROM policies WHERE policy_number = :number AND current_revision > 0")
                .param("number", number).query((rs, row) -> new QuestionVersion(rs.getLong("current_revision"),
                        rs.getString("content_hash"))).optional();
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
        var current = jdbc.sql("""
                SELECT current_revision, content_hash, last_collected_at, last_request_sequence
                FROM policies WHERE policy_number = :number FOR UPDATE
                """)
                .param("number", number).query((rs, row) -> new Current(rs.getLong(1), rs.getString(2),
                        rs.getObject(3, OffsetDateTime.class), rs.getLong(4))).single();
        var currentCorrectionId = jdbc.sql("SELECT correction_id FROM policy_revisions WHERE policy_number = :number AND revision = :revision")
                .param("number", number).param("revision", current.revision()).query(UUID.class).optional().orElse(null);
        var correction = corrections.active(number).orElse(null);
        var snapshot = jdbc.sql("""
                INSERT INTO policy_source_snapshots(policy_number, capture_hash, captured_at, raw_policy)
                VALUES (:number, :hash, :at, CAST(:raw AS jsonb))
                ON CONFLICT DO NOTHING RETURNING id
                """).param("number", number).param("hash", captureHash)
                .param("at", capturedAt.atOffset(ZoneOffset.UTC)).param("raw", rawPolicy).query(Long.class).optional();
        if (correction == null && currentCorrectionId == null && snapshot.isEmpty() && contentHash.equals(current.hash()) && requestSequence <= current.requestSequence()) {
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
        UUID correctionId = null;
        if (correction != null) {
            if (requestSequence == 0 && correction.conflictAt() != null
                    && (capturedAt.isBefore(correction.conflictAt()) || (capturedAt.equals(correction.conflictAt()) && snapshot.isPresent())))
                return ImportResult.STALE;
            var raw = mapper.readTree(rawPolicy);
            if (correction.status().equals("CONFLICT") || !PolicyCorrectionStore.sourceValue(raw, correction.field())
                    .equals(PolicyCorrectionStore.sourceValue(correction.source(), correction.field()))) {
                corrections.conflict(correction.id(), snapshotId);
                jdbc.sql("UPDATE policies SET last_request_sequence = :sequence WHERE policy_number = :number")
                        .param("sequence", requestSequence).param("number", number).update();
                return ImportResult.CORRECTION_CONFLICT;
            }
            var corrected = (tools.jackson.databind.node.ObjectNode) raw.deepCopy();
            corrected.put(PolicyCorrectionStore.sourceKey(correction.field()), correction.value());
            var parsed = new OntongPolicyCapture(mapper).item(corrected);
            content = parsed.content(); contentHash = parsed.contentHash(); correctionId = correction.id();
        }
        var changed = !contentHash.equals(current.hash()) || !Objects.equals(correctionId, currentCorrectionId);
        var revision = current.revision() + (changed ? 1 : 0);
        var json = mapper.writeValueAsString(content);
        if (changed) {
            jdbc.sql("""
                    INSERT INTO policy_revisions(policy_number, revision, source_snapshot_id, content, correction_id)
                    VALUES (:number, :revision, :snapshot, CAST(:content AS jsonb), :correction)
                    """).param("number", number).param("revision", revision).param("snapshot", snapshotId)
                    .param("content", json).param("correction", correctionId).update();
        }
        var window = PolicyRecruitmentWindow.from(number, contentHash, mapper.readTree(rawPolicy));
        jdbc.sql("""
                UPDATE policies SET current_revision = :revision, content_hash = :hash,
                    content = CAST(:content AS jsonb), last_collected_at = :at,
                    last_request_sequence = :sequence,
                    recruitment_kind = CASE WHEN :changed THEN :kind ELSE recruitment_kind END,
                    recruitment_opens_at = CASE WHEN :changed THEN :opensAt ELSE recruitment_opens_at END,
                    recruitment_closes_at = CASE WHEN :changed THEN :closesAt ELSE recruitment_closes_at END
                    WHERE policy_number = :number
                """).param("number", number).param("revision", revision).param("hash", contentHash)
                .param("content", json).param("at", capturedAt.atOffset(ZoneOffset.UTC)).param("sequence", requestSequence).param("changed", changed)
                .param("kind", window.kind()).param("opensAt", window.opensAt()).param("closesAt", window.closesAt()).update();
        return changed ? ImportResult.APPLIED : ImportResult.UNCHANGED;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PolicyListResponse list(String query, int page, int pageSize, boolean questionsOnly, RecruitmentStatus recruitmentStatus, Instant now) {
        var reviewed = ReviewedPolicyQuestions.contentHashesAt(now);
        var parameters = new HashMap<String, Object>();
        parameters.put("query", query);
        // strpos는 검색어의 %·_를 패턴으로 해석하지 않고 바인딩한 문자열 그대로 검색한다.
        var where = " WHERE p.current_revision > 0 AND (:query = '' OR strpos(lower((p.content->>'title') || ' ' || (p.content->>'description')), lower(:query)) > 0)";
        if (questionsOnly) {
            var alternatives = new StringJoiner(" OR ", "(", ")").setEmptyValue("FALSE");
            int index = 0;
            for (var entry : reviewed.entrySet()) {
                alternatives.add("(p.policy_number = :number" + index + " AND p.content_hash = :hash" + index + ")");
                parameters.put("number" + index, entry.getKey());
                parameters.put("hash" + index, entry.getValue());
                index++;
            }
            where += " AND " + alternatives;
        }
        where += recruitmentFilter(recruitmentStatus, now, parameters);
        var total = jdbc.sql("SELECT count(*) FROM policies p" + where).params(parameters).query(Long.class).single();
        var items = jdbc.sql("""
                SELECT p.policy_number, p.current_revision, p.content_hash, p.last_collected_at,
                    p.content->>'title' AS title, p.content->>'description' AS description,
                    p.content->>'category' AS category, p.content->>'organization' AS organization,
                    p.content->>'applicationPeriod' AS application_period,
                """ + PERIOD_SOURCE + " FROM policies p " + SOURCE_JOIN + where
                        + " ORDER BY p.last_collected_at DESC, p.policy_number LIMIT :limit OFFSET :offset")
                .params(parameters).param("limit", pageSize).param("offset", (page - 1) * pageSize)
                .query((rs, row) -> new PolicySummary(rs.getString("policy_number"), rs.getString("title"),
                            rs.getString("description"), rs.getString("category"), rs.getString("organization"),
                            rs.getString("application_period"),
                            rs.getObject("last_collected_at", OffsetDateTime.class).toInstant(),
                            reviewed.containsKey(rs.getString("policy_number"))
                                    && reviewed.get(rs.getString("policy_number")).equals(rs.getString("content_hash")),
                            PolicyRecruitment.from(rs.getString("policy_number"), rs.getLong("current_revision"), rs.getString("content_hash"),
                                    mapper.readTree(rs.getString("raw_policy")), now)))
                .list();
        return new PolicyListResponse(items, page, pageSize, total, (long) page * pageSize < total);
    }

    private static String recruitmentFilter(RecruitmentStatus status, Instant now, Map<String, Object> parameters) {
        if (status == null) return "";
        parameters.put("recruitmentNow", now.atOffset(ZoneOffset.UTC));
        return " AND " + switch (status) {
            case BEFORE_OPENING -> "(p.recruitment_kind = 'PERIOD' AND p.recruitment_opens_at > :recruitmentNow)";
            case OPEN -> "(p.recruitment_kind = 'PERIOD' AND p.recruitment_opens_at <= :recruitmentNow AND p.recruitment_closes_at > :recruitmentNow)";
            case CLOSED -> "(p.recruitment_kind = 'CLOSED' OR (p.recruitment_kind = 'PERIOD' AND p.recruitment_closes_at <= :recruitmentNow))";
            case ROLLING, UNTIL_EXHAUSTED, UNKNOWN -> {
                parameters.put("recruitmentKind", status.name());
                yield "p.recruitment_kind = :recruitmentKind";
            }
        };
    }

    public Optional<PolicyDetailResponse> find(String number) {
        var now = clock.instant();
        return jdbc.sql("SELECT p.policy_number, p.current_revision, p.content_hash, p.content, p.last_collected_at, "
                        + PERIOD_SOURCE + " FROM policies p " + SOURCE_JOIN + " WHERE p.policy_number = :number AND p.current_revision > 0")
                .param("number", number).query((rs, row) -> detail(rs, mapper.readTree(rs.getString("raw_policy")), now)).optional();
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<CheckSource> listForCheck(PageRequest page, String query, PolicyCheckResponse.Sort sort,
                                         Map<String, BasicConditionRules.Comparison> comparisons, RecruitmentStatus recruitmentStatus, Instant now) {
        var reviewed = ReviewedPolicyQuestions.contentHashesAt(now);
        var parameters = new HashMap<String, Object>();
        parameters.put("query", query);
        var where = " WHERE p.current_revision > 0 AND (:query = '' OR strpos(lower((p.content->>'title') || ' ' || (p.content->>'description')), lower(:query)) > 0)";
        where += recruitmentFilter(recruitmentStatus, now, parameters);
        var total = jdbc.sql("SELECT count(*) FROM policies p" + where).params(parameters).query(Long.class).single();
        var order = "p.last_collected_at DESC, p.policy_number";
        if (sort == PolicyCheckResponse.Sort.AGE_MATCH && !comparisons.isEmpty()) {
            var priority = new StringBuilder("CASE");
            int index = 0;
            for (var entry : comparisons.entrySet()) {
                priority.append(" WHEN p.policy_number = :number").append(index).append(" AND p.content_hash = :hash").append(index)
                        .append(" THEN :priority").append(index);
                parameters.put("number" + index, entry.getKey());
                parameters.put("hash" + index, entry.getValue().contentHash());
                parameters.put("priority" + index, entry.getValue().priority());
                index++;
            }
            order = priority.append(" ELSE 1 END, ") + order;
        }
        var items = jdbc.sql("""
                SELECT p.policy_number, p.current_revision, p.content, p.content_hash, p.last_collected_at, s.raw_policy
                FROM policies p
                LEFT JOIN policy_revisions r ON r.policy_number = p.policy_number AND r.revision = p.current_revision
                LEFT JOIN policy_source_snapshots s ON s.id = r.source_snapshot_id
                """ + where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset")
                .params(parameters).param("limit", page.getPageSize()).param("offset", page.getOffset())
                .query((rs, row) -> {
                    var raw = rs.getString("raw_policy");
                    if (raw == null) throw new PolicyNotFoundException();
                    var number = rs.getString("policy_number");
                    var hash = rs.getString("content_hash");
                    var comparison = comparisons.get(number);
                    var source = mapper.readTree(raw);
                    return new CheckSource(detail(rs, source, now), source,
                            reviewed.containsKey(number) && reviewed.get(number).equals(hash),
                            comparison != null && comparison.contentHash().equals(hash) ? comparison : null);
                }).list();
        return new PageImpl<>(items, page, total);
    }

    private PolicyDetailResponse detail(ResultSet rs, JsonNode raw, Instant now) throws SQLException {
        var number = rs.getString("policy_number");
        return new PolicyDetailResponse(number, rs.getLong("current_revision"),
                mapper.readValue(rs.getString("content"), PolicyContent.class),
                sourceUrl(number),
                rs.getObject("last_collected_at", OffsetDateTime.class).toInstant(),
                PolicyRecruitment.from(number, rs.getLong("current_revision"), rs.getString("content_hash"), raw, now));
    }

    static String sourceUrl(String number) {
        return "https://www.youthcenter.go.kr/youthPolicy/ythPlcyTotalSearch/ythPlcyDetail/" + number + "?isNew=N";
    }

    public Optional<tools.jackson.databind.JsonNode> source(String number) {
        return jdbc.sql("""
                SELECT s.raw_policy FROM policies p
                JOIN policy_revisions r ON r.policy_number = p.policy_number AND r.revision = p.current_revision
                JOIN policy_source_snapshots s ON s.id = r.source_snapshot_id WHERE p.policy_number = :number
                """).param("number", number).query((rs, row) -> mapper.readTree(rs.getString(1))).optional();
    }

    public enum ImportResult { APPLIED, UNCHANGED, REPLAYED, STALE, CORRECTION_CONFLICT }
    record CheckSource(PolicyDetailResponse policy, JsonNode raw, boolean questionnaireAvailable, BasicConditionRules.Comparison comparison) {}
    record QuestionVersion(long revision, String contentHash) {}
    private record Current(long revision, String hash, OffsetDateTime collectedAt, long requestSequence) {}
}
