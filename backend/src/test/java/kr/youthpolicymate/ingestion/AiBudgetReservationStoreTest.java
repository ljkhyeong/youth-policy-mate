package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationStore.Decision;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.ChargeConfirmation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Cancellation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Dispatch;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.NoChargeConfirmation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Phase;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.UncertainOutcome;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.UncertainReason;
import kr.youthpolicymate.ingestion.AiRequestBudget.Balance;
import kr.youthpolicymate.ingestion.AiRequestBudget.CostCeiling;
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

import static kr.youthpolicymate.ingestion.AiBudgetReservationStore.Decision.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest
class AiBudgetReservationStoreTest {
    private static final Instant NOW = Instant.parse("2026-09-01T01:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    AiBudgetReservationStore store;

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
    @DisplayName("Flyway 테이블에 요청 근거를 저장하고 예산 예약액을 같은 트랜잭션에서 갱신한다")
    void reservesInPostgres() {
        var balance = insertBudget("budget-a", "100", "20", "5");
        var required = required(balance, request(10), "10.25");

        var attempt = store.reserve("reservation-a", required, NOW);

        assertThat(attempt.decision()).isEqualTo(RESERVED);
        assertThat(attempt.balance().orElseThrow().reservedWon()).isEqualByComparingTo("15.25");
        assertThat(jdbcClient.sql("select reserved_won from ai_budgets where budget_id = 'budget-a'")
                .query(BigDecimal.class).single()).isEqualByComparingTo("15.25");
        assertThat(jdbcClient.sql("""
                select policy_id, source_revision_number, source_collection_sequence, ai_kind,
                       generation_version, request_sequence, pricing_version, maximum_won, phase
                from ai_request_reservations
                where reservation_id = 'reservation-a'
                """).query((resultSet, rowNumber) -> List.of(
                        resultSet.getString("policy_id"),
                        resultSet.getString("source_revision_number"),
                        resultSet.getString("source_collection_sequence"),
                        resultSet.getString("ai_kind"),
                        resultSet.getString("generation_version"),
                        resultSet.getString("request_sequence"),
                        resultSet.getString("pricing_version"),
                        resultSet.getBigDecimal("maximum_won").stripTrailingZeros().toPlainString(),
                        resultSet.getString("phase"))).single())
                .containsExactly("synthetic-policy", "1", "1", "SUMMARY", "generation-a", "10",
                        "price-a", "10.25", "HELD");
    }

    @Test
    @DisplayName("같은 예약은 재생하고 요청 중복과 예약 식별자 충돌은 예약액을 늘리지 않는다")
    void keepsReservationIdempotent() {
        var balance = insertBudget("budget-a", "100", "0", "0");
        var first = required(balance, request(10), "10");
        assertThat(store.reserve("reservation-a", first, NOW).decision()).isEqualTo(RESERVED);

        assertThat(store.reserve("reservation-a", first, NOW.plusSeconds(1)).decision()).isEqualTo(REPLAYED);
        assertThat(store.reserve("reservation-b", first, NOW.plusSeconds(1)).decision())
                .isEqualTo(REQUEST_ALREADY_RESERVED);
        assertThat(store.reserve("reservation-a", required(balance, request(20), "10"), NOW.plusSeconds(1)).decision())
                .isEqualTo(RESERVATION_ID_CONFLICT);
        assertThat(jdbcClient.sql("select count(*) from ai_request_reservations").query(Long.class).single()).isOne();
        assertThat(jdbcClient.sql("select reserved_won from ai_budgets where budget_id = 'budget-a'")
                .query(BigDecimal.class).single()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("DB의 최신 잔액과 다른 판단, 없는 예산과 부족한 잔액을 구분한다")
    void rejectsMissingStaleAndInsufficientBudget() {
        var absent = balance("missing", "100", "0", "0");
        assertThat(store.reserve("missing", required(absent, request(10), "1"), NOW).decision())
                .isEqualTo(BUDGET_NOT_FOUND);

        var current = insertBudget("budget-a", "100", "0", "20");
        var stale = balance("budget-a", "100", "0", "0");
        assertThat(store.reserve("stale", required(stale, request(10), "1"), NOW).decision())
                .isEqualTo(STALE_BALANCE);
        assertThat(store.reserve("limit", required(current, request(20), "80.01"), NOW).decision())
                .isEqualTo(BUDGET_LIMIT);
        assertThat(jdbcClient.sql("select count(*) from ai_request_reservations").query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("예약 시점에 예산 시작·종료와 비용 유효기간을 다시 확인한다")
    void rechecksReservationTimeBoundaries() {
        var balance = insertBudget("budget-a", "100", "0", "0");
        var startsAt = NOW.plusSeconds(1);
        jdbcClient.sql("update ai_budgets set starts_at = :startsAt where budget_id = :budgetId")
                .param("startsAt", dbTime(startsAt)).param("budgetId", balance.budgetId()).update();
        var future = new Balance(balance.budgetId(), startsAt, balance.endsAt(), balance.limitWon(),
                balance.confirmedWon(), balance.reservedWon());
        assertThat(store.reserve("before-budget", required(future, request(10), "1"), NOW).decision())
                .isEqualTo(BUDGET_PERIOD_INACTIVE);
        assertThat(store.reserve("after-budget", required(future, request(10), "1"), future.endsAt()).decision())
                .isEqualTo(BUDGET_PERIOD_INACTIVE);

        var expired = new ReservationRequired(future,
                new CostCeiling(request(10), "price-a", startsAt, money("1")));
        assertThat(store.reserve("expired", expired, startsAt).decision()).isEqualTo(COST_EXPIRED);
        assertThat(jdbcClient.sql("select count(*) from ai_request_reservations").query(Long.class).single()).isZero();
        assertThat(budgetAmounts("budget-a").reservedWon()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("없는 예약·호출 전 상태 변경·역전된 시각과 음수 청구를 거절한다")
    void rejectsInvalidLifecycleOrderAndValues() {
        assertThat(lifecycleStore.dispatch("missing", new Dispatch("dispatch-a", NOW)).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.RESERVATION_NOT_FOUND);
        reserve("budget-a", "reservation-a", 10, "10");
        assertThat(lifecycleStore.markOutcomeUnknown("reservation-a",
                new UncertainOutcome("unknown-a", NOW.plusSeconds(1), UncertainReason.TIMEOUT)).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.INVALID_STATE);
        assertThat(lifecycleStore.settle("reservation-a", new ChargeConfirmation("charge-a", NOW, money("1"))).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.INVALID_STATE);
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ChargeConfirmation("charge-a", NOW, money("-0.01")));
        assertThatThrownBy(() -> lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.minusNanos(1))))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));
        assertThatThrownBy(() -> lifecycleStore.markOutcomeUnknown("reservation-a",
                new UncertainOutcome("unknown-a", NOW, UncertainReason.TIMEOUT)))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase()).isEqualTo(Phase.DISPATCHED);
        assertThat(budgetAmounts("budget-a").reservedWon()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("동시에 같은 잔액을 본 두 요청 중 하나만 예약한다")
    void serializesConcurrentReservations() throws Exception {
        var balance = insertBudget("budget-a", "100", "0", "0");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return store.reserve("reservation-a", required(balance, request(10), "60"), NOW).decision();
            });
            var second = executor.submit(() -> {
                start.await();
                return store.reserve("reservation-b", required(balance, request(20), "60"), NOW).decision();
            });
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(RESERVED, STALE_BALANCE);
        }
        assertThat(jdbcClient.sql("select count(*) from ai_request_reservations").query(Long.class).single()).isOne();
        assertThat(jdbcClient.sql("select reserved_won from ai_budgets where budget_id = 'budget-a'")
                .query(BigDecimal.class).single()).isEqualByComparingTo("60");
    }

