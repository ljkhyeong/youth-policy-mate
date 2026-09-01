package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationState.Phase;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
public class AiReservationRecoveryStore {
    private final JdbcClient jdbcClient;

    public AiReservationRecoveryStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public ClaimOutcome claim(String reservationId, Lease lease) {
        requireText(reservationId, "복구할 AI 요청 예약 식별자가 필요합니다.");
        Objects.requireNonNull(lease, "AI 예약 복구 임대 정보가 필요합니다.");
        lockAttemptId(lease.attemptId());
        var existing = findByAttemptId(lease.attemptId());
        if (existing.isPresent()) return classifyExistingClaim(existing.orElseThrow(), reservationId, lease);

        var reservation = lockReservation(reservationId);
        if (reservation.isEmpty()) return claimOutcome(ClaimDecision.RESERVATION_NOT_FOUND, Optional.empty());
        return claimLocked(reservation.orElseThrow(), lease);
    }

    @Transactional
    public ClaimOutcome claimNext(Lease lease) {
        Objects.requireNonNull(lease, "AI 예약 복구 임대 정보가 필요합니다.");
        lockAttemptId(lease.attemptId());
        var existing = findByAttemptId(lease.attemptId());
        if (existing.isPresent()) return classifyExistingClaim(existing.orElseThrow(), null, lease);

        var reservation = jdbcClient.sql("""
                select reservation_id, phase, updated_at
                from ai_request_reservations reservation
                where phase in ('HELD', 'DISPATCHED', 'OUTCOME_UNKNOWN')
                  and updated_at <= :claimedAt
                  and not exists (
                      select 1
                      from ai_reservation_recovery_attempts attempt
                      where attempt.reservation_id = reservation.reservation_id
                        and attempt.status = 'ACTIVE'
                        and attempt.lease_until > :claimedAt
                  )
                order by updated_at, reservation_id
                for update of reservation skip locked
                limit 1
                """)
                .param("claimedAt", dbTime(lease.claimedAt()))
                .query(AiReservationRecoveryStore::reservationRow)
                .optional();
        if (reservation.isEmpty()) return claimOutcome(ClaimDecision.NO_RESERVATION_AVAILABLE, Optional.empty());
        return claimLocked(reservation.orElseThrow(), lease);
    }

    @Transactional
    public CompletionOutcome complete(Completion completion) {
        Objects.requireNonNull(completion, "AI 예약 복구 완료 정보가 필요합니다.");
        var reservationId = jdbcClient.sql("""
                select reservation_id
                from ai_reservation_recovery_attempts
                where attempt_id = :attemptId
                """)
                .param("attemptId", completion.attemptId())
                .query(String.class)
                .optional();
        if (reservationId.isEmpty()) {
            return completionOutcome(CompletionDecision.ATTEMPT_NOT_FOUND, Optional.empty());
        }

        var reservation = lockReservation(reservationId.orElseThrow())
                .orElseThrow(() -> new IllegalStateException("복구 시도에 연결된 AI 요청 예약이 없습니다."));
        var attempt = lockAttempt(completion.attemptId())
                .orElseThrow(() -> new IllegalStateException("잠글 AI 예약 복구 시도가 없습니다."));
        if (!attempt.ownerId().equals(completion.ownerId())) {
            return completionOutcome(CompletionDecision.OWNER_CONFLICT, Optional.of(attempt));
        }
        if (attempt.status() == Status.COMPLETED) {
            return completionOutcome(attempt.matches(completion)
                    ? CompletionDecision.REPLAYED : CompletionDecision.COMPLETION_CONFLICT, Optional.of(attempt));
        }
        if (attempt.status() == Status.EXPIRED) {
            return completionOutcome(CompletionDecision.LEASE_EXPIRED, Optional.of(attempt));
        }
        if (completion.completedAt().isBefore(attempt.claimedAt())) {
            throw new IllegalArgumentException("복구 완료는 소유권 획득보다 빠를 수 없습니다.");
        }
        if (completion.completedAt().isBefore(reservation.updatedAt())) {
            throw new IllegalArgumentException("복구 완료 시각보다 나중 상태의 예약을 확인할 수 없습니다.");
        }
        if (!completion.completedAt().isBefore(attempt.leaseUntil())) {
            expire(attempt, reservation, completion.completedAt());
            return completionOutcome(CompletionDecision.LEASE_EXPIRED,
                    findByAttemptId(attempt.attemptId()));
        }

        int updated = jdbcClient.sql("""
                update ai_reservation_recovery_attempts
                set status = 'COMPLETED', completed_phase = :completedPhase,
                    completed_at = :completedAt, result = :result, updated_at = :completedAt
                where attempt_id = :attemptId
                """)
                .param("completedPhase", reservation.phase().name())
                .param("completedAt", dbTime(completion.completedAt()))
                .param("result", completion.result().name())
                .param("attemptId", completion.attemptId())
                .update();
        requireSingleUpdate(updated);
        return completionOutcome(CompletionDecision.COMPLETED, findByAttemptId(completion.attemptId()));
    }

