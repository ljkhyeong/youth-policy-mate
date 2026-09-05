CREATE TABLE members (
    id uuid PRIMARY KEY,
    provider text NOT NULL CHECK (provider IN ('kakao', 'naver')),
    provider_subject text NOT NULL,
    display_name text NOT NULL,
    conditions jsonb,
    conditions_updated_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, provider_subject)
);

CREATE TABLE saved_policies (
    member_id uuid NOT NULL REFERENCES members(id),
    policy_number text NOT NULL REFERENCES policies(policy_number),
    generation uuid NOT NULL,
    saved_revision bigint NOT NULL,
    current_revision bigint NOT NULL,
    deadline_on date,
    deadline_note text NOT NULL,
    saved_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id, policy_number)
);

CREATE TABLE policy_reminders (
    id uuid PRIMARY KEY,
    member_id uuid NOT NULL REFERENCES members(id),
    policy_number text NOT NULL REFERENCES policies(policy_number),
    generation uuid NOT NULL,
    policy_revision bigint NOT NULL,
    days_before integer NOT NULL CHECK (days_before IN (7,3,1)),
    due_on date NOT NULL,
    state text NOT NULL CHECK (state IN ('PENDING','DELIVERED','CANCELED','SKIPPED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at timestamptz,
    UNIQUE (member_id, policy_number, generation, policy_revision, days_before)
);
CREATE INDEX policy_reminders_pending ON policy_reminders(due_on, member_id) WHERE state = 'PENDING';

CREATE TABLE member_notifications (
    id uuid PRIMARY KEY,
    member_id uuid NOT NULL REFERENCES members(id),
    policy_number text NOT NULL REFERENCES policies(policy_number),
    generation uuid NOT NULL,
    policy_revision bigint NOT NULL,
    kind text NOT NULL,
    title text NOT NULL,
    message text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at timestamptz,
    UNIQUE (member_id, policy_number, generation, policy_revision, kind)
);
CREATE INDEX member_notifications_recent ON member_notifications(member_id, created_at DESC);
