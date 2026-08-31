package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.PolicyAiResult.Generated;
import kr.youthpolicymate.ingestion.PolicyAiResult.Kind;
import kr.youthpolicymate.ingestion.PolicyAiResult.Request;
import kr.youthpolicymate.policy.PolicyRevisionState;

import java.util.Objects;
import java.util.Optional;

public final class PolicyAiCandidateState {
    private final String policyId;
    private final Kind kind;
    private final Optional<PolicyAiResult> lastProcessedResult;
    private final Optional<PolicyAiResult> lastGeneratedResult;

    private PolicyAiCandidateState(String policyId, Kind kind, Optional<PolicyAiResult> lastProcessedResult,
                                   Optional<PolicyAiResult> lastGeneratedResult) {
        this.policyId = policyId;
        this.kind = kind;
        this.lastProcessedResult = lastProcessedResult;
        this.lastGeneratedResult = lastGeneratedResult;
    }

    public static PolicyAiCandidateState empty(String policyId, Kind kind) {
        if (policyId == null || policyId.isBlank()) throw new IllegalArgumentException("내부 정책 식별자가 필요합니다.");
        Objects.requireNonNull(kind, "AI 작업 종류가 필요합니다.");
        return new PolicyAiCandidateState(policyId, kind, Optional.empty(), Optional.empty());
    }

    public Transition consider(PolicyRevisionState policy, Request expected, PolicyAiResult result) {
        requirePolicy(policy);
        requireScope(expected);
        Objects.requireNonNull(result, "AI 처리 결과가 필요합니다.");
        requireScope(result.request());
        if (policy.currentRevision().isEmpty()) return unchanged(Decision.NO_CURRENT_REVISION);

        var current = policy.currentRevision().orElseThrow();
        for (Request request : new Request[]{expected, result.request()}) {
            if (request.sourceRevision().number() != current.number()) return unchanged(Decision.REVISION_MISMATCH);
            if (!request.sourceRevision().equals(current)) return unchanged(Decision.SOURCE_MISMATCH);
        }
        if (!result.request().generationVersion().equals(expected.generationVersion())) {
            return unchanged(Decision.GENERATION_VERSION_MISMATCH);
        }
        if (result.request().sequence() < expected.sequence()) return unchanged(Decision.STALE_REQUEST);
        if (result.request().sequence() > expected.sequence()) return unchanged(Decision.UNEXPECTED_REQUEST);
        if (!result.request().equals(expected)) return unchanged(Decision.REQUEST_CONFLICT);

        if (lastProcessedResult.isPresent()) {
            var previous = lastProcessedResult.orElseThrow();
            if (result.request().sequence() < previous.request().sequence()) return unchanged(Decision.STALE_REQUEST);
            if (result.request().sequence() == previous.request().sequence()) {
                return unchanged(previous.equals(result) ? Decision.REPLAYED : Decision.RESULT_CONFLICT);
            }
        }

        var nextResult = Optional.of(result);
        boolean generated = result.outcome() instanceof Generated;
        return new Transition(generated ? Decision.CANDIDATE_RECORDED : Decision.UNAVAILABLE_RECORDED,
                new PolicyAiCandidateState(policyId, kind, nextResult, generated ? nextResult : lastGeneratedResult));
    }

    public Optional<PolicyAiResult> candidateFor(PolicyRevisionState policy, String generationVersion) {
        requirePolicy(policy);
        if (generationVersion == null || generationVersion.isBlank()) {
            throw new IllegalArgumentException("현재 사용할 AI 생성 방식 버전이 필요합니다.");
        }
        return lastGeneratedResult.filter(result -> policy.currentRevision()
                .filter(revision -> revision.equals(result.request().sourceRevision())).isPresent()
                && generationVersion.equals(result.request().generationVersion()));
    }

    public String policyId() { return policyId; }
    public Kind kind() { return kind; }
    public Optional<PolicyAiResult> lastProcessedResult() { return lastProcessedResult; }

    // 이력 확인용이다. 현재 후보로 사용할 때는 candidateFor로 개정·생성 버전을 다시 확인한다.
    public Optional<PolicyAiResult> lastGeneratedResult() { return lastGeneratedResult; }

    private void requirePolicy(PolicyRevisionState policy) {
        Objects.requireNonNull(policy, "현재 정책 상태가 필요합니다.");
        if (!policyId.equals(policy.policyId())) throw new IllegalArgumentException("다른 정책의 상태입니다.");
    }

    private void requireScope(Request request) {
        Objects.requireNonNull(request, "AI 예정 요청이 필요합니다.");
        if (!policyId.equals(request.policyId()) || kind != request.kind()) {
            throw new IllegalArgumentException("다른 정책 또는 AI 작업 종류의 요청입니다.");
        }
    }

    private Transition unchanged(Decision decision) { return new Transition(decision, this); }

    public enum Decision {
        CANDIDATE_RECORDED, UNAVAILABLE_RECORDED, NO_CURRENT_REVISION, REVISION_MISMATCH, SOURCE_MISMATCH,
        GENERATION_VERSION_MISMATCH, STALE_REQUEST, UNEXPECTED_REQUEST, REQUEST_CONFLICT, REPLAYED, RESULT_CONFLICT
    }

    public record Transition(Decision decision, PolicyAiCandidateState state) {}
}
