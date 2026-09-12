CREATE TABLE policy_ai_rule_requests (
    id uuid PRIMARY KEY,
    sequence bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
    policy_number text NOT NULL,
    revision bigint NOT NULL,
    content_hash char(64) NOT NULL CHECK (content_hash ~ '^[a-f0-9]{64}$'),
    generation_version varchar(100) NOT NULL CHECK (btrim(generation_version) <> ''),
    requested_by varchar(100) NOT NULL CHECK (btrim(requested_by) <> ''),
    prepared_at timestamptz NOT NULL DEFAULT statement_timestamp(),
    FOREIGN KEY (policy_number, revision) REFERENCES policy_revisions(policy_number, revision)
);

CREATE INDEX policy_ai_rule_requests_latest ON policy_ai_rule_requests(policy_number, sequence DESC);

CREATE TABLE policy_ai_rule_candidates (
    request_id uuid PRIMARY KEY REFERENCES policy_ai_rule_requests(id),
    body text NOT NULL CHECK (octet_length(body) <= 131072),
    body_sha256 char(64) NOT NULL CHECK (body_sha256 ~ '^[a-f0-9]{64}$'),
    status text NOT NULL CHECK (status IN ('DRAFT_CREATED', 'SOURCE_CHANGED', 'REQUEST_SUPERSEDED', 'INVALID_DEFINITION', 'INVALID_REFERENCE', 'VERSION_CONFLICT')),
    version_id uuid UNIQUE REFERENCES policy_rule_versions(id),
    recorded_at timestamptz NOT NULL DEFAULT statement_timestamp(),
    CHECK ((status = 'DRAFT_CREATED') = (version_id IS NOT NULL))
);

CREATE FUNCTION reject_policy_ai_rule_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'AI rule requests and candidates are immutable';
END;
$$;

CREATE TRIGGER policy_ai_rule_requests_immutable BEFORE UPDATE ON policy_ai_rule_requests
FOR EACH ROW EXECUTE FUNCTION reject_policy_ai_rule_update();
CREATE TRIGGER policy_ai_rule_candidates_immutable BEFORE UPDATE ON policy_ai_rule_candidates
FOR EACH ROW EXECUTE FUNCTION reject_policy_ai_rule_update();
