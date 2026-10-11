-- 청년예술인 예술활동 적립계좌의 검토한 원문은 상시로 수집되지만 2026년 모집은 예산 소진으로 마감됐다.
-- 목록 필터 열은 원문이 바뀔 때만 다시 계산하므로, 이미 수집된 같은 원문의 행을 PolicyRecruitment의 검토 보정(기간 미확인)과 맞춘다.
UPDATE policies SET recruitment_kind = 'UNKNOWN', recruitment_opens_at = NULL, recruitment_closes_at = NULL
WHERE policy_number = '20260528005400113226'
  AND content_hash = '10d0ad325b17feaafb3eec87cab65012a059ba1eab96de2f7ad1d8d8f164e18f';
