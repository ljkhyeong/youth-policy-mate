CREATE TABLE admin_collection_replays (
    request_id uuid PRIMARY KEY,
    run_id uuid NOT NULL,
    item_index integer NOT NULL,
    actor_id uuid NOT NULL,
    reason varchar(500) NOT NULL CHECK (length(btrim(reason)) > 0),
    expected_attempts integer NOT NULL CHECK (expected_attempts >= 0),
    attempt integer NOT NULL,
    outcome text NOT NULL CHECK (outcome IN ('APPLIED', 'UNCHANGED', 'REPLAYED', 'STALE', 'INVALID_ITEM')),
    policy_number text,
    policy_revision bigint CHECK (policy_revision > 0),
    processed_at timestamptz NOT NULL DEFAULT statement_timestamp(),
    FOREIGN KEY (run_id, item_index, attempt) REFERENCES ontong_collection_item_attempts(run_id, item_index, attempt),
    UNIQUE (run_id, item_index, attempt)
);

CREATE INDEX admin_collection_replays_order ON admin_collection_replays(processed_at DESC, request_id);
