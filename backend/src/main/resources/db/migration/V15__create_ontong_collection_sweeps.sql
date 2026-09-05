-- 이전 수집의 재전송을 막기 위해 기존 행은 이미 발송 시도한 것으로 보존한다.
ALTER TABLE ontong_collection_pages ADD COLUMN dispatch_started_at timestamptz;
UPDATE ontong_collection_pages SET dispatch_started_at = started_at;

-- 요청 전 짧게 잠그며, 외부 응답 대기 중에는 잠금을 유지하지 않는다.
CREATE TABLE ontong_collection_request_gate (
    id integer PRIMARY KEY CHECK (id = 1),
    next_request_at timestamptz NOT NULL DEFAULT '-infinity'
);
INSERT INTO ontong_collection_request_gate(id) VALUES (1);
CREATE INDEX ontong_collection_pages_started_at_idx ON ontong_collection_pages(started_at);

CREATE TABLE ontong_collection_sweeps (
    id uuid PRIMARY KEY,
    origin text NOT NULL CHECK (origin IN ('MANUAL', 'SCHEDULED')),
    first_page integer NOT NULL CHECK (first_page BETWEEN 1 AND 1000),
    last_page integer NOT NULL CHECK (last_page BETWEEN first_page AND 1000),
    next_page integer NOT NULL CHECK (next_page BETWEEN first_page AND last_page + 1),
    state text NOT NULL CHECK (state IN ('ACTIVE', 'PAUSED', 'COMPLETED', 'PARTIAL', 'ABANDONED')),
    started_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
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
