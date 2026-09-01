package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationStore.Decision;
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
}
