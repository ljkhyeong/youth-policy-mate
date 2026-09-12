CREATE TABLE policy_ai_rule_calls (
    request_id uuid PRIMARY KEY REFERENCES policy_ai_rule_requests(id),
    reservation_id text NOT NULL UNIQUE REFERENCES ai_request_reservations(reservation_id),
    request_body jsonb NOT NULL,
    input_tokens bigint NOT NULL CHECK (input_tokens > 0),
    input_won_per_million numeric NOT NULL CHECK (input_won_per_million > 0),
    output_won_per_million numeric NOT NULL CHECK (output_won_per_million > 0),
    response_status integer CHECK (response_status BETWEEN 100 AND 599),
    response_body text CHECK (octet_length(response_body) <= 1048576),
    received_at timestamptz,
    CHECK ((response_status IS NULL AND response_body IS NULL AND received_at IS NULL)
        OR (response_status IS NOT NULL AND response_body IS NOT NULL AND received_at IS NOT NULL))
);

CREATE FUNCTION protect_policy_ai_rule_call() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.request_id IS DISTINCT FROM NEW.request_id
        OR OLD.reservation_id IS DISTINCT FROM NEW.reservation_id
        OR OLD.request_body IS DISTINCT FROM NEW.request_body
        OR OLD.input_tokens IS DISTINCT FROM NEW.input_tokens
        OR OLD.input_won_per_million IS DISTINCT FROM NEW.input_won_per_million
        OR OLD.output_won_per_million IS DISTINCT FROM NEW.output_won_per_million
        OR OLD.response_body IS NOT NULL THEN
        RAISE EXCEPTION 'AI 호출 요청과 저장된 응답은 변경할 수 없습니다.';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER protect_policy_ai_rule_call BEFORE UPDATE ON policy_ai_rule_calls
FOR EACH ROW EXECUTE FUNCTION protect_policy_ai_rule_call();
