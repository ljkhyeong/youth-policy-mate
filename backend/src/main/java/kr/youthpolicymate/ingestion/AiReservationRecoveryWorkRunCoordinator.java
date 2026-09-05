package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Item;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Lease;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.CompletionOutcome;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.FailureOutcome;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.RunCompletion;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.RunFailure;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.StartDecision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.StartOutcome;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.StartRequest;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.Summary;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunner.AssignmentNotClaimed;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunner.BatchRun;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunner.RecoveryFailed;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunner.RecoveryFinished;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunner.SkippedByReport;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Function;

// 실행 시작과 완료만 짧게 저장하고, 후보 조회와 외부 확인은 저장 트랜잭션 밖에서 수행한다.
public final class AiReservationRecoveryWorkRunCoordinator {
    private final AiReservationRecoveryWorkRunStore runStore;
    private final AiReservationRecoveryWorkRunner workRunner;
    private final Clock clock;

    public AiReservationRecoveryWorkRunCoordinator(AiReservationRecoveryWorkRunStore runStore,
                                                   AiReservationRecoveryWorkRunner workRunner,
                                                   Clock clock) {
        this.runStore = Objects.requireNonNull(runStore, "AI 예약 복구 작업 실행 저장소가 필요합니다.");
        this.workRunner = Objects.requireNonNull(workRunner, "AI 예약 복구 제한 목록 실행기가 필요합니다.");
        this.clock = Objects.requireNonNull(clock, "AI 예약 복구 작업 실행 시계가 필요합니다.");
    }

    public Execution run(StartRequest request, Function<Item, Lease> leaseFactory) {
        Objects.requireNonNull(request, "AI 예약 복구 작업 실행 요청이 필요합니다.");
        Objects.requireNonNull(leaseFactory, "준비된 복구 후보의 임대 생성기가 필요합니다.");
        StartOutcome start = runStore.start(request);
        if (start.decision() != StartDecision.STARTED) return new NotStarted(start);

        try {
            BatchRun batch = workRunner.run(request.runId(), request.criteria(), leaseFactory);
            CompletionOutcome completion = runStore.complete(new RunCompletion(
                    request.runId(), request.workerId(), clock.instant(), summarize(batch)));
            return new Finished(start, batch, completion);
        } catch (RuntimeException failure) {
            FailureOutcome recorded = runStore.fail(new RunFailure(
                    request.runId(), request.workerId(), clock.instant()));
            return new Failed(start, failure, recorded);
        }
    }

    private static Summary summarize(BatchRun batch) {
        int reportSkipped = 0;
        int assignmentNotClaimed = 0;
        int recoveryFinished = 0;
        int recoveryNotStarted = 0;
        int recoveryFailed = 0;
        int heartbeatStopped = 0;
        for (var candidate : batch.candidates()) {
            if (candidate instanceof SkippedByReport) {
                reportSkipped++;
            } else if (candidate instanceof AssignmentNotClaimed) {
                assignmentNotClaimed++;
            } else if (candidate instanceof RecoveryFinished finished) {
                switch (finished.recovery()) {
                    case PolicyAiRecoveryCoordinator.Recovered ignored -> recoveryFinished++;
                    case PolicyAiRecoveryCoordinator.NotStarted ignored -> recoveryNotStarted++;
                    case PolicyAiRecoveryCoordinator.HeartbeatStopped ignored -> heartbeatStopped++;
                }
            } else if (candidate instanceof RecoveryFailed) {
                recoveryFailed++;
            }
        }
        return new Summary(batch.candidates().size(), reportSkipped, assignmentNotClaimed,
                recoveryFinished, recoveryNotStarted, recoveryFailed, heartbeatStopped);
    }

    public sealed interface Execution permits NotStarted, Finished, Failed {}

    public record NotStarted(StartOutcome start) implements Execution {
        public NotStarted {
            Objects.requireNonNull(start, "시작하지 않은 AI 예약 복구 작업 결과가 필요합니다.");
        }
    }

    public record Finished(
            StartOutcome start, BatchRun batch, CompletionOutcome completion
    ) implements Execution {
        public Finished {
            Objects.requireNonNull(start, "AI 예약 복구 작업 시작 결과가 필요합니다.");
            Objects.requireNonNull(batch, "AI 예약 복구 제한 목록 실행 결과가 필요합니다.");
            Objects.requireNonNull(completion, "AI 예약 복구 작업 완료 기록 결과가 필요합니다.");
        }
    }

    public record Failed(
            StartOutcome start, RuntimeException failure, FailureOutcome recorded
    ) implements Execution {
        public Failed {
            Objects.requireNonNull(start, "AI 예약 복구 작업 시작 결과가 필요합니다.");
            Objects.requireNonNull(failure, "AI 예약 복구 작업 실패가 필요합니다.");
            Objects.requireNonNull(recorded, "AI 예약 복구 작업 실패 기록 결과가 필요합니다.");
        }
    }
}
