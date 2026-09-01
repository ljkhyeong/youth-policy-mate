package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationState.ChargeConfirmation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Cancellation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Dispatch;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.NoChargeConfirmation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Phase;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.UncertainOutcome;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.UncertainReason;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.RecoveryFence;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Profile("!preview")
public class AiBudgetReservationLifecycleStore {
    private final JdbcClient jdbcClient;

    public AiBudgetReservationLifecycleStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // 외부 호출은 이 메서드 밖에서 수행한다. 이 메서드는 외부 호출에 사용할 식별 정보만 짧게 기록한다.
    @Transactional
    public Transition dispatch(String reservationId, Dispatch dispatch) {
        requireReservationId(reservationId);
        Objects.requireNonNull(dispatch, "외부 호출 식별 정보가 필요합니다.");
        var locked = lock(reservationId);
        if (locked.isEmpty()) return missing();
        var current = locked.orElseThrow().reservation();

        if (current.phase() == Phase.HELD) {
            requireNotBefore(dispatch.dispatchedAt(), current.reservedAt(), "외부 호출은 예약보다 빠를 수 없습니다.");
            updateReservation(reservationId, """
                    phase = 'DISPATCHED', dispatch_id = :eventId, dispatched_at = :eventAt,
                    updated_at = :eventAt
                    """, dispatch.dispatchId(), dispatch.dispatchedAt());
            return result(Decision.DISPATCHED, reservationId);
        }
        if (current.dispatch().isPresent()) {
            return unchanged(sameDispatch(current.dispatch().orElseThrow(), dispatch)
                    ? Decision.REPLAYED : Decision.DISPATCH_CONFLICT, current);
        }
        return unchanged(Decision.INVALID_STATE, current);
    }

    @Transactional
    public Transition markOutcomeUnknown(String reservationId, UncertainOutcome uncertain) {
        requireReservationId(reservationId);
        Objects.requireNonNull(uncertain, "결과 미확인 정보가 필요합니다.");
        var locked = lock(reservationId);
        if (locked.isEmpty()) return missing();
        var current = locked.orElseThrow().reservation();

        if (current.phase() == Phase.DISPATCHED) {
            requireNotBefore(uncertain.recordedAt(), current.dispatch().orElseThrow().dispatchedAt(),
                    "결과 미확인은 외부 호출보다 빠를 수 없습니다.");
            int updated = jdbcClient.sql("""
                    update ai_request_reservations
                    set phase = 'OUTCOME_UNKNOWN',
                        uncertain_observation_id = :observationId,
                        uncertain_recorded_at = :recordedAt,
                        uncertain_reason = :reason,
                        updated_at = :recordedAt
                    where reservation_id = :reservationId
                    """)
                    .param("observationId", uncertain.observationId())
                    .param("recordedAt", dbTime(uncertain.recordedAt()))
                    .param("reason", uncertain.reason().name())
                    .param("reservationId", reservationId)
                    .update();
            requireSingleUpdate(updated);
            return result(Decision.OUTCOME_UNKNOWN, reservationId);
        }
        if (current.phase() == Phase.OUTCOME_UNKNOWN) {
            return unchanged(sameUncertainty(current.uncertain().orElseThrow(), uncertain)
                    ? Decision.REPLAYED : Decision.OUTCOME_ALREADY_UNKNOWN, current);
        }
        return unchanged(isTerminal(current.phase()) ? Decision.TERMINAL_CONFLICT : Decision.INVALID_STATE, current);
    }

    @Transactional
    public Transition settle(String reservationId, ChargeConfirmation confirmation) {
        return settle(reservationId, confirmation, Optional.empty());
    }

    @Transactional
    public Transition settleUnderRecovery(String reservationId, ChargeConfirmation confirmation,
                                          RecoveryFence fence) {
        Objects.requireNonNull(fence, "AI 예약 복구 펜싱 정보가 필요합니다.");
        return settle(reservationId, confirmation, Optional.of(fence));
    }

