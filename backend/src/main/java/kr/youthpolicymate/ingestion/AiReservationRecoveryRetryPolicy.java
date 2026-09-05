package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationLifecycleStore.Snapshot;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Phase;
import kr.youthpolicymate.ingestion.AiReservationRecoveryReviewStore.ResumeRecord;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Attempt;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.RecoveryResult;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Status;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// 작업자를 실행하거나 DB를 바꾸지 않는다. 현재 예약과 복구 이력으로 다음 확인 가능 시점만 계산한다.
public final class AiReservationRecoveryRetryPolicy {
    public Decision decide(Schedule schedule, Snapshot reservation,
                           List<Attempt> history, Instant evaluatedAt) {
        return decide(schedule, reservation, history, List.of(), evaluatedAt);
    }

    public Decision decide(Schedule schedule, Snapshot reservation,
                           List<Attempt> history, List<ResumeRecord> reviewResumes,
                           Instant evaluatedAt) {
        Objects.requireNonNull(schedule, "AI 예약 복구 재확인 일정이 필요합니다.");
        Objects.requireNonNull(reservation, "AI 요청 예약 상태가 필요합니다.");
        Objects.requireNonNull(history, "AI 예약 복구 시도 이력이 필요합니다.");
        Objects.requireNonNull(reviewResumes, "AI 예약 복구 수동 검토 재개 이력이 필요합니다.");
        Objects.requireNonNull(evaluatedAt, "AI 예약 복구 재확인 판단 시각이 필요합니다.");
        if (evaluatedAt.isBefore(reservation.updatedAt())) {
            throw new IllegalArgumentException("재확인 판단은 현재 예약 상태보다 빠를 수 없습니다.");
        }

        List<Attempt> attempts = validateHistory(reservation.reservationId(), history);
        Map<String, ResumeRecord> resumesByAttempt = validateReviewResumes(
                reservation.reservationId(), attempts, reviewResumes);
        if (isTerminal(reservation.phase())) {
            return new Stopped(StopReason.RESERVATION_TERMINAL, attempts.size());
        }
        if (attempts.isEmpty()) {
            return new Ready(1, reservation.updatedAt());
        }

        Attempt latest = attempts.get(attempts.size() - 1);
        if (latest.status() == Status.ACTIVE && evaluatedAt.isBefore(latest.leaseUntil())) {
            return new Deferred(HoldReason.ACTIVE_LEASE, latest.leaseUntil());
        }
        if (latest.status() == Status.COMPLETED
                && latest.result().orElseThrow() == RecoveryResult.MANUAL_REVIEW_REQUIRED
                && !isResumed(latest, resumesByAttempt, evaluatedAt)) {
            return new Stopped(StopReason.MANUAL_REVIEW_REQUIRED, attempts.size());
        }
        if (attempts.size() >= schedule.maximumAttempts()) {
            return new Stopped(StopReason.MAXIMUM_ATTEMPTS_REACHED, attempts.size());
        }

        Instant retryBasis = laterOf(reservation.updatedAt(), completedBoundary(latest));
        ResumeRecord latestResume = resumesByAttempt.get(latest.attemptId());
        if (latestResume != null) retryBasis = laterOf(retryBasis, latestResume.resumedAt());
        Instant eligibleAt = retryBasis.plus(schedule.delayAfter(latest.attemptNumber()));
        if (evaluatedAt.isBefore(eligibleAt)) {
            return new Deferred(HoldReason.RETRY_INTERVAL, eligibleAt);
        }
        return new Ready(latest.attemptNumber() + 1, eligibleAt);
    }

    private static List<Attempt> validateHistory(String reservationId, List<Attempt> history) {
        List<Attempt> attempts = List.copyOf(history);
        for (int index = 0; index < attempts.size(); index++) {
            Attempt attempt = attempts.get(index);
            long expectedNumber = index + 1L;
            if (!reservationId.equals(attempt.reservationId())) {
                throw new IllegalArgumentException("현재 예약과 복구 시도 이력의 예약 식별자가 다릅니다.");
            }
            if (attempt.attemptNumber() != expectedNumber) {
                throw new IllegalArgumentException("AI 예약 복구 시도 이력은 1부터 순서대로 필요합니다.");
            }
            if (index < attempts.size() - 1 && attempt.status() == Status.ACTIVE) {
                throw new IllegalArgumentException("활성 복구 시도 뒤에 다음 시도가 올 수 없습니다.");
            }
            validateCompletion(attempt);
        }
        return attempts;
    }

