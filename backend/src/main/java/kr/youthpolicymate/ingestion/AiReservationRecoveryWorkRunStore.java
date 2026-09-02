package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Criteria;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Schedule;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Profile("!preview")
public class AiReservationRecoveryWorkRunStore {
    private final JdbcClient jdbcClient;

    public AiReservationRecoveryWorkRunStore(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "AI 예약 복구 작업 실행 저장소용 DB 접근이 필요합니다.");
    }

    @Transactional
    public StartOutcome start(StartRequest request) {
        Objects.requireNonNull(request, "AI 예약 복구 작업 실행 요청이 필요합니다.");
        int inserted = jdbcClient.sql("""
                insert into ai_reservation_recovery_work_runs (
                    run_id, worker_id, maximum_attempts, retry_delays,
                    stale_at_or_before, evaluated_at, candidate_limit,
                    status, created_at, updated_at
                ) values (
                    :runId, :workerId, :maximumAttempts, :retryDelays,
                    :staleAtOrBefore, :evaluatedAt, :candidateLimit,
                    'RUNNING', :evaluatedAt, :evaluatedAt
                )
                on conflict (run_id) do nothing
                """)
                .param("runId", request.runId())
                .param("workerId", request.workerId())
                .param("maximumAttempts", request.criteria().schedule().maximumAttempts())
                .param("retryDelays", encodeDelays(request.criteria().schedule().retryDelays()))
                .param("staleAtOrBefore", dbTime(request.criteria().staleAtOrBefore()))
                .param("evaluatedAt", dbTime(request.criteria().evaluatedAt()))
                .param("candidateLimit", request.criteria().limit())
                .update();

        WorkRun run = findRequired(request.runId());
        if (inserted == 1) return new StartOutcome(StartDecision.STARTED, run);
        if (!run.matches(request)) return new StartOutcome(StartDecision.RUN_ID_CONFLICT, run);
        return new StartOutcome(run.status() == Status.RUNNING
                ? StartDecision.ALREADY_RUNNING : StartDecision.REPLAYED, run);
    }

    @Transactional
    public CompletionOutcome complete(RunCompletion completion) {
        Objects.requireNonNull(completion, "AI 예약 복구 작업 실행 완료 정보가 필요합니다.");
        Optional<WorkRun> found = lock(completion.runId());
        if (found.isEmpty()) return new CompletionOutcome(CompletionDecision.RUN_NOT_FOUND, Optional.empty());

        WorkRun current = found.orElseThrow();
        if (!current.workerId().equals(completion.workerId())) {
            return new CompletionOutcome(CompletionDecision.WORKER_CONFLICT, Optional.of(current));
        }
        if (completion.finishedAt().isBefore(current.criteria().evaluatedAt())) {
            throw new IllegalArgumentException("AI 예약 복구 작업 완료는 실행 판단보다 빠를 수 없습니다.");
        }
        if (current.status() == Status.COMPLETED) {
            return new CompletionOutcome(current.matches(completion)
                    ? CompletionDecision.REPLAYED : CompletionDecision.COMPLETION_CONFLICT, Optional.of(current));
        }
        if (current.status() != Status.RUNNING) {
            return new CompletionOutcome(CompletionDecision.RUN_NOT_ACTIVE, Optional.of(current));
        }

        Summary summary = completion.summary();
        int updated = jdbcClient.sql("""
                update ai_reservation_recovery_work_runs
                set status = 'COMPLETED', finished_at = :finishedAt,
                    scanned_count = :scannedCount,
                    report_skipped_count = :reportSkippedCount,
                    assignment_not_claimed_count = :assignmentNotClaimedCount,
                    recovery_finished_count = :recoveryFinishedCount,
                    recovery_not_started_count = :recoveryNotStartedCount,
                    recovery_failed_count = :recoveryFailedCount,
                    updated_at = :finishedAt
                where run_id = :runId and status = 'RUNNING'
                """)
                .param("finishedAt", dbTime(completion.finishedAt()))
                .param("scannedCount", summary.scannedCount())
                .param("reportSkippedCount", summary.reportSkippedCount())
                .param("assignmentNotClaimedCount", summary.assignmentNotClaimedCount())
                .param("recoveryFinishedCount", summary.recoveryFinishedCount())
                .param("recoveryNotStartedCount", summary.recoveryNotStartedCount())
                .param("recoveryFailedCount", summary.recoveryFailedCount())
                .param("runId", completion.runId())
                .update();
        requireSingleUpdate(updated);
        return new CompletionOutcome(CompletionDecision.COMPLETED, find(completion.runId()));
    }

    @Transactional
    public FailureOutcome fail(RunFailure failure) {
        Objects.requireNonNull(failure, "AI 예약 복구 작업 실행 실패 정보가 필요합니다.");
        Optional<WorkRun> found = lock(failure.runId());
        if (found.isEmpty()) return new FailureOutcome(FailureDecision.RUN_NOT_FOUND, Optional.empty());

        WorkRun current = found.orElseThrow();
        if (!current.workerId().equals(failure.workerId())) {
            return new FailureOutcome(FailureDecision.WORKER_CONFLICT, Optional.of(current));
        }
        if (failure.failedAt().isBefore(current.criteria().evaluatedAt())) {
            throw new IllegalArgumentException("AI 예약 복구 작업 실패는 실행 판단보다 빠를 수 없습니다.");
        }
        if (current.status() == Status.FAILED) {
            return new FailureOutcome(FailureDecision.REPLAYED, Optional.of(current));
        }
        if (current.status() != Status.RUNNING) {
            return new FailureOutcome(FailureDecision.RUN_NOT_ACTIVE, Optional.of(current));
        }

        int updated = jdbcClient.sql("""
                update ai_reservation_recovery_work_runs
                set status = 'FAILED', finished_at = :failedAt, updated_at = :failedAt
                where run_id = :runId and status = 'RUNNING'
                """)
                .param("failedAt", dbTime(failure.failedAt()))
                .param("runId", failure.runId())
                .update();
        requireSingleUpdate(updated);
        return new FailureOutcome(FailureDecision.FAILED, find(failure.runId()));
    }

    @Transactional(readOnly = true)
    public RunningReport findOldestRunning(RunningCriteria criteria) {
        Objects.requireNonNull(criteria, "오래된 AI 예약 복구 작업 실행 조회 조건이 필요합니다.");
        List<WorkRun> runs = jdbcClient.sql(select() + """
                 where status = 'RUNNING' and evaluated_at <= :startedAtOrBefore
                 order by evaluated_at, run_id
                 limit :limit
                """)
                .param("startedAtOrBefore", dbTime(criteria.startedAtOrBefore()))
                .param("limit", criteria.limit())
                .query(AiReservationRecoveryWorkRunStore::run)
                .list();
        return new RunningReport(criteria, runs);
    }

    @Transactional
    public AbortOutcome abort(AbortSelection selection) {
        Objects.requireNonNull(selection, "AI 예약 복구 작업 실행 중단 선택이 필요합니다.");
        WorkRun selected = selection.selected();
        AbortCommand command = selection.command();
        Optional<WorkRun> found = lock(selected.runId());
        if (found.isEmpty()) return new AbortOutcome(AbortDecision.RUN_NOT_FOUND, Optional.empty());

        WorkRun current = found.orElseThrow();
        if (current.status() == Status.ABORTED) {
            return new AbortOutcome(current.matches(command)
                    ? AbortDecision.REPLAYED : AbortDecision.ABORT_CONFLICT, Optional.of(current));
        }
        if (current.status() != Status.RUNNING) {
            return new AbortOutcome(AbortDecision.RUN_NOT_ACTIVE, Optional.of(current));
        }

        int updated = jdbcClient.sql("""
                update ai_reservation_recovery_work_runs
                set status = 'ABORTED', finished_at = :abortedAt,
                    abort_reason = :abortReason, aborted_by = :abortedBy,
                    updated_at = :abortedAt
                where run_id = :runId and status = 'RUNNING'
                """)
                .param("abortedAt", dbTime(command.abortedAt()))
                .param("abortReason", command.reason().name())
                .param("abortedBy", command.operatorId())
                .param("runId", selected.runId())
                .update();
        requireSingleUpdate(updated);
        return new AbortOutcome(AbortDecision.ABORTED, find(selected.runId()));
    }

    @Transactional(readOnly = true)
    public Optional<WorkRun> find(String runId) {
        requireText(runId, "조회할 AI 예약 복구 작업 실행 식별자가 필요합니다.");
        return jdbcClient.sql(select() + " where run_id = :runId")
                .param("runId", runId)
                .query(AiReservationRecoveryWorkRunStore::run)
                .optional();
    }

    private Optional<WorkRun> lock(String runId) {
        return jdbcClient.sql(select() + " where run_id = :runId for update")
                .param("runId", runId)
                .query(AiReservationRecoveryWorkRunStore::run)
                .optional();
    }

    private WorkRun findRequired(String runId) {
        return find(runId).orElseThrow(() ->
                new IllegalStateException("저장한 AI 예약 복구 작업 실행을 조회하지 못했습니다."));
    }

    private static String select() {
        return """
                select run_id, worker_id, maximum_attempts, retry_delays,
                       stale_at_or_before, evaluated_at, candidate_limit,
                       status, finished_at, scanned_count, report_skipped_count,
                       assignment_not_claimed_count, recovery_finished_count,
                       recovery_not_started_count, recovery_failed_count,
                       abort_reason, aborted_by
                from ai_reservation_recovery_work_runs
                """;
    }

    private static WorkRun run(ResultSet resultSet, int rowNumber) throws SQLException {
        var schedule = new Schedule(
                resultSet.getInt("maximum_attempts"), decodeDelays(resultSet.getString("retry_delays")));
        var criteria = new Criteria(
                schedule, instant(resultSet, "stale_at_or_before"),
                instant(resultSet, "evaluated_at"), resultSet.getInt("candidate_limit"));
        Status status = Status.valueOf(resultSet.getString("status"));
        Optional<Summary> summary = status == Status.COMPLETED
                ? Optional.of(new Summary(
                        resultSet.getInt("scanned_count"),
                        resultSet.getInt("report_skipped_count"),
                        resultSet.getInt("assignment_not_claimed_count"),
                        resultSet.getInt("recovery_finished_count"),
                        resultSet.getInt("recovery_not_started_count"),
                        resultSet.getInt("recovery_failed_count")))
                : Optional.empty();
        Optional<AbortRecord> abort = status == Status.ABORTED
                ? Optional.of(new AbortRecord(
                        AbortReason.valueOf(resultSet.getString("abort_reason")),
                        resultSet.getString("aborted_by"),
                        instant(resultSet, "finished_at")))
                : Optional.empty();
        return new WorkRun(
                resultSet.getString("run_id"), resultSet.getString("worker_id"), criteria,
                status, nullableInstant(resultSet, "finished_at"), summary, abort);
    }

    private static String encodeDelays(List<Duration> delays) {
        return delays.stream().map(Duration::toString).collect(Collectors.joining(","));
    }

    private static List<Duration> decodeDelays(String encoded) {
        if (encoded.isEmpty()) return List.of();
        return Arrays.stream(encoded.split(",")).map(Duration::parse).toList();
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Optional<Instant> nullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static OffsetDateTime dbTime(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    private static boolean sameDatabaseInstant(Instant left, Instant right) {
        return left.truncatedTo(ChronoUnit.MICROS).equals(right.truncatedTo(ChronoUnit.MICROS));
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

    private static void requireSingleUpdate(int updated) {
        if (updated != 1) throw new IllegalStateException("AI 예약 복구 작업 실행을 갱신하지 못했습니다.");
    }

    public enum Status { RUNNING, COMPLETED, FAILED, ABORTED }
    public enum StartDecision { STARTED, ALREADY_RUNNING, REPLAYED, RUN_ID_CONFLICT }
    public enum CompletionDecision {
        COMPLETED, REPLAYED, RUN_NOT_FOUND, WORKER_CONFLICT, RUN_NOT_ACTIVE, COMPLETION_CONFLICT
    }
    public enum FailureDecision { FAILED, REPLAYED, RUN_NOT_FOUND, WORKER_CONFLICT, RUN_NOT_ACTIVE }
    public enum AbortReason { PROCESS_TERMINATED, WORKER_UNREACHABLE, OPERATOR_DECISION }
    public enum AbortDecision { ABORTED, REPLAYED, RUN_NOT_FOUND, RUN_NOT_ACTIVE, ABORT_CONFLICT }

    public record StartRequest(String runId, String workerId, Criteria criteria) {
        public StartRequest {
            requireText(runId, "AI 예약 복구 작업 실행 식별자가 필요합니다.");
            requireText(workerId, "AI 예약 복구 작업자 식별자가 필요합니다.");
            Objects.requireNonNull(criteria, "AI 예약 복구 운영 조회 조건이 필요합니다.");
        }
    }

    public record Summary(
            int scannedCount,
            int reportSkippedCount,
            int assignmentNotClaimedCount,
            int recoveryFinishedCount,
            int recoveryNotStartedCount,
            int recoveryFailedCount
    ) {
        public Summary {
            if (scannedCount < 0 || reportSkippedCount < 0 || assignmentNotClaimedCount < 0
                    || recoveryFinishedCount < 0 || recoveryNotStartedCount < 0 || recoveryFailedCount < 0) {
                throw new IllegalArgumentException("AI 예약 복구 작업 실행 집계는 음수일 수 없습니다.");
            }
            if (scannedCount != reportSkippedCount + assignmentNotClaimedCount
                    + recoveryFinishedCount + recoveryNotStartedCount + recoveryFailedCount) {
                throw new IllegalArgumentException("AI 예약 복구 작업 실행 후보 수와 결과 집계가 다릅니다.");
            }
        }
    }

    public record RunCompletion(String runId, String workerId, Instant finishedAt, Summary summary) {
        public RunCompletion {
            requireText(runId, "완료할 AI 예약 복구 작업 실행 식별자가 필요합니다.");
            requireText(workerId, "완료할 AI 예약 복구 작업자 식별자가 필요합니다.");
            Objects.requireNonNull(finishedAt, "AI 예약 복구 작업 완료 시각이 필요합니다.");
            Objects.requireNonNull(summary, "AI 예약 복구 작업 실행 집계가 필요합니다.");
        }
    }

    public record RunFailure(String runId, String workerId, Instant failedAt) {
        public RunFailure {
            requireText(runId, "실패한 AI 예약 복구 작업 실행 식별자가 필요합니다.");
            requireText(workerId, "실패한 AI 예약 복구 작업자 식별자가 필요합니다.");
            Objects.requireNonNull(failedAt, "AI 예약 복구 작업 실패 시각이 필요합니다.");
        }
    }

    public record RunningCriteria(Instant startedAtOrBefore, Instant evaluatedAt, int limit) {
        public RunningCriteria {
            Objects.requireNonNull(startedAtOrBefore, "오래된 작업 실행의 시작 시각 기준이 필요합니다.");
            Objects.requireNonNull(evaluatedAt, "오래된 작업 실행의 조회 시각이 필요합니다.");
            if (evaluatedAt.isBefore(startedAtOrBefore)) {
                throw new IllegalArgumentException("오래된 작업 실행의 조회 시각은 시작 시각 기준보다 빠를 수 없습니다.");
            }
            if (limit < 1) throw new IllegalArgumentException("오래된 작업 실행의 최대 조회 수는 1 이상이어야 합니다.");
        }
    }

    public record RunningReport(RunningCriteria criteria, List<WorkRun> runs) {
        public RunningReport {
            Objects.requireNonNull(criteria, "오래된 AI 예약 복구 작업 실행 조회 조건이 필요합니다.");
            runs = List.copyOf(Objects.requireNonNull(runs, "오래된 AI 예약 복구 작업 실행 목록이 필요합니다."));
            if (runs.size() > criteria.limit()) {
                throw new IllegalArgumentException("오래된 AI 예약 복구 작업 실행 목록이 최대 조회 수보다 많습니다.");
            }
            if (runs.stream().anyMatch(run -> run.status() != Status.RUNNING
                    || run.criteria().evaluatedAt().isAfter(criteria.startedAtOrBefore()))) {
                throw new IllegalArgumentException("오래된 실행 조회 결과에는 기준 시각 이하의 실행 중 작업만 포함할 수 있습니다.");
            }
        }
    }

    public record AbortCommand(String operatorId, AbortReason reason, Instant abortedAt) {
        public AbortCommand {
            requireText(operatorId, "AI 예약 복구 작업 실행을 중단한 운영자 식별자가 필요합니다.");
            Objects.requireNonNull(reason, "AI 예약 복구 작업 실행 중단 사유가 필요합니다.");
            Objects.requireNonNull(abortedAt, "AI 예약 복구 작업 실행 중단 시각이 필요합니다.");
        }
    }

    public record AbortSelection(RunningReport report, WorkRun selected, AbortCommand command) {
        public AbortSelection {
            Objects.requireNonNull(report, "오래된 AI 예약 복구 작업 실행 조회 결과가 필요합니다.");
            Objects.requireNonNull(selected, "중단할 AI 예약 복구 작업 실행이 필요합니다.");
            Objects.requireNonNull(command, "AI 예약 복구 작업 실행 중단 명령이 필요합니다.");
            if (!report.runs().contains(selected)) {
                throw new IllegalArgumentException("조회 결과에 포함되지 않은 AI 예약 복구 작업 실행은 중단할 수 없습니다.");
            }
            if (command.abortedAt().isBefore(report.criteria().evaluatedAt())) {
                throw new IllegalArgumentException("AI 예약 복구 작업 실행 중단은 운영 조회 시각보다 빠를 수 없습니다.");
            }
        }
    }

    public record AbortRecord(AbortReason reason, String operatorId, Instant abortedAt) {
        public AbortRecord {
            Objects.requireNonNull(reason, "AI 예약 복구 작업 실행 중단 사유가 필요합니다.");
            requireText(operatorId, "AI 예약 복구 작업 실행을 중단한 운영자 식별자가 필요합니다.");
            Objects.requireNonNull(abortedAt, "AI 예약 복구 작업 실행 중단 시각이 필요합니다.");
        }
    }

    public record WorkRun(
            String runId,
            String workerId,
            Criteria criteria,
            Status status,
            Optional<Instant> finishedAt,
            Optional<Summary> summary,
            Optional<AbortRecord> abort
    ) {
        public WorkRun {
            requireText(runId, "AI 예약 복구 작업 실행 식별자가 필요합니다.");
            requireText(workerId, "AI 예약 복구 작업자 식별자가 필요합니다.");
            Objects.requireNonNull(criteria, "AI 예약 복구 운영 조회 조건이 필요합니다.");
            Objects.requireNonNull(status, "AI 예약 복구 작업 실행 상태가 필요합니다.");
            Objects.requireNonNull(finishedAt, "AI 예약 복구 작업 종료 시각의 존재 여부가 필요합니다.");
            Objects.requireNonNull(summary, "AI 예약 복구 작업 실행 집계의 존재 여부가 필요합니다.");
            Objects.requireNonNull(abort, "AI 예약 복구 작업 실행 중단 기록의 존재 여부가 필요합니다.");
            if (status == Status.RUNNING && (finishedAt.isPresent() || summary.isPresent() || abort.isPresent())) {
                throw new IllegalArgumentException("실행 중인 AI 예약 복구 작업에는 종료 결과를 둘 수 없습니다.");
            }
            if (status == Status.COMPLETED && (finishedAt.isEmpty() || summary.isEmpty() || abort.isPresent())) {
                throw new IllegalArgumentException("완료한 AI 예약 복구 작업에는 종료 시각과 집계만 있어야 합니다.");
            }
            if (status == Status.FAILED && (finishedAt.isEmpty() || summary.isPresent() || abort.isPresent())) {
                throw new IllegalArgumentException("실패한 AI 예약 복구 작업에는 종료 시각만 있어야 합니다.");
            }
            if (status == Status.ABORTED && (finishedAt.isEmpty() || summary.isPresent() || abort.isEmpty()
                    || !sameDatabaseInstant(finishedAt.orElseThrow(), abort.orElseThrow().abortedAt()))) {
                throw new IllegalArgumentException("운영 중단한 AI 예약 복구 작업에는 일치하는 중단 기록이 필요합니다.");
            }
        }

        private boolean matches(StartRequest request) {
            return workerId.equals(request.workerId()) && criteria.equals(request.criteria());
        }

        private boolean matches(RunCompletion completion) {
            return finishedAt.filter(value -> sameDatabaseInstant(value, completion.finishedAt())).isPresent()
                    && summary.filter(value -> value.equals(completion.summary())).isPresent();
        }

        private boolean matches(AbortCommand command) {
            return abort.filter(value -> value.reason() == command.reason()
                    && value.operatorId().equals(command.operatorId())
                    && sameDatabaseInstant(value.abortedAt(), command.abortedAt())).isPresent();
        }
    }

    public record StartOutcome(StartDecision decision, WorkRun run) {
        public StartOutcome {
            Objects.requireNonNull(decision, "AI 예약 복구 작업 시작 결과가 필요합니다.");
            Objects.requireNonNull(run, "AI 예약 복구 작업 실행 기록이 필요합니다.");
        }
    }

    public record CompletionOutcome(CompletionDecision decision, Optional<WorkRun> run) {
        public CompletionOutcome {
            Objects.requireNonNull(decision, "AI 예약 복구 작업 완료 결과가 필요합니다.");
            Objects.requireNonNull(run, "AI 예약 복구 작업 실행 기록의 존재 여부가 필요합니다.");
        }
    }

    public record FailureOutcome(FailureDecision decision, Optional<WorkRun> run) {
        public FailureOutcome {
            Objects.requireNonNull(decision, "AI 예약 복구 작업 실패 기록 결과가 필요합니다.");
            Objects.requireNonNull(run, "AI 예약 복구 작업 실행 기록의 존재 여부가 필요합니다.");
        }
    }

    public record AbortOutcome(AbortDecision decision, Optional<WorkRun> run) {
        public AbortOutcome {
            Objects.requireNonNull(decision, "AI 예약 복구 작업 실행 중단 결과가 필요합니다.");
            Objects.requireNonNull(run, "AI 예약 복구 작업 실행 기록의 존재 여부가 필요합니다.");
        }
    }
}
