CREATE TABLE policies (
    policy_number text PRIMARY KEY,
    current_revision bigint NOT NULL DEFAULT 0 CHECK (current_revision >= 0),
    content_hash text NOT NULL DEFAULT '',
    content jsonb NOT NULL DEFAULT '{}'::jsonb,
    last_collected_at timestamptz,
    CHECK ((current_revision = 0) = (last_collected_at IS NULL))
);

CREATE TABLE policy_source_snapshots (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_number text NOT NULL REFERENCES policies(policy_number),
    capture_hash text NOT NULL,
    captured_at timestamptz NOT NULL,
    raw_policy jsonb NOT NULL,
    UNIQUE (policy_number, capture_hash)
);

CREATE TABLE policy_revisions (
    policy_number text NOT NULL REFERENCES policies(policy_number),
    revision bigint NOT NULL CHECK (revision > 0),
    source_snapshot_id bigint NOT NULL REFERENCES policy_source_snapshots(id),
    content jsonb NOT NULL,
    PRIMARY KEY (policy_number, revision)
);

CREATE INDEX policies_collected_order ON policies(last_collected_at DESC, policy_number);
