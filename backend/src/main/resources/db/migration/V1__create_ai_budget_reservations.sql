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
