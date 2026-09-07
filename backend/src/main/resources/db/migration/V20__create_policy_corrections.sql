CREATE TABLE policy_corrections (
    id uuid PRIMARY KEY,
    policy_number text NOT NULL REFERENCES policies(policy_number),
    field text NOT NULL CHECK (field IN ('TITLE', 'ORGANIZATION')),
    source_snapshot_id bigint NOT NULL REFERENCES policy_source_snapshots(id),
    value varchar(500) NOT NULL CHECK (length(btrim(value)) > 0),
    reason varchar(500) NOT NULL CHECK (length(btrim(reason)) > 0),
    actor_id uuid NOT NULL,
    requested_revision bigint NOT NULL CHECK (requested_revision > 0),
    applied_revision bigint NOT NULL CHECK (applied_revision > 0),
    created_at timestamptz NOT NULL DEFAULT statement_timestamp(),
    status text NOT NULL CHECK (status IN ('ACTIVE', 'CONFLICT', 'RELEASED')),
    conflict_snapshot_id bigint REFERENCES policy_source_snapshots(id),
    previous_correction_id uuid REFERENCES policy_corrections(id),
    resolved_request_id uuid UNIQUE,
    resolution text CHECK (resolution IN ('KEEP', 'USE_SOURCE')),
    resolved_by uuid,
    resolved_reason varchar(500),
    resolved_snapshot_id bigint REFERENCES policy_source_snapshots(id),
    resolved_expected_revision bigint,
    resolved_revision bigint,
    resolved_at timestamptz,
    CHECK ((status = 'RELEASED') = (resolved_request_id IS NOT NULL)),
    CHECK (status <> 'CONFLICT' OR conflict_snapshot_id IS NOT NULL)
);

CREATE UNIQUE INDEX policy_corrections_active ON policy_corrections(policy_number) WHERE status <> 'RELEASED';
CREATE INDEX policy_corrections_created ON policy_corrections(created_at DESC, id);
ALTER TABLE policy_revisions ADD COLUMN correction_id uuid REFERENCES policy_corrections(id);

ALTER TABLE ontong_collection_items DROP CONSTRAINT ontong_collection_items_outcome_check;
ALTER TABLE ontong_collection_items ADD CHECK (outcome IN ('PENDING','APPLIED','UNCHANGED','REPLAYED','STALE','INVALID_ITEM','STORE_FAILED','CORRECTION_CONFLICT'));
ALTER TABLE ontong_collection_item_attempts DROP CONSTRAINT ontong_collection_item_attempts_outcome_check;
ALTER TABLE ontong_collection_item_attempts ADD CHECK (outcome IN ('APPLIED','UNCHANGED','REPLAYED','STALE','INVALID_ITEM','STORE_FAILED','CORRECTION_CONFLICT'));
ALTER TABLE admin_collection_replays DROP CONSTRAINT admin_collection_replays_outcome_check;
ALTER TABLE admin_collection_replays ADD CHECK (outcome IN ('APPLIED','UNCHANGED','REPLAYED','STALE','INVALID_ITEM','CORRECTION_CONFLICT'));
