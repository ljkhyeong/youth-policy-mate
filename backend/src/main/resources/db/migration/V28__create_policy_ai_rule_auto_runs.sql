CREATE TABLE policy_ai_rule_auto_runs (
    id uuid PRIMARY KEY,
    request_id uuid NOT NULL REFERENCES policy_ai_rule_requests(id),
    attempt integer NOT NULL CHECK (attempt > 0),
    started_at timestamptz NOT NULL,
    lease_until timestamptz NOT NULL CHECK (lease_until > started_at),
    finished_at timestamptz CHECK (finished_at >= started_at),
    state text NOT NULL CHECK (state IN ('RUNNING', 'COMPLETED', 'RETRY_PENDING', 'INTERRUPTED', 'REVIEW_REQUIRED', 'SUPERSEDED')),
    result_code text,
    UNIQUE (request_id, attempt),
    CHECK ((state = 'RUNNING') = (finished_at IS NULL))
);
CREATE INDEX policy_ai_rule_auto_runs_started_idx ON policy_ai_rule_auto_runs(started_at DESC);
CREATE INDEX policy_ai_rule_auto_runs_running_idx ON policy_ai_rule_auto_runs(lease_until) WHERE state = 'RUNNING';
CREATE INDEX policy_ai_rule_requests_revision_idx ON policy_ai_rule_requests(policy_number, revision);
