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
import kr.youthpolicymate.ingestion.AiReservationRecoveryLeaseRenewalStore.RenewalCommand;
import kr.youthpolicymate.ingestion.AiReservationRecoveryLeaseRenewalStore.RenewalDecision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Criteria;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Schedule;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Lease;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.RecoveryResult;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.ReadyClaimed;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Status;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.ChargeFound;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.CheckFailed;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Inspection;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.NoChargeFound;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.NotDispatched;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Outcome;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.ResponseFound;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.ReviewRequired;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.ConfirmedCharge;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.PendingCharge;
import kr.youthpolicymate.ingestion.PolicyAiRequestAdmission.ReservationRequired;
import kr.youthpolicymate.ingestion.PolicyAiResult.Kind;
import kr.youthpolicymate.ingestion.PolicyAiResult.Request;
import kr.youthpolicymate.ingestion.PolicyAiResult.Generated;
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
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest
class PolicyAiRecoveryCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-09-01T01:00:00Z");
    private static final Schedule SCHEDULE = new Schedule(
            3, List.of(Duration.ofSeconds(5), Duration.ofSeconds(20)));

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired JdbcClient jdbcClient;
    @Autowired AiBudgetReservationStore reservationStore;
    @Autowired AiBudgetReservationLifecycleStore lifecycleStore;
    @Autowired AiReservationRecoveryStore recoveryStore;
    @Autowired AiReservationRecoveryLeaseRenewalStore leaseRenewalStore;
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
    @DisplayName("DB 트랜잭션 밖에서 미확인 호출을 확인하고 활성 임대로 정산한다")
    void settlesUnknownOutcomeWithActiveFence() {
        var request = request(10);
        reserve("budget-a", "reservation-a", request, "10");
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));
        lifecycleStore.markOutcomeUnknown("reservation-a", new UncertainOutcome(
                "unknown-a", NOW.plusSeconds(2), UncertainReason.TIMEOUT));
        var result = new PolicyAiResult(request, NOW.plusSeconds(3),
                new Generated("candidate-a", "c".repeat(64)));
        var port = new ScriptedPort(new ResponseFound(result, new ConfirmedCharge(
                new ChargeConfirmation("charge-a", NOW.plusSeconds(4), money("7.50")))));

        var run = coordinator(port, NOW.plusSeconds(5)).recoverNext(
                lease("attempt-a", "worker-a", NOW.plusSeconds(3), NOW.plusSeconds(30)));

        assertThat(run).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.Recovered.class, recovered -> {
            assertThat(recovered.application().transition().orElseThrow().decision())
                    .isEqualTo(AiBudgetReservationLifecycleStore.Decision.SETTLED);
            assertThat(recovered.application().completion().decision())
                    .isEqualTo(AiReservationRecoveryStore.CompletionDecision.COMPLETED);
            assertThat(recovered.outcome()).isEqualTo(new ResponseFound(result, new ConfirmedCharge(
                    new ChargeConfirmation("charge-a", NOW.plusSeconds(4), money("7.50")))));
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
    @DisplayName("외부 확인 중 갱신한 임대로 기존 만료 뒤 결과를 안전하게 적용한다")
    void appliesRecoveryResultWithinRenewedLease() {
        reserve("budget-a", "reservation-a", 10, "10");
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));
        PolicyAiRecoveryPort renewingPort = inspection -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(leaseRenewalStore.renew(new RenewalCommand(
                    "renewal-a", "reservation-a", inspection.attempt().attemptId(),
                    inspection.attempt().attemptNumber(), inspection.attempt().ownerId(),
                    NOW.plusSeconds(4), NOW.plusSeconds(3), NOW.plusSeconds(10))).decision())
                    .isEqualTo(RenewalDecision.RENEWED);
            return new ChargeFound(new ChargeConfirmation(
                    "charge-a", NOW.plusSeconds(3), money("7")));
        };

        var run = coordinator(renewingPort, NOW.plusSeconds(6)).recoverNext(
                lease("attempt-a", "worker-a", NOW.plusSeconds(2), NOW.plusSeconds(4)));

        assertThat(run).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.Recovered.class, recovered -> {
            assertThat(recovered.application().transition().orElseThrow().decision())
                    .isEqualTo(AiBudgetReservationLifecycleStore.Decision.SETTLED);
            assertThat(recovered.application().completion().decision())
                    .isEqualTo(AiReservationRecoveryStore.CompletionDecision.COMPLETED);
        });
        assertThat(recoveryStore.findAttempt("attempt-a").orElseThrow().leaseUntil())
                .isEqualTo(NOW.plusSeconds(10));
        assertThat(leaseRenewalStore.history("attempt-a")).hasSize(1);
        assertThat(budgetAmounts("budget-a")).isEqualTo(new BudgetAmounts(money("7"), money("0")));
    }

    @Test
    @DisplayName("복구한 AI 응답의 청구가 대기 중이면 예약액을 유지하고 응답 확인을 완료한다")
    void recoversResponseWhileChargeRemainsPending() {
        var request = request(10);
        reserve("budget-a", "reservation-a", request, "10");
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));
        var result = new PolicyAiResult(request, NOW.plusSeconds(3),
                new Generated("candidate-a", "c".repeat(64)));

        var run = coordinator(new ScriptedPort(new ResponseFound(result, PendingCharge.INSTANCE)),
                NOW.plusSeconds(4)).recoverNext(
                lease("attempt-a", "worker-a", NOW.plusSeconds(2), NOW.plusSeconds(20)));

        assertThat(run).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.Recovered.class, recovered -> {
            assertThat(recovered.outcome()).isEqualTo(new ResponseFound(result, PendingCharge.INSTANCE));
            assertThat(recovered.application().transition()).isEmpty();
            assertThat(recovered.application().completion().attempt().orElseThrow().result())
                    .contains(RecoveryResult.CHECK_COMPLETED);
        });
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase()).isEqualTo(Phase.DISPATCHED);
        assertThat(budgetAmounts("budget-a")).isEqualTo(new BudgetAmounts(money("0"), money("10")));
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

    @Test
    @DisplayName("배정된 활성 시도를 다시 소유하지 않고 트랜잭션 밖에서 확인한다")
    void recoversAssignedAttemptWithoutClaimingAgain() {
        reserve("budget-a", "reservation-a", 10, "10");
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));
        ReadyClaimed assignment = assign("reservation-a",
                lease("attempt-a", "worker-a", NOW.plusSeconds(2), NOW.plusSeconds(20)));
        var port = new ScriptedPort(new ChargeFound(
                new ChargeConfirmation("charge-a", NOW.plusSeconds(3), money("7"))));

        var run = coordinator(port, NOW.plusSeconds(4)).recoverAssigned(assignment);

        assertThat(run).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.Recovered.class, recovered -> {
            assertThat(recovered.inspection().attempt().attemptId()).isEqualTo("attempt-a");
            assertThat(recovered.application().transition().orElseThrow().decision())
                    .isEqualTo(AiBudgetReservationLifecycleStore.Decision.SETTLED);
        });
        assertThat(port.calls()).isOne();
        assertThat(port.transactionActive()).isFalse();
        assertThat(recoveryStore.history("reservation-a")).singleElement()
                .satisfies(attempt -> assertThat(attempt.status()).isEqualTo(Status.COMPLETED));
    }

    @Test
    @DisplayName("배정 뒤 완료된 시도는 외부 확인을 다시 실행하지 않는다")
    void doesNotRecoverAssignedAttemptCompletedBeforeExecution() {
        reserve("budget-a", "reservation-a", 10, "10");
        ReadyClaimed assignment = assign("reservation-a",
                lease("attempt-a", "worker-a", NOW.plusSeconds(1), NOW.plusSeconds(20)));
        recoveryStore.complete(new AiReservationRecoveryStore.Completion(
                "attempt-a", "worker-a", NOW.plusSeconds(2), RecoveryResult.CHECK_FAILED));
        var port = new ScriptedPort(ReviewRequired.INSTANCE);

        var run = coordinator(port, NOW.plusSeconds(3)).recoverAssigned(assignment);

        assertThat(run).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.NotStarted.class, stopped ->
                assertThat(stopped.reason())
                        .isEqualTo(PolicyAiRecoveryCoordinator.StopReason.ATTEMPT_NOT_ACTIVE));
        assertThat(port.calls()).isZero();
    }

    @Test
    @DisplayName("배정 뒤 교체된 시도는 외부 확인을 실행하지 않는다")
    void doesNotRecoverAssignedAttemptReplacedBeforeExecution() {
        reserve("budget-a", "reservation-a", 10, "10");
        ReadyClaimed assignment = assign("reservation-a",
                lease("attempt-a", "worker-a", NOW.plusSeconds(1), NOW.plusSeconds(3)));
        assertThat(recoveryStore.claim("reservation-a",
                lease("attempt-b", "worker-b", NOW.plusSeconds(4), NOW.plusSeconds(20))).decision())
                .isEqualTo(AiReservationRecoveryStore.ClaimDecision.CLAIMED);
        var port = new ScriptedPort(ReviewRequired.INSTANCE);

        var run = coordinator(port, NOW.plusSeconds(5)).recoverAssigned(assignment);

        assertThat(run).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.NotStarted.class, stopped ->
                assertThat(stopped.reason())
                        .isEqualTo(PolicyAiRecoveryCoordinator.StopReason.ATTEMPT_NOT_ACTIVE));
        assertThat(port.calls()).isZero();
        assertThat(recoveryStore.history("reservation-a")).satisfiesExactly(
                first -> assertThat(first.status()).isEqualTo(Status.EXPIRED),
                second -> assertThat(second.status()).isEqualTo(Status.ACTIVE));
    }

    @Test
    @DisplayName("배정 뒤 예약이 종료되면 공급자를 확인하지 않고 수동 검토로 시도를 마친다")
    void completesAssignedAttemptForTerminalReservationWithoutInspection() {
        reserve("budget-a", "reservation-a", 10, "10");
        ReadyClaimed assignment = assign("reservation-a",
                lease("attempt-a", "worker-a", NOW.plusSeconds(1), NOW.plusSeconds(20)));
        lifecycleStore.cancelBeforeDispatch(
                "reservation-a", new Cancellation("cancel-a", NOW.plusSeconds(2)));
        var port = new ScriptedPort(CheckFailed.INSTANCE);

        var run = coordinator(port, NOW.plusSeconds(3)).recoverAssigned(assignment);

        assertThat(run).isInstanceOfSatisfying(PolicyAiRecoveryCoordinator.Recovered.class, recovered -> {
            assertThat(recovered.outcome()).isEqualTo(ReviewRequired.INSTANCE);
            assertThat(recovered.application().completion().attempt().orElseThrow().result())
                    .contains(RecoveryResult.MANUAL_REVIEW_REQUIRED);
        });
        assertThat(port.calls()).isZero();
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase()).isEqualTo(Phase.CANCELLED);
    }

    private PolicyAiRecoveryCoordinator coordinator(PolicyAiRecoveryPort port, Instant appliedAt) {
        return new PolicyAiRecoveryCoordinator(recoveryStore, lifecycleStore, applier, port,
                Clock.fixed(appliedAt, ZoneOffset.UTC));
    }

    private ReadyClaimed assign(String reservationId, Lease lease) {
        var report = operationsQuery.findOldestUnresolved(new Criteria(
                SCHEDULE, lease.claimedAt(), lease.claimedAt(), 10));
        var candidate = report.items().stream()
                .filter(item -> item.reservation().reservationId().equals(reservationId))
                .findFirst().orElseThrow();
        return (ReadyClaimed) workAssigner.assign(report, candidate, lease);
    }

    private void reserve(String budgetId, String reservationId, long requestSequence, String maximum) {
        reserve(budgetId, reservationId, request(requestSequence), maximum);
    }

    private void reserve(String budgetId, String reservationId, Request request, String maximum) {
        var balance = insertBudget(budgetId);
        assertThat(reservationStore.reserve(reservationId,
                required(balance, request, maximum), NOW).decision())
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
        jdbcClient.sql("delete from ai_reservation_recovery_lease_renewals").update();
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
