package kr.youthpolicymate.ingestion;

import static kr.youthpolicymate.ingestion.AiDatabaseTime.dbTime;
import static kr.youthpolicymate.ingestion.AiDatabaseTime.sameDatabaseInstant;

import kr.youthpolicymate.ingestion.AiBudgetReservationState.Phase;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Status;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Profile("!preview")
public class AiReservationRecoveryLeaseRenewalStore {
    private final JdbcClient jdbcClient;

    public AiReservationRecoveryLeaseRenewalStore(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "AI 예약 복구 임대 갱신 DB 접근이 필요합니다.");
    }

    @Transactional
    public RenewalOutcome renew(RenewalCommand command) {
        Objects.requireNonNull(command, "AI 예약 복구 임대 갱신 명령이 필요합니다.");
        lockRenewalId(command.renewalId());

        Optional<RenewalRecord> replay = findByRenewalId(command.renewalId());
        if (replay.isPresent()) {
            RenewalRecord current = replay.orElseThrow();
            return outcome(current.matches(command)
                    ? RenewalDecision.REPLAYED : RenewalDecision.RENEWAL_ID_CONFLICT, replay);
        }

        Optional<ReservationRow> foundReservation = lockReservation(command.reservationId());
        if (foundReservation.isEmpty()) {
            return outcome(RenewalDecision.RESERVATION_NOT_FOUND, Optional.empty());
        }
        ReservationRow reservation = foundReservation.orElseThrow();

        Optional<AttemptRow> foundAttempt = lockAttempt(command.attemptId());
        if (foundAttempt.isEmpty()) {
            return outcome(RenewalDecision.ATTEMPT_NOT_FOUND, Optional.empty());
        }
        AttemptRow attempt = foundAttempt.orElseThrow();
        if (!attempt.reservationId().equals(command.reservationId())
                || attempt.attemptNumber() != command.attemptNumber()) {
            return outcome(RenewalDecision.ATTEMPT_CONFLICT, Optional.empty());
        }
        if (!attempt.ownerId().equals(command.ownerId())) {
            return outcome(RenewalDecision.OWNER_CONFLICT, Optional.empty());
        }
        if (attempt.status() != Status.ACTIVE) {
            return outcome(RenewalDecision.ATTEMPT_NOT_ACTIVE, Optional.empty());
        }
        if (reservation.phase().isTerminal()) {
            return outcome(RenewalDecision.RESERVATION_TERMINAL, Optional.empty());
        }
        if (command.renewedAt().isBefore(attempt.claimedAt())) {
            throw new IllegalArgumentException("AI 예약 복구 임대 갱신은 소유권 획득보다 빠를 수 없습니다.");
        }
        if (command.renewedAt().isBefore(reservation.updatedAt())
                || command.renewedAt().isBefore(attempt.updatedAt())) {
            return outcome(RenewalDecision.RENEWAL_TIME_CONFLICT, Optional.empty());
        }
        if (!sameDatabaseInstant(attempt.leaseUntil(), command.observedLeaseUntil())) {
            return outcome(RenewalDecision.LEASE_CHANGED, Optional.empty());
        }
        if (!command.renewedAt().isBefore(attempt.leaseUntil())) {
            return outcome(RenewalDecision.LEASE_EXPIRED, Optional.empty());
        }

        int updated = jdbcClient.sql("""
                update ai_reservation_recovery_attempts
                set lease_until = :renewedLeaseUntil, updated_at = :renewedAt
                where attempt_id = :attemptId
                  and owner_id = :ownerId
                  and status = 'ACTIVE'
                  and lease_until = :observedLeaseUntil
                """)
                .param("renewedLeaseUntil", dbTime(command.renewedLeaseUntil()))
                .param("renewedAt", dbTime(command.renewedAt()))
                .param("attemptId", command.attemptId())
                .param("ownerId", command.ownerId())
                .param("observedLeaseUntil", dbTime(command.observedLeaseUntil()))
                .update();
        requireSingleUpdate(updated);

        int inserted = jdbcClient.sql("""
                insert into ai_reservation_recovery_lease_renewals (
                    renewal_id, attempt_id, attempt_number, owner_id,
                    observed_lease_until, renewed_at, renewed_lease_until
                ) values (
                    :renewalId, :attemptId, :attemptNumber, :ownerId,
                    :observedLeaseUntil, :renewedAt, :renewedLeaseUntil
                )
                """)
                .param("renewalId", command.renewalId())
                .param("attemptId", command.attemptId())
                .param("attemptNumber", command.attemptNumber())
                .param("ownerId", command.ownerId())
                .param("observedLeaseUntil", dbTime(command.observedLeaseUntil()))
                .param("renewedAt", dbTime(command.renewedAt()))
                .param("renewedLeaseUntil", dbTime(command.renewedLeaseUntil()))
                .update();
        requireSingleUpdate(inserted);
        return outcome(RenewalDecision.RENEWED, findByRenewalId(command.renewalId()));
    }

    @Transactional(readOnly = true)
    public List<RenewalRecord> history(String attemptId) {
        requireText(attemptId, "조회할 AI 예약 복구 시도 식별자가 필요합니다.");
        return jdbcClient.sql(select() + """
                where renewal.attempt_id = :attemptId
                order by renewal.renewed_at, renewal.renewal_id
                """)
                .param("attemptId", attemptId)
                .query(AiReservationRecoveryLeaseRenewalStore::renewalRecord)
                .list();
    }

    @Transactional(readOnly = true)
    public Optional<RenewalRecord> find(String renewalId) {
        requireText(renewalId, "조회할 AI 예약 복구 임대 갱신 식별자가 필요합니다.");
        return findByRenewalId(renewalId);
    }

    private Optional<ReservationRow> lockReservation(String reservationId) {
        return jdbcClient.sql("""
                select reservation_id, phase, updated_at
                from ai_request_reservations
                where reservation_id = :reservationId
                for update
                """)
                .param("reservationId", reservationId)
                .query((resultSet, rowNumber) -> new ReservationRow(
                        resultSet.getString("reservation_id"),
                        Phase.valueOf(resultSet.getString("phase")),
                        instant(resultSet, "updated_at")))
                .optional();
    }

    private Optional<AttemptRow> lockAttempt(String attemptId) {
        return jdbcClient.sql("""
                select attempt_id, reservation_id, attempt_number, owner_id,
                       claimed_at, lease_until, status, updated_at
                from ai_reservation_recovery_attempts
                where attempt_id = :attemptId
                for update
                """)
                .param("attemptId", attemptId)
                .query((resultSet, rowNumber) -> new AttemptRow(
                        resultSet.getString("attempt_id"),
                        resultSet.getString("reservation_id"),
                        resultSet.getLong("attempt_number"),
                        resultSet.getString("owner_id"),
                        instant(resultSet, "claimed_at"),
                        instant(resultSet, "lease_until"),
                        Status.valueOf(resultSet.getString("status")),
                        instant(resultSet, "updated_at")))
                .optional();
    }

    private Optional<RenewalRecord> findByRenewalId(String renewalId) {
        return jdbcClient.sql(select() + " where renewal.renewal_id = :renewalId")
                .param("renewalId", renewalId)
                .query(AiReservationRecoveryLeaseRenewalStore::renewalRecord)
                .optional();
    }

    private void lockRenewalId(String renewalId) {
        jdbcClient.sql("select pg_advisory_xact_lock(hashtextextended(:renewalId, 2))")
                .param("renewalId", renewalId)
                .query((resultSet, rowNumber) -> true)
                .single();
    }

    private static String select() {
        return """
                select renewal.renewal_id, attempt.reservation_id, renewal.attempt_id,
                       renewal.attempt_number, renewal.owner_id,
                       renewal.observed_lease_until, renewal.renewed_at,
                       renewal.renewed_lease_until
                from ai_reservation_recovery_lease_renewals renewal
                join ai_reservation_recovery_attempts attempt
                  on attempt.attempt_id = renewal.attempt_id
                """;
    }

    private static RenewalRecord renewalRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RenewalRecord(
                resultSet.getString("renewal_id"),
                resultSet.getString("reservation_id"),
                resultSet.getString("attempt_id"),
                resultSet.getLong("attempt_number"),
                resultSet.getString("owner_id"),
                instant(resultSet, "observed_lease_until"),
                instant(resultSet, "renewed_at"),
                instant(resultSet, "renewed_lease_until"));
    }

    private static RenewalOutcome outcome(RenewalDecision decision, Optional<RenewalRecord> record) {
        return new RenewalOutcome(decision, record);
    }

    private static void requireSingleUpdate(int updated) {
        if (updated != 1) throw new IllegalStateException("AI 예약 복구 임대 갱신을 저장하지 못했습니다.");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    public enum RenewalDecision {
        RENEWED,
        REPLAYED,
        RENEWAL_ID_CONFLICT,
        RESERVATION_NOT_FOUND,
        RESERVATION_TERMINAL,
        ATTEMPT_NOT_FOUND,
        ATTEMPT_CONFLICT,
        OWNER_CONFLICT,
        ATTEMPT_NOT_ACTIVE,
        RENEWAL_TIME_CONFLICT,
        LEASE_CHANGED,
        LEASE_EXPIRED
    }

    public record RenewalCommand(
            String renewalId,
            String reservationId,
            String attemptId,
            long attemptNumber,
            String ownerId,
            Instant observedLeaseUntil,
            Instant renewedAt,
            Instant renewedLeaseUntil
    ) {
        public RenewalCommand {
            requireText(renewalId, "AI 예약 복구 임대 갱신 식별자가 필요합니다.");
            requireText(reservationId, "AI 요청 예약 식별자가 필요합니다.");
            requireText(attemptId, "AI 예약 복구 시도 식별자가 필요합니다.");
            if (attemptNumber < 1) throw new IllegalArgumentException("AI 예약 복구 시도 순번은 1 이상이어야 합니다.");
            requireText(ownerId, "AI 예약 복구 작업자 식별자가 필요합니다.");
            Objects.requireNonNull(observedLeaseUntil, "확인한 AI 예약 복구 임대 만료 시각이 필요합니다.");
            Objects.requireNonNull(renewedAt, "AI 예약 복구 임대 갱신 시각이 필요합니다.");
            Objects.requireNonNull(renewedLeaseUntil, "갱신할 AI 예약 복구 임대 만료 시각이 필요합니다.");
            if (!observedLeaseUntil.isBefore(renewedLeaseUntil)) {
                throw new IllegalArgumentException("갱신한 AI 예약 복구 임대는 확인한 만료보다 길어야 합니다.");
            }
            if (!renewedAt.isBefore(renewedLeaseUntil)) {
                throw new IllegalArgumentException("AI 예약 복구 임대 갱신 시각은 새 만료보다 빨라야 합니다.");
            }
        }
    }

    public record RenewalRecord(
            String renewalId,
            String reservationId,
            String attemptId,
            long attemptNumber,
            String ownerId,
            Instant observedLeaseUntil,
            Instant renewedAt,
            Instant renewedLeaseUntil
    ) {
        public RenewalRecord {
            requireText(renewalId, "AI 예약 복구 임대 갱신 식별자가 필요합니다.");
            requireText(reservationId, "AI 요청 예약 식별자가 필요합니다.");
            requireText(attemptId, "AI 예약 복구 시도 식별자가 필요합니다.");
            if (attemptNumber < 1) throw new IllegalArgumentException("AI 예약 복구 시도 순번은 1 이상이어야 합니다.");
            requireText(ownerId, "AI 예약 복구 작업자 식별자가 필요합니다.");
            Objects.requireNonNull(observedLeaseUntil, "확인한 AI 예약 복구 임대 만료 시각이 필요합니다.");
            Objects.requireNonNull(renewedAt, "AI 예약 복구 임대 갱신 시각이 필요합니다.");
            Objects.requireNonNull(renewedLeaseUntil, "갱신한 AI 예약 복구 임대 만료 시각이 필요합니다.");
            if (!renewedAt.isBefore(observedLeaseUntil)) {
                throw new IllegalArgumentException("끝난 AI 예약 복구 임대는 갱신 기록으로 만들 수 없습니다.");
            }
            if (!observedLeaseUntil.isBefore(renewedLeaseUntil)) {
                throw new IllegalArgumentException("AI 예약 복구 임대 갱신은 기존 만료보다 길어야 합니다.");
            }
        }

        private boolean matches(RenewalCommand command) {
            return reservationId.equals(command.reservationId())
                    && attemptId.equals(command.attemptId())
                    && attemptNumber == command.attemptNumber()
                    && ownerId.equals(command.ownerId())
                    && sameDatabaseInstant(observedLeaseUntil, command.observedLeaseUntil())
                    && sameDatabaseInstant(renewedAt, command.renewedAt())
                    && sameDatabaseInstant(renewedLeaseUntil, command.renewedLeaseUntil());
        }
    }

    public record RenewalOutcome(RenewalDecision decision, Optional<RenewalRecord> record) {
        public RenewalOutcome {
            Objects.requireNonNull(decision, "AI 예약 복구 임대 갱신 결과가 필요합니다.");
            Objects.requireNonNull(record, "AI 예약 복구 임대 갱신 기록의 존재 여부가 필요합니다.");
        }
    }

    private record ReservationRow(String reservationId, Phase phase, Instant updatedAt) {}

    private record AttemptRow(
            String attemptId,
            String reservationId,
            long attemptNumber,
            String ownerId,
            Instant claimedAt,
            Instant leaseUntil,
            Status status,
            Instant updatedAt
    ) {}
}
