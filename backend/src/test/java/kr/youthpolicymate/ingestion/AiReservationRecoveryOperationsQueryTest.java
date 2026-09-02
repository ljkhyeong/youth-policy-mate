package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationState.Cancellation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Dispatch;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Phase;
import kr.youthpolicymate.ingestion.AiRequestBudget.Balance;
import kr.youthpolicymate.ingestion.AiRequestBudget.CostCeiling;
import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Criteria;
import kr.youthpolicymate.ingestion.AiReservationRecoveryOperationsQuery.Scope;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Deferred;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.HoldReason;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Ready;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Schedule;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.StopReason;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Stopped;
import kr.youthpolicymate.ingestion.AiReservationRecoveryReviewStore.ResumeCommand;
import kr.youthpolicymate.ingestion.AiReservationRecoveryReviewStore.ResumeDecision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryReviewStore.ResumeReason;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Attempt;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Completion;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Lease;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.RecoveryResult;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.ReadyClaimOutcome;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.ReadyClaimed;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.ReadyClaimSkipped;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
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
class AiReservationRecoveryOperationsQueryTest {
    private static final Instant NOW = Instant.parse("2026-09-02T01:00:00Z");
    private static final Schedule SCHEDULE = new Schedule(
            3, List.of(Duration.ofSeconds(5), Duration.ofSeconds(20)));

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

    @Autowired
    AiReservationRecoveryReviewStore reviewStore;

    @Autowired
    AiReservationRecoveryOperationsQuery operationsQuery;

    @Autowired
    AiReservationRecoveryWorkAssigner workAssigner;

