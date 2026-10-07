-- 규칙 추출 요청별 OpenAI 호출 한 건과 그 비용 예약·수명주기를 한 행에 둔다.
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
