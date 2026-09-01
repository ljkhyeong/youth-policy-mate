package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationState.ChargeConfirmation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Dispatch;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Phase;
import kr.youthpolicymate.ingestion.AiRequestBudget.Balance;
import kr.youthpolicymate.ingestion.AiRequestBudget.CostCeiling;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Completion;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Lease;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.RecoveryResult;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Status;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest
class AiReservationRecoveryStoreTest {
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

    @Autowired
    AiReservationRecoveryStore recoveryStore;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("delete from ai_reservation_recovery_attempts").update();
        jdbcClient.sql("delete from ai_request_reservations").update();
        jdbcClient.sql("delete from ai_budgets").update();
    }

    @AfterEach
    void tearDown() {
        jdbcClient.sql("delete from ai_reservation_recovery_attempts").update();
        jdbcClient.sql("delete from ai_request_reservations").update();
        jdbcClient.sql("delete from ai_budgets").update();
    }

    @Test
    @DisplayName("미완료 예약의 복구 소유권을 획득하고 같은 요청을 재생한다")
    void claimsAndReplaysRecoveryLease() {
        reserve("budget-a", "reservation-a", 10, "10");
        var lease = lease("attempt-a", "worker-a", NOW.plusSeconds(1), NOW.plusSeconds(31));

        assertThat(recoveryStore.claim("reservation-a", lease).decision())
                .isEqualTo(AiReservationRecoveryStore.ClaimDecision.CLAIMED);
        assertThat(recoveryStore.claim("reservation-a", lease).decision())
                .isEqualTo(AiReservationRecoveryStore.ClaimDecision.REPLAYED);
        assertThat(recoveryStore.claim("reservation-a",
                lease("attempt-a", "worker-b", NOW.plusSeconds(1), NOW.plusSeconds(31))).decision())
                .isEqualTo(AiReservationRecoveryStore.ClaimDecision.ATTEMPT_ID_CONFLICT);

        assertThat(recoveryStore.history("reservation-a")).singleElement().satisfies(attempt -> {
            assertThat(attempt.attemptNumber()).isOne();
            assertThat(attempt.ownerId()).isEqualTo("worker-a");
            assertThat(attempt.claimedPhase()).isEqualTo(Phase.HELD);
            assertThat(attempt.status()).isEqualTo(Status.ACTIVE);
        });
    }

    @Test
    @DisplayName("활성 임대는 다른 작업자를 막고 만료 뒤 새 시도로 교체한다")
    void replacesExpiredLeaseWithNextAttempt() {
        reserve("budget-a", "reservation-a", 10, "10");
        recoveryStore.claim("reservation-a",
                lease("attempt-a", "worker-a", NOW.plusSeconds(1), NOW.plusSeconds(3)));

        assertThat(recoveryStore.claim("reservation-a",
                lease("attempt-b", "worker-b", NOW.plusSeconds(2), NOW.plusSeconds(32))).decision())
                .isEqualTo(AiReservationRecoveryStore.ClaimDecision.ALREADY_CLAIMED);
        assertThat(recoveryStore.claim("reservation-a",
                lease("attempt-b", "worker-b", NOW.plusSeconds(3), NOW.plusSeconds(33))).decision())
                .isEqualTo(AiReservationRecoveryStore.ClaimDecision.CLAIMED);

        assertThat(recoveryStore.history("reservation-a")).satisfiesExactly(first -> {
            assertThat(first.status()).isEqualTo(Status.EXPIRED);
            assertThat(first.completedPhase()).contains(Phase.HELD);
            assertThat(first.completedAt()).contains(NOW.plusSeconds(3));
        }, second -> {
            assertThat(second.attemptNumber()).isEqualTo(2);
            assertThat(second.status()).isEqualTo(Status.ACTIVE);
            assertThat(second.ownerId()).isEqualTo("worker-b");
        });
    }

    @Test
    @DisplayName("동시에 다음 예약을 요청한 두 작업자 중 하나만 소유권을 얻는다")
    void claimsNextReservationOnceUnderConcurrency() throws Exception {
        reserve("budget-a", "reservation-a", 10, "10");
        var start = new CountDownLatch(1);
        List<AiReservationRecoveryStore.ClaimDecision> decisions;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return recoveryStore.claimNext(
                        lease("attempt-a", "worker-a", NOW.plusSeconds(1), NOW.plusSeconds(31))).decision();
            });
            var second = executor.submit(() -> {
                start.await();
                return recoveryStore.claimNext(
                        lease("attempt-b", "worker-b", NOW.plusSeconds(1), NOW.plusSeconds(31))).decision();
            });
            start.countDown();
            decisions = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }

        assertThat(decisions).containsExactlyInAnyOrder(
                AiReservationRecoveryStore.ClaimDecision.CLAIMED,
                AiReservationRecoveryStore.ClaimDecision.NO_RESERVATION_AVAILABLE);
        assertThat(recoveryStore.history("reservation-a")).hasSize(1);
    }

    @Test
    @DisplayName("소유 기간 안에 확인을 마치면 시작 단계와 완료 단계를 함께 기록한다")
    void completesAttemptWithObservedPhase() {
        reserve("budget-a", "reservation-a", 10, "10");
        var lease = lease("attempt-a", "worker-a", NOW.plusSeconds(1), NOW.plusSeconds(31));
        recoveryStore.claim("reservation-a", lease);
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(2)));
        var completion = new Completion("attempt-a", "worker-a", NOW.plusSeconds(3),
                RecoveryResult.CHECK_COMPLETED);

        assertThat(recoveryStore.complete(completion).decision())
                .isEqualTo(AiReservationRecoveryStore.CompletionDecision.COMPLETED);
        assertThat(recoveryStore.complete(completion).decision())
                .isEqualTo(AiReservationRecoveryStore.CompletionDecision.REPLAYED);
        assertThat(recoveryStore.complete(new Completion("attempt-a", "worker-a", NOW.plusSeconds(3),
                RecoveryResult.CHECK_FAILED)).decision())
                .isEqualTo(AiReservationRecoveryStore.CompletionDecision.COMPLETION_CONFLICT);

        assertThat(recoveryStore.history("reservation-a")).singleElement().satisfies(attempt -> {
            assertThat(attempt.claimedPhase()).isEqualTo(Phase.HELD);
            assertThat(attempt.completedPhase()).contains(Phase.DISPATCHED);
            assertThat(attempt.result()).contains(RecoveryResult.CHECK_COMPLETED);
            assertThat(attempt.status()).isEqualTo(Status.COMPLETED);
        });
    }

    @Test
    @DisplayName("다른 작업자는 완료할 수 없고 임대 만료 시도는 만료로 기록한다")
    void rejectsWrongOwnerAndExpiredCompletion() {
        reserve("budget-a", "reservation-a", 10, "10");
        recoveryStore.claim("reservation-a",
                lease("attempt-a", "worker-a", NOW.plusSeconds(1), NOW.plusSeconds(3)));

        assertThat(recoveryStore.complete(new Completion("attempt-a", "worker-b", NOW.plusSeconds(2),
                RecoveryResult.CHECK_COMPLETED)).decision())
                .isEqualTo(AiReservationRecoveryStore.CompletionDecision.OWNER_CONFLICT);
        assertThat(recoveryStore.complete(new Completion("attempt-a", "worker-a", NOW.plusSeconds(3),
                RecoveryResult.CHECK_COMPLETED)).decision())
                .isEqualTo(AiReservationRecoveryStore.CompletionDecision.LEASE_EXPIRED);

        assertThat(recoveryStore.history("reservation-a")).singleElement().satisfies(attempt -> {
            assertThat(attempt.status()).isEqualTo(Status.EXPIRED);
            assertThat(attempt.result()).isEmpty();
            assertThat(attempt.completedPhase()).contains(Phase.HELD);
        });
    }

    @Test
    @DisplayName("종료된 예약은 직접 또는 다음 대상 조회로 다시 소유하지 않는다")
    void doesNotClaimTerminalReservation() {
        reserve("budget-a", "reservation-a", 10, "10");
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));
        lifecycleStore.settle("reservation-a",
                new ChargeConfirmation("charge-a", NOW.plusSeconds(2), money("7")));
        var lease = lease("attempt-a", "worker-a", NOW.plusSeconds(3), NOW.plusSeconds(33));

        assertThat(recoveryStore.claim("reservation-a", lease).decision())
                .isEqualTo(AiReservationRecoveryStore.ClaimDecision.RESERVATION_TERMINAL);
        assertThat(recoveryStore.claimNext(
                lease("attempt-b", "worker-b", NOW.plusSeconds(3), NOW.plusSeconds(33))).decision())
                .isEqualTo(AiReservationRecoveryStore.ClaimDecision.NO_RESERVATION_AVAILABLE);
        assertThat(recoveryStore.history("reservation-a")).isEmpty();
    }

    @Test
    @DisplayName("PostgreSQL 제약은 잘못된 복구 임대 기간을 저장하지 않는다")
    void enforcesRecoveryLeaseConstraints() {
        reserve("budget-a", "reservation-a", 10, "10");

        assertThatThrownBy(() -> jdbcClient.sql("""
                insert into ai_reservation_recovery_attempts (
                    attempt_id, reservation_id, attempt_number, owner_id, claimed_phase,
                    claimed_at, lease_until, status, created_at, updated_at
                ) values (
                    'invalid-attempt', 'reservation-a', 1, 'worker-a', 'HELD',
                    :at, :at, 'ACTIVE', :at, :at
                )
                """).param("at", dbTime(NOW.plusSeconds(1))).update())
                .isInstanceOf(DataIntegrityViolationException.class);
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
        var observation = new PolicyObservation("synthetic-policy", 1, NOW.minusSeconds(120), new Readable(
                new SnapshotReference("synthetic-source", "raw-1", "a".repeat(64)),
                new ContentFingerprint("comparison-a", "b".repeat(64))));
        var revision = PolicyRevisionState.empty("synthetic-policy").consider(observation)
                .state().currentRevision().orElseThrow();
        return new Request(revision, Kind.SUMMARY, "generation-a", sequence, NOW.minusSeconds(30));
    }

    private static Lease lease(String attemptId, String ownerId, Instant claimedAt, Instant leaseUntil) {
        return new Lease(attemptId, ownerId, claimedAt, leaseUntil);
    }

    private static BigDecimal money(String amount) { return new BigDecimal(amount); }
    private static OffsetDateTime dbTime(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