    @Transactional(readOnly = true)
    public List<Attempt> history(String reservationId) {
        requireText(reservationId, "조회할 AI 요청 예약 식별자가 필요합니다.");
        return jdbcClient.sql(attemptSelect() + """
                where reservation_id = :reservationId
                order by attempt_number
                """)
                .param("reservationId", reservationId)
                .query(AiReservationRecoveryStore::attempt)
                .list();
    }

    private ClaimOutcome claimLocked(ReservationRow reservation, Lease lease) {
        if (isTerminal(reservation.phase())) {
            return claimOutcome(ClaimDecision.RESERVATION_TERMINAL, Optional.empty());
        }
        if (lease.claimedAt().isBefore(reservation.updatedAt())) {
            throw new IllegalArgumentException("복구 소유권 획득은 현재 예약 상태보다 빠를 수 없습니다.");
        }

        var active = lockActiveAttempt(reservation.reservationId());
        if (active.isPresent()) {
            var current = active.orElseThrow();
            if (current.leaseUntil().isAfter(lease.claimedAt())) {
                return claimOutcome(ClaimDecision.ALREADY_CLAIMED, Optional.of(current));
            }
            expire(current, reservation, lease.claimedAt());
        }

        long attemptNumber = nextAttemptNumber(reservation.reservationId());
        int inserted = jdbcClient.sql("""
                insert into ai_reservation_recovery_attempts (
                    attempt_id, reservation_id, attempt_number, owner_id, claimed_phase,
                    claimed_at, lease_until, status, created_at, updated_at
                ) values (
                    :attemptId, :reservationId, :attemptNumber, :ownerId, :claimedPhase,
                    :claimedAt, :leaseUntil, 'ACTIVE', :claimedAt, :claimedAt
                )
                """)
                .param("attemptId", lease.attemptId())
                .param("reservationId", reservation.reservationId())
                .param("attemptNumber", attemptNumber)
                .param("ownerId", lease.ownerId())
                .param("claimedPhase", reservation.phase().name())
                .param("claimedAt", dbTime(lease.claimedAt()))
                .param("leaseUntil", dbTime(lease.leaseUntil()))
                .update();
        requireSingleUpdate(inserted);
        return claimOutcome(ClaimDecision.CLAIMED, findByAttemptId(lease.attemptId()));
    }

    private ClaimOutcome classifyExistingClaim(Attempt existing, String expectedReservationId, Lease lease) {
        boolean sameReservation = expectedReservationId == null || existing.reservationId().equals(expectedReservationId);
        return claimOutcome(sameReservation && existing.matches(lease)
                ? ClaimDecision.REPLAYED : ClaimDecision.ATTEMPT_ID_CONFLICT, Optional.of(existing));
    }

    private void expire(Attempt attempt, ReservationRow reservation, Instant expiredAt) {
        int updated = jdbcClient.sql("""
                update ai_reservation_recovery_attempts
                set status = 'EXPIRED', completed_phase = :completedPhase,
                    completed_at = :completedAt, updated_at = :completedAt
                where attempt_id = :attemptId and status = 'ACTIVE'
                """)
                .param("completedPhase", reservation.phase().name())
                .param("completedAt", dbTime(expiredAt))
                .param("attemptId", attempt.attemptId())
                .update();
        requireSingleUpdate(updated);
    }

    private Optional<ReservationRow> lockReservation(String reservationId) {
        return jdbcClient.sql("""
                select reservation_id, phase, updated_at
                from ai_request_reservations
                where reservation_id = :reservationId
                for update
                """)
                .param("reservationId", reservationId)
                .query(AiReservationRecoveryStore::reservationRow)
                .optional();
    }

    private Optional<Attempt> lockActiveAttempt(String reservationId) {
        return jdbcClient.sql(attemptSelect() + """
                where reservation_id = :reservationId and status = 'ACTIVE'
                for update
                """)
                .param("reservationId", reservationId)
                .query(AiReservationRecoveryStore::attempt)
                .optional();
    }

    private Optional<Attempt> lockAttempt(String attemptId) {
        return jdbcClient.sql(attemptSelect() + " where attempt_id = :attemptId for update")
                .param("attemptId", attemptId)
                .query(AiReservationRecoveryStore::attempt)
                .optional();
    }

    private Optional<Attempt> findByAttemptId(String attemptId) {
        return jdbcClient.sql(attemptSelect() + " where attempt_id = :attemptId")
                .param("attemptId", attemptId)
                .query(AiReservationRecoveryStore::attempt)
                .optional();
    }

