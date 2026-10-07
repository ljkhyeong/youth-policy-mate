-- 운영 DB를 만들기 전에 이전 마이그레이션 V1~V33을 하나로 합친 기준 스키마다.
-- 이후 변경은 V3부터 새 파일로 추가한다.

-- AI 월 예산. 호출별 예약·정산은 policy_ai_rule_calls에 둔다.
CREATE TABLE ai_budgets (
    budget_id text PRIMARY KEY,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    limit_won numeric NOT NULL,
    confirmed_won numeric NOT NULL DEFAULT 0,
    reserved_won numeric NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ai_budgets_id_not_blank CHECK (btrim(budget_id) <> ''),
    CONSTRAINT ai_budgets_period_valid CHECK (starts_at < ends_at),
    CONSTRAINT ai_budgets_limit_non_negative CHECK (limit_won >= 0),
    CONSTRAINT ai_budgets_confirmed_non_negative CHECK (confirmed_won >= 0),
    CONSTRAINT ai_budgets_reserved_non_negative CHECK (reserved_won >= 0)
);

-- 정책 원본·현재 내용·개정. 검색용 모집 기간은 현재 개정에서 계산해 함께 저장한다.
CREATE TABLE policies (
    policy_number text PRIMARY KEY,
    current_revision bigint NOT NULL DEFAULT 0 CHECK (current_revision >= 0),
    content_hash text NOT NULL DEFAULT '',
    content jsonb NOT NULL DEFAULT '{}'::jsonb,
    last_collected_at timestamptz,
    last_request_sequence bigint NOT NULL DEFAULT 0 CHECK (last_request_sequence >= 0),
    recruitment_kind text NOT NULL DEFAULT 'UNKNOWN',
    recruitment_opens_at timestamptz,
    recruitment_closes_at timestamptz,
    CHECK ((current_revision = 0) = (last_collected_at IS NULL)),
    CONSTRAINT policies_recruitment_window CHECK (
        (recruitment_kind = 'PERIOD' AND recruitment_opens_at IS NOT NULL
            AND recruitment_closes_at IS NOT NULL AND recruitment_opens_at < recruitment_closes_at)
        OR (recruitment_kind IN ('ROLLING', 'UNTIL_EXHAUSTED', 'CLOSED', 'UNKNOWN')
            AND recruitment_opens_at IS NULL AND recruitment_closes_at IS NULL))
);
CREATE INDEX policies_collected_order ON policies(last_collected_at DESC, policy_number);
CREATE INDEX policies_recruitment_kind ON policies(recruitment_kind);

CREATE TABLE policy_source_snapshots (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_number text NOT NULL REFERENCES policies(policy_number),
    capture_hash text NOT NULL,
    captured_at timestamptz NOT NULL,
    raw_policy jsonb NOT NULL,
    UNIQUE (policy_number, capture_hash)
);

-- 관리자 보정은 원본과 분리해 저장하고, 새 원본과 충돌하면 관리자가 해소한다.
CREATE TABLE policy_corrections (
    id uuid PRIMARY KEY,
    policy_number text NOT NULL REFERENCES policies(policy_number),
    field text NOT NULL CHECK (field IN ('TITLE', 'ORGANIZATION')),
    source_snapshot_id bigint NOT NULL REFERENCES policy_source_snapshots(id),
    value varchar(500) NOT NULL CHECK (length(btrim(value)) > 0),
    reason varchar(500) NOT NULL CHECK (length(btrim(reason)) > 0),
    actor_id uuid NOT NULL,
    requested_revision bigint NOT NULL CHECK (requested_revision > 0),
    created_at timestamptz NOT NULL DEFAULT statement_timestamp(),
    status text NOT NULL CHECK (status IN ('ACTIVE', 'CONFLICT', 'RELEASED')),
    conflict_snapshot_id bigint REFERENCES policy_source_snapshots(id),
    resolved_request_id uuid UNIQUE,
    resolution text CHECK (resolution IN ('KEEP', 'USE_SOURCE')),
    resolved_by uuid,
    resolved_reason varchar(500),
    resolved_snapshot_id bigint REFERENCES policy_source_snapshots(id),
    resolved_expected_revision bigint,
    resolved_at timestamptz,
    CHECK ((status = 'RELEASED') = (resolved_request_id IS NOT NULL)),
    CHECK (status <> 'CONFLICT' OR conflict_snapshot_id IS NOT NULL)
);
CREATE UNIQUE INDEX policy_corrections_active ON policy_corrections(policy_number) WHERE status <> 'RELEASED';
CREATE INDEX policy_corrections_created ON policy_corrections(created_at DESC, id);

