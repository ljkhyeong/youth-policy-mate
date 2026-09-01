package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationState.Cancellation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.ChargeConfirmation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Dispatch;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.NoChargeConfirmation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Phase;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.UncertainOutcome;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.UncertainReason;
import kr.youthpolicymate.ingestion.AiRequestBudget.Balance;
import kr.youthpolicymate.ingestion.AiRequestBudget.CostCeiling;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Lease;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.RecoveryResult;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Status;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.ChargeFound;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.CheckFailed;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Inspection;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.NoChargeFound;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.NotDispatched;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Outcome;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.ReviewRequired;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest
class PolicyAiRecoveryCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-09-01T01:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired JdbcClient jdbcClient;
    @Autowired AiBudgetReservationStore reservationStore;
    @Autowired AiBudgetReservationLifecycleStore lifecycleStore;
    @Autowired AiReservationRecoveryStore recoveryStore;
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
    @DisplayName("DB 트랜잭션 밖에서 미확인 호출을 확인하고 활성 임대로 정산한다")
    void settlesUnknownOutcomeWithActiveFence() {
        reserve("budget-a", "reservation-a", 10, "10");
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));
        lifecycleStore.markOutcomeUnknown("reservation-a", new UncertainOutcome(
                "unknown-a", NOW.plusSeconds(2), UncertainReason.TIMEOUT));
        var port = new ScriptedPort(new ChargeFound(
                new ChargeConfirmation("charge-a", NOW.plusSeconds(4), money("7.50"))));

        var run = coordinator(port, NOW.plusSeconds(5)).recoverNext(
                lease("attempt-a", "worker-a", NOW.plusSeconds(3), NOW.plusSeconds(30)));

        assertThat(run).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.Recovered.class, recovered -> {
            assertThat(recovered.application().transition().orElseThrow().decision())
                    .isEqualTo(AiBudgetReservationLifecycleStore.Decision.SETTLED);
            assertThat(recovered.application().completion().decision())
                    .isEqualTo(AiReservationRecoveryStore.CompletionDecision.COMPLETED);
        });
        assertThat(port.calls()).isOne();
        assertThat(port.transactionActive()).isFalse();
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase()).isEqualTo(Phase.SETTLED);
        assertThat(budgetAmounts("budget-a")).isEqualTo(new BudgetAmounts(money("7.50"), money("0")));
        assertThat(recoveryStore.history("reservation-a")).singleElement().satisfies(attempt -> {
            assertThat(attempt.status()).isEqualTo(Status.COMPLETED);
            assertThat(attempt.result()).contains(RecoveryResult.CHECK_COMPLETED);
            assertThat(attempt.claimedPhase()).isEqualTo(Phase.OUTCOME_UNKNOWN);
            assertThat(attempt.completedPhase()).contains(Phase.SETTLED);
        });
    }

    @Test
    @DisplayName("확인된 무과금과 호출 전 중단을 각 예약 단계에 맞게 적용한다")
    void appliesNoChargeAndNotDispatchedOutcomes() {
        reserve("budget-a", "reservation-a", 10, "10");
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));
        var noCharge = coordinator(new ScriptedPort(new NoChargeFound(
                        new NoChargeConfirmation("no-charge-a", NOW.plusSeconds(3)))), NOW.plusSeconds(4))
                .recoverNext(lease("attempt-a", "worker-a", NOW.plusSeconds(2), NOW.plusSeconds(20)));

        assertThat(noCharge).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.Recovered.class, recovered ->
                assertThat(recovered.application().transition().orElseThrow().decision())
                        .isEqualTo(AiBudgetReservationLifecycleStore.Decision.RELEASED_NO_CHARGE));
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase())
                .isEqualTo(Phase.RELEASED_NO_CHARGE);

        reserve("budget-b", "reservation-b", 20, "5");
        var notDispatched = coordinator(new ScriptedPort(new NotDispatched(
                        new Cancellation("cancel-b", NOW.plusSeconds(3)))), NOW.plusSeconds(4))
                .recoverNext(lease("attempt-b", "worker-b", NOW.plusSeconds(2), NOW.plusSeconds(20)));

        assertThat(notDispatched).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.Recovered.class, recovered ->
                assertThat(recovered.application().transition().orElseThrow().decision())
                        .isEqualTo(AiBudgetReservationLifecycleStore.Decision.RELEASED_BEFORE_DISPATCH));
        assertThat(lifecycleStore.find("reservation-b").orElseThrow().phase()).isEqualTo(Phase.CANCELLED);
        assertThat(budgetAmounts("budget-b")).isEqualTo(new BudgetAmounts(money("0"), money("0")));
    }

    @Test
    @DisplayName("확인 실패와 수동 검토는 예약액을 유지하고 시도 결과만 기록한다")
    void recordsFailedAndManualChecksWithoutReservationTransition() {
        reserve("budget-a", "reservation-a", 10, "10");

        var failed = coordinator(new ScriptedPort(CheckFailed.INSTANCE), NOW.plusSeconds(3))
                .recoverNext(lease("attempt-a", "worker-a", NOW.plusSeconds(1), NOW.plusSeconds(20)));
        assertThat(failed).isInstanceOf(PolicyAiRecoveryCoordinator.Recovered.class);

        var review = coordinator(new ScriptedPort(ReviewRequired.INSTANCE), NOW.plusSeconds(6))
                .recoverNext(lease("attempt-b", "worker-b", NOW.plusSeconds(4), NOW.plusSeconds(20)));
        assertThat(review).isInstanceOf(PolicyAiRecoveryCoordinator.Recovered.class);

        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase()).isEqualTo(Phase.HELD);
        assertThat(budgetAmounts("budget-a")).isEqualTo(new BudgetAmounts(money("0"), money("10")));
        assertThat(recoveryStore.history("reservation-a")).satisfiesExactly(first -> {
            assertThat(first.result()).contains(RecoveryResult.CHECK_FAILED);
            assertThat(first.status()).isEqualTo(Status.COMPLETED);
        }, second -> {
            assertThat(second.result()).contains(RecoveryResult.MANUAL_REVIEW_REQUIRED);
            assertThat(second.status()).isEqualTo(Status.COMPLETED);
        });
    }

    @Test
    @DisplayName("외부 확인 중 새 임대가 시작되면 오래된 작업자의 정산을 막는다")
    void fencesStaleWorkerAfterLeaseReplacement() {
        reserve("budget-a", "reservation-a", 10, "10");
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));
        PolicyAiRecoveryPort replacingPort = inspection -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(recoveryStore.claim("reservation-a",
                    lease("attempt-b", "worker-b", NOW.plusSeconds(4), NOW.plusSeconds(30))).decision())
                    .isEqualTo(AiReservationRecoveryStore.ClaimDecision.CLAIMED);
            return new ChargeFound(new ChargeConfirmation("charge-a", NOW.plusSeconds(3), money("7")));
        };

        var run = coordinator(replacingPort, NOW.plusSeconds(6)).recoverNext(
                lease("attempt-a", "worker-a", NOW.plusSeconds(2), NOW.plusSeconds(4)));

        assertThat(run).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.Recovered.class, recovered -> {
            assertThat(recovered.application().transition().orElseThrow().decision())
                    .isEqualTo(AiBudgetReservationLifecycleStore.Decision.RECOVERY_ATTEMPT_INACTIVE);
            assertThat(recovered.application().completion().decision())
                    .isEqualTo(AiReservationRecoveryStore.CompletionDecision.LEASE_EXPIRED);
        });
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase()).isEqualTo(Phase.DISPATCHED);
        assertThat(budgetAmounts("budget-a")).isEqualTo(new BudgetAmounts(money("0"), money("10")));
        assertThat(recoveryStore.history("reservation-a")).satisfiesExactly(first ->
                assertThat(first.status()).isEqualTo(Status.EXPIRED), second -> {
            assertThat(second.status()).isEqualTo(Status.ACTIVE);
            assertThat(second.ownerId()).isEqualTo("worker-b");
        });
    }

    @Test
    @DisplayName("활성 시도여도 적용 시각이 임대 만료와 같으면 정산을 막는다")
    void fencesExpiredActiveAttemptAtLeaseBoundary() {
        reserve("budget-a", "reservation-a", 10, "10");
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));

        var run = coordinator(new ScriptedPort(new ChargeFound(
                        new ChargeConfirmation("charge-a", NOW.plusSeconds(3), money("7")))), NOW.plusSeconds(4))
                .recoverNext(lease("attempt-a", "worker-a", NOW.plusSeconds(2), NOW.plusSeconds(4)));

        assertThat(run).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.Recovered.class, recovered -> {
            assertThat(recovered.application().transition().orElseThrow().decision())
                    .isEqualTo(AiBudgetReservationLifecycleStore.Decision.RECOVERY_LEASE_EXPIRED);
            assertThat(recovered.application().completion().decision())
                    .isEqualTo(AiReservationRecoveryStore.CompletionDecision.LEASE_EXPIRED);
        });
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase()).isEqualTo(Phase.DISPATCHED);
        assertThat(budgetAmounts("budget-a")).isEqualTo(new BudgetAmounts(money("0"), money("10")));
        assertThat(recoveryStore.history("reservation-a")).singleElement().satisfies(attempt ->
                assertThat(attempt.status()).isEqualTo(Status.EXPIRED));
    }

    @Test
    @DisplayName("외부 확인 중 예약 단계가 바뀌면 결과를 적용하지 않고 수동 검토로 남긴다")
    void rejectsChangedReservationVersion() {
        reserve("budget-a", "reservation-a", 10, "10");
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));
        PolicyAiRecoveryPort changingPort = inspection -> {
            lifecycleStore.markOutcomeUnknown("reservation-a", new UncertainOutcome(
                    "unknown-a", NOW.plusSeconds(3), UncertainReason.CONNECTION_LOST));
            return new ChargeFound(new ChargeConfirmation("charge-a", NOW.plusSeconds(4), money("7")));
        };

        var run = coordinator(changingPort, NOW.plusSeconds(5)).recoverNext(
                lease("attempt-a", "worker-a", NOW.plusSeconds(2), NOW.plusSeconds(20)));

        assertThat(run).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.Recovered.class, recovered -> {
            assertThat(recovered.application().transition().orElseThrow().decision())
                    .isEqualTo(AiBudgetReservationLifecycleStore.Decision.RECOVERY_RESERVATION_CHANGED);
            assertThat(recovered.application().completion().attempt().orElseThrow().result())
                    .contains(RecoveryResult.MANUAL_REVIEW_REQUIRED);
        });
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase()).isEqualTo(Phase.OUTCOME_UNKNOWN);
        assertThat(budgetAmounts("budget-a")).isEqualTo(new BudgetAmounts(money("0"), money("10")));
    }

    @Test
    @DisplayName("완료한 같은 시도를 재전달하면 외부 확인을 다시 실행하지 않는다")
    void doesNotInspectCompletedAttemptAgain() {
        reserve("budget-a", "reservation-a", 10, "10");
        var lease = lease("attempt-a", "worker-a", NOW.plusSeconds(1), NOW.plusSeconds(20));
        var firstPort = new ScriptedPort(CheckFailed.INSTANCE);
        coordinator(firstPort, NOW.plusSeconds(3)).recoverNext(lease);
        var replayPort = new ScriptedPort(ReviewRequired.INSTANCE);

        var replay = coordinator(replayPort, NOW.plusSeconds(4)).recoverNext(lease);

        assertThat(replay).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.NotStarted.class, stopped ->
                assertThat(stopped.reason()).isEqualTo(PolicyAiRecoveryCoordinator.StopReason.ATTEMPT_NOT_ACTIVE));
        assertThat(firstPort.calls()).isOne();
        assertThat(replayPort.calls()).isZero();
    }

    @Test
    @DisplayName("복구할 예약이 없으면 외부 확인을 시작하지 않는다")
    void doesNotInspectWhenNoReservationIsAvailable() {
        var port = new ScriptedPort(CheckFailed.INSTANCE);

        var run = coordinator(port, NOW.plusSeconds(2)).recoverNext(
                lease("attempt-a", "worker-a", NOW.plusSeconds(1), NOW.plusSeconds(20)));

        assertThat(run).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.NotStarted.class, stopped -> {
            assertThat(stopped.reason()).isEqualTo(PolicyAiRecoveryCoordinator.StopReason.CLAIM_REJECTED);
            assertThat(stopped.claim().decision())
                    .isEqualTo(AiReservationRecoveryStore.ClaimDecision.NO_RESERVATION_AVAILABLE);
        });
        assertThat(port.calls()).isZero();
    }

    @Test
    @DisplayName("예상하지 못한 외부 확인 오류는 활성 임대와 예약액을 유지한다")
    void preservesActiveAttemptForUnexpectedInspectionFailure() {
        reserve("budget-a", "reservation-a", 10, "10");
        PolicyAiRecoveryPort throwing = inspection -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            throw new IllegalStateException("인공 복구 조회 오류");
        };

        assertThatThrownBy(() -> coordinator(throwing, NOW.plusSeconds(3)).recoverNext(
                lease("attempt-a", "worker-a", NOW.plusSeconds(1), NOW.plusSeconds(20))))
                .isInstanceOf(IllegalStateException.class).hasMessage("인공 복구 조회 오류");

        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase()).isEqualTo(Phase.HELD);
        assertThat(recoveryStore.history("reservation-a")).singleElement().satisfies(attempt -> {
            assertThat(attempt.status()).isEqualTo(Status.ACTIVE);
            assertThat(attempt.result()).isEmpty();
        });
    }

    private PolicyAiRecoveryCoordinator coordinator(PolicyAiRecoveryPort port, Instant appliedAt) {
        return new PolicyAiRecoveryCoordinator(recoveryStore, lifecycleStore, applier, port,
                Clock.fixed(appliedAt, ZoneOffset.UTC));
    }

    private void reserve(String budgetId, String reservationId, long requestSequence, String maximum) {
        var balance = insertBudget(budgetId);
        assertThat(reservationStore.reserve(reservationId,
                required(balance, request(requestSequence), maximum), NOW).decision())
                .isEqualTo(AiBudgetReservationStore.Decision.RESERVED);
    }

    private Balance insertBudget(String budgetId) {
        var balance = new Balance(budgetId, NOW.minusSeconds(60), NOW.plusSeconds(60),
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
                .param("createdAt", dbTime(NOW.minusSeconds(120)))
                .update();
        return balance;
    }

    private static ReservationRequired required(Balance balance, Request request, String maximum) {
        return new ReservationRequired(balance,
                new CostCeiling(request, "price-a", NOW.plusSeconds(30), money(maximum)));
    }

    private static Request request(long sequence) {
        var observation = new PolicyObservation("synthetic-policy", sequence, NOW.minusSeconds(120), new Readable(
                new SnapshotReference("synthetic-source", "raw-" + sequence, "a".repeat(64)),
                new ContentFingerprint("comparison-a", "b".repeat(64))));
        var revision = PolicyRevisionState.empty("synthetic-policy").consider(observation)
                .state().currentRevision().orElseThrow();
        return new Request(revision, Kind.SUMMARY, "generation-a", sequence, NOW.minusSeconds(30));
    }

    private void clearDatabase() {
        jdbcClient.sql("delete from ai_reservation_recovery_attempts").update();
        jdbcClient.sql("delete from ai_request_reservations").update();
        jdbcClient.sql("delete from ai_budgets").update();
    }

    private BudgetAmounts budgetAmounts(String budgetId) {
        return jdbcClient.sql("""
                select confirmed_won, reserved_won from ai_budgets where budget_id = :budgetId
                """)
                .param("budgetId", budgetId)
                .query((resultSet, rowNumber) -> new BudgetAmounts(
                        resultSet.getBigDecimal("confirmed_won"), resultSet.getBigDecimal("reserved_won")))
                .single();
    }

    private static Lease lease(String attemptId, String ownerId, Instant claimedAt, Instant leaseUntil) {
        return new Lease(attemptId, ownerId, claimedAt, leaseUntil);
    }

    private static BigDecimal money(String amount) { return new BigDecimal(amount); }
    private static OffsetDateTime dbTime(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    private static final class ScriptedPort implements PolicyAiRecoveryPort {
        private final Outcome outcome;
        private int calls;
        private boolean transactionActive;

        private ScriptedPort(Outcome outcome) {
            this.outcome = outcome;
        }

        @Override
        public Outcome inspect(Inspection inspection) {
            calls++;
            transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            return outcome;
        }

        private int calls() { return calls; }
        private boolean transactionActive() { return transactionActive; }
    }

    private record BudgetAmounts(BigDecimal confirmedWon, BigDecimal reservedWon) {
        private BudgetAmounts {
            confirmedWon = confirmedWon.stripTrailingZeros();
            reservedWon = reservedWon.stripTrailingZeros();
        }
    }
}
