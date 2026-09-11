CREATE TABLE policy_rule_versions (
    id uuid PRIMARY KEY,
    policy_number varchar(20) NOT NULL,
    rule_version varchar(80) NOT NULL,
    definition jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by text NOT NULL,
    reason text NOT NULL,
    published_at timestamptz,
    published_by text,
    UNIQUE (policy_number, rule_version),
    UNIQUE (policy_number, id),
    CHECK (definition->>'policyNumber' = policy_number AND definition->>'ruleVersion' = rule_version),
    CHECK ((published_at IS NULL) = (published_by IS NULL))
);
CREATE TABLE policy_rule_heads (
    policy_number varchar(20) PRIMARY KEY,
    version_id uuid NOT NULL,
    FOREIGN KEY (policy_number, version_id) REFERENCES policy_rule_versions(policy_number, id)
);

CREATE FUNCTION preserve_policy_rule_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.policy_number IS DISTINCT FROM OLD.policy_number
        OR NEW.rule_version IS DISTINCT FROM OLD.rule_version OR NEW.definition IS DISTINCT FROM OLD.definition
        OR NEW.created_at IS DISTINCT FROM OLD.created_at OR NEW.created_by IS DISTINCT FROM OLD.created_by
        OR NEW.reason IS DISTINCT FROM OLD.reason OR OLD.published_at IS NOT NULL THEN
        RAISE EXCEPTION '공고 규칙은 변경할 수 없습니다. 새 버전을 등록해주세요.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER policy_rule_version_immutable BEFORE UPDATE ON policy_rule_versions
FOR EACH ROW EXECUTE FUNCTION preserve_policy_rule_version();
