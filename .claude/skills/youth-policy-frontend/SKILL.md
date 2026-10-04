---
name: youth-policy-frontend
description: 청년정책메이트 Next.js의 정책 화면·조건 입력·로그인·개인 저장/일정/알림·관리자 화면·API 상태 표시 변경 기준. 저장소 공통 스킬에 이 저장소에서 반복된 화면 오류 유형과 재사용할 공통 코드를 더한다.
when_to_use: frontend/ 아래 페이지·컴포넌트·훅·API 중계·화면 문구를 수정하거나, 비동기 요청·계정 전환·키보드 초점·로딩/오류 상태를 다룰 때.
---

# 프런트엔드 개발

공통 기준은 저장소 `skills/youth-policy-frontend/SKILL.md`가 원본이다. 아래에 내용이 보이지 않으면 그 파일을 직접 읽는다. 공통 기준의 상대 링크는 원본 위치 기준이며 `../../docs/`는 저장소 루트의 `docs/`다.

## 공통 기준

!`cat "${CLAUDE_SKILL_DIR}/../../../skills/youth-policy-frontend/SKILL.md"`

## Claude Code 보충

### 먼저 확인할 것

- Next.js 16·React 19를 사용한다. 라우팅·캐시·메타데이터·서버/클라이언트 경계 API는 기억 대신 설치된 문서 `node_modules/next/dist/docs/`와 `node_modules/next/package.json`의 버전으로 확인한다.
- 화면 문구는 [PRD 3.4](../../../docs/PRD/0001_product-baseline/spec.md#34-화면-문구)와 [문구 정리](../../../docs/development/ui-wording.md)의 바뀐 표현을, 상태 화면은 [페이지 상태](../../../docs/development/page-states.md)를 따른다.

### 재사용할 공통 코드

| 필요 | 위치 |
|---|---|
| 회원·관리자 API 호출(`signal`·CSRF) | `features/member/member-api.ts`의 `memberApi`, `MemberApiError` |
| 관리자 변경 요청의 단일 실행·화면 이탈 후 무시 | `features/admin/use-admin-mutation.ts`의 `useAdminMutation` |
| 계정 전환·뒤로 가기 복원 시 개인 상태 초기화 | `features/member/account-transitions.tsx`의 `announceAccountChange`, `AccountTransitions` |
| 확인한 생년월일의 메모리 보관 | `features/conditions/confirmed-birth.ts` |
| 로딩·빈 결과·오류·재시도 화면 | `components/page-state.tsx`의 `PageState`, `LoadingState`, `LoadErrorState` |
| 서울 기준 날짜 | `lib/seoul-date.ts`의 `getSeoulDate` |

경로는 `frontend/src/` 기준이다. 같은 역할의 훅·컴포넌트를 새로 만들기 전에 위 코드를 확장할 수 있는지 확인한다.

### 이 저장소에서 반복된 오류

최근 커밋의 수정 대부분이 아래 유형이다. 비동기 상호작용을 바꾸면 해당 항목을 구현과 테스트에서 확인한다.

- **늦은 응답**: 입력 수정·계정 전환·화면 이동·재시도 뒤 도착한 이전 응답이 현재 상태를 덮어쓰지 않게 `AbortController`나 요청 순번으로 무시한다. 늦은 조건 조회가 사용자가 입력 중인 값을 덮어쓰지 않게 한다.
- **중복 요청**: 저장·비교·재처리 같은 변경 요청은 진행 중에 다시 보내지 않는다. 자동 입력 중에는 그 결과에 의존하는 제출을 막는다.
- **키보드 초점**: 질문 시작·재조회·재시도·개정 변경 뒤 다음 입력이나 결과로 초점을 옮긴다. 초점이 `body`나 사라진 버튼에 있을 때만 옮기고, 사용자가 다른 입력·탭으로 이동했으면 현재 초점을 유지한다. 예시는 `features/member/saved-policy-changes.tsx`다.
- **개인 상태 잔존**: 로그아웃·계정 전환·`pageshow`의 `persisted` 복원 때 이전 계정의 화면과 생년월일을 지운다. 생년월일·조건을 URL·브라우저 저장소에 남기지 않는다.
- **실패 표시**: 조회 실패·로딩을 불충족·결과 없음·저장 안 됨으로 표시하지 않고, 재시도 뒤에도 사용자가 입력한 답변을 유지한다.

### 검증

- 단위 테스트는 컴포넌트 옆 `*.test.ts(x)`(Vitest)다. 관련 파일만 `npm run verify -- test:web -- <frontend 기준 경로>`로 실행한다.
- 사용자 흐름·초점·모바일 배치 확인은 `youth-policy-browser-check` 스킬로 헤드리스 검증한다. 검증 범위 선택은 `youth-policy-verify` 스킬을 따른다.
