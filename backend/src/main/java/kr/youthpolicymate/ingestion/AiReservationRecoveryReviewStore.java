package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationState.Phase;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.RecoveryResult;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Status;
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
public class AiReservationRecoveryReviewStore {
    private final JdbcClient jdbcClient;

    public AiReservationRecoveryReviewStore(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "AI 예약 복구 수동 검토 DB 접근이 필요합니다.");
    }

    @Transactional
    public ResumeOutcome resume(ResumeCommand command) {
        Objects.requireNonNull(command, "AI 예약 복구 수동 검토 재개 명령이 필요합니다.");
        lockResumeId(command.resumeId());

        Optional<ResumeRecord> replay = findByResumeId(command.resumeId());
        if (replay.isPresent()) {
            ResumeRecord current = replay.orElseThrow();
            return new ResumeOutcome(current.matches(command)
                    ? ResumeDecision.REPLAYED : ResumeDecision.RESUME_ID_CONFLICT, Optional.of(current));
        }

        Optional<ReservationRow> foundReservation = lockReservation(command.reservationId());
        if (foundReservation.isEmpty()) {
            return outcome(ResumeDecision.RESERVATION_NOT_FOUND, Optional.empty());
        }
        ReservationRow reservation = foundReservation.orElseThrow();
        if (isTerminal(reservation.phase())) {
            return outcome(ResumeDecision.RESERVATION_TERMINAL, Optional.empty());
        }
        if (reservation.phase() != command.observedPhase()
                || !sameDatabaseInstant(reservation.updatedAt(), command.observedUpdatedAt())) {
            return outcome(ResumeDecision.RESERVATION_CHANGED, Optional.empty());
        }

        Optional<AttemptRow> foundAttempt = lockAttempt(command.manualAttemptId());
        if (foundAttempt.isEmpty()
                || !foundAttempt.orElseThrow().reservationId().equals(command.reservationId())) {
            return outcome(ResumeDecision.MANUAL_ATTEMPT_NOT_FOUND, Optional.empty());
        }
        AttemptRow attempt = foundAttempt.orElseThrow();
        Optional<ResumeRecord> existing = findByManualAttemptId(command.manualAttemptId());
        if (existing.isPresent()) {
            return outcome(ResumeDecision.ALREADY_RESUMED, existing);
        }
        if (!latestAttemptId(command.reservationId()).filter(command.manualAttemptId()::equals).isPresent()) {
            return outcome(ResumeDecision.MANUAL_ATTEMPT_NOT_LATEST, Optional.empty());
        }
        if (attempt.status() != Status.COMPLETED
                || attempt.result().filter(result -> result == RecoveryResult.MANUAL_REVIEW_REQUIRED).isEmpty()) {
            return outcome(ResumeDecision.MANUAL_REVIEW_NOT_ACTIVE, Optional.empty());
        }
        if (command.resumedAt().isBefore(attempt.completedAt().orElseThrow())) {
            throw new IllegalArgumentException("수동 검토 재개는 대상 복구 시도의 완료보다 빠를 수 없습니다.");
        }

        int inserted = jdbcClient.sql("""
                insert into ai_reservation_recovery_review_resumes (
                    resume_id, manual_attempt_id, resumed_by, resume_reason,
                    observed_reservation_phase, observed_reservation_updated_at, resumed_at
                ) values (
                    :resumeId, :manualAttemptId, :resumedBy, :resumeReason,
                    :observedPhase, :observedUpdatedAt, :resumedAt
                )
                """)
                .param("resumeId", command.resumeId())
                .param("manualAttemptId", command.manualAttemptId())
                .param("resumedBy", command.operatorId())
                .param("resumeReason", command.reason().name())
                .param("observedPhase", command.observedPhase().name())
                .param("observedUpdatedAt", dbTime(command.observedUpdatedAt()))
                .param("resumedAt", dbTime(command.resumedAt()))
                .update();
        requireSingleUpdate(inserted);
        return outcome(ResumeDecision.RESUMED, findByResumeId(command.resumeId()));
    }

    @Transactional(readOnly = true)
    public List<ResumeRecord> history(String reservationId) {
        requireText(reservationId, "조회할 AI 요청 예약 식별자가 필요합니다.");
        return jdbcClient.sql(select() + """
                where attempt.reservation_id = :reservationId
                order by resume.resumed_at, resume.resume_id
                """)
                .param("reservationId", reservationId)
                .query(AiReservationRecoveryReviewStore::resumeRecord)
                .list();
    }

    @Transactional(readOnly = true)
    public Optional<ResumeRecord> find(String resumeId) {
        requireText(resumeId, "조회할 AI 예약 복구 수동 검토 재개 식별자가 필요합니다.");
        return findByResumeId(resumeId);
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
                select attempt_id, reservation_id, status, completed_at, result
                from ai_reservation_recovery_attempts
                where attempt_id = :attemptId
                for update
                """)
                .param("attemptId", attemptId)
                .query((resultSet, rowNumber) -> new AttemptRow(
                        resultSet.getString("attempt_id"),
                        resultSet.getString("reservation_id"),
                        Status.valueOf(resultSet.getString("status")),
                        nullableInstant(resultSet, "completed_at"),
                        optionalRecoveryResult(resultSet, "result")))
                .optional();
    }

    private Optional<String> latestAttemptId(String reservationId) {
        return jdbcClient.sql("""
                select attempt_id
                from ai_reservation_recovery_attempts
                where reservation_id = :reservationId
                order by attempt_number desc
                limit 1
                """)
                .param("reservationId", reservationId)
                .query(String.class)
                .optional();
    }

    private Optional<ResumeRecord> findByResumeId(String resumeId) {
        return jdbcClient.sql(select() + " where resume.resume_id = :resumeId")
                .param("resumeId", resumeId)
                .query(AiReservationRecoveryReviewStore::resumeRecord)
                .optional();
    }

    private Optional<ResumeRecord> findByManualAttemptId(String attemptId) {
        return jdbcClient.sql(select() + " where resume.manual_attempt_id = :attemptId")
                .param("attemptId", attemptId)
                .query(AiReservationRecoveryReviewStore::resumeRecord)
                .optional();
    }

    private void lockResumeId(String resumeId) {
        jdbcClient.sql("select pg_advisory_xact_lock(hashtextextended(:resumeId, 1))")
                .param("resumeId", resumeId)
                .query((resultSet, rowNumber) -> true)
                .single();
    }

    private static String select() {
        return """
                select resume.resume_id, attempt.reservation_id, resume.manual_attempt_id,
                       resume.resumed_by, resume.resume_reason,
                       resume.observed_reservation_phase,
                       resume.observed_reservation_updated_at, resume.resumed_at
                from ai_reservation_recovery_review_resumes resume
                join ai_reservation_recovery_attempts attempt
                  on attempt.attempt_id = resume.manual_attempt_id
                """;
    }

    private static ResumeRecord resumeRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ResumeRecord(
                resultSet.getString("resume_id"), resultSet.getString("reservation_id"),
                resultSet.getString("manual_attempt_id"), resultSet.getString("resumed_by"),
                ResumeReason.valueOf(resultSet.getString("resume_reason")),
                Phase.valueOf(resultSet.getString("observed_reservation_phase")),
                instant(resultSet, "observed_reservation_updated_at"),
                instant(resultSet, "resumed_at"));
    }

    private static Optional<RecoveryResult> optionalRecoveryResult(ResultSet resultSet, String column)
            throws SQLException {
        String result = resultSet.getString(column);
        return result == null ? Optional.empty() : Optional.of(RecoveryResult.valueOf(result));
    }

    private static boolean isTerminal(Phase phase) {
        return phase == Phase.SETTLED || phase == Phase.CANCELLED || phase == Phase.RELEASED_NO_CHARGE;
    }

    private static ResumeOutcome outcome(ResumeDecision decision, Optional<ResumeRecord> record) {
        return new ResumeOutcome(decision, record);
    }

    private static void requireSingleUpdate(int updated) {
        if (updated != 1) throw new IllegalStateException("AI 예약 복구 수동 검토 재개를 저장하지 못했습니다.");
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
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static OffsetDateTime dbTime(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    public enum ResumeDecision {
        RESUMED,
        REPLAYED,
        RESUME_ID_CONFLICT,
        RESERVATION_NOT_FOUND,
        RESERVATION_TERMINAL,
        RESERVATION_CHANGED,
        MANUAL_ATTEMPT_NOT_FOUND,
        MANUAL_ATTEMPT_NOT_LATEST,
        MANUAL_REVIEW_NOT_ACTIVE,
        ALREADY_RESUMED
    }

    public enum ResumeReason {
        SUPPLIER_STATE_VERIFIED,
        INTERNAL_STATE_VERIFIED,
        RECOVERY_INCIDENT_RESOLVED
    }

    public record ResumeCommand(
            String resumeId,
            String reservationId,
            String manualAttemptId,
            String operatorId,
            ResumeReason reason,
            Phase observedPhase,
            Instant observedUpdatedAt,
            Instant resumedAt
    ) {
        public ResumeCommand {
            requireText(resumeId, "AI 예약 복구 수동 검토 재개 식별자가 필요합니다.");
            requireText(reservationId, "재개할 AI 요청 예약 식별자가 필요합니다.");
            requireText(manualAttemptId, "재개할 수동 검토 복구 시도 식별자가 필요합니다.");
            requireText(operatorId, "수동 검토 재개 운영자 식별자가 필요합니다.");
            Objects.requireNonNull(reason, "수동 검토 재개 사유가 필요합니다.");
            Objects.requireNonNull(observedPhase, "수동 검토 재개 시 확인한 예약 단계가 필요합니다.");
            Objects.requireNonNull(observedUpdatedAt, "수동 검토 재개 시 확인한 예약 갱신 시각이 필요합니다.");
            Objects.requireNonNull(resumedAt, "수동 검토 재개 시각이 필요합니다.");
            if (resumedAt.isBefore(observedUpdatedAt)) {
                throw new IllegalArgumentException("수동 검토 재개는 확인한 예약 상태보다 빠를 수 없습니다.");
            }
        }
    }

    public record ResumeRecord(
            String resumeId,
            String reservationId,
            String manualAttemptId,
            String operatorId,
            ResumeReason reason,
            Phase observedPhase,
            Instant observedUpdatedAt,
            Instant resumedAt
    ) {
        public ResumeRecord {
            requireText(resumeId, "AI 예약 복구 수동 검토 재개 식별자가 필요합니다.");
            requireText(reservationId, "재개한 AI 요청 예약 식별자가 필요합니다.");
            requireText(manualAttemptId, "재개한 수동 검토 복구 시도 식별자가 필요합니다.");
            requireText(operatorId, "수동 검토 재개 운영자 식별자가 필요합니다.");
            Objects.requireNonNull(reason, "수동 검토 재개 사유가 필요합니다.");
            Objects.requireNonNull(observedPhase, "수동 검토 재개 시 확인한 예약 단계가 필요합니다.");
            Objects.requireNonNull(observedUpdatedAt, "수동 검토 재개 시 확인한 예약 갱신 시각이 필요합니다.");
            Objects.requireNonNull(resumedAt, "수동 검토 재개 시각이 필요합니다.");
            if (isTerminal(observedPhase)) {
                throw new IllegalArgumentException("종료된 AI 요청 예약은 수동 검토 재개 기록으로 만들 수 없습니다.");
            }
            if (resumedAt.isBefore(observedUpdatedAt)) {
                throw new IllegalArgumentException("수동 검토 재개는 확인한 예약 상태보다 빠를 수 없습니다.");
            }
        }

        private boolean matches(ResumeCommand command) {
            return reservationId.equals(command.reservationId())
                    && manualAttemptId.equals(command.manualAttemptId())
                    && operatorId.equals(command.operatorId())
                    && reason == command.reason()
                    && observedPhase == command.observedPhase()
                    && sameDatabaseInstant(observedUpdatedAt, command.observedUpdatedAt())
                    && sameDatabaseInstant(resumedAt, command.resumedAt());
        }
    }

    public record ResumeOutcome(ResumeDecision decision, Optional<ResumeRecord> record) {
        public ResumeOutcome {
            Objects.requireNonNull(decision, "AI 예약 복구 수동 검토 재개 결과가 필요합니다.");
            Objects.requireNonNull(record, "AI 예약 복구 수동 검토 재개 기록의 존재 여부가 필요합니다.");
        }
    }

    private record ReservationRow(String reservationId, Phase phase, Instant updatedAt) {}

    private record AttemptRow(
            String attemptId,
            String reservationId,
            Status status,
            Optional<Instant> completedAt,
            Optional<RecoveryResult> result
    ) {}
}