CREATE TABLE policy_revisions (
    policy_number text NOT NULL REFERENCES policies(policy_number),
    revision bigint NOT NULL CHECK (revision > 0),
    source_snapshot_id bigint NOT NULL REFERENCES policy_source_snapshots(id),
    content jsonb NOT NULL,
    correction_id uuid REFERENCES policy_corrections(id),
    PRIMARY KEY (policy_number, revision)
);

-- 온통청년 수집. 요청 순번·원본·항목 결과로 재처리 위치와 오래된 응답을 판정한다.
CREATE TABLE ontong_collection_pages (
    request_sequence bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id uuid NOT NULL UNIQUE,
    page_number integer NOT NULL CHECK (page_number BETWEEN 1 AND 1000),
    state text NOT NULL CHECK (state IN ('FETCHING', 'RECEIVED', 'READY', 'FETCH_FAILED', 'INVALID_RESPONSE')),
    started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_at timestamptz,
    raw_body text,
    failure_code text,
    total_count bigint CHECK (total_count >= 0),
    item_count integer CHECK (item_count BETWEEN 0 AND 10),
    dispatch_started_at timestamptz,
    CHECK ((raw_body IS NULL) = (received_at IS NULL))
);
CREATE INDEX ontong_collection_pages_started_at_idx ON ontong_collection_pages(started_at);

CREATE TABLE ontong_collection_items (
    run_id uuid NOT NULL REFERENCES ontong_collection_pages(run_id),
    item_index integer NOT NULL CHECK (item_index BETWEEN 0 AND 9),
    raw_policy jsonb NOT NULL,
    policy_number text,
    outcome text NOT NULL DEFAULT 'PENDING'
        CHECK (outcome IN ('PENDING', 'APPLIED', 'UNCHANGED', 'REPLAYED', 'STALE', 'INVALID_ITEM', 'STORE_FAILED', 'CORRECTION_CONFLICT')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    updated_at timestamptz,
    PRIMARY KEY (run_id, item_index)
);

CREATE TABLE ontong_collection_sweeps (
    id uuid PRIMARY KEY,
    origin text NOT NULL CHECK (origin IN ('MANUAL', 'SCHEDULED')),
    first_page integer NOT NULL CHECK (first_page BETWEEN 1 AND 1000),
    last_page integer NOT NULL CHECK (last_page BETWEEN first_page AND 1000),
    next_page integer NOT NULL CHECK (next_page BETWEEN first_page AND last_page + 1),
    state text NOT NULL CHECK (state IN ('ACTIVE', 'PAUSED', 'COMPLETED', 'PARTIAL', 'ABANDONED')),
    started_at timestamptz NOT NULL,
    completed_at timestamptz,
    last_successful_at timestamptz,
    failure_code text,
    CHECK ((state IN ('COMPLETED', 'PARTIAL', 'ABANDONED')) = (completed_at IS NOT NULL))
);
CREATE UNIQUE INDEX ontong_one_open_sweep ON ontong_collection_sweeps((true)) WHERE state IN ('ACTIVE', 'PAUSED');
CREATE INDEX ontong_sweeps_started_at_idx ON ontong_collection_sweeps(started_at DESC);

CREATE TABLE ontong_collection_sweep_pages (
    sweep_id uuid NOT NULL REFERENCES ontong_collection_sweeps(id),
    page_number integer NOT NULL CHECK (page_number BETWEEN 1 AND 1000),
    run_id uuid NOT NULL UNIQUE REFERENCES ontong_collection_pages(run_id),
    outcome text NOT NULL CHECK (outcome IN ('REQUESTED', 'COMPLETED', 'PARTIAL', 'FAILED')),
    failure_code text,
    PRIMARY KEY (sweep_id, page_number)
);

CREATE TABLE admin_collection_replays (
    request_id uuid PRIMARY KEY,
    run_id uuid NOT NULL,
    item_index integer NOT NULL,
    actor_id uuid NOT NULL,
    reason varchar(500) NOT NULL CHECK (length(btrim(reason)) > 0),
    expected_attempts integer NOT NULL CHECK (expected_attempts >= 0),
    attempt integer NOT NULL,
    outcome text NOT NULL CHECK (outcome IN ('APPLIED', 'UNCHANGED', 'REPLAYED', 'STALE', 'INVALID_ITEM', 'CORRECTION_CONFLICT')),
    policy_number text,
    policy_revision bigint CHECK (policy_revision > 0),
    processed_at timestamptz NOT NULL DEFAULT statement_timestamp(),
    FOREIGN KEY (run_id, item_index) REFERENCES ontong_collection_items(run_id, item_index),
    UNIQUE (run_id, item_index, attempt)
);
CREATE INDEX admin_collection_replays_order ON admin_collection_replays(processed_at DESC, request_id);

-- 회원. 소셜 제공자의 고정 식별자로만 구분하고, 탈퇴하면 개인 데이터를 함께 지운다.
CREATE TABLE members (
    id uuid PRIMARY KEY,
    provider text NOT NULL CHECK (provider IN ('kakao', 'naver')),
    provider_subject text NOT NULL,
    conditions jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, provider_subject)
);

