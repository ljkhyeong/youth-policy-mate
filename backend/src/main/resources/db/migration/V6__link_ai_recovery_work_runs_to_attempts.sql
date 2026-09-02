create table ai_reservation_recovery_work_run_attempts (
    attempt_id text primary key references ai_reservation_recovery_attempts (attempt_id),
    run_id text not null references ai_reservation_recovery_work_runs (run_id)
);

create index ai_reservation_recovery_work_run_attempts_run_idx
    on ai_reservation_recovery_work_run_attempts (run_id, attempt_id);