    @Test
    @DisplayName("PostgreSQL 제약은 음수 예산과 잘못된 기간을 저장하지 않는다")
    void enforcesBudgetConstraints() {
        assertThatThrownBy(() -> jdbcClient.sql("""
                insert into ai_budgets (
                    budget_id, starts_at, ends_at, limit_won, confirmed_won, reserved_won, created_at, updated_at
                ) values ('negative', :startsAt, :endsAt, -1, 0, 0, :createdAt, :createdAt)
                """).param("startsAt", dbTime(NOW.minusSeconds(60))).param("endsAt", dbTime(NOW.plusSeconds(60)))
                .param("createdAt", dbTime(NOW)).update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcClient.sql("""
                insert into ai_budgets (
                    budget_id, starts_at, ends_at, limit_won, confirmed_won, reserved_won, created_at, updated_at
                ) values ('period', :at, :at, 1, 0, 0, :at, :at)
                """).param("at", dbTime(NOW)).update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("외부 호출 식별자를 기록하고 재전달과 충돌을 구분해 미완료 목록에서 조회한다")
    void recordsDispatchAndFindsUnresolvedReservation() {
        reserve("budget-a", "reservation-a", 10, "10");
        var dispatch = new Dispatch("dispatch-a", NOW.plusSeconds(1).plusNanos(123_456_789));

        assertThat(lifecycleStore.dispatch("reservation-a", dispatch).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.DISPATCHED);
        assertThat(lifecycleStore.dispatch("reservation-a", dispatch).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.REPLAYED);
        assertThat(lifecycleStore.dispatch("reservation-a",
                new Dispatch("dispatch-b", dispatch.dispatchedAt())).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.DISPATCH_CONFLICT);

        var unresolved = lifecycleStore.unresolved(10);
        assertThat(unresolved).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.phase()).isEqualTo(Phase.DISPATCHED);
            assertThat(snapshot.dispatch().orElseThrow().dispatchId()).isEqualTo("dispatch-a");
            assertThat(snapshot.actualWon()).isEmpty();
        });
    }

    @Test
    @DisplayName("결과 미확인은 예약액을 유지하고 같은 관찰 재전달과 다른 관찰을 구분한다")
    void keepsReservationWhileOutcomeIsUnknown() {
        reserve("budget-a", "reservation-a", 10, "10");
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));
        var uncertain = new UncertainOutcome("observation-a", NOW.plusSeconds(2).plusNanos(987_654_321),
                UncertainReason.TIMEOUT);

        assertThat(lifecycleStore.markOutcomeUnknown("reservation-a", uncertain).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.OUTCOME_UNKNOWN);
        assertThat(lifecycleStore.markOutcomeUnknown("reservation-a", uncertain).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.REPLAYED);
        assertThat(lifecycleStore.markOutcomeUnknown("reservation-a",
                new UncertainOutcome("observation-b", uncertain.recordedAt(), UncertainReason.TIMEOUT)).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.OUTCOME_ALREADY_UNKNOWN);

        assertThat(budgetAmounts("budget-a").reservedWon()).isEqualByComparingTo("10");
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().uncertain().orElseThrow().reason())
                .isEqualTo(UncertainReason.TIMEOUT);
    }

    @Test
    @DisplayName("확인된 실제 비용을 정산하고 예약 초과 비용도 사실대로 반영한다")
    void settlesConfirmedChargeIncludingOverReservation() {
        reserve("budget-a", "reservation-a", 10, "10");
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));
        lifecycleStore.markOutcomeUnknown("reservation-a", new UncertainOutcome(
                "observation-a", NOW.plusSeconds(2), UncertainReason.CONNECTION_LOST));
        var confirmation = new ChargeConfirmation("confirmation-a", NOW.plusSeconds(3), money("7.50"));

        assertThat(lifecycleStore.settle("reservation-a", confirmation).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.SETTLED);
        assertThat(lifecycleStore.settle("reservation-a", confirmation).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.REPLAYED);
        assertThat(lifecycleStore.releaseAfterNoCharge("reservation-a",
                new NoChargeConfirmation("no-charge-a", NOW.plusSeconds(4))).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.TERMINAL_CONFLICT);
        assertThat(lifecycleStore.settle("reservation-a",
                new ChargeConfirmation("other-charge", NOW.plusSeconds(4), money("8"))).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.TERMINAL_CONFLICT);
        assertThat(budgetAmounts("budget-a"))
                .isEqualTo(new BudgetAmounts(money("7.50"), money("0")));
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().uncertain()).isPresent();

        reserve("budget-b", "reservation-b", 20, "5");
        lifecycleStore.dispatch("reservation-b", new Dispatch("dispatch-b", NOW.plusSeconds(1)));
        assertThat(lifecycleStore.settle("reservation-b",
                new ChargeConfirmation("confirmation-b", NOW.plusSeconds(2), money("6.25"))).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.SETTLED_OVER_RESERVATION);
        assertThat(budgetAmounts("budget-b"))
                .isEqualTo(new BudgetAmounts(money("6.25"), money("0")));
    }

    @Test
    @DisplayName("외부 호출 전 취소만 예약액을 해제하고 이후 호출을 막는다")
    void cancelsOnlyBeforeDispatch() {
        reserve("budget-a", "reservation-a", 10, "10");
        var cancellation = new Cancellation("cancellation-a", NOW.plusSeconds(1));

        assertThat(lifecycleStore.cancelBeforeDispatch("reservation-a", cancellation).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.RELEASED_BEFORE_DISPATCH);
        assertThat(lifecycleStore.cancelBeforeDispatch("reservation-a", cancellation).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.REPLAYED);
        assertThat(lifecycleStore.dispatch("reservation-a",
                new Dispatch("dispatch-a", NOW.plusSeconds(2))).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.INVALID_STATE);
        assertThat(budgetAmounts("budget-a"))
                .isEqualTo(new BudgetAmounts(money("0"), money("0")));
    }

    @Test
    @DisplayName("명시적으로 무과금이 확인된 외부 호출만 예약액을 해제한다")
    void releasesOnlyAfterNoChargeConfirmation() {
        reserve("budget-a", "reservation-a", 10, "10");
        var confirmation = new NoChargeConfirmation("no-charge-a", NOW.plusSeconds(3));

        assertThat(lifecycleStore.releaseAfterNoCharge("reservation-a", confirmation).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.INVALID_STATE);
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));
        lifecycleStore.markOutcomeUnknown("reservation-a", new UncertainOutcome(
                "observation-a", NOW.plusSeconds(2), UncertainReason.PROVIDER_STATUS_UNAVAILABLE));
        assertThat(lifecycleStore.releaseAfterNoCharge("reservation-a", confirmation).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.RELEASED_NO_CHARGE);
        assertThat(lifecycleStore.releaseAfterNoCharge("reservation-a", confirmation).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.REPLAYED);
        assertThat(lifecycleStore.settle("reservation-a",
                new ChargeConfirmation("charge-a", NOW.plusSeconds(4), money("1"))).decision())
                .isEqualTo(AiBudgetReservationLifecycleStore.Decision.TERMINAL_CONFLICT);

        var snapshot = lifecycleStore.find("reservation-a").orElseThrow();
        assertThat(snapshot.phase()).isEqualTo(Phase.RELEASED_NO_CHARGE);
        assertThat(snapshot.uncertain()).isPresent();
        assertThat(budgetAmounts("budget-a"))
                .isEqualTo(new BudgetAmounts(money("0"), money("0")));
    }

    @Test
    @DisplayName("동시에 도착한 정산과 무과금 확인 중 하나만 예약액을 해제한다")
    void serializesConcurrentTerminalResults() throws Exception {
        reserve("budget-a", "reservation-a", 10, "10");
        lifecycleStore.dispatch("reservation-a", new Dispatch("dispatch-a", NOW.plusSeconds(1)));
        var start = new CountDownLatch(1);
        List<AiBudgetReservationLifecycleStore.Decision> decisions;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var settlement = executor.submit(() -> {
                start.await();
                return lifecycleStore.settle("reservation-a",
                        new ChargeConfirmation("charge-a", NOW.plusSeconds(2), money("7"))).decision();
            });
            var noCharge = executor.submit(() -> {
                start.await();
                return lifecycleStore.releaseAfterNoCharge("reservation-a",
                        new NoChargeConfirmation("no-charge-a", NOW.plusSeconds(2))).decision();
            });
            start.countDown();
            decisions = List.of(settlement.get(10, TimeUnit.SECONDS), noCharge.get(10, TimeUnit.SECONDS));
        }

        assertThat(decisions).contains(AiBudgetReservationLifecycleStore.Decision.TERMINAL_CONFLICT);
        var snapshot = lifecycleStore.find("reservation-a").orElseThrow();
        assertThat(snapshot.phase()).isIn(Phase.SETTLED, Phase.RELEASED_NO_CHARGE);
        assertThat(budgetAmounts("budget-a").reservedWon()).isEqualByComparingTo("0");
        if (snapshot.phase() == Phase.SETTLED) {
            assertThat(decisions).contains(AiBudgetReservationLifecycleStore.Decision.SETTLED);
            assertThat(budgetAmounts("budget-a").confirmedWon()).isEqualByComparingTo("7");
        } else {
            assertThat(decisions).contains(AiBudgetReservationLifecycleStore.Decision.RELEASED_NO_CHARGE);
            assertThat(budgetAmounts("budget-a").confirmedWon()).isEqualByComparingTo("0");
        }
    }

    @Test
    @DisplayName("PostgreSQL 제약은 단계에 필요한 외부 호출 정보를 누락할 수 없게 한다")
    void enforcesLifecycleFieldConstraints() {
        reserve("budget-a", "reservation-a", 10, "10");

        assertThatThrownBy(() -> jdbcClient.sql("""
                update ai_request_reservations set phase = 'DISPATCHED'
                where reservation_id = 'reservation-a'
                """).update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(lifecycleStore.find("reservation-a").orElseThrow().phase()).isEqualTo(Phase.HELD);
    }

    private Balance insertBudget(String budgetId, String limit, String confirmed, String reserved) {
        var balance = balance(budgetId, limit, confirmed, reserved);
        jdbcClient.sql("""
                insert into ai_budgets (
                    budget_id, starts_at, ends_at, limit_won, confirmed_won, reserved_won, created_at, updated_at
                ) values (:budgetId, :startsAt, :endsAt, :limitWon, :confirmedWon, :reservedWon, :createdAt, :createdAt)
                """)
                .param("budgetId", balance.budgetId())
                .param("startsAt", dbTime(balance.startsAt()))
                .param("endsAt", dbTime(balance.endsAt()))
                .param("limitWon", balance.limitWon())
                .param("confirmedWon", balance.confirmedWon())
                .param("reservedWon", balance.reservedWon())
                .param("createdAt", dbTime(NOW.minusSeconds(120)))
                .update();
        return balance;
    }

    private void reserve(String budgetId, String reservationId, long requestSequence, String maximum) {
        var balance = insertBudget(budgetId, "100", "0", "0");
        assertThat(store.reserve(reservationId, required(balance, request(requestSequence), maximum), NOW).decision())
                .isEqualTo(RESERVED);
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

    private static Balance balance(String budgetId, String limit, String confirmed, String reserved) {
        return new Balance(budgetId, NOW.minusSeconds(60).plusNanos(123_456_789),
                NOW.plusSeconds(60).plusNanos(987_654_321),
                money(limit), money(confirmed), money(reserved));
    }

    private static ReservationRequired required(Balance balance, Request request, String maximum) {
        return new ReservationRequired(balance,
                new CostCeiling(request, "price-a", NOW.plusSeconds(30), money(maximum)));
    }

    private static Request request(long sequence) {
        var observation = new PolicyObservation("synthetic-policy", 1,
                NOW.minusSeconds(120).plusNanos(123_456_789), new Readable(
                new SnapshotReference("synthetic-source", "raw-1", "a".repeat(64)),
                new ContentFingerprint("comparison-a", "b".repeat(64))));
        var revision = PolicyRevisionState.empty("synthetic-policy").consider(observation)
                .state().currentRevision().orElseThrow();
        return new Request(revision, Kind.SUMMARY, "generation-a", sequence,
                NOW.minusSeconds(30).plusNanos(987_654_321));
    }

    private static BigDecimal money(String amount) { return new BigDecimal(amount); }
    private static OffsetDateTime dbTime(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    private record BudgetAmounts(BigDecimal confirmedWon, BigDecimal reservedWon) {
        private BudgetAmounts {
            confirmedWon = confirmedWon.stripTrailingZeros();
            reservedWon = reservedWon.stripTrailingZeros();
        }
    }
}