CREATE TABLE saved_policies (
    member_id uuid NOT NULL REFERENCES members(id) ON DELETE CASCADE,
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
    member_id uuid NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    policy_number text NOT NULL REFERENCES policies(policy_number),
    generation uuid NOT NULL,
    policy_revision bigint NOT NULL,
    days_before integer NOT NULL CHECK (days_before IN (7,3,1)),
    due_on date NOT NULL,
    state text NOT NULL CHECK (state IN ('PENDING','DELIVERED','CANCELED','SKIPPED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (member_id, policy_number, generation, policy_revision, days_before)
);
CREATE INDEX policy_reminders_pending ON policy_reminders(due_on, member_id) WHERE state = 'PENDING';

CREATE TABLE member_notifications (
    id uuid PRIMARY KEY,
    member_id uuid NOT NULL REFERENCES members(id) ON DELETE CASCADE,
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

-- Spring Session JDBC 공식 PostgreSQL 세션 스키마를 사용한다.
CREATE TABLE SPRING_SESSION (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BYTEA NOT NULL,
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);

-- 이메일 주소는 암호화해 저장하고, 발송은 Outbox로 트랜잭션 밖에서 처리한다.
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
    delivery_issue varchar(20) CHECK (delivery_issue IN ('BOUNCED', 'COMPLAINED', 'SUPPRESSED')),
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
    state varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz,
    started_at timestamptz,
    finished_at timestamptz,
    provider_message_id uuid UNIQUE,
    provider_event_at timestamptz,
    unsubscribe_token_hash varchar(64),
    CHECK ((kind = 'POLICY') = (notification_id IS NOT NULL)),
    CHECK (kind = 'VERIFICATION' OR code_cipher IS NULL),
    CONSTRAINT member_email_outbox_state_check
        CHECK (state IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'UNKNOWN', 'CANCELED',
                         'DELIVERED', 'DELAYED', 'BOUNCED', 'COMPLAINED', 'SUPPRESSED')),
    CONSTRAINT member_email_unsubscribe_kind CHECK (unsubscribe_token_hash IS NULL OR kind = 'POLICY')
);
CREATE INDEX member_email_pending ON member_email_outbox(created_at) WHERE state = 'PENDING';
CREATE INDEX member_email_requests ON member_email_outbox(member_id, created_at) WHERE kind = 'VERIFICATION';
CREATE INDEX member_email_outbox_history ON member_email_outbox (created_at DESC, id DESC);
CREATE UNIQUE INDEX member_email_unsubscribe_token ON member_email_outbox(unsubscribe_token_hash)
    WHERE unsubscribe_token_hash IS NOT NULL;

-- 공고별 질문·판정 규칙. 적용한 버전은 바꾸지 않고 새 버전을 등록한다.
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

-- AI 규칙 추출. 요청·후보는 바꾸지 않고, 호출 한 건과 그 비용 예약·수명주기를 한 행에 둔다.
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
CREATE INDEX policy_ai_rule_requests_revision_idx ON policy_ai_rule_requests(policy_number, revision);

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

CREATE TABLE policy_ai_rule_calls (
    request_id uuid PRIMARY KEY REFERENCES policy_ai_rule_requests(id),
    budget_id text NOT NULL REFERENCES ai_budgets(budget_id),
    request_body jsonb NOT NULL,
    input_tokens bigint NOT NULL CHECK (input_tokens > 0),
    input_won_per_million numeric NOT NULL CHECK (input_won_per_million > 0),
    output_won_per_million numeric NOT NULL CHECK (output_won_per_million > 0),
    pricing_version text NOT NULL CHECK (btrim(pricing_version) <> ''),
    maximum_won numeric NOT NULL CHECK (maximum_won >= 0),
    reserved_at timestamptz NOT NULL,
    cost_valid_until timestamptz NOT NULL,
    phase text NOT NULL CHECK (phase IN ('HELD', 'DISPATCHED', 'OUTCOME_UNKNOWN', 'SETTLED', 'CANCELLED', 'RELEASED_NO_CHARGE')),
    dispatched_at timestamptz,
    outcome_unknown_at timestamptz,
    completed_at timestamptz,
    completion_id text CHECK (btrim(completion_id) <> ''),
    actual_won numeric CHECK (actual_won >= 0),
    response_status integer CHECK (response_status BETWEEN 100 AND 599),
    response_body text CHECK (octet_length(response_body) <= 1048576),
    received_at timestamptz,
    CHECK (reserved_at < cost_valid_until),
    CHECK (dispatched_at >= reserved_at),
    CHECK (outcome_unknown_at >= dispatched_at),
    CHECK (completed_at >= greatest(reserved_at, dispatched_at, outcome_unknown_at)),
    CHECK ((response_status IS NULL) = (response_body IS NULL) AND (response_body IS NULL) = (received_at IS NULL)),
    -- 단계마다 있어야 할 기록을 고정한다. 완료 ID는 운영자가 확인한 청구·무과금 근거에만 둔다.
    CHECK (CASE phase
        WHEN 'HELD' THEN dispatched_at IS NULL AND outcome_unknown_at IS NULL AND completed_at IS NULL
            AND completion_id IS NULL AND actual_won IS NULL
        WHEN 'DISPATCHED' THEN dispatched_at IS NOT NULL AND outcome_unknown_at IS NULL AND completed_at IS NULL
            AND completion_id IS NULL AND actual_won IS NULL
        WHEN 'OUTCOME_UNKNOWN' THEN dispatched_at IS NOT NULL AND outcome_unknown_at IS NOT NULL AND completed_at IS NULL
            AND completion_id IS NULL AND actual_won IS NULL
        WHEN 'CANCELLED' THEN dispatched_at IS NULL AND outcome_unknown_at IS NULL AND completed_at IS NOT NULL
            AND completion_id IS NULL AND actual_won IS NULL
        WHEN 'SETTLED' THEN dispatched_at IS NOT NULL AND completed_at IS NOT NULL
            AND completion_id IS NOT NULL AND actual_won IS NOT NULL
        ELSE dispatched_at IS NOT NULL AND completed_at IS NOT NULL
            AND completion_id IS NOT NULL AND actual_won IS NULL END)
);

-- 전송 내용·비용 예약은 바꿀 수 없고 응답은 한 번만 기록한다. 단계·정산 열은 응답 저장 뒤에도 갱신한다.
CREATE FUNCTION protect_policy_ai_rule_call() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.request_id, NEW.budget_id, NEW.request_body, NEW.input_tokens, NEW.input_won_per_million,
            NEW.output_won_per_million, NEW.pricing_version, NEW.maximum_won, NEW.reserved_at, NEW.cost_valid_until)
        IS DISTINCT FROM
        (OLD.request_id, OLD.budget_id, OLD.request_body, OLD.input_tokens, OLD.input_won_per_million,
            OLD.output_won_per_million, OLD.pricing_version, OLD.maximum_won, OLD.reserved_at, OLD.cost_valid_until)
        OR (OLD.response_body IS NOT NULL AND (NEW.response_status, NEW.response_body, NEW.received_at)
            IS DISTINCT FROM (OLD.response_status, OLD.response_body, OLD.received_at)) THEN
        RAISE EXCEPTION 'AI 호출 요청·비용 예약과 저장된 응답은 변경할 수 없습니다.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER protect_policy_ai_rule_call BEFORE UPDATE ON policy_ai_rule_calls
FOR EACH ROW EXECUTE FUNCTION protect_policy_ai_rule_call();

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
