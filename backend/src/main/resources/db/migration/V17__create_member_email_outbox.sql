CREATE TABLE member_email_settings (
    member_id uuid PRIMARY KEY REFERENCES members(id) ON DELETE CASCADE,
    version uuid NOT NULL,
    address_cipher text NOT NULL,
    verified_at timestamptz,
    enabled boolean NOT NULL DEFAULT false,
    consented_at timestamptz,
    code_hash text,
    expires_at timestamptz,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    CHECK (NOT enabled OR (verified_at IS NOT NULL AND consented_at IS NOT NULL)),
    CHECK ((code_hash IS NULL) = (expires_at IS NULL))
);
CREATE TABLE member_email_outbox (
    id uuid PRIMARY KEY,
    member_id uuid NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    settings_version uuid NOT NULL,
    kind varchar(20) NOT NULL CHECK (kind IN ('VERIFICATION', 'POLICY')),
    notification_id uuid UNIQUE REFERENCES member_notifications(id) ON DELETE CASCADE,
    code_cipher text,
    state varchar(20) NOT NULL CHECK (state IN ('PENDING','SENDING','SENT','FAILED','UNKNOWN','CANCELED')),
    created_at timestamptz NOT NULL,
    expires_at timestamptz,
    started_at timestamptz,
    finished_at timestamptz,
    CHECK ((kind = 'POLICY') = (notification_id IS NOT NULL)),
    CHECK (kind = 'VERIFICATION' OR code_cipher IS NULL)
);
CREATE INDEX member_email_pending ON member_email_outbox(created_at) WHERE state = 'PENDING';
CREATE INDEX member_email_requests ON member_email_outbox(member_id, created_at) WHERE kind = 'VERIFICATION';
