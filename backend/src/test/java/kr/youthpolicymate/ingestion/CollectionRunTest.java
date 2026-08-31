package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.CollectionAttempt.EndOfRange;
import kr.youthpolicymate.ingestion.CollectionAttempt.Failed;
import kr.youthpolicymate.ingestion.CollectionAttempt.FailureCode;
import kr.youthpolicymate.ingestion.CollectionAttempt.Id;
import kr.youthpolicymate.ingestion.CollectionAttempt.Interrupted;
import kr.youthpolicymate.ingestion.CollectionAttempt.ItemProcessed;
import kr.youthpolicymate.ingestion.CollectionAttempt.NextPage;
import kr.youthpolicymate.ingestion.CollectionAttempt.Outcome;
import kr.youthpolicymate.ingestion.CollectionAttempt.PageFetched;
import kr.youthpolicymate.ingestion.CollectionAttempt.Result;
import kr.youthpolicymate.ingestion.CollectionPosition.Item;
import kr.youthpolicymate.ingestion.CollectionPosition.Page;
import kr.youthpolicymate.policy.PolicyObservation.SnapshotReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static kr.youthpolicymate.ingestion.CollectionRun.Decision.*;
import static kr.youthpolicymate.ingestion.CollectionRun.Status.*;
import static org.assertj.core.api.Assertions.*;

class CollectionRunTest {
    private static final Instant BASE = Instant.parse("2026-08-31T00:00:00Z");
    private static final Page FIRST = new Page("page-a");
    private static final Page SECOND = new Page("page-b");
    private static final Item ITEM_A = new Item(FIRST.reference(), "item-a");
    private static final Item ITEM_B = new Item(FIRST.reference(), "item-b");
    private static final SnapshotReference SNAPSHOT = new SnapshotReference("synthetic-source", "raw-page-a", "a".repeat(64));

