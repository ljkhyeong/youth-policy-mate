# 전용 스킬의 출처와 적용 범위

청년정책메이트 스킬의 관리 원본은 저장소의 `skills/`다. 사용자 스킬 폴더 `/Users/lim/.codex/skills/`에는 같은 이름의 심볼릭 링크로 연결한다. 원본 수정은 이 저장소에서 검증·커밋하며 기존 happyGallery·BATON 스킬은 변경하지 않는다.

현재 스킬은 확정된 제품 요구와 필요한 개발 절차를 담는다. 앱 구현이 완료되었다는 뜻이 아니며, 이후 실제 코드 경로와 빌드 설정이 생기면 필요한 위치 안내만 추가한다.

## 가져온 절차

| 전용 스킬 | 참고한 기존 스킬 | 가져온 내용 |
|---|---|---|
| [문서](../../skills/youth-policy-docs/SKILL.md) | happygallery-documentation-flows, baton-cal-flows | PRD·ADR·인계의 역할, 기준 문서 우선 수정, 미구현 상태 구분 |
| [백엔드](../../skills/youth-policy-backend/SKILL.md) | happygallery-spring-backend, entity-migration-sync, baton-cal-flows | 책임 경계, 표준 API 우선, 최소 테스트, Flyway와 엔티티 동기화 |
| [API 계약](../../skills/youth-policy-api-contract/SKILL.md) | api-contract, baton-cal-flows | 이름 있는 DTO, 안정적인 operationId, 누락·널 의미, 생성 계약과 실제 응답 확인 |
| [프론트엔드](../../skills/youth-policy-frontend/SKILL.md) | happygallery-frontend-flows | 서버 타입 재사용, 계정 전환 시 개인 상태 분리, 시간 표현, 대표 흐름 검증 |
| [정책 수집](../../skills/youth-policy-ingestion/SKILL.md) | happygallery-batch-flows, baton-cal-flows | 원본·개정·파생 결과 분리, 멱등 재처리, 오래된 결과 방지, 항목별 실패 격리 |
| [자격 판정](../../skills/youth-policy-eligibility/SKILL.md) | time-boundary-policy, happygallery-spring-backend | Clock과 기준일, 규칙 소유권. 3단계 판정과 미확인 처리는 이번 제품 합의로 신규 작성 |
| [일정·알림](../../skills/youth-policy-reminders/SKILL.md) | happygallery-notification-flows, time-boundary-policy, baton-cal-flows | Outbox, 동의·취소, 전달 결과 미확인, 날짜·시각 구분 |

기존 스킬은 `/Users/lim/.codex/skills/`에 있는 같은 이름의 폴더를 읽어 참고했다. 원본 지침 전체를 복사하지 않았으며, 이 프로젝트의 PRD와 충돌하는 규칙은 제외했다.

## 가져오지 않은 규칙

- happyGallery의 MySQL, Vite·Bootstrap, 예약·주문·결제·환불·이용권 규칙
- happyGallery의 모듈 경로, 특정 Gradle 태스크, 관리자 API 키·sessionStorage 규칙
- 카카오 우선·SMS 대체 발송과 전화번호 인증 전용 처리
- BATON CAL의 `.ics`, UID/SEQUENCE, 구독 토큰, iCal4j 버전별 예외, 서비스 간 계약 패키지 배포
- BATON GO의 공개 단축 링크와 다른 서비스로의 라우팅
- 특정 장비·Kubernetes·클라우드 배포 절차와 고정된 운영 수치

Next.js 서버·클라이언트 경계, PostgreSQL 사용, 자동 공개와 미확인 판정, 소셜 생년월일 확인, 이메일 동의, AI 비용 한도는 이 프로젝트에 맞게 다시 작성했다. 공통 원칙만 재사용하며 다른 프로젝트 저장소나 운영 환경을 변경할 권한까지 가져오지 않는다.

## 검증과 설치 원칙

- 스킬 내용과 참조 경로를 먼저 검증하고, 시스템 skill-creator의 `quick_validate.py`로 이름·frontmatter 형식을 확인한다.
- `agents/openai.yaml`은 스킬 UI 설명이며 자동 선택을 비활성화하지 않는다.
- 사용자 스킬 폴더에 같은 이름의 다른 파일·디렉터리가 있으면 덮어쓰지 않는다.
- 저장소가 다른 위치로 이동하면 해당 스킬의 저장소 경로와 사용자 폴더의 링크를 함께 갱신한다.
- 새 운영 정책을 스킬에만 추가하지 않는다. 제품 동작은 PRD, 구조 결정은 ADR에 먼저 반영한다.