    private Transition settle(String reservationId, ChargeConfirmation confirmation,
                              Optional<RecoveryFence> fence) {
        requireReservationId(reservationId);
        Objects.requireNonNull(confirmation, "청구 확인 정보가 필요합니다.");
        var locked = lock(reservationId);
        if (locked.isEmpty()) return missing();
        var rows = locked.orElseThrow();
        var current = rows.reservation();
        var fenceRejection = rejectFence(current, fence);
        if (fenceRejection.isPresent()) return unchanged(fenceRejection.orElseThrow(), current);

        if (current.phase() == Phase.SETTLED) {
            return unchanged(sameCompletion(current, confirmation.confirmationId(), confirmation.confirmedAt())
                    && sameMoney(current.actualWon().orElseThrow(), confirmation.actualWon())
                    ? Decision.REPLAYED : Decision.TERMINAL_CONFLICT, current);
        }
        if (isTerminal(current.phase())) return unchanged(Decision.TERMINAL_CONFLICT, current);
        if (current.dispatch().isEmpty()) return unchanged(Decision.INVALID_STATE, current);
        requireNotBefore(confirmation.confirmedAt(), lastActivityAt(current),
                "청구 확인은 현재 예약 상태보다 빠를 수 없습니다.");
        if (rows.budget().reservedWon().compareTo(current.maximumWon()) < 0) {
            return unchanged(Decision.BUDGET_INCONSISTENT, current);
        }

        updateBudget(rows.budget(), current.maximumWon(), confirmation.actualWon(), confirmation.confirmedAt());
        int updated = jdbcClient.sql("""
                update ai_request_reservations
                set phase = 'SETTLED', completion_id = :completionId, completed_at = :completedAt,
                    actual_won = :actualWon, updated_at = :completedAt
                where reservation_id = :reservationId
                """)
                .param("completionId", confirmation.confirmationId())
                .param("completedAt", dbTime(confirmation.confirmedAt()))
                .param("actualWon", confirmation.actualWon())
                .param("reservationId", reservationId)
                .update();
        requireSingleUpdate(updated);
        return result(confirmation.actualWon().compareTo(current.maximumWon()) > 0
                ? Decision.SETTLED_OVER_RESERVATION : Decision.SETTLED, reservationId);
    }

    @Transactional
    public Transition cancelBeforeDispatch(String reservationId, Cancellation cancellation) {
        return cancelBeforeDispatch(reservationId, cancellation, Optional.empty());
    }

    @Transactional
    public Transition cancelBeforeDispatchUnderRecovery(String reservationId, Cancellation cancellation,
                                                        RecoveryFence fence) {
        Objects.requireNonNull(fence, "AI 예약 복구 펜싱 정보가 필요합니다.");
        return cancelBeforeDispatch(reservationId, cancellation, Optional.of(fence));
    }

    private Transition cancelBeforeDispatch(String reservationId, Cancellation cancellation,
                                            Optional<RecoveryFence> fence) {
        requireReservationId(reservationId);
        Objects.requireNonNull(cancellation, "호출 전 취소 정보가 필요합니다.");
        var locked = lock(reservationId);
        if (locked.isEmpty()) return missing();
        var rows = locked.orElseThrow();
        var current = rows.reservation();
        var fenceRejection = rejectFence(current, fence);
        if (fenceRejection.isPresent()) return unchanged(fenceRejection.orElseThrow(), current);

        if (current.phase() == Phase.CANCELLED) {
            return unchanged(sameCompletion(current, cancellation.cancellationId(), cancellation.cancelledAt())
                    ? Decision.REPLAYED : Decision.TERMINAL_CONFLICT, current);
        }
        if (current.phase() != Phase.HELD) {
            return unchanged(isTerminal(current.phase()) ? Decision.TERMINAL_CONFLICT : Decision.INVALID_STATE, current);
        }
        requireNotBefore(cancellation.cancelledAt(), current.reservedAt(), "호출 전 취소는 예약보다 빠를 수 없습니다.");
        if (rows.budget().reservedWon().compareTo(current.maximumWon()) < 0) {
            return unchanged(Decision.BUDGET_INCONSISTENT, current);
        }

        updateBudget(rows.budget(), current.maximumWon(), BigDecimal.ZERO, cancellation.cancelledAt());
        updateReservation(reservationId, """
                phase = 'CANCELLED', completion_id = :eventId, completed_at = :eventAt,
                updated_at = :eventAt
                """, cancellation.cancellationId(), cancellation.cancelledAt());
        return result(Decision.RELEASED_BEFORE_DISPATCH, reservationId);
    }