    private long nextAttemptNumber(String reservationId) {
        return jdbcClient.sql("""
                select coalesce(max(attempt_number), 0) + 1
                from ai_reservation_recovery_attempts
                where reservation_id = :reservationId
                """)
                .param("reservationId", reservationId)
                .query(Long.class)
                .single();
    }

    private void lockAttemptId(String attemptId) {
        jdbcClient.sql("select pg_advisory_xact_lock(hashtextextended(:attemptId, 0))")
                .param("attemptId", attemptId)
                .query((resultSet, rowNumber) -> true)
                .single();
    }

    private static String attemptSelect() {
        return """
                select attempt_id, reservation_id, attempt_number, owner_id, claimed_phase,
                       claimed_at, lease_until, status, completed_phase, completed_at, result
                from ai_reservation_recovery_attempts
                """;
    }

    private static Attempt attempt(ResultSet resultSet, int rowNumber) throws SQLException {
        var completedPhase = resultSet.getString("completed_phase");
        var result = resultSet.getString("result");
        return new Attempt(
                resultSet.getString("attempt_id"), resultSet.getString("reservation_id"),
                resultSet.getLong("attempt_number"), resultSet.getString("owner_id"),
                Phase.valueOf(resultSet.getString("claimed_phase")), instant(resultSet, "claimed_at"),
                instant(resultSet, "lease_until"), Status.valueOf(resultSet.getString("status")),
                completedPhase == null ? Optional.empty() : Optional.of(Phase.valueOf(completedPhase)),
                nullableInstant(resultSet, "completed_at"),
                result == null ? Optional.empty() : Optional.of(RecoveryResult.valueOf(result)));
    }

