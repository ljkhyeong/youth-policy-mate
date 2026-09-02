package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationLifecycleStore.Snapshot;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Dispatch;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Phase;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.UncertainOutcome;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.UncertainReason;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Decision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryRetryPolicy.Schedule;
import kr.youthpolicymate.ingestion.AiReservationRecoveryReviewStore.ResumeReason;
import kr.youthpolicymate.ingestion.AiReservationRecoveryReviewStore.ResumeRecord;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Attempt;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.RecoveryResult;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Status;
import kr.youthpolicymate.ingestion.PolicyAiResult.Kind;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// 운영 조치를 실행하지 않는다. 오래된 미완료 예약과 복구 판단 근거를 읽기 전용으로 묶어 반환한다.
@Repository
@Profile("!preview")
public class AiReservationRecoveryOperationsQuery {
    private final JdbcClient jdbcClient;
    private final AiReservationRecoveryRetryPolicy retryPolicy = new AiReservationRecoveryRetryPolicy();

    public AiReservationRecoveryOperationsQuery(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "AI 예약 복구 운영 조회용 DB 접근이 필요합니다.");
    }

    @Transactional(readOnly = true)
    public Report findOldestUnresolved(Criteria criteria) {
        Objects.requireNonNull(criteria, "AI 예약 복구 운영 조회 조건이 필요합니다.");
        List<Row> rows = jdbcClient.sql("""
                select reservation.reservation_id, reservation.budget_id, reservation.policy_id,
                       reservation.ai_kind, reservation.generation_version, reservation.request_sequence,
                       reservation.phase, reservation.maximum_won, reservation.reserved_at,
                       reservation.dispatch_id, reservation.dispatched_at,
                       reservation.uncertain_observation_id, reservation.uncertain_recorded_at,
                       reservation.uncertain_reason, reservation.completion_id,
                       reservation.completed_at as reservation_completed_at,
                       reservation.actual_won, reservation.updated_at as reservation_updated_at,
                       attempt.attempt_id, attempt.attempt_number, attempt.owner_id,
                       attempt.claimed_phase, attempt.claimed_at, attempt.lease_until,
                       attempt.status as attempt_status, attempt.completed_phase as attempt_completed_phase,
                       attempt.completed_at as attempt_completed_at, attempt.result as attempt_result,
                       resume.resume_id, resume.resumed_by, resume.resume_reason,
                       resume.observed_reservation_phase,
                       resume.observed_reservation_updated_at, resume.resumed_at
                from (
                    select reservation_id, budget_id, policy_id, ai_kind, generation_version,
                           request_sequence, phase, maximum_won, reserved_at,
                           dispatch_id, dispatched_at, uncertain_observation_id,
                           uncertain_recorded_at, uncertain_reason, completion_id,
                           completed_at, actual_won, updated_at
                    from ai_request_reservations
                    where phase in ('HELD', 'DISPATCHED', 'OUTCOME_UNKNOWN')
                      and updated_at <= :staleAtOrBefore
                    order by updated_at, reservation_id
                    limit :limit
                ) reservation
                left join ai_reservation_recovery_attempts attempt
                  on attempt.reservation_id = reservation.reservation_id
                left join ai_reservation_recovery_review_resumes resume
                  on resume.manual_attempt_id = attempt.attempt_id
                order by reservation.updated_at, reservation.reservation_id, attempt.attempt_number
                """)
                .param("staleAtOrBefore", dbTime(criteria.staleAtOrBefore()))
                .param("limit", criteria.limit())
                .query(AiReservationRecoveryOperationsQuery::row)
                .list();

        var grouped = new LinkedHashMap<String, Group>();
        for (Row row : rows) {
            Group group = grouped.computeIfAbsent(row.reservation().reservationId(), ignored ->
                    new Group(row.scope(), row.reservation()));
            row.attempt().ifPresent(group.attempts()::add);
            row.resume().ifPresent(group.reviewResumes()::add);
        }

        List<Item> items = grouped.values().stream()
                .map(group -> new Item(group.scope(), group.reservation(), group.attempts(), group.reviewResumes(),
                        retryPolicy.decide(criteria.schedule(), group.reservation(),
                                group.attempts(), group.reviewResumes(), criteria.evaluatedAt())))
                .toList();
        return new Report(criteria, items);
    }

    private static Row row(ResultSet resultSet, int rowNumber) throws SQLException {
        var scope = new Scope(
                resultSet.getString("policy_id"), Kind.valueOf(resultSet.getString("ai_kind")),
                resultSet.getString("generation_version"), resultSet.getLong("request_sequence"));
        var reservation = new Snapshot(
                resultSet.getString("reservation_id"), resultSet.getString("budget_id"),
                Phase.valueOf(resultSet.getString("phase")), resultSet.getBigDecimal("maximum_won"),
                instant(resultSet, "reserved_at"),
                optionalDispatch(resultSet), optionalUncertainty(resultSet),
                Optional.ofNullable(resultSet.getString("completion_id")),
                nullableInstant(resultSet, "reservation_completed_at"),
                Optional.ofNullable(resultSet.getBigDecimal("actual_won")),
                instant(resultSet, "reservation_updated_at"));
        return new Row(scope, reservation, optionalAttempt(resultSet), optionalResume(resultSet));
    }

    private static Optional<Dispatch> optionalDispatch(ResultSet resultSet) throws SQLException {
        var dispatchId = resultSet.getString("dispatch_id");
        return dispatchId == null ? Optional.empty()
                : Optional.of(new Dispatch(dispatchId, instant(resultSet, "dispatched_at")));
    }

    private static Optional<UncertainOutcome> optionalUncertainty(ResultSet resultSet) throws SQLException {
        var observationId = resultSet.getString("uncertain_observation_id");
        return observationId == null ? Optional.empty() : Optional.of(new UncertainOutcome(
                observationId, instant(resultSet, "uncertain_recorded_at"),
                UncertainReason.valueOf(resultSet.getString("uncertain_reason"))));
    }

    private static Optional<Attempt> optionalAttempt(ResultSet resultSet) throws SQLException {
        var attemptId = resultSet.getString("attempt_id");
        if (attemptId == null) return Optional.empty();
        var completedPhase = resultSet.getString("attempt_completed_phase");
        var result = resultSet.getString("attempt_result");
        return Optional.of(new Attempt(
                attemptId, resultSet.getString("reservation_id"), resultSet.getLong("attempt_number"),
                resultSet.getString("owner_id"), Phase.valueOf(resultSet.getString("claimed_phase")),
                instant(resultSet, "claimed_at"), instant(resultSet, "lease_until"),
                Status.valueOf(resultSet.getString("attempt_status")),
                completedPhase == null ? Optional.empty() : Optional.of(Phase.valueOf(completedPhase)),
                nullableInstant(resultSet, "attempt_completed_at"),
                result == null ? Optional.empty() : Optional.of(RecoveryResult.valueOf(result))));
    }

    private static Optional<ResumeRecord> optionalResume(ResultSet resultSet) throws SQLException {
        String resumeId = resultSet.getString("resume_id");
        if (resumeId == null) return Optional.empty();
        return Optional.of(new ResumeRecord(
                resumeId, resultSet.getString("reservation_id"), resultSet.getString("attempt_id"),
                resultSet.getString("resumed_by"),
                ResumeReason.valueOf(resultSet.getString("resume_reason")),
                Phase.valueOf(resultSet.getString("observed_reservation_phase")),
                instant(resultSet, "observed_reservation_updated_at"),
                instant(resultSet, "resumed_at")));
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

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

    public record Criteria(Schedule schedule, Instant staleAtOrBefore, Instant evaluatedAt, int limit) {
        public Criteria {
            Objects.requireNonNull(schedule, "AI 예약 복구 재확인 일정이 필요합니다.");
            Objects.requireNonNull(staleAtOrBefore, "오래된 미완료 예약의 기준 시각이 필요합니다.");
            Objects.requireNonNull(evaluatedAt, "AI 예약 복구 운영 조회 시각이 필요합니다.");
            if (evaluatedAt.isBefore(staleAtOrBefore)) {
                throw new IllegalArgumentException("운영 조회 시각은 오래된 예약 기준보다 빠를 수 없습니다.");
            }
            if (limit < 1) throw new IllegalArgumentException("조회할 미완료 예약 수는 1 이상이어야 합니다.");
        }
    }

    public record Scope(String policyId, Kind kind, String generationVersion, long requestSequence) {
        public Scope {
            requireText(policyId, "운영 조회할 정책 식별자가 필요합니다.");
            Objects.requireNonNull(kind, "운영 조회할 AI 작업 종류가 필요합니다.");
            requireText(generationVersion, "운영 조회할 AI 생성 방식 버전이 필요합니다.");
            if (requestSequence < 1) throw new IllegalArgumentException("운영 조회할 AI 요청 순번은 1 이상이어야 합니다.");
        }
    }

    public record Item(Scope scope, Snapshot reservation, List<Attempt> attempts,
                       List<ResumeRecord> reviewResumes, Decision decision) {
        public Item {
            Objects.requireNonNull(scope, "AI 예약 복구 운영 조회 범위가 필요합니다.");
            Objects.requireNonNull(reservation, "AI 예약 복구 운영 조회 예약이 필요합니다.");
            attempts = List.copyOf(Objects.requireNonNull(attempts, "AI 예약 복구 시도 이력이 필요합니다."));
            reviewResumes = List.copyOf(Objects.requireNonNull(
                    reviewResumes, "AI 예약 복구 수동 검토 재개 이력이 필요합니다."));
            Objects.requireNonNull(decision, "AI 예약 복구 운영 판단이 필요합니다.");
        }
    }

    public record Report(Criteria criteria, List<Item> items) {
        public Report {
            Objects.requireNonNull(criteria, "AI 예약 복구 운영 조회 조건이 필요합니다.");
            items = List.copyOf(Objects.requireNonNull(items, "AI 예약 복구 운영 조회 결과가 필요합니다."));
        }
    }

    private record Row(Scope scope, Snapshot reservation, Optional<Attempt> attempt,
                       Optional<ResumeRecord> resume) {}

    private record Group(Scope scope, Snapshot reservation, List<Attempt> attempts,
                         List<ResumeRecord> reviewResumes) {
        private Group(Scope scope, Snapshot reservation) {
            this(scope, reservation, new ArrayList<>(), new ArrayList<>());
        }
    }
}
