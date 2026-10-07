package kr.youthpolicymate.ingestion;

import jakarta.validation.Validator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import kr.youthpolicymate.policy.catalog.PolicyContent;
import kr.youthpolicymate.policy.catalog.PolicyRuleDefinition;
import kr.youthpolicymate.policy.catalog.PolicyRuleStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PolicyAiRuleDraftStore {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final PolicyRuleStore rules;

    public PolicyAiRuleDraftStore(JdbcClient jdbc, ObjectMapper mapper, Validator validator, PolicyRuleStore rules) {
        this.jdbc = jdbc; this.mapper = mapper; this.validator = validator; this.rules = rules;
    }

    @Transactional
    public Prepared prepare(Preparation input) {
        if (input == null || !validator.validate(input).isEmpty()) throw new IllegalArgumentException("요청 ID·정책번호·개정·생성 방식·작업자를 확인해주세요.");
        var current = lockPolicy(input.policyNumber());
        var previous = findPrepared(input.id());
        if (previous.isPresent()) {
            var saved = previous.get();
            if (!saved.policyNumber().equals(input.policyNumber()) || saved.revision() != input.revision()
                    || !saved.generationVersion().equals(input.generationVersion()) || !saved.requestedBy().equals(input.requestedBy()))
                throw new IllegalStateException("다른 입력에 사용한 요청 ID입니다.");
            if (!current.matches(saved.revision(), saved.contentHash())) throw new IllegalStateException("요청 기준 공고가 바뀌었습니다. 현재 개정으로 새 요청을 준비해주세요.");
            if (hasNewerRequest(saved)) throw new IllegalStateException("더 최근의 추출 요청이 있습니다. 최신 요청을 확인해주세요.");
            return saved;
        }
        if (current.currentRevision() != input.revision()) throw new IllegalStateException("공고 개정이 바뀌었습니다. 최신 내용을 확인해주세요.");
        jdbc.sql("""
                INSERT INTO policy_ai_rule_requests(id, policy_number, revision, content_hash, generation_version, requested_by)
                VALUES (:id, :number, :revision, :hash, :generation, :actor)
                """).param("id", input.id()).param("number", input.policyNumber()).param("revision", input.revision())
                .param("hash", current.contentHash()).param("generation", input.generationVersion()).param("actor", input.requestedBy()).update();
        return findPrepared(input.id()).orElseThrow();
    }

    @Transactional
    public Result complete(UUID requestId, String body) {
        if (body == null || body.getBytes(StandardCharsets.UTF_8).length > PolicyRuleStore.MAX_DEFINITION_BYTES)
            throw new IllegalArgumentException("추출 결과는 UTF-8 128KB까지 저장할 수 있습니다.");
        var request = findPrepared(requestId).orElseThrow(() -> new IllegalArgumentException("AI 조건 추출 요청을 찾을 수 없습니다."));
        var current = lockPolicy(request.policyNumber());
        var previous = storedResult(requestId);
        if (previous.isPresent()) {
            if (!previous.get().body().equals(body)) throw new IllegalStateException("이미 다른 결과를 저장한 요청입니다. 새 요청 ID를 사용해주세요.");
            return previous.get().result();
        }

        Status status;
        UUID versionId = null;
        if (!current.matches(request.revision(), request.contentHash())) status = Status.SOURCE_CHANGED;
        else if (hasNewerRequest(request)) status = Status.REQUEST_SUPERSEDED;
        else {
            var definition = parse(body);
            if (definition == null) status = Status.INVALID_DEFINITION;
            else if (!definition.policyNumber().equals(request.policyNumber()) || !definition.contentHash().equals(request.contentHash())
                    || !definition.ruleVersion().equals(request.ruleVersion())) status = Status.INVALID_REFERENCE;
            else if (jdbc.sql("SELECT EXISTS(SELECT 1 FROM policy_rule_versions WHERE policy_number = :number AND rule_version = :version)")
                    .param("number", request.policyNumber()).param("version", request.ruleVersion()).query(Boolean.class).single()) status = Status.VERSION_CONFLICT;
            else {
                versionId = rules.draft(definition, "AI/" + request.generationVersion(),
                        "AI 추출 후보 · 검토 필요 · 요청 " + request.id() + " · 요청자 " + request.requestedBy());
                status = Status.DRAFT_CREATED;
            }
        }
        jdbc.sql("""
                INSERT INTO policy_ai_rule_candidates(request_id, body, body_sha256, status, version_id)
                VALUES (:id, :body, :hash, :status, :version)
                """).param("id", requestId).param("body", body).param("hash", OntongPolicyCapture.hash(body))
                .param("status", status.name()).param("version", versionId).update();
        return storedResult(requestId).orElseThrow().result();
    }

    public Optional<Result> result(UUID requestId) { return storedResult(requestId).map(StoredResult::result); }

    public Prepared prepared(UUID id) {
        return findPrepared(id).orElseThrow(() -> new IllegalArgumentException("AI 조건 추출 요청을 찾을 수 없습니다."));
    }

    // 호출 예약·발송 기록과 같은 트랜잭션에서 검사할 때 정책 행 잠금을 유지한다.
    @Transactional
    public boolean lockCurrent(Prepared request) {
        return lockPolicy(request.policyNumber()).matches(request.revision(), request.contentHash()) && !hasNewerRequest(request)
                && storedResult(request.id()).isEmpty();
    }

    private boolean hasNewerRequest(Prepared request) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM policy_ai_rule_requests WHERE policy_number = :number AND sequence > :sequence)")
                .param("number", request.policyNumber()).param("sequence", request.sequence()).query(Boolean.class).single();
    }

    private PolicyRuleDefinition parse(String body) {
        try { return rules.parseStrict(body); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private PolicyRuleStore.PublishedHead lockPolicy(String number) {
        return rules.lockPublished(number).orElseThrow(() -> new IllegalArgumentException("공개된 정책 원문을 찾을 수 없습니다."));
    }

    private Optional<Prepared> findPrepared(UUID id) {
        return jdbc.sql("""
                SELECT q.*, r.content::text, s.id AS snapshot_id, s.captured_at, s.raw_policy::text
                FROM policy_ai_rule_requests q
                JOIN policy_revisions r ON r.policy_number = q.policy_number AND r.revision = q.revision
                JOIN policy_source_snapshots s ON s.id = r.source_snapshot_id WHERE q.id = :id
                """).param("id", id).query((rs, row) -> new Prepared(rs.getObject("id", UUID.class), rs.getLong("sequence"),
                        rs.getString("policy_number"), rs.getLong("revision"), rs.getString("content_hash"), "ai-" + id,
                        rs.getString("generation_version"), rs.getString("requested_by"), rs.getObject("prepared_at", OffsetDateTime.class).toInstant(),
                        rs.getLong("snapshot_id"), rs.getObject("captured_at", OffsetDateTime.class).toInstant(),
                        mapper.readTree(rs.getString("raw_policy")), mapper.readValue(rs.getString("content"), PolicyContent.class))).optional();
    }

    private Optional<StoredResult> storedResult(UUID id) {
        return jdbc.sql("SELECT * FROM policy_ai_rule_candidates WHERE request_id = :id").param("id", id)
                .query((rs, row) -> new StoredResult(rs.getString("body"), new Result(rs.getObject("request_id", UUID.class),
                        Status.valueOf(rs.getString("status")), rs.getObject("version_id", UUID.class), rs.getString("body_sha256"),
                        rs.getObject("recorded_at", OffsetDateTime.class).toInstant()))).optional();
    }

    public record Preparation(@NotNull UUID id, @NotBlank @Pattern(regexp = "[0-9]{20}") String policyNumber,
                              @Positive long revision, @NotBlank @Size(max = 100) String generationVersion,
                              @NotBlank @Size(max = 100) String requestedBy) {}
    public record Prepared(UUID id, long sequence, String policyNumber, long revision, String contentHash, String ruleVersion,
                           String generationVersion, @JsonIgnore String requestedBy, Instant preparedAt, long sourceSnapshotId,
                           Instant sourceCapturedAt, JsonNode rawPolicy, PolicyContent content) {}
    public enum Status { DRAFT_CREATED, SOURCE_CHANGED, REQUEST_SUPERSEDED, INVALID_DEFINITION, INVALID_REFERENCE, VERSION_CONFLICT }
    public record Result(UUID requestId, Status status, UUID versionId, String bodySha256, Instant recordedAt) {}
    private record StoredResult(String body, Result result) {}
}