    @BeforeEach
    void setUp() {
        clearDatabase();
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    @Test
    @DisplayName("기준 시각 이전의 미완료 예약만 오래된 순서와 개수 제한에 맞춰 조회한다")
    void findsOnlyOldestStaleUnresolvedReservations() {
        reserve("old", "policy-old", Kind.SUMMARY, 10, at(0));
        lifecycleStore.dispatch("old", new Dispatch("dispatch-old", at(1)));
        reserve("middle", "policy-middle", Kind.CONDITION_EXTRACTION, 20, at(2));
        reserve("later", "policy-later", Kind.SUMMARY, 30, at(3));
        reserve("recent", "policy-recent", Kind.SUMMARY, 40, at(20));
        reserve("terminal", "policy-terminal", Kind.SUMMARY, 50, at(0));
        lifecycleStore.cancelBeforeDispatch("terminal", new Cancellation("cancel-terminal", at(1)));

        var report = operationsQuery.findOldestUnresolved(criteria(at(5), at(30), 2));

        assertThat(report.items()).extracting(item -> item.reservation().reservationId())
                .containsExactly("old", "middle");
        assertThat(report.items().getFirst().scope())
                .isEqualTo(new Scope("policy-old", Kind.SUMMARY, "generation-a", 10));
        assertThat(report.items().get(1).scope().kind()).isEqualTo(Kind.CONDITION_EXTRACTION);
        assertThat(report.items()).allSatisfy(item -> {
            assertThat(item.attempts()).isEmpty();
            assertThat(item.decision()).isInstanceOf(Ready.class);
        });
    }

    @Test
    @DisplayName("활성 임대의 작업자와 전체 이력을 보존하고 다음 확인을 보류한다")
    void reportsActiveLeaseWithoutChangingIt() {
        reserve("active", "policy-active", Kind.SUMMARY, 10, at(0));
        lifecycleStore.dispatch("active", new Dispatch("dispatch-active", at(1)));
        recoveryStore.claim("active", lease("attempt-active", at(2), at(20)));

        var report = operationsQuery.findOldestUnresolved(criteria(at(5), at(10), 10));

        assertThat(report.items()).singleElement().satisfies(item -> {
            assertThat(item.attempts()).singleElement().satisfies(attempt -> {
                assertThat(attempt.ownerId()).isEqualTo("worker-a");
                assertThat(attempt.status()).isEqualTo(Status.ACTIVE);
            });
            assertThat(item.decision()).isEqualTo(new Deferred(HoldReason.ACTIVE_LEASE, at(20)));
        });
        assertThat(recoveryStore.history("active")).singleElement()
                .satisfies(attempt -> assertThat(attempt.status()).isEqualTo(Status.ACTIVE));
    }

    @Test
    @DisplayName("확인 실패 뒤 재확인 간격이 남았으면 다시 볼 시각을 제공한다")
    void reportsRetryIntervalAfterFailedCheck() {
        reserve("retry", "policy-retry", Kind.SUMMARY, 10, at(0));
        lifecycleStore.dispatch("retry", new Dispatch("dispatch-retry", at(1)));
        recoveryStore.claim("retry", lease("attempt-retry", at(2), at(20)));
        recoveryStore.complete(new Completion("attempt-retry", "worker-a", at(4), RecoveryResult.CHECK_FAILED));

        var report = operationsQuery.findOldestUnresolved(criteria(at(5), at(8), 10));

        assertThat(report.items()).singleElement().satisfies(item ->
                assertThat(item.decision()).isEqualTo(new Deferred(HoldReason.RETRY_INTERVAL, at(9))));
    }

    @Test
    @DisplayName("수동 검토와 만료를 포함한 최대 시도 도달을 서로 다른 중단 사유로 조회한다")
    void distinguishesManualReviewAndMaximumAttempts() {
        reserve("manual", "policy-manual", Kind.SUMMARY, 10, at(0));
        lifecycleStore.dispatch("manual", new Dispatch("dispatch-manual", at(1)));
        recoveryStore.claim("manual", lease("attempt-manual", at(2), at(20)));
        recoveryStore.complete(new Completion(
                "attempt-manual", "worker-a", at(3), RecoveryResult.MANUAL_REVIEW_REQUIRED));

        reserve("maximum", "policy-maximum", Kind.SUMMARY, 20, at(0));
        lifecycleStore.dispatch("maximum", new Dispatch("dispatch-maximum", at(1)));
        recoveryStore.claim("maximum", lease("attempt-max-1", at(2), at(5)));
        recoveryStore.claim("maximum", lease("attempt-max-2", at(6), at(20)));
        recoveryStore.complete(new Completion("attempt-max-2", "worker-a", at(7), RecoveryResult.CHECK_FAILED));
        recoveryStore.claim("maximum", lease("attempt-max-3", at(8), at(30)));
        recoveryStore.complete(new Completion("attempt-max-3", "worker-a", at(9), RecoveryResult.CHECK_FAILED));

        var report = operationsQuery.findOldestUnresolved(criteria(at(10), at(40), 10));

        assertThat(report.items()).anySatisfy(item -> {
            assertThat(item.reservation().reservationId()).isEqualTo("manual");
            assertThat(item.decision()).isEqualTo(new Stopped(StopReason.MANUAL_REVIEW_REQUIRED, 1));
        }).anySatisfy(item -> {
            assertThat(item.reservation().reservationId()).isEqualTo("maximum");
            assertThat(item.attempts()).extracting(Attempt::status)
                    .containsExactly(Status.EXPIRED, Status.COMPLETED, Status.COMPLETED);
            assertThat(item.decision()).isEqualTo(new Stopped(StopReason.MAXIMUM_ATTEMPTS_REACHED, 3));
        });
    }

    @Test
    @DisplayName("수동 검토 재개 이력을 조회하고 재개 시각부터 간격이 지나면 다음 배정을 허용한다")
    void reportsAndAssignsResumedManualReview() {
        reserve("manual", "policy-manual", Kind.SUMMARY, 10, at(0));
        lifecycleStore.dispatch("manual", new Dispatch("dispatch-manual", at(1)));
        recoveryStore.claim("manual", lease("attempt-manual", at(2), at(20)));
        recoveryStore.complete(new Completion(
                "attempt-manual", "worker-a", at(3), RecoveryResult.MANUAL_REVIEW_REQUIRED));
        assertThat(reviewStore.resume(new ResumeCommand(
                "resume-manual", "manual", "attempt-manual", "operator-a",
                ResumeReason.SUPPLIER_STATE_VERIFIED, Phase.DISPATCHED, at(1), at(5))).decision())
                .isEqualTo(ResumeDecision.RESUMED);

        var deferred = operationsQuery.findOldestUnresolved(criteria(at(1), at(9), 10));
        assertThat(deferred.items()).singleElement().satisfies(item -> {
            assertThat(item.reviewResumes()).singleElement().satisfies(resume ->
                    assertThat(resume.resumeId()).isEqualTo("resume-manual"));
            assertThat(item.decision()).isEqualTo(new Deferred(HoldReason.RETRY_INTERVAL, at(10)));
        });

        var ready = operationsQuery.findOldestUnresolved(criteria(at(1), at(10), 10));
        var outcome = workAssigner.assign(
                ready, ready.items().getFirst(), lease("attempt-after-resume", at(10), at(30)));

        assertThat(outcome).isInstanceOf(ReadyClaimed.class);
        assertThat(recoveryStore.history("manual")).extracting(Attempt::attemptNumber)
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("운영 조회 기준 시각과 조회 개수의 잘못된 입력을 거절한다")
    void rejectsInvalidCriteria() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Criteria(SCHEDULE, at(10), at(9), 10));
        assertThatIllegalArgumentException().isThrownBy(() -> new Criteria(SCHEDULE, at(10), at(10), 0));
    }

