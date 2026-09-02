create table ai_reservation_recovery_review_resumes (
    resume_id text primary key,
    manual_attempt_id text not null unique references ai_reservation_recovery_attempts (attempt_id),
    resumed_by text not null,
    resume_reason text not null,
    observed_reservation_phase text not null,
    observed_reservation_updated_at timestamptz not null,
    resumed_at timestamptz not null,
    constraint ai_reservation_recovery_review_resumes_id_not_blank check (btrim(resume_id) <> ''),
    constraint ai_reservation_recovery_review_resumes_operator_not_blank check (btrim(resumed_by) <> ''),
    constraint ai_reservation_recovery_review_resumes_reason_valid check (
        resume_reason in ('SUPPLIER_STATE_VERIFIED', 'INTERNAL_STATE_VERIFIED', 'RECOVERY_INCIDENT_RESOLVED')
    ),
    constraint ai_reservation_recovery_review_resumes_phase_valid check (
        observed_reservation_phase in ('HELD', 'DISPATCHED', 'OUTCOME_UNKNOWN')
    ),
    constraint ai_reservation_recovery_review_resumes_time_valid check (
        resumed_at >= observed_reservation_updated_at
    )
);
