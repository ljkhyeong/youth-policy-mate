package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationState.ChargeConfirmation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Dispatch;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.NoChargeConfirmation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Phase;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.UncertainOutcome;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.UncertainReason;
import kr.youthpolicymate.ingestion.AiRequestBudget.Balance;
import kr.youthpolicymate.ingestion.AiRequestBudget.CostCeiling;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.ConfirmedCharge;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.ConfirmedNoCharge;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.Invocation;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.Outcome;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.PendingCharge;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.ResponseReceived;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.Uncertain;
import kr.youthpolicymate.ingestion.PolicyAiRequestAdmission.ReservationRequired;
import kr.youthpolicymate.ingestion.PolicyAiResult.Generated;
import kr.youthpolicymate.ingestion.PolicyAiResult.Kind;
import kr.youthpolicymate.ingestion.PolicyAiResult.Request;
import kr.youthpolicymate.ingestion.PolicyAiResult.Unavailable;
import kr.youthpolicymate.ingestion.PolicyAiResult.UnavailableReason;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest
class PolicyAiExecutionCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-09-01T01:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    AiBudgetReservationStore reservationStore;

    @Autowired
    AiBudgetReservationLifecycleStore lifecycleStore;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("delete from ai_request_reservations").update();
        jdbcClient.sql("delete from ai_budgets").update();
    }

    @AfterEach
    void tearDown() {
        jdbcClient.sql("delete from ai_request_reservations").update();
        jdbcClient.sql("delete from ai_budgets").update();
    }

    @Test
    @DisplayName("DB 트랜잭션 밖에서 인공 AI를 실행하고 확인된 비용을 정산한다")
    void executesOutsideTransactionAndSettlesConfirmedCharge() {
        var required = required(insertBudget("budget-a"), request(10), "10");
        var result = generated(required.cost().request(), NOW.plusSeconds(2));
        var port = new ScriptedPort(new ResponseReceived(result, new ConfirmedCharge(
                new ChargeConfirmation("charge-a", NOW.plusSeconds(3), money("7.50")))));

        var run = coordinator(port).execute("reservation-a", required, NOW,
                new Dispatch("dispatch-a", NOW.plusSeconds(1)));

        assertThat(run).isInstanceOfSatisfying(PolicyAiExecutionCoordinator.Responded.class, responded -> {
            assertThat(responded.result()).isEqualTo(result);
            assertThat(responded.transition().decision())
                    .isEqualTo(AiBudgetReservationLifecycleStore.Decision.SETTLED);
        });
        assertThat(port.calls()).isOne();
        assertThat(port.transactionActive()).isFalse();
        assertThat(port.invocation().reservationId()).isEqualTo("reservation-a");
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase()).isEqualTo(Phase.SETTLED);
        assertThat(budgetAmounts("budget-a")).isEqualTo(new BudgetAmounts(money("7.50"), money("0")));
    }

    @Test
    @DisplayName("타임아웃으로 결과를 확인하지 못하면 예약액을 유지한다")
    void keepsReservationForUncertainOutcome() {
        var required = required(insertBudget("budget-a"), request(10), "10");
        var uncertain = new UncertainOutcome("observation-a", NOW.plusSeconds(2), UncertainReason.TIMEOUT);
        var port = new ScriptedPort(new Uncertain(uncertain));

        var run = coordinator(port).execute("reservation-a", required, NOW,
                new Dispatch("dispatch-a", NOW.plusSeconds(1)));

        assertThat(run).isInstanceOfSatisfying(PolicyAiExecutionCoordinator.UncertainRun.class, recorded -> {
            assertThat(recorded.uncertain()).isEqualTo(uncertain);
            assertThat(recorded.transition().decision())
                    .isEqualTo(AiBudgetReservationLifecycleStore.Decision.OUTCOME_UNKNOWN);
        });
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase()).isEqualTo(Phase.OUTCOME_UNKNOWN);
        assertThat(budgetAmounts("budget-a")).isEqualTo(new BudgetAmounts(money("0"), money("10")));
    }

    @Test
    @DisplayName("AI 응답을 받아도 청구가 미확인이면 예약액을 해제하지 않는다")
    void keepsReservationWhileChargeIsPending() {
        var required = required(insertBudget("budget-a"), request(10), "10");
        var result = generated(required.cost().request(), NOW.plusSeconds(2));
        var port = new ScriptedPort(new ResponseReceived(result, PendingCharge.INSTANCE));

        var run = coordinator(port).execute("reservation-a", required, NOW,
                new Dispatch("dispatch-a", NOW.plusSeconds(1)));

        assertThat(run).isInstanceOfSatisfying(PolicyAiExecutionCoordinator.Responded.class, responded -> {
            assertThat(responded.result()).isEqualTo(result);
            assertThat(responded.billing()).isEqualTo(PendingCharge.INSTANCE);
            assertThat(responded.transition().reservation().orElseThrow().phase()).isEqualTo(Phase.DISPATCHED);
        });
        assertThat(budgetAmounts("budget-a")).isEqualTo(new BudgetAmounts(money("0"), money("10")));
    }

    @Test
    @DisplayName("AI 응답과 무과금 확인이 함께 오면 예약액만 해제한다")
    void releasesConfirmedNoChargeResponse() {
        var required = required(insertBudget("budget-a"), request(10), "10");
        var result = new PolicyAiResult(required.cost().request(), NOW.plusSeconds(2),
                new Unavailable(UnavailableReason.REQUEST_FAILED));
        var port = new ScriptedPort(new ResponseReceived(result, new ConfirmedNoCharge(
                new NoChargeConfirmation("no-charge-a", NOW.plusSeconds(3)))));

        var run = coordinator(port).execute("reservation-a", required, NOW,
                new Dispatch("dispatch-a", NOW.plusSeconds(1)));

        assertThat(run).isInstanceOfSatisfying(PolicyAiExecutionCoordinator.Responded.class, responded ->
                assertThat(responded.transition().decision())
                        .isEqualTo(AiBudgetReservationLifecycleStore.Decision.RELEASED_NO_CHARGE));
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase())
                .isEqualTo(Phase.RELEASED_NO_CHARGE);
        assertThat(budgetAmounts("budget-a")).isEqualTo(new BudgetAmounts(money("0"), money("0")));
    }

    @Test
    @DisplayName("이미 호출을 기록한 예약은 인공 AI를 다시 실행하지 않는다")
    void doesNotRepeatAlreadyDispatchedExecution() {
        var required = required(insertBudget("budget-a"), request(10), "10");
        var port = new ScriptedPort(new ResponseReceived(
                generated(required.cost().request(), NOW.plusSeconds(2)), PendingCharge.INSTANCE));
        var coordinator = coordinator(port);
        var dispatch = new Dispatch("dispatch-a", NOW.plusSeconds(1));

        coordinator.execute("reservation-a", required, NOW, dispatch);
        var replay = coordinator.execute("reservation-a", required, NOW, dispatch);

        assertThat(replay).isInstanceOfSatisfying(PolicyAiExecutionCoordinator.NotStarted.class, stopped -> {
            assertThat(stopped.reason()).isEqualTo(PolicyAiExecutionCoordinator.StopReason.ALREADY_DISPATCHED);
            assertThat(stopped.dispatch().orElseThrow().decision())
                    .isEqualTo(AiBudgetReservationLifecycleStore.Decision.REPLAYED);
        });
        assertThat(port.calls()).isOne();
        assertThat(budgetAmounts("budget-a")).isEqualTo(new BudgetAmounts(money("0"), money("10")));
    }

    @Test
    @DisplayName("예산 예약을 거절하면 인공 AI를 실행하지 않는다")
    void doesNotExecuteWhenReservationIsRejected() {
        var absent = balance("missing-budget");
        var required = required(absent, request(10), "10");
        var port = new ScriptedPort(new ResponseReceived(
                generated(required.cost().request(), NOW.plusSeconds(2)), PendingCharge.INSTANCE));

        var run = coordinator(port).execute("reservation-a", required, NOW,
                new Dispatch("dispatch-a", NOW.plusSeconds(1)));

        assertThat(run).isInstanceOfSatisfying(PolicyAiExecutionCoordinator.NotStarted.class, stopped -> {
            assertThat(stopped.reason()).isEqualTo(PolicyAiExecutionCoordinator.StopReason.RESERVATION_REJECTED);
            assertThat(stopped.reservation().decision()).isEqualTo(AiBudgetReservationStore.Decision.BUDGET_NOT_FOUND);
        });
        assertThat(port.calls()).isZero();
        assertThat(lifecycleStore.find("reservation-a")).isEmpty();
    }

    @Test
    @DisplayName("예상하지 못한 실행 오류와 다른 요청의 응답은 예약액을 보수적으로 유지한다")
    void preservesReservationForUnexpectedFailureAndMismatchedResponse() {
        var first = required(insertBudget("budget-a"), request(10), "10");
        PolicyAiExecutionPort throwing = invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            throw new IllegalStateException("인공 실행 오류");
        };

        assertThatThrownBy(() -> coordinator(throwing).execute("reservation-a", first, NOW,
                new Dispatch("dispatch-a", NOW.plusSeconds(1))))
                .isInstanceOf(IllegalStateException.class).hasMessage("인공 실행 오류");
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase()).isEqualTo(Phase.DISPATCHED);
        assertThat(budgetAmounts("budget-a")).isEqualTo(new BudgetAmounts(money("0"), money("10")));

        var second = required(insertBudget("budget-b"), request(20), "5");
        var mismatched = new ScriptedPort(new ResponseReceived(
                generated(request(30), NOW.plusSeconds(2)), PendingCharge.INSTANCE));
        assertThatThrownBy(() -> coordinator(mismatched).execute("reservation-b", second, NOW,
                new Dispatch("dispatch-b", NOW.plusSeconds(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI 실행 포트가 다른 요청의 결과를 반환했습니다.");
        assertThat(lifecycleStore.find("reservation-b").orElseThrow().phase()).isEqualTo(Phase.DISPATCHED);
        assertThat(budgetAmounts("budget-b")).isEqualTo(new BudgetAmounts(money("0"), money("5")));
    }

    private PolicyAiExecutionCoordinator coordinator(PolicyAiExecutionPort port) {
        return new PolicyAiExecutionCoordinator(reservationStore, lifecycleStore, port);
    }

    private Balance insertBudget(String budgetId) {
        var balance = balance(budgetId);
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

    private static Balance balance(String budgetId) {
        return new Balance(budgetId, NOW.minusSeconds(60), NOW.plusSeconds(60), money("100"),
                money("0"), money("0"));
    }

    private static ReservationRequired required(Balance balance, Request request, String maximumWon) {
        return new ReservationRequired(balance,
                new CostCeiling(request, "price-a", NOW.plusSeconds(30), money(maximumWon)));
    }

    private static Request request(long sequence) {
        var observation = new PolicyObservation("synthetic-policy", 1, NOW.minusSeconds(120), new Readable(
                new SnapshotReference("synthetic-source", "raw-1", "a".repeat(64)),
                new ContentFingerprint("comparison-a", "b".repeat(64))));
        var revision = PolicyRevisionState.empty("synthetic-policy").consider(observation)
                .state().currentRevision().orElseThrow();
        return new Request(revision, Kind.SUMMARY, "generation-a", sequence, NOW.minusSeconds(30));
    }

    private static PolicyAiResult generated(Request request, Instant recordedAt) {
        return new PolicyAiResult(request, recordedAt, new Generated("candidate-a", "c".repeat(64)));
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

    private static BigDecimal money(String amount) { return new BigDecimal(amount); }
    private static OffsetDateTime dbTime(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    private static final class ScriptedPort implements PolicyAiExecutionPort {
        private final Outcome outcome;
        private int calls;
        private boolean transactionActive;
        private Invocation invocation;

        private ScriptedPort(Outcome outcome) {
            this.outcome = outcome;
        }

        @Override
        public Outcome execute(Invocation invocation) {
            calls++;
            transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            this.invocation = invocation;
            return outcome;
        }

        private int calls() { return calls; }
        private boolean transactionActive() { return transactionActive; }
        private Invocation invocation() { return invocation; }
    }

    private record BudgetAmounts(BigDecimal confirmedWon, BigDecimal reservedWon) {
        private BudgetAmounts {
            confirmedWon = confirmedWon.stripTrailingZeros();
            reservedWon = reservedWon.stripTrailingZeros();
        }
    }
}
