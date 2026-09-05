package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Criteria;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Schedule;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.AbortCommand;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.AbortDecision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.AbortReason;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.AbortSelection;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.CompletionDecision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.RunCompletion;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.RunFailure;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.RunningCriteria;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.StartRequest;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.Status;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.Summary;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.WorkRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class AiReservationRecoveryWorkRunOperationsTest {
    private static final Instant NOW = Instant.parse("2026-09-02T02:00:00Z");
    private static final Schedule SCHEDULE = new Schedule(
            3, List.of(Duration.ofSeconds(5), Duration.ofSeconds(20)));
    private static final Summary EMPTY_SUMMARY = new Summary(0, 0, 0, 0, 0, 0, 0);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired JdbcClient jdbcClient;
    @Autowired AiReservationRecoveryWorkRunStore runStore;

    @BeforeEach
    void setUp() {
        clearDatabase();
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    @Test
    @DisplayName("기준 시각 이하의 실행 중 기록만 오래된 순서와 최대 개수로 조회한다")
    void findsOnlyOldRunningRunsInStableLimitedOrder() {
        start("a-old", 0);
        start("b-old", 1);
        start("c-completed", 2);
        runStore.complete(new RunCompletion("c-completed", "worker-a", at(6), EMPTY_SUMMARY));
        start("d-failed", 3);
        runStore.fail(new RunFailure("d-failed", "worker-a", at(6)));
        WorkRun aborted = start("e-aborted", 4);
        var abortReport = runStore.findOldestRunning(new RunningCriteria(at(4), at(5), 10));
        runStore.abort(new AbortSelection(abortReport, aborted,
                new AbortCommand("operator-a", AbortReason.PROCESS_TERMINATED, at(6))));
        start("f-recent", 11);

        var report = runStore.findOldestRunning(new RunningCriteria(at(10), at(12), 2));

        assertThat(report.criteria()).isEqualTo(new RunningCriteria(at(10), at(12), 2));
        assertThat(report.runs()).extracting(WorkRun::runId)
                .containsExactly("a-old", "b-old");
    }

    @Test
    @DisplayName("운영 중단은 사유와 운영자를 저장하고 같은 명령만 재전달한다")
    void recordsAbortAuditAndReplaysOnlySameCommand() {
        start("run-abort", 0);
        var report = runStore.findOldestRunning(new RunningCriteria(at(5), at(6), 10));
        WorkRun selected = report.runs().getFirst();
        var command = new AbortCommand("operator-a", AbortReason.WORKER_UNREACHABLE, at(7));

        var aborted = runStore.abort(new AbortSelection(report, selected, command));
        var replayed = runStore.abort(new AbortSelection(report, selected, command));
        var conflicting = runStore.abort(new AbortSelection(report, selected,
                new AbortCommand("operator-b", AbortReason.OPERATOR_DECISION, at(7))));

        assertThat(aborted.decision()).isEqualTo(AbortDecision.ABORTED);
        assertThat(aborted.run()).hasValueSatisfying(run -> {
            assertThat(run.status()).isEqualTo(Status.ABORTED);
            assertThat(run.finishedAt()).contains(at(7));
            assertThat(run.summary()).isEmpty();
            assertThat(run.abort()).hasValueSatisfying(record -> {
                assertThat(record.reason()).isEqualTo(AbortReason.WORKER_UNREACHABLE);
                assertThat(record.operatorId()).isEqualTo("operator-a");
                assertThat(record.abortedAt()).isEqualTo(at(7));
            });
        });
        assertThat(replayed.decision()).isEqualTo(AbortDecision.REPLAYED);
        assertThat(conflicting.decision()).isEqualTo(AbortDecision.ABORT_CONFLICT);
    }

    @Test
    @DisplayName("조회 결과에 없거나 조회보다 이른 운영 중단 명령은 거절한다")
    void rejectsUnselectedRunAndAbortBeforeObservation() {
        start("run-selected", 0);
        WorkRun recent = start("run-recent", 7);
        var report = runStore.findOldestRunning(new RunningCriteria(at(5), at(6), 10));

        assertThatThrownBy(() -> new AbortSelection(report, recent,
                new AbortCommand("operator-a", AbortReason.OPERATOR_DECISION, at(8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("조회 결과에 포함되지 않은 AI 예약 복구 작업 실행은 중단할 수 없습니다.");
        assertThatThrownBy(() -> new AbortSelection(report, report.runs().getFirst(),
                new AbortCommand("operator-a", AbortReason.OPERATOR_DECISION, at(5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI 예약 복구 작업 실행 중단은 운영 조회 시각보다 빠를 수 없습니다.");
    }

    @Test
    @DisplayName("완료와 운영 중단 중 먼저 확정된 결과를 나중 요청이 덮어쓰지 않는다")
    void preservesFirstTerminalTransition() {
        start("run-completed-first", 0);
        var completedReport = runStore.findOldestRunning(new RunningCriteria(at(5), at(6), 10));
        WorkRun completedCandidate = completedReport.runs().getFirst();
        runStore.complete(new RunCompletion("run-completed-first", "worker-a", at(7), EMPTY_SUMMARY));

        var lateAbort = runStore.abort(new AbortSelection(completedReport, completedCandidate,
                new AbortCommand("operator-a", AbortReason.PROCESS_TERMINATED, at(8))));

        assertThat(lateAbort.decision()).isEqualTo(AbortDecision.RUN_NOT_ACTIVE);
        assertThat(lateAbort.run()).hasValueSatisfying(run ->
                assertThat(run.status()).isEqualTo(Status.COMPLETED));

        start("run-aborted-first", 1);
        var abortReport = runStore.findOldestRunning(new RunningCriteria(at(5), at(6), 10));
        WorkRun abortCandidate = abortReport.runs().stream()
                .filter(run -> run.runId().equals("run-aborted-first"))
                .findFirst().orElseThrow();
        runStore.abort(new AbortSelection(abortReport, abortCandidate,
                new AbortCommand("operator-a", AbortReason.PROCESS_TERMINATED, at(7))));

        var lateCompletion = runStore.complete(
                new RunCompletion("run-aborted-first", "worker-a", at(8), EMPTY_SUMMARY));

        assertThat(lateCompletion.decision()).isEqualTo(CompletionDecision.RUN_NOT_ACTIVE);
        assertThat(lateCompletion.run()).hasValueSatisfying(run ->
                assertThat(run.status()).isEqualTo(Status.ABORTED));
    }

    @Test
    @DisplayName("PostgreSQL 제약은 감사 정보가 없는 운영 중단 상태를 거절한다")
    void rejectsAbortedStatusWithoutAuditFieldsInDatabase() {
        start("run-invalid-abort", 0);

        assertThatThrownBy(() -> jdbcClient.sql("""
                update ai_reservation_recovery_work_runs
                set status = 'ABORTED', finished_at = :finishedAt
                where run_id = 'run-invalid-abort'
                """).param("finishedAt", dbTime(at(7))).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(runStore.find("run-invalid-abort")).hasValueSatisfying(run ->
                assertThat(run.status()).isEqualTo(Status.RUNNING));
    }

    private WorkRun start(String runId, long evaluatedOffset) {
        Instant evaluatedAt = at(evaluatedOffset);
        var criteria = new Criteria(SCHEDULE, evaluatedAt.minusSeconds(10), evaluatedAt, 10);
        return runStore.start(new StartRequest(runId, "worker-a", criteria)).run();
    }

    private void clearDatabase() {
        jdbcClient.sql("delete from ai_reservation_recovery_work_runs").update();
    }

    private static Instant at(long seconds) {
        return NOW.plusSeconds(seconds);
    }

    private static OffsetDateTime dbTime(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
