create table ai_reservation_recovery_lease_renewals (
    renewal_id text primary key,
    attempt_id text not null references ai_reservation_recovery_attempts (attempt_id),
    attempt_number bigint not null,
    owner_id text not null,
    observed_lease_until timestamptz not null,
    renewed_at timestamptz not null,
    renewed_lease_until timestamptz not null,
    constraint ai_reservation_recovery_lease_renewals_id_not_blank check (btrim(renewal_id) <> ''),
    constraint ai_reservation_recovery_lease_renewals_owner_not_blank check (btrim(owner_id) <> ''),
    constraint ai_reservation_recovery_lease_renewals_number_positive check (attempt_number > 0),
    constraint ai_reservation_recovery_lease_renewals_active_time check (
        renewed_at < observed_lease_until
    ),
    constraint ai_reservation_recovery_lease_renewals_extension_time check (
        observed_lease_until < renewed_lease_until
    ),
    constraint ai_reservation_recovery_lease_renewals_attempt_snapshot_unique unique (
        attempt_id, observed_lease_until
    )
);
