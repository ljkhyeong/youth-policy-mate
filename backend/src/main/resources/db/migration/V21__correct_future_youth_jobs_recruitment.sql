-- 검토한 5월 모집의 공고 시작일과 접수 시작일을 구분한다. 원본·정책 개정은 유지한다.
UPDATE policies
SET recruitment_kind = 'PERIOD',
    recruitment_opens_at = TIMESTAMPTZ '2026-05-18 00:00:00+09',
    recruitment_closes_at = TIMESTAMPTZ '2026-06-01 00:00:00+09'
WHERE policy_number = '20260722005400213264'
  AND content_hash = '0d98b50fc87fc4e319676be23e6a900304a4434215e997004ed3e1f47bb8dfea';
