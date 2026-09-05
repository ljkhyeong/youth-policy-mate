package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiRequestBudget.Balance;
import kr.youthpolicymate.ingestion.AiRequestBudget.CostCeiling;
import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Criteria;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Schedule;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Lease;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.ReadyClaimed;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.ReadyClaimRejected;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunCoordinator.Failed;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunCoordinator.Finished;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunCoordinator.NotStarted;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.FailureDecision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.CompletionDecision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.RunCompletion;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.StartDecision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.StartRequest;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.Status;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.Summary;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.CheckFailed;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Inspection;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Outcome;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryHeartbeat.HeartbeatPlan;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryHeartbeat.HeartbeatScheduler;
import kr.youthpolicymate.ingestion.PolicyAiRequestAdmission.ReservationRequired;
import kr.youthpolicymate.ingestion.PolicyAiResult.Kind;
import kr.youthpolicymate.ingestion.PolicyAiResult.Request;
import kr.youthpolicymate.policy.PolicyObservation;
import kr.youthpolicymate.policy.PolicyObservation.ContentFingerprint;
import kr.youthpolicymate.policy.PolicyObservation.Readable;
import kr.youthpolicymate.policy.PolicyObservation.SnapshotReference;
import kr.youthpolicymate.policy.PolicyRevisionState;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class AiReservationRecoveryWorkRunCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-09-02T01:00:00Z");
    private static final Schedule SCHEDULE = new Schedule(
            3, List.of(Duration.ofSeconds(5), Duration.ofSeconds(20)));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired JdbcClient jdbcClient;
    @Autowired AiBudgetReservationStore reservationStore;
    @Autowired AiBudgetReservationLifecycleStore lifecycleStore;
    @Autowired AiReservationRecoveryStore recoveryStore;
    @Autowired AiReservationRecoveryOperationsQuery operationsQuery;
    @Autowired AiReservationRecoveryWorkAssigner workAssigner;
    @Autowired PolicyAiRecoveryApplier applier;
    @Autowired AiReservationRecoveryWorkRunStore runStore;
    @Autowired AiReservationRecoveryLeaseRenewalStore leaseRenewalStore;

    @BeforeEach
    void setUp() {
        clearDatabase();
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    @Test
    @DisplayName("실행 식별자와 제한 목록의 건너뜀·완료·실패 개수를 함께 저장한다")
    void storesRunIdentityAndCandidateSummary() {
        reserve("a-deferred", at(0));
        recoveryStore.claim("a-deferred", lease("attempt-active", at(1), at(20)));
        reserve("b-finished", at(0));
        reserve("c-fails", at(0));
        PolicyAiRecoveryPort port = inspection -> {
            if (inspection.reservation().reservationId().equals("c-fails")) {
                throw new IllegalStateException("인공 공급자 조회 오류");
            }
            return CheckFailed.INSTANCE;
        };

        var execution = coordinator(port).run(request("run-a", "worker-a"), candidate ->
                lease("attempt-" + candidate.reservation().reservationId(), at(5), at(20)));

        assertThat(execution).isInstanceOf(Finished.class);
        assertThat(runStore.find("run-a")).hasValueSatisfying(run -> {
            assertThat(run.workerId()).isEqualTo("worker-a");
            assertThat(run.criteria()).isEqualTo(criteria());
            assertThat(run.status()).isEqualTo(Status.COMPLETED);
            assertThat(run.summary()).contains(new Summary(3, 1, 0, 1, 0, 1, 0));
            assertThat(run.finishedAt()).contains(at(7));
        });
        assertThat(recoveryStore.historyForWorkRun("run-a"))
                .extracting(AiReservationRecoveryStore.Attempt::attemptId)
                .containsExactly("attempt-b-finished", "attempt-c-fails");
        assertThat(recoveryStore.findWorkRunId("attempt-b-finished")).contains("run-a");
        assertThat(recoveryStore.findWorkRunId("attempt-active")).isEmpty();
    }

    @Test
    @DisplayName("완료한 같은 실행 식별자는 집계를 재전달하고 외부 확인을 반복하지 않는다")
    void replaysCompletedRunWithoutExecutingAgain() {
        reserve("a-ready", at(0));
        var port = new RecordingPort(CheckFailed.INSTANCE);
        var coordinator = coordinator(port);
        var request = request("run-replay", "worker-a");

        var first = coordinator.run(request, candidate ->
                lease("attempt-ready", at(5), at(20)));
        var replay = coordinator.run(request, candidate ->
                lease("attempt-should-not-be-used", at(5), at(20)));

        assertThat(first).isInstanceOf(Finished.class);
        assertThat(replay).isInstanceOfSatisfying(NotStarted.class, stopped -> {
            assertThat(stopped.start().decision()).isEqualTo(StartDecision.REPLAYED);
            assertThat(stopped.start().run().summary()).contains(new Summary(1, 0, 0, 1, 0, 0, 0));
        });
        assertThat(port.calls()).isOne();
        assertThat(recoveryStore.history("a-ready")).hasSize(1);
    }

    @Test
    @DisplayName("heartbeat 중단을 따로 저장하고 다음 후보를 처리하며 재전달 때 집계를 보존한다")
    void persistsHeartbeatStopSeparatelyAndContinuesBatch() {
        reserve("a-heartbeat-stopped", at(0));
        reserve("b-finished", at(0));
        var scheduler = new ControlledHeartbeatScheduler();
        var heartbeat = new PolicyAiRecoveryHeartbeat(
                leaseRenewalStore, scheduler, Clock.fixed(at(7), ZoneOffset.UTC),
                new HeartbeatPlan(Duration.ofSeconds(2), Duration.ofSeconds(7)));
        var calls = new AtomicInteger();
        PolicyAiRecoveryPort port = inspection -> {
            calls.incrementAndGet();
            if (inspection.reservation().reservationId().equals("a-heartbeat-stopped")) {
                scheduler.runOnce();
            }
            return CheckFailed.INSTANCE;
        };
        var recoveryCoordinator = new PolicyAiRecoveryCoordinator(
                recoveryStore, lifecycleStore, applier, port, Clock.fixed(at(8), ZoneOffset.UTC), heartbeat);
        var coordinator = new AiReservationRecoveryWorkRunCoordinator(runStore,
                new AiReservationRecoveryWorkRunner(operationsQuery, workAssigner, recoveryCoordinator),
                Clock.fixed(at(9), ZoneOffset.UTC));
        var request = request("run-heartbeat", "worker-a");

        var execution = coordinator.run(request, candidate -> {
            String reservationId = candidate.reservation().reservationId();
            return lease("attempt-" + reservationId, at(5),
                    reservationId.equals("a-heartbeat-stopped") ? at(7) : at(20));
        });

        assertThat(execution).isInstanceOfSatisfying(Finished.class, finished -> {
            assertThat(finished.completion().decision()).isEqualTo(CompletionDecision.COMPLETED);
            assertThat(finished.batch().candidates().getFirst())
                    .isInstanceOfSatisfying(AiReservationRecoveryWorkRunner.RecoveryFinished.class, result ->
                            assertThat(result.recovery()).isInstanceOf(PolicyAiRecoveryCoordinator.HeartbeatStopped.class));
        });
        var summary = new Summary(2, 0, 0, 1, 0, 0, 1);
        assertThat(runStore.find("run-heartbeat").orElseThrow().summary()).contains(summary);
        assertThat(recoveryStore.historyForWorkRun("run-heartbeat"))
                .extracting(AiReservationRecoveryStore.Attempt::attemptId)
                .containsExactly("attempt-a-heartbeat-stopped", "attempt-b-finished");
        assertThat(recoveryStore.findAttempt("attempt-a-heartbeat-stopped").orElseThrow().status())
                .isEqualTo(AiReservationRecoveryStore.Status.ACTIVE);

        assertThat(coordinator.run(request, candidate -> {
            throw new AssertionError("완료 실행의 임대를 다시 만들면 안 됩니다.");
        })).isInstanceOfSatisfying(NotStarted.class, replay -> {
            assertThat(replay.start().decision()).isEqualTo(StartDecision.REPLAYED);
            assertThat(replay.start().run().summary()).contains(summary);
        });
        assertThat(calls.get()).isEqualTo(2);
        assertThat(runStore.complete(new RunCompletion("run-heartbeat", "worker-a", at(9), summary)).decision())
                .isEqualTo(CompletionDecision.REPLAYED);
        assertThat(runStore.complete(new RunCompletion("run-heartbeat", "worker-a", at(9),
                new Summary(2, 0, 0, 1, 1, 0, 0))).decision())
                .isEqualTo(CompletionDecision.COMPLETION_CONFLICT);
    }

    @Test
    @DisplayName("V8 기록을 V9로 옮겨도 과거 heartbeat 집계 없음과 기존 실행 상태를 보존한다")
    void migratesLegacyRunsWithoutInventingHeartbeatCounts() {
        String schema = "heartbeat_summary_migration";
        var flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema);
        try {
            flyway.target("8").load().migrate();
            String schemaUrl = postgres.getJdbcUrl()
                    + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema;
            var legacyJdbc = JdbcClient.create(new DriverManagerDataSource(
                    schemaUrl, postgres.getUsername(), postgres.getPassword()));
            for (String runId : List.of("legacy-completed", "legacy-running", "legacy-failed", "legacy-aborted")) {
                legacyJdbc.sql("""
                        insert into ai_reservation_recovery_work_runs (
                            run_id, worker_id, maximum_attempts, retry_delays,
                            stale_at_or_before, evaluated_at, candidate_limit, status, created_at, updated_at
                        ) values (:runId, 'worker-a', 3, 'PT5S,PT20S', :staleAt, :evaluatedAt,
                            10, 'RUNNING', :evaluatedAt, :evaluatedAt)
                        """).param("runId", runId).param("staleAt", dbTime(at(0)))
                        .param("evaluatedAt", dbTime(at(5))).update();
            }
            legacyJdbc.sql("""
                    update ai_reservation_recovery_work_runs
                    set status = 'COMPLETED', finished_at = :finishedAt,
                        scanned_count = 1, report_skipped_count = 0, assignment_not_claimed_count = 0,
                        recovery_finished_count = 0, recovery_not_started_count = 1, recovery_failed_count = 0
                    where run_id = 'legacy-completed'
                    """).param("finishedAt", dbTime(at(7))).update();
            legacyJdbc.sql("""
                    update ai_reservation_recovery_work_runs set status = 'FAILED', finished_at = :finishedAt
                    where run_id = 'legacy-failed'
                    """).param("finishedAt", dbTime(at(7))).update();
            legacyJdbc.sql("""
                    update ai_reservation_recovery_work_runs set status = 'ABORTED', finished_at = :finishedAt,
                        abort_reason = 'OPERATOR_DECISION', aborted_by = 'operator-a'
                    where run_id = 'legacy-aborted'
                    """).param("finishedAt", dbTime(at(7))).update();

            flyway.target("latest").load().migrate();

            var legacyStore = new AiReservationRecoveryWorkRunStore(legacyJdbc);
            var replay = legacyStore.start(request("legacy-completed", "worker-a"));
            assertThat(replay.decision()).isEqualTo(StartDecision.REPLAYED);
            var legacySummary = new Summary(1, 0, 0, 0, 1, 0, Optional.empty());
            assertThat(replay.run().summary()).contains(legacySummary);
            assertThat(legacyStore.find("legacy-running").orElseThrow().status()).isEqualTo(Status.RUNNING);
            assertThat(legacyStore.find("legacy-failed").orElseThrow().status()).isEqualTo(Status.FAILED);
            assertThat(legacyStore.find("legacy-aborted").orElseThrow().abort().orElseThrow().operatorId())
                    .isEqualTo("operator-a");
            assertThat(legacyJdbc.sql("select count(*) from ai_reservation_recovery_work_runs "
                    + "where heartbeat_stopped_count is null").query(Integer.class).single()).isEqualTo(4);
            assertThatThrownBy(() -> legacyStore.complete(new RunCompletion(
                    "legacy-running", "worker-a", at(7), legacySummary)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("heartbeat 중단 수");
            assertThat(legacyStore.find("legacy-running").orElseThrow().status()).isEqualTo(Status.RUNNING);
            assertThat(legacyStore.complete(new RunCompletion("legacy-running", "worker-a", at(7),
                    new Summary(0, 0, 0, 0, 0, 0, 0))).run().orElseThrow().summary().orElseThrow()
                    .heartbeatStoppedCount()).contains(0);
        } finally {
            jdbcClient.sql("drop schema if exists heartbeat_summary_migration cascade").update();
        }
    }

    @Test
    @DisplayName("같은 실행 식별자의 동시 기동은 한 작업자만 후보를 확인한다")
    void allowsOnlyOneConcurrentStartForSameRun() throws Exception {
        reserve("a-ready", at(0));
        var port = new BlockingPort();
        var coordinator = coordinator(port);
        var request = request("run-concurrent", "worker-a");

        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> coordinator.run(request, candidate ->
                    lease("attempt-ready", at(5), at(20))));
            assertThat(port.awaitInspection()).isTrue();

            var duplicate = coordinator.run(request, candidate ->
                    lease("attempt-should-not-be-used", at(5), at(20)));

            assertThat(duplicate).isInstanceOfSatisfying(NotStarted.class, stopped ->
                    assertThat(stopped.start().decision()).isEqualTo(StartDecision.ALREADY_RUNNING));
            port.release();
            assertThat(first.get(10, TimeUnit.SECONDS)).isInstanceOf(Finished.class);
        }
        assertThat(port.calls()).isOne();
        assertThat(recoveryStore.history("a-ready")).hasSize(1);
    }

    @Test
    @DisplayName("같은 실행 식별자를 다른 작업자나 조회 조건으로 사용할 수 없다")
    void rejectsConflictingRunIdentity() {
        var request = request("run-conflict", "worker-a");

        assertThat(runStore.start(request).decision()).isEqualTo(StartDecision.STARTED);
        assertThat(runStore.start(request).decision()).isEqualTo(StartDecision.ALREADY_RUNNING);
        assertThat(runStore.start(request("run-conflict", "worker-b")).decision())
                .isEqualTo(StartDecision.RUN_ID_CONFLICT);
        assertThat(runStore.start(new StartRequest(
                "run-conflict", "worker-a",
                new Criteria(SCHEDULE, at(0), at(5), 1))).decision())
                .isEqualTo(StartDecision.RUN_ID_CONFLICT);
    }

    @Test
    @DisplayName("작업자나 실행 상태가 맞지 않으면 실행에 연결된 복구 시도를 만들지 않는다")
    void rejectsAssignmentForWrongWorkerOrInactiveRun() {
        reserve("a-ready", at(0));
        var report = operationsQuery.findOldestUnresolved(criteria());
        var candidate = report.items().getFirst();
        runStore.start(request("run-guarded", "worker-a"));

        var wrongWorker = workAssigner.assignForRun(
                "run-guarded", report, candidate,
                new Lease("attempt-wrong-worker", "worker-b", at(5), at(20)));
        runStore.fail(new AiReservationRecoveryWorkRunStore.RunFailure(
                "run-guarded", "worker-a", at(6)));
        var inactive = workAssigner.assignForRun(
                "run-guarded", report, candidate,
                lease("attempt-inactive", at(6), at(20)));

        assertThat(wrongWorker).isInstanceOfSatisfying(ReadyClaimRejected.class, rejected ->
                assertThat(rejected.claim().decision()).isEqualTo(
                        AiReservationRecoveryStore.ClaimDecision.WORK_RUN_WORKER_CONFLICT));
        assertThat(inactive).isInstanceOfSatisfying(ReadyClaimRejected.class, rejected ->
                assertThat(rejected.claim().decision()).isEqualTo(
                        AiReservationRecoveryStore.ClaimDecision.WORK_RUN_NOT_ACTIVE));
        assertThat(recoveryStore.history("a-ready")).isEmpty();
        assertThat(recoveryStore.historyForWorkRun("run-guarded")).isEmpty();
    }

    @Test
    @DisplayName("같은 실행의 연결만 재전달하고 같은 시도를 다른 실행에 연결하지 않는다")
    void replaysLinkOnlyForSameWorkRun() {
        reserve("a-ready", at(0));
        var report = operationsQuery.findOldestUnresolved(criteria());
        var candidate = report.items().getFirst();
        var lease = lease("attempt-linked", at(5), at(20));
        runStore.start(request("run-first", "worker-a"));
        runStore.start(request("run-second", "worker-a"));

        var first = workAssigner.assignForRun("run-first", report, candidate, lease);
        var replay = workAssigner.assignForRun("run-first", report, candidate, lease);
        var conflict = workAssigner.assignForRun("run-second", report, candidate, lease);

        assertThat(first).isInstanceOfSatisfying(ReadyClaimed.class, claimed ->
                assertThat(claimed.decision()).isEqualTo(
                        AiReservationRecoveryStore.ClaimDecision.CLAIMED));
        assertThat(replay).isInstanceOfSatisfying(ReadyClaimed.class, claimed ->
                assertThat(claimed.decision()).isEqualTo(
                        AiReservationRecoveryStore.ClaimDecision.REPLAYED));
        assertThat(conflict).isInstanceOfSatisfying(ReadyClaimRejected.class, rejected ->
                assertThat(rejected.claim().decision()).isEqualTo(
                        AiReservationRecoveryStore.ClaimDecision.ATTEMPT_ID_CONFLICT));
        assertThat(recoveryStore.findWorkRunId("attempt-linked")).contains("run-first");
        assertThat(recoveryStore.historyForWorkRun("run-second")).isEmpty();
    }

    @Test
    @DisplayName("목록 실행 자체가 실패하면 실패 상태를 남기고 같은 실행을 반복하지 않는다")
    void recordsWholeRunFailureAndReplaysIt() {
        reserve("a-ready", at(0));
        var factoryCalls = new AtomicInteger();
        var coordinator = coordinator(new RecordingPort(CheckFailed.INSTANCE));
        var request = request("run-failed", "worker-a");

        var failed = coordinator.run(request, candidate -> {
            factoryCalls.incrementAndGet();
            throw new IllegalStateException("인공 임대 생성 오류");
        });
        var replay = coordinator.run(request, candidate -> {
            factoryCalls.incrementAndGet();
            return lease("attempt-should-not-be-used", at(5), at(20));
        });

        assertThat(failed).isInstanceOfSatisfying(Failed.class, result -> {
            assertThat(result.failure()).hasMessage("인공 임대 생성 오류");
            assertThat(result.recorded().decision()).isEqualTo(FailureDecision.FAILED);
        });
        assertThat(replay).isInstanceOfSatisfying(NotStarted.class, stopped ->
                assertThat(stopped.start().decision()).isEqualTo(StartDecision.REPLAYED));
        assertThat(factoryCalls).hasValue(1);
        assertThat(runStore.find("run-failed")).hasValueSatisfying(run -> {
            assertThat(run.status()).isEqualTo(Status.FAILED);
            assertThat(run.summary()).isEmpty();
        });
        assertThat(recoveryStore.history("a-ready")).isEmpty();
    }

    @Test
    @DisplayName("PostgreSQL 제약은 완료 실행의 누락되거나 맞지 않는 집계를 거절한다")
    void rejectsInvalidCompletedSummaryInDatabase() {
        runStore.start(request("run-invalid", "worker-a"));

        assertThatThrownBy(() -> jdbcClient.sql("""
                update ai_reservation_recovery_work_runs
                set status = 'COMPLETED', finished_at = :finishedAt,
                    scanned_count = 2, report_skipped_count = 1,
                    assignment_not_claimed_count = 0, recovery_finished_count = 0,
                    recovery_not_started_count = 0, recovery_failed_count = 0,
                    heartbeat_stopped_count = 0
                where run_id = 'run-invalid'
                """).param("finishedAt", dbTime(at(7))).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(runStore.find("run-invalid")).hasValueSatisfying(run ->
                assertThat(run.status()).isEqualTo(Status.RUNNING));
    }

    @Test
    @DisplayName("heartbeat 집계는 완료에만 저장하고 음수와 이중 집계를 거절한다")
    void rejectsHeartbeatCountsOutsideCompletedRunsAndInvalidTotals() {
        runStore.start(request("run-heartbeat-constraints", "worker-a"));
        assertThatThrownBy(() -> jdbcClient.sql("""
                update ai_reservation_recovery_work_runs set heartbeat_stopped_count = 0
                where run_id = 'run-heartbeat-constraints'
                """).update()).isInstanceOf(DataIntegrityViolationException.class);
        runStore.complete(new RunCompletion("run-heartbeat-constraints", "worker-a", at(7),
                new Summary(1, 0, 0, 0, 0, 0, 1)));
        assertThatThrownBy(() -> jdbcClient.sql("""
                update ai_reservation_recovery_work_runs
                set heartbeat_stopped_count = -1, recovery_not_started_count = 2
                where run_id = 'run-heartbeat-constraints'
                """).update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcClient.sql("""
                update ai_reservation_recovery_work_runs set recovery_not_started_count = 1
                where run_id = 'run-heartbeat-constraints'
                """).update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("PostgreSQL 외래 키는 존재하지 않는 실행과 복구 시도의 연결을 거절한다")
    void rejectsLinkToMissingWorkRunInDatabase() {
        reserve("a-ready", at(0));
        recoveryStore.claim("a-ready", lease("attempt-direct", at(1), at(20)));
        runStore.start(request("run-existing", "worker-a"));

        assertThatThrownBy(() -> jdbcClient.sql("""
                insert into ai_reservation_recovery_work_run_attempts (attempt_id, run_id)
                values ('attempt-direct', 'missing-run')
                """).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcClient.sql("""
                insert into ai_reservation_recovery_work_run_attempts (attempt_id, run_id)
                values ('missing-attempt', 'run-existing')
                """).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(recoveryStore.findWorkRunId("attempt-direct")).isEmpty();
    }

    private AiReservationRecoveryWorkRunCoordinator coordinator(PolicyAiRecoveryPort port) {
        var recoveryCoordinator = new PolicyAiRecoveryCoordinator(
                recoveryStore, lifecycleStore, applier, port, Clock.fixed(at(6), ZoneOffset.UTC));
        var workRunner = new AiReservationRecoveryWorkRunner(
                operationsQuery, workAssigner, recoveryCoordinator);
        return new AiReservationRecoveryWorkRunCoordinator(
                runStore, workRunner, Clock.fixed(at(7), ZoneOffset.UTC));
    }

    private StartRequest request(String runId, String workerId) {
        return new StartRequest(runId, workerId, criteria());
    }

    private Criteria criteria() {
        return new Criteria(SCHEDULE, at(0), at(5), 10);
    }

    private void reserve(String reservationId, Instant reservedAt) {
        String budgetId = "budget-" + reservationId;
        var balance = insertBudget(budgetId);
        var request = policyRequest("policy-" + reservationId);
        var required = new ReservationRequired(balance,
                new CostCeiling(request, "price-a", NOW.plusSeconds(300), money("10")));
        assertThat(reservationStore.reserve(reservationId, required, reservedAt).decision())
                .isEqualTo(AiBudgetReservationStore.Decision.RESERVED);
    }

    private Balance insertBudget(String budgetId) {
        var balance = new Balance(budgetId, NOW.minusSeconds(300), NOW.plusSeconds(600),
                money("100"), money("0"), money("0"));
        jdbcClient.sql("""
                insert into ai_budgets (
                    budget_id, starts_at, ends_at, limit_won, confirmed_won, reserved_won, created_at, updated_at
                ) values (:budgetId, :startsAt, :endsAt, :limitWon, 0, 0, :createdAt, :createdAt)
                """)
                .param("budgetId", balance.budgetId())
                .param("startsAt", dbTime(balance.startsAt()))
                .param("endsAt", dbTime(balance.endsAt()))
                .param("limitWon", balance.limitWon())
                .param("createdAt", dbTime(NOW.minusSeconds(400)))
                .update();
        return balance;
    }

    private static Request policyRequest(String policyId) {
        var observation = new PolicyObservation(policyId, 1, NOW.minusSeconds(250), new Readable(
                new SnapshotReference("synthetic-source", "raw-" + policyId, "a".repeat(64)),
                new ContentFingerprint("comparison-a", "b".repeat(64))));
        var revision = PolicyRevisionState.empty(policyId).consider(observation)
                .state().currentRevision().orElseThrow();
        return new Request(revision, Kind.SUMMARY, "generation-a", 1, NOW.minusSeconds(200));
    }

    private static Lease lease(String attemptId, Instant claimedAt, Instant leaseUntil) {
        return new Lease(attemptId, "worker-a", claimedAt, leaseUntil);
    }

    private void clearDatabase() {
        jdbcClient.sql("delete from ai_reservation_recovery_work_run_attempts").update();
        jdbcClient.sql("delete from ai_reservation_recovery_attempts").update();
        jdbcClient.sql("delete from ai_request_reservations").update();
        jdbcClient.sql("delete from ai_budgets").update();
        jdbcClient.sql("delete from ai_reservation_recovery_work_runs").update();
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private static Instant at(long seconds) {
        return NOW.plusSeconds(seconds);
    }

    private static OffsetDateTime dbTime(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    private static final class RecordingPort implements PolicyAiRecoveryPort {
        private final Outcome outcome;
        private final AtomicInteger calls = new AtomicInteger();

        private RecordingPort(Outcome outcome) {
            this.outcome = outcome;
        }

        @Override
        public Outcome inspect(Inspection inspection) {
            calls.incrementAndGet();
            return outcome;
        }

        private int calls() {
            return calls.get();
        }
    }

    private static final class ControlledHeartbeatScheduler implements HeartbeatScheduler {
        private Runnable heartbeat;

        @Override
        public PolicyAiRecoveryHeartbeat.Cancellation schedule(Duration initialDelay, Duration delay, Runnable heartbeat) {
            this.heartbeat = heartbeat;
            return () -> this.heartbeat = null;
        }

        private void runOnce() {
            heartbeat.run();
        }
    }

    private static final class BlockingPort implements PolicyAiRecoveryPort {
        private final CountDownLatch inspected = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Outcome inspect(Inspection inspection) {
            calls.incrementAndGet();
            inspected.countDown();
            try {
                if (!released.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("인공 공급자 조회 대기 시간이 지났습니다.");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("인공 공급자 조회 대기가 중단됐습니다.", interrupted);
            }
            return CheckFailed.INSTANCE;
        }

        private boolean awaitInspection() throws InterruptedException {
            return inspected.await(10, TimeUnit.SECONDS);
        }

        private void release() {
            released.countDown();
        }

        private int calls() {
            return calls.get();
        }
    }
}
