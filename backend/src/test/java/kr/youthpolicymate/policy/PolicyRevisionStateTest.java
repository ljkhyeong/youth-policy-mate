package kr.youthpolicymate.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import kr.youthpolicymate.policy.PolicyObservation.ContentFingerprint;
import kr.youthpolicymate.policy.PolicyObservation.Failed;
import kr.youthpolicymate.policy.PolicyObservation.FailureReason;
import kr.youthpolicymate.policy.PolicyObservation.Readable;
import kr.youthpolicymate.policy.PolicyObservation.SnapshotReference;
import static kr.youthpolicymate.policy.PolicyRevisionState.Decision.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PolicyRevisionStateTest {
    private static final String POLICY = "synthetic-policy";
    private static final Instant BASE = Instant.parse("2026-08-31T00:00:00Z");
    private static final ContentFingerprint CONTENT_A = new ContentFingerprint("comparison-1", "a".repeat(64));
    private static final ContentFingerprint CONTENT_B = new ContentFingerprint("comparison-1", "b".repeat(64));

    @Test
    @DisplayName("첫 정상 결과를 개정 1로 만들고 원본·확인 시각·수집 순번을 보존한다")
    void createsFirstRevision() {
        var initial = PolicyRevisionState.empty(POLICY);
        var observation = readable(10, CONTENT_A);
        var transition = initial.consider(observation);

        assertThat(transition.decision()).isEqualTo(REVISION_CREATED);
        assertThat(transition.state().currentRevision()).contains(new PolicyRevisionState.AppliedRevision(1, observation));
        assertThat(transition.state().lastProcessedObservation()).contains(observation);
        assertThat(transition.state().lastConfirmedObservation()).contains(observation);
        assertThat(initial.currentRevision()).isEmpty();
    }

    @Test
    @DisplayName("원본 해시·수집 시각만 바뀌면 개정 근거는 유지하고 정상 확인만 갱신한다")
    void separatesRawChangesFromContentChanges() {
        var first = readable(10, CONTENT_A);
        var state = applied(first);
        var changedRaw = new PolicyObservation(POLICY, 20, BASE.plusSeconds(100),
                new Readable(new SnapshotReference("synthetic-source", "different-raw", "d".repeat(64)), CONTENT_A));
        var transition = state.consider(changedRaw);

        assertThat(transition.decision()).isEqualTo(UNCHANGED);
        assertThat(transition.state().currentRevision()).isEqualTo(state.currentRevision());
        assertThat(transition.state().currentRevision().orElseThrow().observation()).isEqualTo(first);
        assertThat(transition.state().lastConfirmedObservation()).contains(changedRaw);
        assertThat(transition.state().lastProcessedObservation()).contains(changedRaw);
    }

    @Test
    @DisplayName("같은 수집 결과 재처리는 새 개정이나 상태 변경을 만들지 않는다")
    void replaysSameResult() {
        var observation = readable(10, CONTENT_A);
        var state = applied(observation);
        var transition = state.consider(observation);

        assertThat(transition.decision()).isEqualTo(REPLAYED);
        assertThat(transition.state()).isSameAs(state);
    }

    @Test
    @DisplayName("같은 순번에 다른 결과를 붙이면 순번 충돌로 남기고 적용하지 않는다")
    void rejectsConflictingSequence() {
        var state = applied(readable(10, CONTENT_A));
        var conflict = state.consider(readable(10, CONTENT_B));

        assertThat(conflict.decision()).isEqualTo(SEQUENCE_CONFLICT);
        assertThat(conflict.state()).isSameAs(state);
    }

    @Test
    @DisplayName("확인 시각이 더 늦어도 낮은 수집 순번이면 현재 내용을 덮지 않는다")
    void ignoresLateResponse() {
        var state = applied(readable(20, CONTENT_A));
        var older = new PolicyObservation(POLICY, 10, BASE.plusSeconds(200), readable(10, CONTENT_B).outcome());
        var transition = state.consider(older);

        assertThat(transition.decision()).isEqualTo(STALE);
        assertThat(transition.state()).isSameAs(state);
    }

    @Test
    @DisplayName("내용 미변경도 처리 순번을 갱신해 중간 순번의 변경을 막는다")
    void advancesSequenceWithoutNewRevision() {
        var state = applied(readable(10, CONTENT_A)).consider(readable(30, CONTENT_A)).state();
        var late = state.consider(readable(20, CONTENT_B));

        assertThat(late.decision()).isEqualTo(STALE);
        assertThat(late.state()).isSameAs(state);
        assertThat(state.currentRevision().orElseThrow().number()).isEqualTo(1);
        assertThat(state.lastProcessedObservation().orElseThrow().collectionSequence()).isEqualTo(30);
    }

    @Test
    @DisplayName("내용이 A에서 B를 거쳐 A로 돌아오면 세 번째 개정을 만든다")
    void createsRevisionWhenContentReturns() {
        var first = applied(readable(10, CONTENT_A));
        var second = first.consider(readable(20, CONTENT_B));
        var thirdObservation = readable(30, CONTENT_A);
        var third = second.state().consider(thirdObservation);

        assertThat(second.decision()).isEqualTo(REVISION_CREATED);
        assertThat(second.state().currentRevision().orElseThrow().number()).isEqualTo(2);
        assertThat(third.decision()).isEqualTo(REVISION_CREATED);
        assertThat(third.state().currentRevision()).contains(new PolicyRevisionState.AppliedRevision(3, thirdObservation));
        assertThat(first.currentRevision().orElseThrow().number()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패는 정상 개정·확인 시각을 유지하고 늦은 성공의 적용을 막는다")
    void preservesLastGoodContentOnFailure() {
        var original = readable(10, CONTENT_A);
        var failed = new PolicyObservation(POLICY, 30, BASE.plusSeconds(30),
                new Failed(FailureReason.UNREADABLE_CONTENT, Optional.of(snapshot(30))));
        var transition = applied(original).consider(failed);

        assertThat(transition.decision()).isEqualTo(OBSERVATION_FAILED);
        assertThat(transition.state().currentRevision()).contains(new PolicyRevisionState.AppliedRevision(1, original));
        assertThat(transition.state().lastConfirmedObservation()).contains(original);
        assertThat(transition.state().lastProcessedObservation()).contains(failed);
        assertThat(transition.state().consider(readable(20, CONTENT_B)).decision()).isEqualTo(STALE);
        assertThat(transition.state().consider(failed).decision()).isEqualTo(REPLAYED);
        assertThat(transition.state().consider(readable(40, CONTENT_B)).decision()).isEqualTo(REVISION_CREATED);
    }

    @Test
    @DisplayName("첫 수집 실패에는 정상 개정·정상 확인 시각을 만들지 않는다")
    void hasNoGoodRevisionOnInitialFailure() {
        var failed = new PolicyObservation(POLICY, 1, BASE, new Failed(FailureReason.FETCH_FAILED, Optional.empty()));
        var transition = PolicyRevisionState.empty(POLICY).consider(failed);

        assertThat(transition.decision()).isEqualTo(OBSERVATION_FAILED);
        assertThat(transition.state().currentRevision()).isEmpty();
        assertThat(transition.state().lastConfirmedObservation()).isEmpty();
        assertThat(transition.state().lastProcessedObservation()).contains(failed);
    }

    @Test
    @DisplayName("원본·내용 해시가 같아도 비교 방식이 달라지면 재확인 전 개정을 유지한다")
    void requiresMatchingComparisonVersion() {
        var original = readable(10, CONTENT_A);
        var previous = (Readable) original.outcome();
        var changedVersion = new PolicyObservation(POLICY, 30, BASE.plusSeconds(30), new Readable(previous.snapshot(),
                new ContentFingerprint("comparison-2", CONTENT_A.sha256())));
        var transition = applied(original).consider(changedVersion);

        assertThat(transition.decision()).isEqualTo(COMPARISON_REQUIRED);
        assertThat(transition.state().currentRevision()).contains(new PolicyRevisionState.AppliedRevision(1, original));
        assertThat(transition.state().lastConfirmedObservation()).contains(original);
        assertThat(transition.state().lastProcessedObservation()).contains(changedVersion);
        assertThat(transition.state().consider(readable(20, CONTENT_B)).decision()).isEqualTo(STALE);
    }

    @Test
    @DisplayName("다른 정책의 결과와 유효하지 않은 내부 순번·식별값은 거절한다")
    void rejectsInvalidIdentityAndSequence() {
        var state = applied(readable(10, CONTENT_A));
        assertThatIllegalArgumentException().isThrownBy(() -> state.consider(
                new PolicyObservation("another-policy", 20, BASE, readable(20, CONTENT_B).outcome())));
        assertThatIllegalArgumentException().isThrownBy(() -> readable(0, CONTENT_A));
        assertThatIllegalArgumentException().isThrownBy(() -> PolicyRevisionState.empty(" "));
        assertThatIllegalArgumentException().isThrownBy(() -> new ContentFingerprint("comparison-1", ""));
    }

    private static PolicyRevisionState applied(PolicyObservation observation) {
        return PolicyRevisionState.empty(POLICY).consider(observation).state();
    }

    private static PolicyObservation readable(long sequence, ContentFingerprint content) {
        return new PolicyObservation(POLICY, sequence, BASE.plusSeconds(sequence), new Readable(snapshot(sequence), content));
    }

    private static SnapshotReference snapshot(long sequence) {
        return new SnapshotReference("synthetic-source", "snapshot-" + sequence, "c".repeat(64));
    }
}