    private static Map<String, ResumeRecord> validateReviewResumes(
            String reservationId, List<Attempt> attempts, List<ResumeRecord> reviewResumes) {
        Map<String, Attempt> attemptsById = new HashMap<>();
        attempts.forEach(attempt -> attemptsById.put(attempt.attemptId(), attempt));

        Map<String, ResumeRecord> resumesByAttempt = new HashMap<>();
        for (ResumeRecord resume : List.copyOf(reviewResumes)) {
            if (!reservationId.equals(resume.reservationId())) {
                throw new IllegalArgumentException("현재 예약과 수동 검토 재개 이력의 예약 식별자가 다릅니다.");
            }
            Attempt attempt = attemptsById.get(resume.manualAttemptId());
            if (attempt == null
                    || attempt.status() != Status.COMPLETED
                    || attempt.result().filter(result -> result == RecoveryResult.MANUAL_REVIEW_REQUIRED).isEmpty()) {
                throw new IllegalArgumentException("수동 검토 재개 이력은 완료된 수동 검토 시도를 가리켜야 합니다.");
            }
            if (resume.resumedAt().isBefore(attempt.completedAt().orElseThrow())) {
                throw new IllegalArgumentException("수동 검토 재개는 대상 복구 시도의 완료보다 빠를 수 없습니다.");
            }
            if (resumesByAttempt.putIfAbsent(resume.manualAttemptId(), resume) != null) {
                throw new IllegalArgumentException("한 수동 검토 시도에는 재개 이력이 하나만 있어야 합니다.");
            }
        }
        return resumesByAttempt;
    }

    private static boolean isResumed(Attempt attempt, Map<String, ResumeRecord> resumesByAttempt,
                                     Instant evaluatedAt) {
        ResumeRecord resume = resumesByAttempt.get(attempt.attemptId());
        return resume != null && !resume.resumedAt().isAfter(evaluatedAt);
    }

    private static void validateCompletion(Attempt attempt) {
        if (attempt.status() == Status.COMPLETED
                && (attempt.completedAt().isEmpty() || attempt.result().isEmpty())) {
            throw new IllegalArgumentException("완료한 복구 시도에는 완료 시각과 확인 결과가 필요합니다.");
        }
        if (attempt.status() == Status.EXPIRED && attempt.completedAt().isEmpty()) {
            throw new IllegalArgumentException("만료된 복구 시도에는 만료 확인 시각이 필요합니다.");
        }
    }

    private static Instant completedBoundary(Attempt attempt) {
        if (attempt.status() == Status.ACTIVE) return attempt.leaseUntil();
        return attempt.completedAt().orElseThrow();
    }

    private static Instant laterOf(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }

    private static boolean isTerminal(Phase phase) {
        return phase == Phase.SETTLED || phase == Phase.CANCELLED || phase == Phase.RELEASED_NO_CHARGE;
    }

    public record Schedule(int maximumAttempts, List<Duration> retryDelays) {
        public Schedule {
            if (maximumAttempts < 1) {
                throw new IllegalArgumentException("AI 예약 복구 최대 시도 횟수는 1 이상이어야 합니다.");
            }
            retryDelays = List.copyOf(Objects.requireNonNull(
                    retryDelays, "AI 예약 복구 재확인 간격이 필요합니다."));
            if (retryDelays.size() != maximumAttempts - 1) {
                throw new IllegalArgumentException("재확인 간격은 최대 시도 횟수보다 하나 적어야 합니다.");
            }
            if (retryDelays.stream().anyMatch(delay -> delay.isZero() || delay.isNegative())) {
                throw new IllegalArgumentException("AI 예약 복구 재확인 간격은 0보다 길어야 합니다.");
            }
        }

        private Duration delayAfter(long attemptNumber) {
            return retryDelays.get(Math.toIntExact(attemptNumber - 1));
        }
    }

    public sealed interface Decision permits Ready, Deferred, Stopped {}

    public enum HoldReason { ACTIVE_LEASE, RETRY_INTERVAL }

    public enum StopReason {
        RESERVATION_TERMINAL,
        MANUAL_REVIEW_REQUIRED,
        MAXIMUM_ATTEMPTS_REACHED
    }

    public record Ready(long nextAttemptNumber, Instant eligibleAt) implements Decision {
        public Ready {
            if (nextAttemptNumber < 1) throw new IllegalArgumentException("다음 복구 시도 순번은 1 이상이어야 합니다.");
            Objects.requireNonNull(eligibleAt, "AI 예약 복구 가능 시각이 필요합니다.");
        }
    }

    public record Deferred(HoldReason reason, Instant reconsiderAt) implements Decision {
        public Deferred {
            Objects.requireNonNull(reason, "AI 예약 복구 보류 사유가 필요합니다.");
            Objects.requireNonNull(reconsiderAt, "AI 예약 복구 재검사 시각이 필요합니다.");
        }
    }

    public record Stopped(StopReason reason, long attemptsUsed) implements Decision {
        public Stopped {
            Objects.requireNonNull(reason, "AI 예약 복구 자동 중단 사유가 필요합니다.");
            if (attemptsUsed < 0) throw new IllegalArgumentException("사용한 복구 시도 횟수는 음수일 수 없습니다.");
        }
    }
}
