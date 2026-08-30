# 프로젝트 작업 지침

- 작업이 끝나면 변경 내용을 분류하고 한글로 커밋한다.
- 커밋 메시지, PR 리뷰, 문서, 필요한 코드 주석은 실무에서 이해하기 쉬운 한글로 작성한다.
- 의미가 불분명한 추상적인 표현은 사용하지 않는다.
- 검증 코드는 변경 사항을 확인하는 데 필요한 범위로만 작성한다.

## 작업 시작과 문서 기준

- `HANDOFF.md`에서 현재 작업을 확인하고 제품 동작은 `docs/PRD/0001_product-baseline/spec.md`, 기술 결정은 관련 `docs/ADR/` 문서를 기준으로 한다.
- 구현 예정과 실제 구현 상태를 구분한다. 존재하지 않는 코드 경로·실행 명령을 현재 동작처럼 쓰지 않는다.
- 제품 정책을 스킬이나 인계 문서에만 변경하지 않는다. 기준 문서를 먼저 수정하고 관련 요약을 맞춘다.

## 전용 스킬

전용 스킬 원본은 `skills/`에 있다. 요청과 맞는 스킬의 `SKILL.md`를 읽고 적용하며, 다른 프로젝트 전용 경로·명령·운영 정책을 그대로 사용하지 않는다.

| 작업 | 스킬 |
|---|---|
| PRD·ADR·문서 정리 | `skills/youth-policy-docs/SKILL.md` |
| 공통 백엔드·인증·JPA/Flyway | `skills/youth-policy-backend/SKILL.md` |
| API·OpenAPI·생성 타입 | `skills/youth-policy-api-contract/SKILL.md` |
| Next.js 화면·개인 상태 | `skills/youth-policy-frontend/SKILL.md` |
| 정책 수집·개정·AI 추출 | `skills/youth-policy-ingestion/SKILL.md` |
| 자격 규칙·미확인·추천 근거 | `skills/youth-policy-eligibility/SKILL.md` |
| 저장·마감·동의·알림 | `skills/youth-policy-reminders/SKILL.md` |