    @Test
    @DisplayName("운영 조회에서 준비된 후보를 현재 상태로 다시 확인하고 소유권을 배정한다")
    void assignsReadyCandidateAfterRecheck() {
        reserve("ready", "policy-ready", Kind.SUMMARY, 10, at(0));
        var report = operationsQuery.findOldestUnresolved(criteria(at(0), at(5), 10));
        var lease = lease("attempt-ready", at(5), at(20));

        var outcome = workAssigner.assign(report, report.items().getFirst(), lease);

        assertThat(outcome).isEqualTo(new ReadyClaimed(
                AiReservationRecoveryStore.ClaimDecision.CLAIMED,
                recoveryStore.history("ready").getFirst()));
        assertThat(recoveryStore.history("ready").getFirst().attemptNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("운영 조회에서 보류된 후보는 소유권 획득을 시도하지 않는다")
    void doesNotAssignCandidateDeferredByReport() {
        reserve("deferred", "policy-deferred", Kind.SUMMARY, 10, at(0));
        recoveryStore.claim("deferred", lease("attempt-active", at(1), at(20)));
        var report = operationsQuery.findOldestUnresolved(criteria(at(0), at(5), 10));

        var outcome = workAssigner.assign(
                report, report.items().getFirst(), lease("attempt-new", at(5), at(30)));

        assertThat(outcome).isEqualTo(new ReadyClaimSkipped(
                new Deferred(HoldReason.ACTIVE_LEASE, at(20))));
        assertThat(recoveryStore.history("deferred")).hasSize(1);
    }

    @Test
    @DisplayName("조회 뒤 수동 검토가 기록되면 잠근 현재 이력으로 다시 확인해 배정을 중단한다")
    void rechecksManualReviewRecordedAfterReport() {
        reserve("changed", "policy-changed", Kind.SUMMARY, 10, at(0));
        var report = operationsQuery.findOldestUnresolved(criteria(at(0), at(5), 10));
        recoveryStore.claim("changed", lease("attempt-manual", at(1), at(4)));
        recoveryStore.complete(new Completion(
                "attempt-manual", "worker-a", at(2), RecoveryResult.MANUAL_REVIEW_REQUIRED));

        var outcome = workAssigner.assign(
                report, report.items().getFirst(), lease("attempt-new", at(5), at(30)));

        assertThat(outcome).isEqualTo(new ReadyClaimSkipped(
                new Stopped(StopReason.MANUAL_REVIEW_REQUIRED, 1)));
        assertThat(recoveryStore.history("changed")).hasSize(1);
    }

    @Test
    @DisplayName("같은 후보를 동시에 배정해도 한 작업자만 소유권을 얻는다")
    void assignsCandidateOnceUnderConcurrency() throws Exception {
        reserve("concurrent", "policy-concurrent", Kind.SUMMARY, 10, at(0));
        var report = operationsQuery.findOldestUnresolved(criteria(at(0), at(5), 10));
        var candidate = report.items().getFirst();
        var start = new CountDownLatch(1);
        List<ReadyClaimOutcome> outcomes;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return workAssigner.assign(report, candidate,
                        new Lease("attempt-first", "worker-first", at(5), at(20)));
            });
            var second = executor.submit(() -> {
                start.await();
                return workAssigner.assign(report, candidate,
                        new Lease("attempt-second", "worker-second", at(5), at(20)));
            });
            start.countDown();
            outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }

        assertThat(outcomes).filteredOn(ReadyClaimed.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(ReadyClaimSkipped.class::isInstance).hasSize(1);
        assertThat(recoveryStore.history("concurrent")).singleElement()
                .satisfies(attempt -> assertThat(attempt.status()).isEqualTo(Status.ACTIVE));
    }

    @Test
    @DisplayName("같은 배정 요청을 다시 보내면 새 시도 없이 기존 소유권을 재전달한다")
    void replaysSameAssignment() {
        reserve("replay", "policy-replay", Kind.SUMMARY, 10, at(0));
        var report = operationsQuery.findOldestUnresolved(criteria(at(0), at(5), 10));
        var lease = lease("attempt-replay", at(5), at(20));
        workAssigner.assign(report, report.items().getFirst(), lease);

        var replay = workAssigner.assign(report, report.items().getFirst(), lease);

        assertThat(replay).isInstanceOfSatisfying(ReadyClaimed.class, claimed ->
                assertThat(claimed.decision()).isEqualTo(AiReservationRecoveryStore.ClaimDecision.REPLAYED));
        assertThat(recoveryStore.history("replay")).hasSize(1);
    }

    private void reserve(String reservationId, String policyId, Kind kind, long sequence, Instant reservedAt) {
        String budgetId = "budget-" + reservationId;
        var balance = insertBudget(budgetId);
        var request = request(policyId, kind, sequence);
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

    private static Request request(String policyId, Kind kind, long sequence) {
        var observation = new PolicyObservation(policyId, sequence, NOW.minusSeconds(250), new Readable(
                new SnapshotReference("synthetic-source", "raw-" + sequence, "a".repeat(64)),
                new ContentFingerprint("comparison-a", "b".repeat(64))));
        var revision = PolicyRevisionState.empty(policyId).consider(observation)
                .state().currentRevision().orElseThrow();
        return new Request(revision, kind, "generation-a", sequence, NOW.minusSeconds(200));
    }

    private static Criteria criteria(Instant staleAtOrBefore, Instant evaluatedAt, int limit) {
        return new Criteria(SCHEDULE, staleAtOrBefore, evaluatedAt, limit);
    }

    private static Lease lease(String attemptId, Instant claimedAt, Instant leaseUntil) {
        return new Lease(attemptId, "worker-a", claimedAt, leaseUntil);
    }

    private void clearDatabase() {
        jdbcClient.sql("delete from ai_reservation_recovery_review_resumes").update();
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
}
