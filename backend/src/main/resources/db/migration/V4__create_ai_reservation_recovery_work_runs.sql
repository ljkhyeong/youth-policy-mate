create table ai_reservation_recovery_work_runs (
    run_id text primary key,
    worker_id text not null,
    maximum_attempts integer not null,
    retry_delays text not null,
    stale_at_or_before timestamptz not null,
    evaluated_at timestamptz not null,
    candidate_limit integer not null,
    status text not null,
    finished_at timestamptz,
    scanned_count integer,
    report_skipped_count integer,
    assignment_not_claimed_count integer,
    recovery_finished_count integer,
    recovery_not_started_count integer,
    recovery_failed_count integer,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ai_reservation_recovery_work_runs_id_not_blank check (btrim(run_id) <> ''),
    constraint ai_reservation_recovery_work_runs_worker_not_blank check (btrim(worker_id) <> ''),
    constraint ai_reservation_recovery_work_runs_maximum_attempts_positive check (maximum_attempts > 0),
    constraint ai_reservation_recovery_work_runs_candidate_limit_positive check (candidate_limit > 0),
    constraint ai_reservation_recovery_work_runs_evaluation_order check (evaluated_at >= stale_at_or_before),
    constraint ai_reservation_recovery_work_runs_status_valid check (
        status in ('RUNNING', 'COMPLETED', 'FAILED')
    ),
    constraint ai_reservation_recovery_work_runs_result_fields check (
        (status = 'RUNNING'
            and finished_at is null
            and scanned_count is null
            and report_skipped_count is null
            and assignment_not_claimed_count is null
            and recovery_finished_count is null
            and recovery_not_started_count is null
            and recovery_failed_count is null)
        or (status = 'COMPLETED'
            and finished_at is not null
            and scanned_count is not null
            and report_skipped_count is not null
            and assignment_not_claimed_count is not null
            and recovery_finished_count is not null
            and recovery_not_started_count is not null
            and recovery_failed_count is not null
            and scanned_count = report_skipped_count + assignment_not_claimed_count
                + recovery_finished_count + recovery_not_started_count + recovery_failed_count)
        or (status = 'FAILED'
            and finished_at is not null
            and scanned_count is null
            and report_skipped_count is null
            and assignment_not_claimed_count is null
            and recovery_finished_count is null
            and recovery_not_started_count is null
            and recovery_failed_count is null)
    ),
    constraint ai_reservation_recovery_work_runs_counts_non_negative check (
        (scanned_count is null or scanned_count >= 0)
        and (report_skipped_count is null or report_skipped_count >= 0)
        and (assignment_not_claimed_count is null or assignment_not_claimed_count >= 0)
        and (recovery_finished_count is null or recovery_finished_count >= 0)
        and (recovery_not_started_count is null or recovery_not_started_count >= 0)
        and (recovery_failed_count is null or recovery_failed_count >= 0)
    ),
    constraint ai_reservation_recovery_work_runs_finish_order check (
        finished_at is null or finished_at >= evaluated_at
    )
);

create index ai_reservation_recovery_work_runs_status_time_idx
    on ai_reservation_recovery_work_runs (status, evaluated_at);
