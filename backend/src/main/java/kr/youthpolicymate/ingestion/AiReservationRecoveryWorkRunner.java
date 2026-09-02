package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Criteria;
import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Item;
import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Report;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Ready;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Lease;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.ReadyClaimOutcome;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.ReadyClaimed;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

// 한 번 조회한 제한된 후보를 순서대로 처리한다. 운영 일정과 임대 식별자는 호출 측이 제공한다.
public final class AiReservationRecoveryWorkRunner {
    private final AiReservationRecoveryOperationsQuery operationsQuery;
    private final AiReservationRecoveryWorkAssigner workAssigner;
    private final PolicyAiRecoveryCoordinator recoveryCoordinator;

    public AiReservationRecoveryWorkRunner(AiReservationRecoveryOperationsQuery operationsQuery,
                                           AiReservationRecoveryWorkAssigner workAssigner,
                                           PolicyAiRecoveryCoordinator recoveryCoordinator) {
        this.operationsQuery = Objects.requireNonNull(operationsQuery, "AI 예약 복구 운영 조회가 필요합니다.");
        this.workAssigner = Objects.requireNonNull(workAssigner, "AI 예약 복구 작업 배정기가 필요합니다.");
        this.recoveryCoordinator = Objects.requireNonNull(recoveryCoordinator, "AI 예약 복구 조정자가 필요합니다.");
    }

    public BatchRun run(Criteria criteria, Function<Item, Lease> leaseFactory) {
        Objects.requireNonNull(criteria, "AI 예약 복구 운영 조회 조건이 필요합니다.");
        Objects.requireNonNull(leaseFactory, "준비된 복구 후보의 임대 생성기가 필요합니다.");

        Report report = operationsQuery.findOldestUnresolved(criteria);
        var results = new ArrayList<CandidateRun>(report.items().size());
        for (Item candidate : report.items()) {
            if (!(candidate.decision() instanceof Ready)) {
                results.add(new SkippedByReport(candidate));
                continue;
            }

            Lease lease = Objects.requireNonNull(
                    leaseFactory.apply(candidate), "준비된 복구 후보의 임대 정보가 필요합니다.");
            ReadyClaimOutcome assignment = workAssigner.assign(report, candidate, lease);
            if (!(assignment instanceof ReadyClaimed claimed)) {
                results.add(new AssignmentNotClaimed(candidate, assignment));
                continue;
            }

            try {
                results.add(new RecoveryFinished(
                        candidate, claimed, recoveryCoordinator.recoverAssigned(claimed)));
            } catch (RuntimeException failure) {
                results.add(new RecoveryFailed(candidate, claimed, failure));
            }
        }
        return new BatchRun(report, results);
    }

    public record BatchRun(Report report, List<CandidateRun> candidates) {
        public BatchRun {
            Objects.requireNonNull(report, "AI 예약 복구 운영 조회 결과가 필요합니다.");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "AI 예약 복구 후보별 실행 결과가 필요합니다."));
        }
    }

    public sealed interface CandidateRun
            permits SkippedByReport, AssignmentNotClaimed, RecoveryFinished, RecoveryFailed {
        Item candidate();
    }

    public record SkippedByReport(Item candidate) implements CandidateRun {
        public SkippedByReport {
            Objects.requireNonNull(candidate, "건너뛴 AI 예약 복구 후보가 필요합니다.");
            if (candidate.decision() instanceof Ready) {
                throw new IllegalArgumentException("복구 가능 후보는 운영 조회 판단으로 건너뛸 수 없습니다.");
            }
        }
    }

    public record AssignmentNotClaimed(Item candidate, ReadyClaimOutcome assignment) implements CandidateRun {
        public AssignmentNotClaimed {
            Objects.requireNonNull(candidate, "배정하지 못한 AI 예약 복구 후보가 필요합니다.");
            Objects.requireNonNull(assignment, "AI 예약 복구 작업 배정 결과가 필요합니다.");
            if (assignment instanceof ReadyClaimed) {
                throw new IllegalArgumentException("획득한 복구 소유권은 미배정 결과로 반환할 수 없습니다.");
            }
        }
    }

    public record RecoveryFinished(
            Item candidate, ReadyClaimed assignment, PolicyAiRecoveryCoordinator.Run recovery
    ) implements CandidateRun {
        public RecoveryFinished {
            Objects.requireNonNull(candidate, "실행한 AI 예약 복구 후보가 필요합니다.");
            Objects.requireNonNull(assignment, "실행한 AI 예약 복구 배정이 필요합니다.");
            Objects.requireNonNull(recovery, "AI 예약 복구 실행 결과가 필요합니다.");
        }
    }

    public record RecoveryFailed(
            Item candidate, ReadyClaimed assignment, RuntimeException failure
    ) implements CandidateRun {
        public RecoveryFailed {
            Objects.requireNonNull(candidate, "실패한 AI 예약 복구 후보가 필요합니다.");
            Objects.requireNonNull(assignment, "실패한 AI 예약 복구 배정이 필요합니다.");
            Objects.requireNonNull(failure, "AI 예약 복구 실행 실패가 필요합니다.");
        }
    }
}
