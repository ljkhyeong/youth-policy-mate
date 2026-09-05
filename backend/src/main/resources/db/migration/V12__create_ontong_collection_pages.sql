CREATE TABLE ontong_collection_pages (
    request_sequence bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id uuid NOT NULL UNIQUE,
    page_number integer NOT NULL CHECK (page_number BETWEEN 1 AND 1000),
    endpoint text NOT NULL DEFAULT 'https://www.youthcenter.go.kr/go/ythip/getPlcy',
    request_parameters jsonb NOT NULL DEFAULT '{"zipCd":"11000","pageSize":"10","pageType":"1","rtnType":"json"}',
    state text NOT NULL CHECK (state IN ('FETCHING', 'RECEIVED', 'READY', 'FETCH_FAILED', 'INVALID_RESPONSE')),
    started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_at timestamptz,
    raw_body text,
    failure_code text,
    total_count bigint CHECK (total_count >= 0),
    item_count integer CHECK (item_count BETWEEN 0 AND 10),
    CHECK ((raw_body IS NULL) = (received_at IS NULL))
);

CREATE TABLE ontong_collection_items (
    run_id uuid NOT NULL REFERENCES ontong_collection_pages(run_id),
    item_index integer NOT NULL CHECK (item_index BETWEEN 0 AND 9),
    raw_policy jsonb NOT NULL,
    policy_number text,
    outcome text NOT NULL DEFAULT 'PENDING'
        CHECK (outcome IN ('PENDING', 'APPLIED', 'UNCHANGED', 'REPLAYED', 'STALE', 'INVALID_ITEM', 'STORE_FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    updated_at timestamptz,
    PRIMARY KEY (run_id, item_index)
);

CREATE TABLE ontong_collection_item_attempts (
    run_id uuid NOT NULL,
    item_index integer NOT NULL,
    attempt integer NOT NULL CHECK (attempt > 0),
    outcome text NOT NULL CHECK (outcome IN ('APPLIED', 'UNCHANGED', 'REPLAYED', 'STALE', 'INVALID_ITEM', 'STORE_FAILED')),
    finished_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (run_id, item_index, attempt),
    FOREIGN KEY (run_id, item_index) REFERENCES ontong_collection_items(run_id, item_index)
);

ALTER TABLE policies ADD COLUMN last_request_sequence bigint NOT NULL DEFAULT 0
    CHECK (last_request_sequence >= 0);
