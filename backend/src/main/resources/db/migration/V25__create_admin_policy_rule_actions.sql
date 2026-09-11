CREATE TABLE admin_policy_rule_actions (
    request_id uuid PRIMARY KEY,
    policy_number text NOT NULL REFERENCES policies(policy_number),
    version_id uuid NOT NULL REFERENCES policy_rule_versions(id),
    action text NOT NULL CHECK (action IN ('DRAFT', 'PUBLISH')),
    actor_id uuid NOT NULL,
    expected_revision bigint NOT NULL CHECK (expected_revision > 0),
    expected_rule_version varchar(80),
    reason varchar(500) NOT NULL CHECK (length(btrim(reason)) > 0),
    performed_at timestamptz NOT NULL DEFAULT statement_timestamp(),
    UNIQUE (version_id, action),
    CHECK ((action = 'PUBLISH') = (expected_rule_version IS NOT NULL))
);
