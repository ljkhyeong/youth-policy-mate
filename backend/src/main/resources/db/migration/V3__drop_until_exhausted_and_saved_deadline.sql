-- 원문 해석이 만들지 않던 소진형 모집 상태를 없앤다. 예산·인원 소진 안내는 기간 미확인(UNKNOWN)으로 남긴다.
-- 이 값을 쓰는 코드는 없었지만, 남은 행이 있으면 기간 미확인으로 바꾼 뒤 제약을 좁힌다.
UPDATE policies SET recruitment_kind = 'UNKNOWN' WHERE recruitment_kind = 'UNTIL_EXHAUSTED';
ALTER TABLE policies DROP CONSTRAINT policies_recruitment_window;
ALTER TABLE policies ADD CONSTRAINT policies_recruitment_window CHECK (
    (recruitment_kind = 'PERIOD' AND recruitment_opens_at IS NOT NULL
        AND recruitment_closes_at IS NOT NULL AND recruitment_opens_at < recruitment_closes_at)
    OR (recruitment_kind IN ('ROLLING', 'CLOSED', 'UNKNOWN')
        AND recruitment_opens_at IS NULL AND recruitment_closes_at IS NULL));

-- 저장 정책의 마감일·안내는 조회 때 현재 개정의 접수 상태로 계산한다. 마감 알림 예약(policy_reminders)은 그대로 둔다.
ALTER TABLE saved_policies DROP COLUMN deadline_on, DROP COLUMN deadline_note;
