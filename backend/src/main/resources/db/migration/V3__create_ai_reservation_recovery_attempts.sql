create table ai_reservation_recovery_attempts (
    attempt_id text primary key,
    reservation_id text not null references ai_request_reservations (reservation_id),
    attempt_number bigint not null,
    owner_id text not null,
    claimed_phase text not null,
    claimed_at timestamptz not null,
    lease_until timestamptz not null,
    status text not null,
    completed_phase text,
    completed_at timestamptz,
    result text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ai_reservation_recovery_attempts_id_not_blank check (btrim(attempt_id) <> ''),
    constraint ai_reservation_recovery_attempts_owner_not_blank check (btrim(owner_id) <> ''),
    constraint ai_reservation_recovery_attempts_number_positive check (attempt_number > 0),
    constraint ai_reservation_recovery_attempts_claimed_phase_valid check (
        claimed_phase in ('HELD', 'DISPATCHED', 'OUTCOME_UNKNOWN')
    ),
    constraint ai_reservation_recovery_attempts_completed_phase_valid check (
        completed_phase is null
        or completed_phase in ('HELD', 'DISPATCHED', 'OUTCOME_UNKNOWN', 'SETTLED', 'CANCELLED', 'RELEASED_NO_CHARGE')
    ),
    constraint ai_reservation_recovery_attempts_status_valid check (
        status in ('ACTIVE', 'COMPLETED', 'EXPIRED')
    ),
    constraint ai_reservation_recovery_attempts_result_valid check (
        result is null or result in ('CHECK_COMPLETED', 'CHECK_FAILED', 'MANUAL_REVIEW_REQUIRED')
    ),
    constraint ai_reservation_recovery_attempts_lease_period_valid check (claimed_at < lease_until),
    constraint ai_reservation_recovery_attempts_completion_fields check (
        (status = 'ACTIVE' and completed_phase is null and completed_at is null and result is null)
        or (status = 'COMPLETED' and completed_phase is not null and completed_at is not null and result is not null)
        or (status = 'EXPIRED' and completed_phase is not null and completed_at is not null and result is null)
    ),
    constraint ai_reservation_recovery_attempts_completion_time_valid check (
        completed_at is null or completed_at >= claimed_at
    ),
    constraint ai_reservation_recovery_attempts_completed_within_lease check (
        status <> 'COMPLETED' or completed_at < lease_until
    ),
    constraint ai_reservation_recovery_attempts_reservation_number_unique unique (
        reservation_id, attempt_number
    )
);

create unique index ai_reservation_recovery_attempts_one_active_idx
    on ai_reservation_recovery_attempts (reservation_id)
    where status = 'ACTIVE';
