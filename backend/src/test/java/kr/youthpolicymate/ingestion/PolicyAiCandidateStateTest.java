package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.PolicyAiResult.Generated;
import kr.youthpolicymate.ingestion.PolicyAiResult.Kind;
import kr.youthpolicymate.ingestion.PolicyAiResult.Request;
import kr.youthpolicymate.ingestion.PolicyAiResult.Unavailable;
import kr.youthpolicymate.ingestion.PolicyAiResult.UnavailableReason;
import kr.youthpolicymate.policy.PolicyObservation;
import kr.youthpolicymate.policy.PolicyObservation.ContentFingerprint;
import kr.youthpolicymate.policy.PolicyObservation.Failed;
import kr.youthpolicymate.policy.PolicyObservation.FailureReason;
import kr.youthpolicymate.policy.PolicyObservation.Readable;
import kr.youthpolicymate.policy.PolicyObservation.SnapshotReference;
import kr.youthpolicymate.policy.PolicyRevisionState;
import kr.youthpolicymate.policy.PolicyRevisionState.AppliedRevision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.Optional;

import static kr.youthpolicymate.ingestion.PolicyAiCandidateState.Decision.*;
import static org.assertj.core.api.Assertions.*;

class PolicyAiCandidateStateTest {
    private static final String POLICY = "synthetic-policy";
    private static final String VERSION = "synthetic-generation-a";
    private static final Instant BASE = Instant.parse("2026-08-31T00:00:00Z");

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("요약과 조건 추출 결과를 정책 변경 없이 각각의 후보로 보존한다")
    void recordsCandidateWithRevisionAndRequest(Kind kind) {
        var policy = policy();
        var initial = PolicyAiCandidateState.empty(POLICY, kind);
        var request = request(policy, kind, VERSION, 10);
        var result = generated(request, "candidate-a");
        var transition = initial.consider(policy, request, result);

        assertThat(transition.decision()).isEqualTo(CANDIDATE_RECORDED);
        assertThat(transition.state().candidateFor(policy, VERSION)).contains(result);
        assertThat(transition.state().lastProcessedResult()).contains(result);
        assertThat(transition.state().lastGeneratedResult()).contains(result);
        assertThat(result.request().sourceRevision()).isEqualTo(policy.currentRevision().orElseThrow());
        assertThat(initial.lastGeneratedResult()).isEmpty();
        assertThat(policy.currentRevision().orElseThrow().number()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 적용 개정이 없으면 후보를 수용하거나 현재 결과로 제공하지 않는다")
    void requiresCurrentRevision() {
        var existing = policy();
        var request = request(existing, Kind.SUMMARY, VERSION, 10);
        var result = generated(request, "candidate-a");
        var initial = empty();
        var missing = PolicyRevisionState.empty(POLICY);

        assertThat(initial.consider(missing, request, result).decision()).isEqualTo(NO_CURRENT_REVISION);
        var recorded = initial.consider(existing, request, result).state();
        assertThat(recorded.candidateFor(missing, VERSION)).isEmpty();
    }

    @Test
    @DisplayName("같은 내용의 새 원본이나 수집 실패는 기존 개정의 후보를 지우지 않는다")
    void reusesCandidateWhenAppliedRevisionIsUnchanged() {
        var first = policy();
        var request = request(first, Kind.SUMMARY, VERSION, 10);
        var result = generated(request, "candidate-a");
        var candidates = empty().consider(first, request, result).state();
        var recollected = first.consider(observation(2, "a")).state();
        var failed = recollected.consider(new PolicyObservation(POLICY, 3, at(3),
                new Failed(FailureReason.FETCH_FAILED, Optional.empty()))).state();

        assertThat(recollected.lastConfirmedObservation()).isNotEqualTo(first.lastConfirmedObservation());
        assertThat(candidates.candidateFor(recollected, VERSION)).contains(result);
        assertThat(candidates.candidateFor(failed, VERSION)).contains(result);
        assertThat(request.sourceRevision()).isEqualTo(failed.currentRevision().orElseThrow());
    }

    @Test
    @DisplayName("개정 변경과 A→B→A에서 이전 후보를 현재 결과로 제공하거나 늦게 수용하지 않는다")
    void rejectsPreviousRevisionEvenWhenContentReturns() {
        var first = policy();
        var oldRequest = request(first, Kind.SUMMARY, VERSION, 10);
        var oldResult = generated(oldRequest, "candidate-a");
        var state = empty().consider(first, oldRequest, oldResult).state();
        var changed = first.consider(observation(2, "b")).state();
        var returned = changed.consider(observation(3, "a")).state();

        for (var current : new PolicyRevisionState[]{changed, returned}) {
            var expected = request(current, Kind.SUMMARY, VERSION, 20);
            var transition = state.consider(current, expected, oldResult);
            assertThat(transition.decision()).isEqualTo(REVISION_MISMATCH);
            assertThat(transition.state()).isSameAs(state);
            assertThat(state.candidateFor(current, VERSION)).isEmpty();
            assertThat(state.lastGeneratedResult()).contains(oldResult);
            assertThat(state.consider(current, oldRequest, oldResult).decision()).isEqualTo(REVISION_MISMATCH);
        }
    }

    @Test
    @DisplayName("개정 번호가 같아도 원본 근거를 바꾼 요청과 결과는 거절한다")
    void checksFullSourceRevisionNotOnlyNumber() {
        var current = policy();
        var expected = request(current, Kind.SUMMARY, VERSION, 10);
        var differentSource = new AppliedRevision(1, observation(2, "a"));
        var mismatched = new Request(differentSource, Kind.SUMMARY, VERSION, 10, expected.preparedAt());
        var result = generated(mismatched, "candidate-a");

        assertThat(empty().consider(current, expected, result).decision()).isEqualTo(SOURCE_MISMATCH);
        assertThat(empty().consider(current, mismatched, result).decision()).isEqualTo(SOURCE_MISMATCH);
    }

    @Test
    @DisplayName("생성 방식 변경 시 이전 후보는 이력으로만 남고 새 방식의 결과만 받는다")
    void separatesGenerationVersions() {
        var current = policy();
        var previousRequest = request(current, Kind.SUMMARY, VERSION, 10);
        var previousResult = generated(previousRequest, "candidate-a");
        var state = empty().consider(current, previousRequest, previousResult).state();
        String nextVersion = "replacement-generation";
        var expected = request(current, Kind.SUMMARY, nextVersion, 20);

        assertThat(state.candidateFor(current, nextVersion)).isEmpty();
        assertThat(state.lastGeneratedResult()).contains(previousResult);
        assertThat(state.consider(current, expected, previousResult).decision()).isEqualTo(GENERATION_VERSION_MISMATCH);
        var replacement = generated(expected, "candidate-b");
        var updated = state.consider(current, expected, replacement).state();
        assertThat(updated.candidateFor(current, nextVersion)).contains(replacement);
        assertThat(updated.candidateFor(current, VERSION)).isEmpty();
    }

    @Test
    @DisplayName("새 요청 결과 전후 모두 늦은 이전 요청을 거절하고 오래된 예정 요청도 덮어쓰지 못한다")
    void rejectsLateRequestBeforeAndAfterNewResult() {
        var current = policy();
        var oldRequest = request(current, Kind.SUMMARY, VERSION, 10);
        var expected = request(current, Kind.SUMMARY, VERSION, 20);
        var late = new PolicyAiResult(oldRequest, at(500), new Generated("late", "a".repeat(64)));
        assertThat(empty().consider(current, expected, late).decision()).isEqualTo(STALE_REQUEST);

        var accepted = generated(expected, "candidate-b");
        var state = empty().consider(current, expected, accepted).state();
        assertThat(state.consider(current, expected, late).decision()).isEqualTo(STALE_REQUEST);
        assertThat(state.consider(current, oldRequest, late).decision()).isEqualTo(STALE_REQUEST);
        assertThat(state.candidateFor(current, VERSION)).contains(accepted);
    }

    @Test
    @DisplayName("동일 결과 재전달은 그대로 유지하고 같은 요청의 다른 결과는 충돌로 남긴다")
    void handlesReplayAndConflictingResults() {
        var current = policy();
        var request = request(current, Kind.SUMMARY, VERSION, 10);
        var accepted = generated(request, "candidate-a");
        var state = empty().consider(current, request, accepted).state();

        var replay = state.consider(current, request, accepted);
        var conflict = state.consider(current, request, generated(request, "candidate-b"));
        assertThat(replay.decision()).isEqualTo(REPLAYED);
        assertThat(replay.state()).isSameAs(state);
        assertThat(conflict.decision()).isEqualTo(RESULT_CONFLICT);
        assertThat(conflict.state()).isSameAs(state);
    }

    @Test
    @DisplayName("실패·잘못된 출력·한도 보류는 기존 후보를 유지하고 같은 순번의 성공으로 덮지 않는다")
    void keepsCandidateWhenNewResultIsUnavailable() {
        var current = policy();
        var first = request(current, Kind.SUMMARY, VERSION, 10);
        var accepted = generated(first, "candidate-a");
        var state = empty().consider(current, first, accepted).state();
        long sequence = 20;
        for (var reason : UnavailableReason.values()) {
            var request = request(current, Kind.SUMMARY, VERSION, sequence++);
            var failure = new PolicyAiResult(request, at(200), new Unavailable(reason));
            var transition = state.consider(current, request, failure);
            assertThat(transition.decision()).isEqualTo(UNAVAILABLE_RECORDED);
            state = transition.state();
            assertThat(state.lastProcessedResult()).contains(failure);
            assertThat(state.candidateFor(current, VERSION)).contains(accepted);
            assertThat(state.consider(current, request, generated(request, "same-request-success")).decision()).isEqualTo(RESULT_CONFLICT);
            assertThat(empty().consider(current, request, failure).state().candidateFor(current, VERSION)).isEmpty();
        }
        var retry = request(current, Kind.SUMMARY, VERSION, 30);
        var recovered = generated(retry, "candidate-recovered");
        assertThat(state.consider(current, retry, recovered).state().candidateFor(current, VERSION)).contains(recovered);
    }

    @Test
    @DisplayName("다른 정책·작업 종류, 미발급 순번과 같은 순번의 요청 정보 충돌을 구분한다")
    void rejectsWrongScopeAndRequestIdentity() {
        var current = policy();
        var expected = request(current, Kind.SUMMARY, VERSION, 10);
        var extraction = request(current, Kind.CONDITION_EXTRACTION, VERSION, 10);
        assertThatIllegalArgumentException().isThrownBy(() -> empty().consider(current, expected, generated(extraction, "extraction")));
        assertThatIllegalArgumentException().isThrownBy(() -> empty().consider(PolicyRevisionState.empty("other-policy"), expected, generated(expected, "summary")));
        var otherObservation = new PolicyObservation("other-policy", 1, BASE, observation(1, "a").outcome());
        var otherRequest = new Request(new AppliedRevision(1, otherObservation), Kind.SUMMARY, VERSION, 10, at(20));
        assertThatIllegalArgumentException().isThrownBy(() -> empty().consider(current, expected, generated(otherRequest, "other")));

        var future = request(current, Kind.SUMMARY, VERSION, 11);
        assertThat(empty().consider(current, expected, generated(future, "future")).decision()).isEqualTo(UNEXPECTED_REQUEST);
        var conflict = new Request(expected.sourceRevision(), expected.kind(), VERSION, 10, at(21));
        assertThat(empty().consider(current, expected, generated(conflict, "conflict")).decision()).isEqualTo(REQUEST_CONFLICT);
    }

    @Test
    @DisplayName("요청 순번·버전·결과 확인 시각의 필수 경계를 확인한다")
    void rejectsInvalidRequestAndResultMetadata() {
        var current = policy();
        var revision = current.currentRevision().orElseThrow();
        assertThatIllegalArgumentException().isThrownBy(() -> new Request(revision, Kind.SUMMARY, VERSION, 0, BASE));
        assertThatIllegalArgumentException().isThrownBy(() -> new Request(revision, Kind.SUMMARY, " ", 1, BASE));
        var request = request(current, Kind.SUMMARY, VERSION, 10);
        assertThatIllegalArgumentException().isThrownBy(() -> new PolicyAiResult(request, BASE, new Unavailable(UnavailableReason.REQUEST_FAILED)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Generated("candidate-a", "not-a-sha256"));
    }

    private static PolicyAiCandidateState empty() { return PolicyAiCandidateState.empty(POLICY, Kind.SUMMARY); }
    private static Instant at(long seconds) { return BASE.plusSeconds(seconds); }
    private static PolicyRevisionState policy() { return PolicyRevisionState.empty(POLICY).consider(observation(1, "a")).state(); }

    private static PolicyObservation observation(long sequence, String content) {
        return new PolicyObservation(POLICY, sequence, at(sequence), new Readable(
                new SnapshotReference("synthetic-source", "raw-" + sequence, "a".repeat(64)),
                new ContentFingerprint("comparison-a", content.repeat(64))));
    }

    private static Request request(PolicyRevisionState policy, Kind kind, String version, long sequence) {
        return new Request(policy.currentRevision().orElseThrow(), kind, version, sequence, at(10 + sequence));
    }

    private static PolicyAiResult generated(Request request, String candidate) {
        return new PolicyAiResult(request, at(100 + request.sequence()), new Generated(candidate, "f".repeat(64)));
    }
}
