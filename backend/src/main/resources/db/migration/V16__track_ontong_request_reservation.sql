-- 늦게 시작한 요청이 이후 예약을 추월해 짧은 간격으로 발송되지 않게 한다.
ALTER TABLE ontong_collection_request_gate ADD COLUMN reserved_run_id uuid;
