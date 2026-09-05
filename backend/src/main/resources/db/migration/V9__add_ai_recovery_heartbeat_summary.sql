-- 기존 완료 기록은 heartbeat 중단을 분리하지 않았으므로 NULL을 유지한다.
alter table ai_reservation_recovery_work_runs
    add column heartbeat_stopped_count integer;

alter table ai_reservation_recovery_work_runs
    drop constraint ai_reservation_recovery_work_runs_result_fields;

alter table ai_reservation_recovery_work_runs
    add constraint ai_recovery_work_runs_heartbeat_non_negative check (
        heartbeat_stopped_count is null or heartbeat_stopped_count >= 0
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
            and heartbeat_stopped_count is null
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
            and scanned_count = report_skipped_count::bigint + assignment_not_claimed_count
                + recovery_finished_count + recovery_not_started_count + recovery_failed_count
                + coalesce(heartbeat_stopped_count, 0)
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
            and heartbeat_stopped_count is null
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
            and heartbeat_stopped_count is null
            and abort_reason in ('PROCESS_TERMINATED', 'WORKER_UNREACHABLE', 'OPERATOR_DECISION')
            and aborted_by is not null)
    );