    @Transactional
    public Transition releaseAfterNoCharge(String reservationId, NoChargeConfirmation confirmation) {
        return releaseAfterNoCharge(reservationId, confirmation, Optional.empty());
    }

    @Transactional
    public Transition releaseAfterNoChargeUnderRecovery(String reservationId,
                                                        NoChargeConfirmation confirmation,
                                                        RecoveryFence fence) {
        Objects.requireNonNull(fence, "AI 예약 복구 펜싱 정보가 필요합니다.");
        return releaseAfterNoCharge(reservationId, confirmation, Optional.of(fence));
    }

    private Transition releaseAfterNoCharge(String reservationId, NoChargeConfirmation confirmation,
                                            Optional<RecoveryFence> fence) {
        requireReservationId(reservationId);
        Objects.requireNonNull(confirmation, "무과금 확인 정보가 필요합니다.");
        var locked = lock(reservationId);
        if (locked.isEmpty()) return missing();
        var rows = locked.orElseThrow();
        var current = rows.reservation();
        var fenceRejection = rejectFence(current, fence);
        if (fenceRejection.isPresent()) return unchanged(fenceRejection.orElseThrow(), current);

        if (current.phase() == Phase.RELEASED_NO_CHARGE) {
            return unchanged(sameCompletion(current, confirmation.confirmationId(), confirmation.confirmedAt())
                    ? Decision.REPLAYED : Decision.TERMINAL_CONFLICT, current);
        }
        if (isTerminal(current.phase())) return unchanged(Decision.TERMINAL_CONFLICT, current);
        if (current.dispatch().isEmpty()) return unchanged(Decision.INVALID_STATE, current);
        requireNotBefore(confirmation.confirmedAt(), lastActivityAt(current),
                "무과금 확인은 현재 예약 상태보다 빠를 수 없습니다.");
        if (rows.budget().reservedWon().compareTo(current.maximumWon()) < 0) {
            return unchanged(Decision.BUDGET_INCONSISTENT, current);
        }

        updateBudget(rows.budget(), current.maximumWon(), BigDecimal.ZERO, confirmation.confirmedAt());
        updateReservation(reservationId, """
                phase = 'RELEASED_NO_CHARGE', completion_id = :eventId, completed_at = :eventAt,
                updated_at = :eventAt
                """, confirmation.confirmationId(), confirmation.confirmedAt());
        return result(Decision.RELEASED_NO_CHARGE, reservationId);
    }

    @Transactional(readOnly = true)
    public Optional<Snapshot> find(String reservationId) {
        requireReservationId(reservationId);
        return findById(reservationId);
    }

    @Transactional(readOnly = true)
    public List<Snapshot> unresolved(int limit) {
        if (limit <= 0) throw new IllegalArgumentException("조회할 미완료 예약 수는 1 이상이어야 합니다.");
        return jdbcClient.sql(reservationSelect() + """
                where phase in ('HELD', 'DISPATCHED', 'OUTCOME_UNKNOWN')
                order by updated_at, reservation_id
                limit :limit
                """)
                .param("limit", limit)
                .query(AiBudgetReservationLifecycleStore::snapshot)
                .list();
    }

