package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationLifecycleStore.Snapshot;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Phase;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Deferred;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.HoldReason;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Ready;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Schedule;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.StopReason;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Stopped;
import kr.youthpolicymate.ingestion.AiReservationRecoveryReviewStore.ResumeReason;
import kr.youthpolicymate.ingestion.AiReservationRecoveryReviewStore.ResumeRecord;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Attempt;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.RecoveryResult;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class AiReservationRecoveryRetryPolicyTest {
    private static final Instant BASE = Instant.parse("2026-09-01T01:00:00Z");

    private final AiReservationRecoveryRetryPolicy policy = new AiReservationRecoveryRetryPolicy();
    private final Schedule schedule = new Schedule(3, List.of(Duration.ofSeconds(5), Duration.ofSeconds(20)));

    @Test
    @DisplayName("복구 이력이 없는 미완료 예약은 첫 시도를 바로 허용한다")
    void allowsFirstAttemptImmediately() {
        assertThat(policy.decide(schedule, snapshot(Phase.DISPATCHED, at(1)), List.of(), at(1)))
                .isEqualTo(new Ready(1, at(1)));
    }

    @Test
    @DisplayName("활성 임대가 끝나기 전에는 다음 복구 시도를 보류한다")
    void defersWhileLeaseIsActive() {
        var active = attempt(1, Status.ACTIVE, at(2), at(12), Optional.empty(), Optional.empty());

        assertThat(policy.decide(schedule, snapshot(), List.of(active), at(11)))
                .isEqualTo(new Deferred(HoldReason.ACTIVE_LEASE, at(12)));
    }

    @Test
    @DisplayName("작업자 임대가 만료돼도 설정한 재확인 간격 전에는 보류한다")
    void defersExpiredLeaseUntilRetryInterval() {
        var active = attempt(1, Status.ACTIVE, at(2), at(12), Optional.empty(), Optional.empty());

        assertThat(policy.decide(schedule, snapshot(), List.of(active), at(15)))
                .isEqualTo(new Deferred(HoldReason.RETRY_INTERVAL, at(17)));
    }

    @Test
    @DisplayName("재확인 가능 시각과 같으면 만료된 임대의 다음 시도를 허용한다")
    void allowsRetryAtExactBoundary() {
        var active = attempt(1, Status.ACTIVE, at(2), at(12), Optional.empty(), Optional.empty());

        assertThat(policy.decide(schedule, snapshot(), List.of(active), at(17)))
                .isEqualTo(new Ready(2, at(17)));
    }

    @Test
    @DisplayName("확인 실패는 완료 시각부터 해당 순번의 간격을 적용한다")
    void schedulesRetryAfterFailedCheck() {
        var failed = completed(1, at(7), RecoveryResult.CHECK_FAILED);

        assertThat(policy.decide(schedule, snapshot(), List.of(failed), at(11)))
                .isEqualTo(new Deferred(HoldReason.RETRY_INTERVAL, at(12)));
        assertThat(policy.decide(schedule, snapshot(), List.of(failed), at(12)))
                .isEqualTo(new Ready(2, at(12)));
    }

    @Test
    @DisplayName("확인을 완료했어도 예약이 더 늦게 갱신된 채 미완료면 최근 시각부터 재확인을 예약한다")
    void schedulesRetryForCompletedCheckOnUnresolvedReservation() {
        var checked = completed(1, at(7), RecoveryResult.CHECK_COMPLETED);
        var recentlyUpdated = snapshot(Phase.DISPATCHED, at(20));

        assertThat(policy.decide(schedule, recentlyUpdated, List.of(checked), at(24)))
                .isEqualTo(new Deferred(HoldReason.RETRY_INTERVAL, at(25)));
        assertThat(policy.decide(schedule, recentlyUpdated, List.of(checked), at(25)))
                .isEqualTo(new Ready(2, at(25)));
    }

    @Test
    @DisplayName("수동 검토 결과는 남은 횟수와 관계없이 자동 재시도를 중단한다")
    void stopsAfterManualReview() {
        var review = completed(1, at(7), RecoveryResult.MANUAL_REVIEW_REQUIRED);

        assertThat(policy.decide(schedule, snapshot(), List.of(review), at(20)))
                .isEqualTo(new Stopped(StopReason.MANUAL_REVIEW_REQUIRED, 1));
    }

    @Test
    @DisplayName("수동 검토를 재개해도 재확인 간격과 최대 시도 횟수를 유지한다")
    void resumesManualReviewWithoutResettingRetryLimits() {
        var review = completed(1, at(7), RecoveryResult.MANUAL_REVIEW_REQUIRED);
        var resume = new ResumeRecord(
                "resume-1", "reservation-a", review.attemptId(), "operator-a",
                ResumeReason.INTERNAL_STATE_VERIFIED, Phase.DISPATCHED, at(1), at(20));

        assertThat(policy.decide(schedule, snapshot(), List.of(review), List.of(resume), at(24)))
                .isEqualTo(new Deferred(HoldReason.RETRY_INTERVAL, at(25)));
        assertThat(policy.decide(schedule, snapshot(), List.of(review), List.of(resume), at(25)))
                .isEqualTo(new Ready(2, at(25)));
        assertThat(policy.decide(new Schedule(1, List.of()), snapshot(),
                List.of(review), List.of(resume), at(25)))
                .isEqualTo(new Stopped(StopReason.MAXIMUM_ATTEMPTS_REACHED, 1));
    }

    @Test
    @DisplayName("최대 시도 횟수를 사용하면 미완료 예약도 자동 재시도를 중단한다")
    void stopsAtMaximumAttempts() {
        var history = List.of(
                completed(1, at(7), RecoveryResult.CHECK_FAILED),
                completed(2, at(30), RecoveryResult.CHECK_FAILED),
                expired(3, at(60)));

        assertThat(policy.decide(schedule, snapshot(), history, at(100)))
                .isEqualTo(new Stopped(StopReason.MAXIMUM_ATTEMPTS_REACHED, 3));
    }

    @Test
    @DisplayName("예약이 종료 상태면 복구 이력이 없어도 자동 확인을 중단한다")
    void stopsForTerminalReservation() {
        assertThat(policy.decide(schedule, snapshot(Phase.SETTLED, at(1)), List.of(), at(10)))
                .isEqualTo(new Stopped(StopReason.RESERVATION_TERMINAL, 0));
    }

    @Test
    @DisplayName("일정과 복구 이력의 필수 경계가 맞지 않으면 입력 오류로 거절한다")
    void rejectsInvalidScheduleAndHistory() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Schedule(0, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Schedule(3, List.of(Duration.ofSeconds(5))));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new Schedule(2, List.of(Duration.ZERO)));

        var otherReservation = new Attempt("attempt-a", "other-reservation", 1, "worker-a",
                Phase.DISPATCHED, at(2), at(12), Status.ACTIVE,
                Optional.empty(), Optional.empty(), Optional.empty());
        assertThatIllegalArgumentException().isThrownBy(() ->
                policy.decide(schedule, snapshot(), List.of(otherReservation), at(20)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                policy.decide(schedule, snapshot(), List.of(completed(2, at(7), RecoveryResult.CHECK_FAILED)), at(20)));
        assertThatIllegalArgumentException().isThrownBy(() ->
                policy.decide(schedule, snapshot(), List.of(), at(-1)));
    }

    private static Snapshot snapshot() {
        return snapshot(Phase.DISPATCHED, at(1));
    }

    private static Snapshot snapshot(Phase phase, Instant updatedAt) {
        return new Snapshot("reservation-a", "budget-a", phase, new BigDecimal("10"), at(0),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), updatedAt);
    }

    private static Attempt completed(long number, Instant completedAt, RecoveryResult result) {
        return attempt(number, Status.COMPLETED, at(number), at(number + 10),
                Optional.of(completedAt), Optional.of(result));
    }

    private static Attempt expired(long number, Instant expiredAt) {
        return attempt(number, Status.EXPIRED, at(number), at(number + 10),
                Optional.of(expiredAt), Optional.empty());
    }

    private static Attempt attempt(long number, Status status, Instant claimedAt, Instant leaseUntil,
                                   Optional<Instant> completedAt, Optional<RecoveryResult> result) {
        return new Attempt("attempt-" + number, "reservation-a", number, "worker-a",
                Phase.DISPATCHED, claimedAt, leaseUntil, status,
                status == Status.ACTIVE ? Optional.empty() : Optional.of(Phase.DISPATCHED),
                completedAt, result);
    }

    private static Instant at(long seconds) {
        return BASE.plusSeconds(seconds);
    }
}
