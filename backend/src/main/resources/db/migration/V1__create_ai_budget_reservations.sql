create table ai_budgets (
    budget_id text primary key,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    limit_won numeric not null,
    confirmed_won numeric not null default 0,
    reserved_won numeric not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ai_budgets_id_not_blank check (btrim(budget_id) <> ''),
    constraint ai_budgets_period_valid check (starts_at < ends_at),
    constraint ai_budgets_limit_non_negative check (limit_won >= 0),
    constraint ai_budgets_confirmed_non_negative check (confirmed_won >= 0),
    constraint ai_budgets_reserved_non_negative check (reserved_won >= 0)
);

create table ai_request_reservations (
    reservation_id text primary key,
    budget_id text not null references ai_budgets (budget_id),
    policy_id text not null,
    source_revision_number bigint not null,
    source_collection_sequence bigint not null,
    source_observed_at timestamptz not null,
    source_name text not null,
    source_snapshot_id text not null,
    source_body_sha256 char(64) not null,
    comparison_version text not null,
    source_content_sha256 char(64) not null,
    ai_kind text not null,
    generation_version text not null,
    request_sequence bigint not null,
    request_prepared_at timestamptz not null,
    pricing_version text not null,
    cost_valid_until timestamptz not null,
    maximum_won numeric not null,
    phase text not null,
    reserved_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ai_request_reservations_id_not_blank check (btrim(reservation_id) <> ''),
    constraint ai_request_reservations_budget_id_not_blank check (btrim(budget_id) <> ''),
    constraint ai_request_reservations_policy_id_not_blank check (btrim(policy_id) <> ''),
    constraint ai_request_reservations_source_revision_positive check (source_revision_number > 0),
    constraint ai_request_reservations_collection_sequence_positive check (source_collection_sequence > 0),
    constraint ai_request_reservations_source_name_not_blank check (btrim(source_name) <> ''),
    constraint ai_request_reservations_snapshot_id_not_blank check (btrim(source_snapshot_id) <> ''),
    constraint ai_request_reservations_body_sha256_valid check (source_body_sha256 ~ '^[0-9a-f]{64}$'),
    constraint ai_request_reservations_comparison_version_not_blank check (btrim(comparison_version) <> ''),
    constraint ai_request_reservations_content_sha256_valid check (source_content_sha256 ~ '^[0-9a-f]{64}$'),
    constraint ai_request_reservations_kind_valid check (ai_kind in ('SUMMARY', 'CONDITION_EXTRACTION')),
    constraint ai_request_reservations_generation_version_not_blank check (btrim(generation_version) <> ''),
    constraint ai_request_reservations_request_sequence_positive check (request_sequence > 0),
    constraint ai_request_reservations_pricing_version_not_blank check (btrim(pricing_version) <> ''),
    constraint ai_request_reservations_cost_period_valid check (request_prepared_at < cost_valid_until),
    constraint ai_request_reservations_maximum_non_negative check (maximum_won >= 0),
    constraint ai_request_reservations_phase_valid check (
        phase in ('HELD', 'DISPATCHED', 'OUTCOME_UNKNOWN', 'SETTLED', 'CANCELLED', 'RELEASED_NO_CHARGE')
    ),
    constraint ai_request_reservations_reserved_after_prepared check (reserved_at >= request_prepared_at),
    constraint ai_request_reservations_request_unique unique (policy_id, ai_kind, request_sequence)
);

create index ai_request_reservations_budget_phase_idx
    on ai_request_reservations (budget_id, phase);