    private Optional<LockedRows> lock(String reservationId) {
        var budgetId = jdbcClient.sql("""
                select budget_id from ai_request_reservations where reservation_id = :reservationId
                """)
                .param("reservationId", reservationId)
                .query(String.class)
                .optional();
        if (budgetId.isEmpty()) return Optional.empty();

        var budget = jdbcClient.sql("""
                select budget_id, reserved_won
                from ai_budgets
                where budget_id = :budgetId
                for update
                """)
                .param("budgetId", budgetId.orElseThrow())
                .query(AiBudgetReservationLifecycleStore::budgetRow)
                .optional()
                .orElseThrow(() -> new IllegalStateException("AI 요청 예약에 연결된 예산이 없습니다."));
        var reservation = jdbcClient.sql(reservationSelect() + """
                where reservation_id = :reservationId
                for update
                """)
                .param("reservationId", reservationId)
                .query(AiBudgetReservationLifecycleStore::snapshot)
                .optional();
        return reservation.map(snapshot -> new LockedRows(budget, snapshot));
    }

    private Optional<Decision> rejectFence(Snapshot current, Optional<RecoveryFence> optionalFence) {
        if (optionalFence.isEmpty()) return Optional.empty();
        var fence = optionalFence.orElseThrow();
        if (!current.reservationId().equals(fence.reservationId())) {
            return Optional.of(Decision.RECOVERY_ATTEMPT_CONFLICT);
        }

        var attempt = jdbcClient.sql("""
                select reservation_id, attempt_number, owner_id, status, claimed_at, lease_until
                from ai_reservation_recovery_attempts
                where attempt_id = :attemptId
                for update
                """)
                .param("attemptId", fence.attemptId())
                .query(AiBudgetReservationLifecycleStore::recoveryAttemptRow)
                .optional();
        if (attempt.isEmpty()) return Optional.of(Decision.RECOVERY_ATTEMPT_NOT_FOUND);

        var stored = attempt.orElseThrow();
        if (!stored.reservationId().equals(fence.reservationId())
                || stored.attemptNumber() != fence.attemptNumber()
                || !stored.ownerId().equals(fence.ownerId())) {
            return Optional.of(Decision.RECOVERY_ATTEMPT_CONFLICT);
        }
        if (stored.status() != AiReservationRecoveryStore.Status.ACTIVE) {
            return Optional.of(Decision.RECOVERY_ATTEMPT_INACTIVE);
        }
        if (fence.checkedAt().isBefore(stored.claimedAt())
                || !fence.checkedAt().isBefore(stored.leaseUntil())) {
            return Optional.of(Decision.RECOVERY_LEASE_EXPIRED);
        }
        if (current.phase() != fence.observedPhase()
                || !sameDatabaseInstant(current.updatedAt(), fence.observedUpdatedAt())) {
            return Optional.of(Decision.RECOVERY_RESERVATION_CHANGED);
        }
        return Optional.empty();
    }

    private void updateBudget(BudgetRow budget, BigDecimal releasedWon, BigDecimal confirmedWon, Instant at) {
        int updated = jdbcClient.sql("""
                update ai_budgets
                set confirmed_won = confirmed_won + :confirmedWon,
                    reserved_won = reserved_won - :releasedWon,
                    updated_at = :updatedAt
                where budget_id = :budgetId
                  and reserved_won >= :releasedWon
                """)
                .param("confirmedWon", confirmedWon)
                .param("releasedWon", releasedWon)
                .param("updatedAt", dbTime(at))
                .param("budgetId", budget.budgetId())
                .update();
        requireSingleUpdate(updated);
    }

    private void updateReservation(String reservationId, String assignments, String eventId, Instant eventAt) {
        int updated = jdbcClient.sql("update ai_request_reservations set " + assignments
                        + " where reservation_id = :reservationId")
                .param("eventId", eventId)
                .param("eventAt", dbTime(eventAt))
                .param("reservationId", reservationId)
                .update();
        requireSingleUpdate(updated);
    }