    private static ReservationRow reservationRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ReservationRow(resultSet.getString("reservation_id"),
                Phase.valueOf(resultSet.getString("phase")), instant(resultSet, "updated_at"));
    }

    private static boolean isTerminal(Phase phase) {
        return phase == Phase.SETTLED || phase == Phase.CANCELLED || phase == Phase.RELEASED_NO_CHARGE;
    }

    private static ClaimOutcome claimOutcome(ClaimDecision decision, Optional<Attempt> attempt) {
        return new ClaimOutcome(decision, attempt);
    }

    private static CompletionOutcome completionOutcome(CompletionDecision decision, Optional<Attempt> attempt) {
        return new CompletionOutcome(decision, attempt);
    }

    private static void requireSingleUpdate(int updated) {
        if (updated != 1) throw new IllegalStateException("AI 예약 복구 시도를 갱신하지 못했습니다.");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

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

    public enum ClaimDecision {
        CLAIMED, REPLAYED, RESERVATION_NOT_FOUND, RESERVATION_TERMINAL,
        ALREADY_CLAIMED, ATTEMPT_ID_CONFLICT, NO_RESERVATION_AVAILABLE
    }

    public enum CompletionDecision {
        COMPLETED, REPLAYED, ATTEMPT_NOT_FOUND, OWNER_CONFLICT,
        LEASE_EXPIRED, COMPLETION_CONFLICT
    }

    public enum Status { ACTIVE, COMPLETED, EXPIRED }
    public enum RecoveryResult { CHECK_COMPLETED, CHECK_FAILED, MANUAL_REVIEW_REQUIRED }

    public record Lease(String attemptId, String ownerId, Instant claimedAt, Instant leaseUntil) {
        public Lease {
            requireText(attemptId, "AI 예약 복구 시도 식별자가 필요합니다.");
            requireText(ownerId, "AI 예약 복구 작업자 식별자가 필요합니다.");
            Objects.requireNonNull(claimedAt, "AI 예약 복구 소유권 획득 시각이 필요합니다.");
            Objects.requireNonNull(leaseUntil, "AI 예약 복구 임대 만료 시각이 필요합니다.");
            if (!claimedAt.isBefore(leaseUntil)) {
                throw new IllegalArgumentException("AI 예약 복구 임대 만료는 소유권 획득보다 늦어야 합니다.");
            }
        }
    }

    public record Completion(String attemptId, String ownerId, Instant completedAt, RecoveryResult result) {
        public Completion {
            requireText(attemptId, "완료할 AI 예약 복구 시도 식별자가 필요합니다.");
            requireText(ownerId, "AI 예약 복구 작업자 식별자가 필요합니다.");
            Objects.requireNonNull(completedAt, "AI 예약 복구 완료 시각이 필요합니다.");
            Objects.requireNonNull(result, "AI 예약 복구 확인 결과가 필요합니다.");
        }
    }

    public record RecoveryFence(
            String attemptId, String reservationId, long attemptNumber, String ownerId,
            Phase observedPhase, Instant observedUpdatedAt, Instant checkedAt
    ) {
        public RecoveryFence {
            requireText(attemptId, "AI 예약 복구 시도 식별자가 필요합니다.");
            requireText(reservationId, "AI 요청 예약 식별자가 필요합니다.");
            if (attemptNumber < 1) throw new IllegalArgumentException("AI 예약 복구 시도 순번은 1 이상이어야 합니다.");
            requireText(ownerId, "AI 예약 복구 작업자 식별자가 필요합니다.");
            Objects.requireNonNull(observedPhase, "확인한 AI 요청 예약 단계가 필요합니다.");
            Objects.requireNonNull(observedUpdatedAt, "확인한 AI 요청 예약 갱신 시각이 필요합니다.");
            Objects.requireNonNull(checkedAt, "AI 예약 복구 결과 적용 시각이 필요합니다.");
            if (checkedAt.isBefore(observedUpdatedAt)) {
                throw new IllegalArgumentException("복구 결과 적용은 확인한 예약 상태보다 빠를 수 없습니다.");
            }
        }

        public static RecoveryFence from(Attempt attempt,
                                         AiBudgetReservationLifecycleStore.Snapshot observed,
                                         Instant checkedAt) {
            Objects.requireNonNull(attempt, "AI 예약 복구 시도가 필요합니다.");
            Objects.requireNonNull(observed, "확인한 AI 요청 예약 상태가 필요합니다.");
            Objects.requireNonNull(checkedAt, "AI 예약 복구 결과 적용 시각이 필요합니다.");
            if (!attempt.reservationId().equals(observed.reservationId())) {
                throw new IllegalArgumentException("복구 시도와 확인한 AI 요청 예약이 다릅니다.");
            }
            if (checkedAt.isBefore(attempt.claimedAt())) {
                throw new IllegalArgumentException("복구 결과 적용은 소유권 획득보다 빠를 수 없습니다.");
            }
            return new RecoveryFence(
                    attempt.attemptId(), attempt.reservationId(), attempt.attemptNumber(), attempt.ownerId(),
                    observed.phase(), observed.updatedAt(), checkedAt);
        }
    }

    public record Attempt(
            String attemptId, String reservationId, long attemptNumber, String ownerId,
            Phase claimedPhase, Instant claimedAt, Instant leaseUntil, Status status,
            Optional<Phase> completedPhase, Optional<Instant> completedAt, Optional<RecoveryResult> result
    ) {
        public Attempt {
            requireText(attemptId, "AI 예약 복구 시도 식별자가 필요합니다.");
            requireText(reservationId, "AI 요청 예약 식별자가 필요합니다.");
            if (attemptNumber < 1) throw new IllegalArgumentException("AI 예약 복구 시도 순번은 1 이상이어야 합니다.");
            requireText(ownerId, "AI 예약 복구 작업자 식별자가 필요합니다.");
            Objects.requireNonNull(claimedPhase, "소유권 획득 당시 예약 단계가 필요합니다.");
            Objects.requireNonNull(claimedAt, "AI 예약 복구 소유권 획득 시각이 필요합니다.");
            Objects.requireNonNull(leaseUntil, "AI 예약 복구 임대 만료 시각이 필요합니다.");
            Objects.requireNonNull(status, "AI 예약 복구 시도 상태가 필요합니다.");
            Objects.requireNonNull(completedPhase, "완료 당시 예약 단계의 존재 여부가 필요합니다.");
            Objects.requireNonNull(completedAt, "AI 예약 복구 완료 시각의 존재 여부가 필요합니다.");
            Objects.requireNonNull(result, "AI 예약 복구 확인 결과의 존재 여부가 필요합니다.");
        }

        private boolean matches(Lease lease) {
            return ownerId.equals(lease.ownerId())
                    && sameDatabaseInstant(claimedAt, lease.claimedAt())
                    && sameDatabaseInstant(leaseUntil, lease.leaseUntil());
        }

        private boolean matches(Completion completion) {
            return ownerId.equals(completion.ownerId())
                    && completedAt.filter(value -> sameDatabaseInstant(value, completion.completedAt())).isPresent()
                    && result.filter(value -> value == completion.result()).isPresent();
        }
    }

    public record ClaimOutcome(ClaimDecision decision, Optional<Attempt> attempt) {
        public ClaimOutcome {
            Objects.requireNonNull(decision, "AI 예약 복구 소유권 결과가 필요합니다.");
            Objects.requireNonNull(attempt, "AI 예약 복구 시도의 존재 여부가 필요합니다.");
        }
    }

    public record CompletionOutcome(CompletionDecision decision, Optional<Attempt> attempt) {
        public CompletionOutcome {
            Objects.requireNonNull(decision, "AI 예약 복구 완료 결과가 필요합니다.");
            Objects.requireNonNull(attempt, "AI 예약 복구 시도의 존재 여부가 필요합니다.");
        }
    }

    private record ReservationRow(String reservationId, Phase phase, Instant updatedAt) {}
}
