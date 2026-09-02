package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiRequestBudget.Balance;
import kr.youthpolicymate.ingestion.AiRequestBudget.CostCeiling;
import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Criteria;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Deferred;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.HoldReason;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Schedule;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.StopReason;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Stopped;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Completion;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Lease;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.ReadyClaimSkipped;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.RecoveryResult;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Status;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunner.AssignmentNotClaimed;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunner.RecoveryFailed;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunner.RecoveryFinished;
import kr.youthpolicymate.ingestion.AiReservationRecoveryWorkRunner.SkippedByReport;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class AiReservationRecoveryWorkRunnerTest {
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

    @BeforeEach
    void setUp() {
        clearDatabase();
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    @Test
    @DisplayName("보류와 중단 후보를 건너뛰고 같은 제한 목록의 준비된 후보를 실행한다")
    void continuesAfterDeferredAndStoppedCandidates() {
        reserve("a-deferred", at(0));
        recoveryStore.claim("a-deferred", lease("attempt-active", at(1), at(20)));
        reserve("b-stopped", at(0));
        recoveryStore.claim("b-stopped", lease("attempt-manual", at(1), at(20)));
        recoveryStore.complete(new Completion(
                "attempt-manual", "worker-a", at(2), RecoveryResult.MANUAL_REVIEW_REQUIRED));
        reserve("c-ready", at(0));
        var port = new RecordingPort(CheckFailed.INSTANCE);

        var batch = runner(port).run(criteria(10), candidate ->
                lease("attempt-" + candidate.reservation().reservationId(), at(5), at(20)));

        assertThat(batch.candidates()).satisfiesExactly(
                first -> assertThat(first).isInstanceOfSatisfying(SkippedByReport.class, skipped ->
                        assertThat(skipped.candidate().decision())
                                .isEqualTo(new Deferred(HoldReason.ACTIVE_LEASE, at(20)))),
                second -> assertThat(second).isInstanceOfSatisfying(SkippedByReport.class, skipped ->
                        assertThat(skipped.candidate().decision())
                                .isEqualTo(new Stopped(StopReason.MANUAL_REVIEW_REQUIRED, 1))),
                third -> assertThat(third).isInstanceOf(RecoveryFinished.class));
        assertThat(port.reservationIds()).containsExactly("c-ready");
        assertThat(recoveryStore.history("a-deferred")).hasSize(1);
        assertThat(recoveryStore.history("b-stopped")).hasSize(1);
        assertThat(recoveryStore.history("c-ready")).singleElement()
                .satisfies(attempt -> assertThat(attempt.status()).isEqualTo(Status.COMPLETED));
    }

    @Test
    @DisplayName("조회 뒤 첫 후보가 수동 검토로 바뀌어도 다음 준비된 후보를 실행한다")
    void continuesAfterAssignmentRecheckSkipsChangedCandidate() {
        reserve("a-changed", at(0));
        reserve("b-ready", at(0));
        var port = new RecordingPort(CheckFailed.INSTANCE);

        var batch = runner(port).run(criteria(10), candidate -> {
            if (candidate.reservation().reservationId().equals("a-changed")) {
                recoveryStore.claim("a-changed", lease("attempt-manual", at(1), at(4)));
                recoveryStore.complete(new Completion(
                        "attempt-manual", "worker-a", at(2), RecoveryResult.MANUAL_REVIEW_REQUIRED));
            }
            return lease("attempt-" + candidate.reservation().reservationId(), at(5), at(20));
        });

        assertThat(batch.candidates()).satisfiesExactly(
                first -> assertThat(first).isInstanceOfSatisfying(AssignmentNotClaimed.class, skipped ->
                        assertThat(skipped.assignment()).isEqualTo(new ReadyClaimSkipped(
                                new Stopped(StopReason.MANUAL_REVIEW_REQUIRED, 1)))),
                second -> assertThat(second).isInstanceOf(RecoveryFinished.class));
        assertThat(port.reservationIds()).containsExactly("b-ready");
        assertThat(recoveryStore.history("a-changed")).hasSize(1);
        assertThat(recoveryStore.history("b-ready")).hasSize(1);
    }

    @Test
    @DisplayName("운영 조회 개수만큼만 후보를 실행하고 뒤의 예약은 건드리지 않는다")
    void respectsOperationsQueryLimit() {
        reserve("a-ready", at(0));
        reserve("b-ready", at(0));
        reserve("c-outside-limit", at(0));
        var port = new RecordingPort(CheckFailed.INSTANCE);

        var batch = runner(port).run(criteria(2), candidate ->
                lease("attempt-" + candidate.reservation().reservationId(), at(5), at(20)));

        assertThat(batch.report().items()).hasSize(2);
        assertThat(batch.candidates()).allMatch(RecoveryFinished.class::isInstance);
        assertThat(port.reservationIds()).containsExactly("a-ready", "b-ready");
        assertThat(recoveryStore.history("c-outside-limit")).isEmpty();
    }

    @Test
    @DisplayName("한 후보의 외부 확인 오류를 기록하고 다음 준비된 후보를 계속 실행한다")
    void continuesAfterCandidateRecoveryFailure() {
        reserve("a-fails", at(0));
        reserve("b-ready", at(0));
        PolicyAiRecoveryPort port = inspection -> {
            if (inspection.reservation().reservationId().equals("a-fails")) {
                throw new IllegalStateException("인공 공급자 조회 오류");
            }
            return CheckFailed.INSTANCE;
        };

        var batch = runner(port).run(criteria(10), candidate ->
                lease("attempt-" + candidate.reservation().reservationId(), at(5), at(20)));

        assertThat(batch.candidates()).satisfiesExactly(
                first -> assertThat(first).isInstanceOfSatisfying(RecoveryFailed.class, failed ->
                        assertThat(failed.failure()).hasMessage("인공 공급자 조회 오류")),
                second -> assertThat(second).isInstanceOf(RecoveryFinished.class));
        assertThat(recoveryStore.history("a-fails")).singleElement()
                .satisfies(attempt -> assertThat(attempt.status()).isEqualTo(Status.ACTIVE));
        assertThat(recoveryStore.history("b-ready")).singleElement()
                .satisfies(attempt -> assertThat(attempt.status()).isEqualTo(Status.COMPLETED));
    }

    private AiReservationRecoveryWorkRunner runner(PolicyAiRecoveryPort port) {
        var coordinator = new PolicyAiRecoveryCoordinator(
                recoveryStore, lifecycleStore, applier, port, Clock.fixed(at(6), ZoneOffset.UTC));
        return new AiReservationRecoveryWorkRunner(operationsQuery, workAssigner, coordinator);
    }

    private Criteria criteria(int limit) {
        return new Criteria(SCHEDULE, at(0), at(5), limit);
    }

    private void reserve(String reservationId, Instant reservedAt) {
        String budgetId = "budget-" + reservationId;
        var balance = insertBudget(budgetId);
        var request = request("policy-" + reservationId);
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

    private static Request request(String policyId) {
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
        private final List<String> reservationIds = new ArrayList<>();

        private RecordingPort(Outcome outcome) {
            this.outcome = outcome;
        }

        @Override
        public Outcome inspect(Inspection inspection) {
            reservationIds.add(inspection.reservation().reservationId());
            return outcome;
        }

        private List<String> reservationIds() {
            return List.copyOf(reservationIds);
        }
    }
}