    private Transition result(Decision decision, String reservationId) {
        return new Transition(decision, findById(reservationId));
    }

    private static Transition unchanged(Decision decision, Snapshot current) {
        return new Transition(decision, Optional.of(current));
    }

    private static Transition missing() {
        return new Transition(Decision.RESERVATION_NOT_FOUND, Optional.empty());
    }

    private Optional<Snapshot> findById(String reservationId) {
        return jdbcClient.sql(reservationSelect() + " where reservation_id = :reservationId")
                .param("reservationId", reservationId)
                .query(AiBudgetReservationLifecycleStore::snapshot)
                .optional();
    }

    private static String reservationSelect() {
        return """
                select reservation_id, budget_id, phase, maximum_won, reserved_at,
                       dispatch_id, dispatched_at,
                       uncertain_observation_id, uncertain_recorded_at, uncertain_reason,
                       completion_id, completed_at, actual_won, updated_at
                from ai_request_reservations
                """;
    }

    private static Snapshot snapshot(ResultSet resultSet, int rowNumber) throws SQLException {
        var dispatchId = resultSet.getString("dispatch_id");
        var uncertaintyId = resultSet.getString("uncertain_observation_id");
        return new Snapshot(
                resultSet.getString("reservation_id"), resultSet.getString("budget_id"),
                Phase.valueOf(resultSet.getString("phase")), resultSet.getBigDecimal("maximum_won"),
                instant(resultSet, "reserved_at"),
                dispatchId == null ? Optional.empty()
                        : Optional.of(new Dispatch(dispatchId, instant(resultSet, "dispatched_at"))),
                uncertaintyId == null ? Optional.empty()
                        : Optional.of(new UncertainOutcome(uncertaintyId, instant(resultSet, "uncertain_recorded_at"),
                        UncertainReason.valueOf(resultSet.getString("uncertain_reason")))),
                Optional.ofNullable(resultSet.getString("completion_id")),
                nullableInstant(resultSet, "completed_at"),
                Optional.ofNullable(resultSet.getBigDecimal("actual_won")),
                instant(resultSet, "updated_at"));
    }

