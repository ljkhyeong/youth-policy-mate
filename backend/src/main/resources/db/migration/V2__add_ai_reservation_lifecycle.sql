alter table ai_request_reservations
    add column dispatch_id text,
    add column dispatched_at timestamptz,
    add column uncertain_observation_id text,
    add column uncertain_recorded_at timestamptz,
    add column uncertain_reason text,
    add column completion_id text,
    add column completed_at timestamptz,
    add column actual_won numeric;

alter table ai_request_reservations
    add constraint ai_request_reservations_dispatch_complete check (
        (dispatch_id is null and dispatched_at is null)
        or (dispatch_id is not null and btrim(dispatch_id) <> '' and dispatched_at is not null)
    ),
    add constraint ai_request_reservations_uncertainty_complete check (
        (uncertain_observation_id is null and uncertain_recorded_at is null and uncertain_reason is null)
        or (
            uncertain_observation_id is not null
            and btrim(uncertain_observation_id) <> ''
            and uncertain_recorded_at is not null
            and uncertain_reason in ('TIMEOUT', 'CONNECTION_LOST', 'PROVIDER_STATUS_UNAVAILABLE')
        )
    ),
    add constraint ai_request_reservations_completion_complete check (
        (completion_id is null and completed_at is null)
        or (completion_id is not null and btrim(completion_id) <> '' and completed_at is not null)
    ),
    add constraint ai_request_reservations_actual_non_negative check (actual_won is null or actual_won >= 0),
    add constraint ai_request_reservations_lifecycle_fields check (
        (phase = 'HELD'
            and dispatch_id is null
            and uncertain_observation_id is null
            and completion_id is null
            and actual_won is null)
        or (phase = 'DISPATCHED'
            and dispatch_id is not null
            and uncertain_observation_id is null
            and completion_id is null
            and actual_won is null)
        or (phase = 'OUTCOME_UNKNOWN'
            and dispatch_id is not null
            and uncertain_observation_id is not null
            and completion_id is null
            and actual_won is null)
        or (phase = 'SETTLED'
            and dispatch_id is not null
            and completion_id is not null
            and actual_won is not null)
        or (phase = 'CANCELLED'
            and dispatch_id is null
            and uncertain_observation_id is null
            and completion_id is not null
            and actual_won is null)
        or (phase = 'RELEASED_NO_CHARGE'
            and dispatch_id is not null
            and completion_id is not null
            and actual_won is null)
    ),
    add constraint ai_request_reservations_lifecycle_time_order check (
        (dispatched_at is null or dispatched_at >= reserved_at)
        and (uncertain_recorded_at is null or uncertain_recorded_at >= dispatched_at)
        and (
            completed_at is null
            or completed_at >= greatest(
                reserved_at,
                coalesce(dispatched_at, reserved_at),
                coalesce(uncertain_recorded_at, reserved_at)
            )
        )
    );

create index ai_request_reservations_unresolved_idx
    on ai_request_reservations (updated_at, reservation_id)
    where phase in ('HELD', 'DISPATCHED', 'OUTCOME_UNKNOWN');
