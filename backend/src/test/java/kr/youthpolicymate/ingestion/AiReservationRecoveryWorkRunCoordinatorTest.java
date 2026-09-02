package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiRequestBudget.Balance;
import kr.youthpolicymate.ingestion.AiRequestBudget.CostCeiling;
import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Criteria;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Schedule;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Lease;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunCoordinator.Failed;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunCoordinator.Finished;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunCoordinator.NotStarted;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.FailureDecision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.StartDecision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.StartRequest;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.Status;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunStore.Summary;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.CheckFailed;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Inspection;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Outcome;
import kr.youthpolicymate.ingestion.PolicyAiRequestAdmission.ReservationRequired;
import kr.youthpolicymate.ingestion.PolicyAiResult.Kind;
import kr.youthpolicymate.ingestion.PolicyAiResult.Request;
import kr.youthpolicymate.policy.PolicyObservation;
import kr.youthpolicymate.policy.PolicyObservation.ContentFingerprint;
import kr.youthpolicymate.policy.PolicyObservation.Readable;
import kr.youthpolicymate.policy.PolicyObservation.SnapshotReference;
import kr.youthpolicymate.policy.PolicyRevisionState;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
            assertThat(run.summary()).contains(new Summary(3, 1, 0, 1, 0, 1));
            assertThat(run.finishedAt()).contains(at(7));
        });
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
            assertThat(stopped.start().run().summary()).contains(new Summary(1, 0, 0, 1, 0, 0));
        });
        assertThat(port.calls()).isOne();
        assertThat(recoveryStore.history("a-ready")).hasSize(1);
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
                    recovery_not_started_count = 0, recovery_failed_count = 0
                where run_id = 'run-invalid'
                """).param("finishedAt", dbTime(at(7))).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(runStore.find("run-invalid")).hasValueSatisfying(run ->
                assertThat(run.status()).isEqualTo(Status.RUNNING));
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