    private static BudgetRow budgetRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BudgetRow(resultSet.getString("budget_id"), resultSet.getBigDecimal("reserved_won"));
    }

    private static RecoveryAttemptRow recoveryAttemptRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RecoveryAttemptRow(
                resultSet.getString("reservation_id"), resultSet.getLong("attempt_number"),
                resultSet.getString("owner_id"),
                AiReservationRecoveryStore.Status.valueOf(resultSet.getString("status")),
                instant(resultSet, "claimed_at"), instant(resultSet, "lease_until"));
    }

    private static Instant lastActivityAt(Snapshot current) {
        return current.uncertain().map(UncertainOutcome::recordedAt)
                .orElseGet(() -> current.dispatch().orElseThrow().dispatchedAt());
    }

    private static boolean sameDispatch(Dispatch left, Dispatch right) {
        return left.dispatchId().equals(right.dispatchId())
                && sameDatabaseInstant(left.dispatchedAt(), right.dispatchedAt());
    }

    private static boolean sameUncertainty(UncertainOutcome left, UncertainOutcome right) {
        return left.observationId().equals(right.observationId())
                && sameDatabaseInstant(left.recordedAt(), right.recordedAt())
                && left.reason() == right.reason();
    }

    private static boolean sameCompletion(Snapshot current, String id, Instant at) {
        return current.completionId().filter(id::equals).isPresent()
                && current.completedAt().filter(stored -> sameDatabaseInstant(stored, at)).isPresent();
    }

    private static boolean isTerminal(Phase phase) {
        return phase == Phase.SETTLED || phase == Phase.CANCELLED || phase == Phase.RELEASED_NO_CHARGE;
    }

    private static void requireNotBefore(Instant actual, Instant minimum, String message) {
        if (actual.isBefore(minimum)) throw new IllegalArgumentException(message);
    }

    private static void requireReservationId(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("AI 요청 예약 식별자가 필요합니다.");
        }
    }

    private static void requireSingleUpdate(int updated) {
        if (updated != 1) throw new IllegalStateException("잠근 AI 요청 예약 상태를 갱신하지 못했습니다.");
    }

    private static boolean sameMoney(BigDecimal left, BigDecimal right) { return left.compareTo(right) == 0; }
    private static boolean sameDatabaseInstant(Instant left, Instant right) {
        return left.truncatedTo(ChronoUnit.MICROS).equals(right.truncatedTo(ChronoUnit.MICROS));
    }
    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
    private static Optional<Instant> nullableInstant(ResultSet resultSet, String column) throws SQLException {
        var value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }
    private static OffsetDateTime dbTime(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    public enum Decision {
        REPLAYED, RESERVATION_NOT_FOUND, DISPATCHED, DISPATCH_CONFLICT, OUTCOME_UNKNOWN,
        OUTCOME_ALREADY_UNKNOWN, SETTLED, SETTLED_OVER_RESERVATION, RELEASED_BEFORE_DISPATCH,
        RELEASED_NO_CHARGE, INVALID_STATE, TERMINAL_CONFLICT, BUDGET_INCONSISTENT,
        RECOVERY_ATTEMPT_NOT_FOUND, RECOVERY_ATTEMPT_CONFLICT, RECOVERY_ATTEMPT_INACTIVE,
        RECOVERY_LEASE_EXPIRED, RECOVERY_RESERVATION_CHANGED
    }

    public record Transition(Decision decision, Optional<Snapshot> reservation) {
        public Transition {
            Objects.requireNonNull(decision, "DB 예약 상태 전이 결과가 필요합니다.");
            Objects.requireNonNull(reservation, "DB 예약 조회 결과가 필요합니다.");
        }
    }

    public record Snapshot(
            String reservationId, String budgetId, Phase phase, BigDecimal maximumWon, Instant reservedAt,
            Optional<Dispatch> dispatch, Optional<UncertainOutcome> uncertain, Optional<String> completionId,
            Optional<Instant> completedAt, Optional<BigDecimal> actualWon, Instant updatedAt
    ) {
        public Snapshot {
            Objects.requireNonNull(reservationId, "AI 요청 예약 식별자가 필요합니다.");
            Objects.requireNonNull(budgetId, "AI 예산 식별자가 필요합니다.");
            Objects.requireNonNull(phase, "AI 요청 예약 단계가 필요합니다.");
            Objects.requireNonNull(maximumWon, "예약 최대 금액이 필요합니다.");
            Objects.requireNonNull(reservedAt, "예약 시각이 필요합니다.");
            Objects.requireNonNull(dispatch, "외부 호출 정보의 존재 여부가 필요합니다.");
            Objects.requireNonNull(uncertain, "결과 미확인 정보의 존재 여부가 필요합니다.");
            Objects.requireNonNull(completionId, "완료 식별자의 존재 여부가 필요합니다.");
            Objects.requireNonNull(completedAt, "완료 시각의 존재 여부가 필요합니다.");
            Objects.requireNonNull(actualWon, "실제 비용의 존재 여부가 필요합니다.");
            Objects.requireNonNull(updatedAt, "예약 상태 갱신 시각이 필요합니다.");
        }
    }

    private record LockedRows(BudgetRow budget, Snapshot reservation) {}
    private record BudgetRow(String budgetId, BigDecimal reservedWon) {}
    private record RecoveryAttemptRow(
            String reservationId, long attemptNumber, String ownerId, AiReservationRecoveryStore.Status status,
            Instant claimedAt, Instant leaseUntil
    ) {}
}