    @Test
    @DisplayName("첫 페이지를 등록하고 진행 중 위치의 중복 시작을 막는다")
    void startsOnlyRegisteredPendingWork() {
        var initial = initial();
        var started = initial.begin(FIRST, BASE);

        assertThat(initial.resumeTargets()).containsExactly(FIRST);
        assertThat(initial.attempts(FIRST)).isEmpty();
        assertThat(started.attemptId()).isEqualTo(new Id("run-a", FIRST, 1));
        assertThat(started.state().status()).isEqualTo(RUNNING);
        assertThat(started.state().resumeTargets()).isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> started.state().begin(FIRST, at(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> initial.begin(SECOND, at(1)));
    }

    @Test
    @DisplayName("빈 페이지도 다음 위치가 있으면 계속 진행하고 명시적 종료 뒤에만 완료한다")
    void requiresExplicitEndOfRange() {
        var first = finish(initial(), FIRST, page(List.of(), new NextPage(SECOND)), 0, 1);

        assertThat(first.status()).isEqualTo(RUNNING);
        assertThat(first.endConfirmed()).isFalse();
        assertThat(first.finishedAt()).isEmpty();
        assertThat(first.resumeTargets()).containsExactly(SECOND);

        var ended = finish(first, SECOND, page(List.of(), EndOfRange.CONFIRMED), 2, 3);
        assertThat(ended.status()).isEqualTo(COMPLETED);
        assertThat(ended.endConfirmed()).isTrue();
        assertThat(ended.finishedAt()).contains(at(3));
        assertThat(ended.pause(at(4))).isSameAs(ended);
    }

    @Test
    @DisplayName("항목 하나가 실패해도 다른 항목·다음 페이지를 처리하고 실패 위치만 재시도한다")
    void retriesOnlyFailedItemAfterPartialSuccess() {
        var discovered = finish(initial(), FIRST, page(List.of("item-a", "item-b"), new NextPage(SECOND)), 0, 1);
        var failure = new Failed(FailureCode.ITEM_PROCESSING_FAILED, Optional.of(SNAPSHOT));
        var failed = finish(discovered, ITEM_A, failure, 2, 3);
        var otherDone = finish(failed, ITEM_B, ItemProcessed.COMPLETED, 2, 4);
        var pagesDone = finish(otherDone, SECOND, page(List.of(), EndOfRange.CONFIRMED), 4, 5);

        assertThat(pagesDone.endConfirmed()).isTrue();
        assertThat(pagesDone.status()).isEqualTo(RETRY_REQUIRED);
        assertThat(pagesDone.finishedAt()).isEmpty();
        assertThat(pagesDone.resumeTargets()).containsExactly(ITEM_A);

        var retried = pagesDone.begin(ITEM_A, at(6));
        var completed = retried.state().record(retried.attemptId(), result(ItemProcessed.COMPLETED, 7)).state();
        assertThat(retried.attemptId().number()).isEqualTo(2);
        assertThat(completed.status()).isEqualTo(COMPLETED);
        assertThat(completed.attempts(ITEM_A)).hasSize(2);
        assertThat(completed.attempts(ITEM_A).getFirst().result()).contains(result(failure, 3));
        assertThat(completed.attempts(ITEM_B)).hasSize(1);
        assertThat(completed.attempts(FIRST)).hasSize(1);
    }

    @Test
    @DisplayName("페이지 실패는 종료나 항목을 만들지 않으며 재시도 후에도 실패 기록을 보존한다")
    void keepsPageFailureHistory() {
        var started = initial().begin(FIRST, BASE);
        var failure = result(new Failed(FailureCode.UNREADABLE_PAGE, Optional.of(SNAPSHOT)), 1);
        var failed = started.state().record(started.attemptId(), failure).state();

        assertThat(failed.positions()).containsExactly(FIRST);
        assertThat(failed.endConfirmed()).isFalse();
        assertThat(failed.resumeTargets()).containsExactly(FIRST);
        assertThat(failed.record(started.attemptId(), failure).decision()).isEqualTo(REPLAYED);

        var retry = failed.begin(FIRST, at(2));
        var done = retry.state().record(retry.attemptId(), result(page(List.of(), EndOfRange.CONFIRMED), 3)).state();
        assertThat(done.attempts(FIRST).getFirst().result()).contains(failure);
        assertThat(done.status()).isEqualTo(COMPLETED);
        assertThat(done.record(started.attemptId(), failure).decision()).isEqualTo(STALE);
    }

    @Test
    @DisplayName("중단 시 진행 중 시도만 닫고 완료 위치를 유지하며 이전 시도의 늦은 결과를 무시한다")
    void pausesAndResumesWithoutAcceptingLateResults() {
        var discovered = finish(initial(), FIRST, page(List.of("item-a", "item-b"), new NextPage(SECOND)), 0, 1);
        var active = discovered.begin(ITEM_A, at(2));
        var otherDone = finish(active.state(), ITEM_B, ItemProcessed.COMPLETED, 2, 3);
        var paused = otherDone.pause(at(4));
        var lateResult = result(ItemProcessed.COMPLETED, 5);

        assertThat(paused.status()).isEqualTo(PAUSED);
        assertThat(paused.lastInterruptedAt()).contains(at(4));
        assertThat(paused.resumeTargets()).containsExactly(ITEM_A, SECOND);
        assertThat(paused.attempts(ITEM_A).getLast().result()).contains(result(Interrupted.PAUSED, 4));
        assertThat(paused.attempts(ITEM_B)).isEqualTo(otherDone.attempts(ITEM_B));
        assertThat(paused.pause(at(5))).isSameAs(paused);
        assertThatIllegalStateException().isThrownBy(() -> paused.begin(SECOND, at(5)));
        assertThat(paused.record(active.attemptId(), lateResult).decision()).isEqualTo(STALE);

        var resumed = paused.resume();
        assertThat(resumed.status()).isEqualTo(RETRY_REQUIRED);
        assertThat(resumed.record(active.attemptId(), lateResult).state()).isSameAs(resumed);
        var retry = resumed.begin(ITEM_A, at(6));
        assertThat(retry.state().record(active.attemptId(), lateResult).decision()).isEqualTo(STALE);
        var done = retry.state().record(retry.attemptId(), result(ItemProcessed.COMPLETED, 7)).state();
        assertThat(done.resumeTargets()).containsExactly(SECOND);
        assertThat(done.attempts(ITEM_A)).hasSize(2);
        assertThat(finish(done, SECOND, page(List.of(), EndOfRange.CONFIRMED), 8, 9).status()).isEqualTo(COMPLETED);
    }

    @Test
    @DisplayName("같은 페이지 결과 재전달은 작업을 늘리지 않고 충돌 결과는 확정 목록을 바꾸지 않는다")
    void freezesSuccessfulPageManifest() {
        var started = initial().begin(FIRST, BASE);
        var accepted = result(page(List.of("item-a"), EndOfRange.CONFIRMED), 1);
        var state = started.state().record(started.attemptId(), accepted).state();
        var replayed = state.record(started.attemptId(), accepted);
        var conflict = state.record(started.attemptId(), result(page(List.of("item-b"), new NextPage(SECOND)), 2));

        assertThat(replayed.decision()).isEqualTo(REPLAYED);
        assertThat(replayed.state()).isSameAs(state);
        assertThat(conflict.decision()).isEqualTo(CONFLICT);
        assertThat(conflict.state()).isSameAs(state);
        assertThat(state.positions()).containsExactly(FIRST, ITEM_A);
        assertThat(state.attempts(FIRST)).hasSize(1);
        assertThatIllegalStateException().isThrownBy(() -> state.begin(FIRST, at(3)));
    }

    @Test
    @DisplayName("검토 필요 항목은 자동 재시도하지 않고 정상 완료와 구분한다")
    void separatesReviewFromRetryableFailure() {
        var discovered = finish(initial(), FIRST, page(List.of("item-a", "item-b"), EndOfRange.CONFIRMED), 0, 1);
        var reviewed = finish(discovered, ITEM_A, ItemProcessed.REVIEW_REQUIRED, 2, 3);
        assertThat(reviewed.status()).isEqualTo(RUNNING);
        var done = finish(reviewed, ITEM_B, ItemProcessed.COMPLETED, 4, 5);

        assertThat(done.status()).isEqualTo(COMPLETED_WITH_REVIEW);
        assertThat(done.reviewTargets()).containsExactly(ITEM_A);
        assertThat(done.resumeTargets()).isEmpty();
        assertThat(done.finishedAt()).contains(at(5));
        assertThatIllegalStateException().isThrownBy(() -> done.begin(ITEM_A, at(6)));
    }

    @Test
    @DisplayName("다른 페이지의 같은 항목 참조를 별개 위치로 유지한다")
    void scopesItemReferenceToPage() {
        var first = finish(initial(), FIRST, page(List.of("item-a"), new NextPage(SECOND)), 0, 1);
        var second = finish(first, SECOND, page(List.of("item-a"), EndOfRange.CONFIRMED), 2, 3);

        assertThat(second.resumeTargets()).containsExactly(ITEM_A, new Item(SECOND.reference(), "item-a"));
        assertThat(second.positions()).hasSize(4);
    }

    @Test
    @DisplayName("다른 실행·미등록 위치·발급하지 않은 시도의 결과를 거절한다")
    void rejectsUnissuedAttemptIdentity() {
        var active = initial().begin(FIRST, BASE).state();
        var result = result(page(List.of(), EndOfRange.CONFIRMED), 1);

        assertThatIllegalArgumentException().isThrownBy(() -> active.record(new Id("other-run", FIRST, 1), result));
        assertThatIllegalArgumentException().isThrownBy(() -> active.record(new Id("run-a", SECOND, 1), result));
        assertThatIllegalArgumentException().isThrownBy(() -> active.record(new Id("run-a", FIRST, 2), result));
        assertThatIllegalArgumentException().isThrownBy(() -> initial().record(new Id("run-a", FIRST, 1), result));
    }

    @Test
    @DisplayName("페이지 순환·중복 항목 목록을 거절하고 진행 상태를 변경하지 않는다")
    void rejectsInvalidPageManifest() {
        var active = initial().begin(FIRST, BASE);
        assertThatIllegalArgumentException().isThrownBy(() -> page(List.of("item-a", "item-a"), EndOfRange.CONFIRMED));
        assertThatIllegalArgumentException().isThrownBy(() -> active.state().record(active.attemptId(),
                result(page(List.of("item-a"), new NextPage(FIRST)), 1)));
        assertThat(active.state().attempts(FIRST).getLast().result()).isEmpty();
        assertThat(active.state().positions()).containsExactly(FIRST);

        var first = active.state().record(active.attemptId(), result(page(List.of(), new NextPage(SECOND)), 1)).state();
        var second = first.begin(SECOND, at(2));
        assertThatIllegalArgumentException().isThrownBy(() -> second.state().record(second.attemptId(),
                result(page(List.of(), new NextPage(FIRST)), 3)));
    }

    @Test
    @DisplayName("작업 종류와 맞지 않는 결과·직접 중단 기록·역전된 시각을 거절한다")
    void rejectsMismatchedResultsAndTimes() {
        assertThatIllegalArgumentException().isThrownBy(() -> initial().begin(FIRST, BASE.minusSeconds(1)));
        var active = initial().begin(FIRST, at(1));
        assertThatIllegalArgumentException().isThrownBy(() -> active.state().record(active.attemptId(), result(ItemProcessed.COMPLETED, 2)));
        assertThatIllegalArgumentException().isThrownBy(() -> active.state().record(active.attemptId(), result(Interrupted.PAUSED, 2)));
        assertThatIllegalArgumentException().isThrownBy(() -> active.state().record(active.attemptId(), result(page(List.of(), EndOfRange.CONFIRMED), 0)));
        assertThatIllegalArgumentException().isThrownBy(() -> active.state().pause(BASE));

        var discovered = finish(initial(), FIRST, page(List.of("item-a"), EndOfRange.CONFIRMED), 0, 1);
        assertThatIllegalArgumentException().isThrownBy(() -> discovered.begin(ITEM_A, BASE));
        var item = discovered.begin(ITEM_A, at(2));
        assertThatIllegalArgumentException().isThrownBy(() -> item.state().record(item.attemptId(), result(page(List.of(), EndOfRange.CONFIRMED), 3)));
        var failure = result(new Failed(FailureCode.ITEM_PROCESSING_FAILED, Optional.empty()), 4);
        var failed = item.state().record(item.attemptId(), failure).state();
        assertThatIllegalArgumentException().isThrownBy(() -> failed.begin(ITEM_A, at(3)));
        var paused = failed.pause(at(5)).resume();
        assertThatIllegalArgumentException().isThrownBy(() -> paused.begin(ITEM_A, at(4)));
    }

    @Test
    @DisplayName("외부 목록과 조회한 이력으로 이미 만든 상태를 수정할 수 없다")
    void protectsImmutableHistory() {
        var references = new ArrayList<>(List.of("item-a"));
        var page = page(references, EndOfRange.CONFIRMED);
        references.add("item-b");
        var done = finish(initial(), FIRST, page, 0, 1);

        assertThat(done.positions()).containsExactly(FIRST, ITEM_A);
        assertThatThrownBy(() -> done.attempts(FIRST).clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> page.itemReferences().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static CollectionRun initial() { return CollectionRun.start("run-a", FIRST, BASE); }
    private static Instant at(long seconds) { return BASE.plusSeconds(seconds); }
    private static Result result(Outcome outcome, long finishedAt) { return new Result(outcome, at(finishedAt)); }

    private static PageFetched page(List<String> references, CollectionAttempt.Continuation continuation) {
        return new PageFetched(SNAPSHOT, references, continuation);
    }

    private static CollectionRun finish(CollectionRun state, CollectionPosition position, Outcome outcome,
                                        long startedAt, long finishedAt) {
        var started = state.begin(position, at(startedAt));
        var transition = started.state().record(started.attemptId(), result(outcome, finishedAt));
        assertThat(transition.decision()).isEqualTo(RECORDED);
        return transition.state();
    }
}
