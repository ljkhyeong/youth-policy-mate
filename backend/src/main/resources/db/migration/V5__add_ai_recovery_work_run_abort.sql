alter table ai_reservation_recovery_work_runs
    add column abort_reason text,
    add column aborted_by text;

alter table ai_reservation_recovery_work_runs
    drop constraint ai_reservation_recovery_work_runs_status_valid,
    drop constraint ai_reservation_recovery_work_runs_result_fields;

alter table ai_reservation_recovery_work_runs
    add constraint ai_reservation_recovery_work_runs_status_valid check (
        status in ('RUNNING', 'COMPLETED', 'FAILED', 'ABORTED')
    ),
    add constraint ai_reservation_recovery_work_runs_abort_fields_not_blank check (
        (abort_reason is null or btrim(abort_reason) <> '')
        and (aborted_by is null or btrim(aborted_by) <> '')
    ),
    add constraint ai_reservation_recovery_work_runs_result_fields check (
        (status = 'RUNNING'
            and finished_at is null
            and scanned_count is null
            and report_skipped_count is null
            and assignment_not_claimed_count is null
            and recovery_finished_count is null
            and recovery_not_started_count is null
            and recovery_failed_count is null
            and abort_reason is null
            and aborted_by is null)
        or (status = 'COMPLETED'
            and finished_at is not null
            and scanned_count is not null
            and report_skipped_count is not null
            and assignment_not_claimed_count is not null
            and recovery_finished_count is not null
            and recovery_not_started_count is not null
            and recovery_failed_count is not null
            and scanned_count = report_skipped_count + assignment_not_claimed_count
                + recovery_finished_count + recovery_not_started_count + recovery_failed_count
            and abort_reason is null
            and aborted_by is null)
        or (status = 'FAILED'
            and finished_at is not null
            and scanned_count is null
            and report_skipped_count is null
            and assignment_not_claimed_count is null
            and recovery_finished_count is null
            and recovery_not_started_count is null
            and recovery_failed_count is null
            and abort_reason is null
            and aborted_by is null)
        or (status = 'ABORTED'
            and finished_at is not null
            and scanned_count is null
            and report_skipped_count is null
            and assignment_not_claimed_count is null
            and recovery_finished_count is null
            and recovery_not_started_count is null
            and recovery_failed_count is null
            and abort_reason in ('PROCESS_TERMINATED', 'WORKER_UNREACHABLE', 'OPERATOR_DECISION')
            and aborted_by is not null)
    );
