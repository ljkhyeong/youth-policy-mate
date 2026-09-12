# 관심 정책 변경 비교

`/my`의 관심 정책·마감 일정에서 저장 이후 개정된 정책의 ‘변경 내용 보기’를 열면 저장 당시와 현재 내용을 비교한다.

## 비교 기준

- 같은 정책번호에서 사용자가 저장한 개정과 현재 공개 개정을 비교한다. 바로 직전 개정이 아니며, 개정이 여러 번 있어도 저장 당시를 기준으로 한다.
- 저장 해제 후 다시 저장하면 새 저장 시점을 기준으로 한다. 비교를 열어도 저장 기준·읽음 상태·알림 예약은 바뀌지 않는다.
- 정책명·설명·분야·운영 기관·신청 기간·상세 안내·공식 링크 중 달라진 항목을 보여준다. 관리자용 지역 코드·원천 수정일은 회원 화면에서 제외한다. 비교 대상이 같은 경우 ‘표시 항목의 변경이 없습니다’로 안내한다.
- 별도 정책번호로 등록된 다음 연도·다음 회차 공고는 연결하지 않는다. 공고 간 동일 사업 여부나 변경 문장의 의미를 자동 판정하지 않는다.

## 구현

`GET /api/v1/me/policies/{number}/changes`는 로그인한 회원이 현재 저장한 정책만 조회한다. 응답은 정책번호, 저장 시각, 저장 당시·현재 개정의 `PolicyContent`와 원본 수집 시각이다. 조회할 개정 번호나 회원 ID를 클라이언트에서 선택하지 않는다. 비회원은 401, 본인의 저장 목록에 없는 정책은 404이며 개인 응답은 `no-store`다.

`MemberPolicyStore.changes`가 기존 저장 기록·공개 개정·원본 수집 기록을 한 SQL 문으로 조회한다. 저장·알림 갱신 경로를 호출하지 않고 원본 JSON이나 회원 식별자를 반환하지 않는다. 스키마·외부 API 호출을 추가하지 않았다. 계약은 서버 DTO에서 생성한다.

`SavedPolicyChanges`는 비교를 펼칠 때만 조회한다. 접기·비교가 사라지는 탭 전환·목록 갱신 시 이전 조회를 취소한다. 로딩·오류·변경 없음을 구분하고 재시도를 제공한다. 재조회 실패 시 재시도 버튼, 성공 시 비교 결과로 초점을 옮기며, 접으면 초점 이동 예약도 지운다. 처음 펼칠 때는 펼치기 버튼의 초점을 유지한다. 관리자 화면의 항목 비교를 `PolicyContentComparison`으로 분리해 재사용하며 기존 관리자 표시 항목은 유지한다. 데스크톱은 두 열, 모바일은 저장 당시·현재 순으로 쌓는다.

## 검증

서버·계약과 초기 화면의 `238a28a` 검증 결과다. 서버·계약은 유지했으며 최신 화면 검증은 아래에 구분한다.

| 명령 | 확인 범위·로그 |
|---|---|
| `npm run generate:api` | OpenAPI·TypeScript 생성. `/tmp/youth-saved-policy-changes-contract.log` |
| `npm run verify -- test:member-flow` | PostgreSQL에서 1→3 개정 비교·회원별 저장 기준·소유권·재저장·알림 변경 없음·기존 회원 흐름. `.local/verification/1789182425286-543a8b9d.log` |
| `npm run verify -- test:web -- 'src/app/api/member/[...path]/route.test.ts' src/features/member/member-policy-list.test.tsx src/features/admin/collection-exception-pages.test.tsx src/features/admin/policy-rule-review-pages.test.tsx` | 회원 중계·변경 버튼·공통 비교를 사용하는 기존 관리자 화면. `.local/verification/1789182395890-6519ba8f.log` |
| `npm run verify -- check:web` | 린트·타입. `.local/verification/1789182395890-da082d3a.log` |
| `npm run verify -- check:api-types` | 생성 계약 일치. `.local/verification/1789182395890-6a1ae43f.log` |
| `npm run verify -- build:web` | 회원·관리자 화면 배포 빌드. `.local/verification/1789182503744-1a3a97e5.log` |
| `npm run verify -- package:backend` | 검증한 서버 실행 파일 생성. `.local/verification/1789182509927-51de03b2.log` |

브라우저는 별도 `saved-policy-changes` 세션과 테스트 회원 응답으로 확인했다. 최초 비교 조회 없음·버튼에서만 조회·503 후 재시도·탭 전환 후 늦은 응답 무시·저장 해제 안내·표시 항목 동일·390px/1280px 배치·키보드 조작을 통과했다. 비교 조회 6회 동안 관심 정책 조회는 최초 1회였다. 흐름과 결과·화면은 `/tmp/youth-saved-policy-changes-ui/`에 보관했다.

당시 로컬 상태 조회 200·비회원 비교 API 401·내 정책 화면 200을 확인했다. 실제 회원 데이터는 변경하지 않았으며 실제 소셜 제공자 로그인·이메일 전달·AI 생성은 검증에 포함하지 않았다.

## 현재 화면 검증

2026-09-12, `0761490`에서 재조회 실패·성공 후 키보드 초점 유실을 수정했다. 웹 검사·중계/회원 목록 테스트와 헤드리스 브라우저를 통과했다. [공통 명령·로그](email-unsubscribe.md#키보드와-오류-복구-검증)

브라우저에서 503·401·404 재시도, 실패·성공 후 초점과 다음 버튼 접근, 처음 펼칠 때 초점 유지, 접기 후 늦은 응답 무시, 다시 펼치기, 표시 항목 동일과 모바일 가로 넘침을 확인했다. 결과는 `/tmp/youth-member-retry-focus/recovery.log`, 화면은 같은 디렉터리의 `mobile-comparison.png`다. 실제 회원 변경은 없었으며 검증 브라우저를 종료했다.
