---
name: youth-policy-api-contract
description: 청년정책메이트의 REST 경로·요청/응답 DTO·오류/상태 코드·OpenAPI·생성 TypeScript 계약 변경 기준. 저장소 공통 스킬에 Claude Code 작업 보충을 더한다.
when_to_use: backend의 Controller·요청/응답 record·예외 처리기를 바꾸거나, api/openapi.*.json·frontend/src/generated/ 차이가 생기거나, 웹에서 서버 응답 타입이 맞지 않을 때.
---

# API 계약 변경

공통 기준은 저장소 `skills/youth-policy-api-contract/SKILL.md`가 원본이다. 아래에 내용이 보이지 않으면 그 파일을 직접 읽는다. 공통 기준의 상대 링크는 원본 위치 기준이며 `../../docs/`는 저장소 루트의 `docs/`다.

## 공통 기준

!`cat "${CLAUDE_SKILL_DIR}/../../../skills/youth-policy-api-contract/SKILL.md"`

## Claude Code 보충

### 생성 파일

- `api/openapi.policy.json`·`api/openapi.preview.json`과 `frontend/src/generated/policy-api.d.ts`·`preview-api.d.ts`는 생성 결과다. Edit·Write로 고치지 않는다. 타입이 틀리면 서버 DTO·컨트롤러를 고치고 다시 생성한다.
- 공개·회원·관리자 계약은 `exportPolicyOpenApi`(PostgreSQL Testcontainers 필요), 개발 전용 preview 계약은 `exportPreviewOpenApi`가 만든다. 생성 전에 `docker info --format '{{.ServerVersion}}'`로 Docker를 확인한다.
- 생성 후 `git diff --stat -- api frontend/src/generated`로 의도한 계약만 바뀌었는지 확인한다. 의도하지 않은 `operationId`·스키마 이름 변경이 보이면 서버 이름을 고정한다.

### 웹의 소비 방식

- 웹은 `import type { components, operations } from "@/generated/policy-api"`에서 `components["schemas"]["이름"]`·`operations["operationId"]`로 타입을 꺼낸다. 예시는 `frontend/src/features/member/member-api.ts`다.
- 회원·관리자 API는 Next.js `frontend/src/app/api/member/[...path]/route.ts` 중계를 거친다. 경로·메서드·쿠키·CSRF 처리를 바꾸면 같은 위치의 `route.test.ts`를 함께 확인한다.
- `operationId`나 스키마 이름이 바뀌면 `grep -rn '<이전 이름>' frontend/src`로 소비 코드를 모두 찾는다.

### 검증 기록

생성 명령 `npm run generate:api`는 직접 실행한다(`verify` 기록 대상이 아니다). 생성 diff를 확인한 뒤 `npm run verify -- check:api-types`, `npm run verify -- check:web`과 변경한 동작의 서버·웹 테스트를 기록한다. 테스트 선택은 `youth-policy-verify` 스킬을 따른다.
