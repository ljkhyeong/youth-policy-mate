# 관리자 수집 예외 조회

2026-09-07, 서버 `df50b0f`·화면 `5286fad` 기준. [PRD 6.2](../PRD/0001_product-baseline/spec.md#62-자동화-실패와-예외)의 수집 항목 실패 조회 API와 관리 화면을 연결했다. 보정·재처리 API는 아직 없다.

## 접근 설정

- 기존 카카오·네이버 로그인으로 생성된 `members.id`를 `ADMIN_MEMBER_IDS`에 쉼표로 구분해 설정하고 서버를 재시작한다. `.env.example`의 기본값은 비어 있다.
- 회원을 식별한 뒤 해당 UUID만 지정한다. 이메일·닉네임·소셜 제공자의 원래 사용자 번호를 넣지 않는다. 별도 관리자 생성·자동 승격·개발용 우회 로그인은 없다.
- Spring Security는 요청마다 OAuth2 인증, `ROLE_MEMBER`, 설정의 회원 ID를 모두 확인한다. 관리자 설정이 비어 있으면 회원도 접근할 수 없다. UUID 형식이 잘못된 설정은 시작 시 바인딩 오류가 난다.
- 비회원은 `401 LOGIN_REQUIRED`, 권한 없는 회원은 `403 ACCESS_DENIED`를 받는다. 성공·오류 응답에 `Cache-Control: no-store`를 적용한다.
- 관리 화면은 Next.js 서버에서 Spring API를 호출한다. 실제 소셜 로그인·관리자 계정 연결은 미검증이다.

## 관리 화면

- `/admin/collection-exceptions`에서 실패 목록을 보고 항목을 선택한다. 상세에서 돌아올 때 목록 페이지를 유지한다. 새로고침·조회 오류 재시도는 현재 페이지를 다시 요청한다. 재처리로 비어 있는 뒷 페이지는 첫 페이지로 안내한다.
- 로그인 필요·권한 없음·빈 목록·항목 없음·서버 장애를 구분한다. `401`이면 `/login?next=admin`으로 이동하고, 로그인 완료 후 관리자 목록으로 돌아온다. 일반 회원의 정책 저장 후 복귀는 기존대로 유지한다.
- 로그인 이동 기록은 탭의 `sessionStorage`에 `admin` 값만 저장하고 복귀 후 지운다. 임의 URL은 이동 대상으로 사용하지 않는다. 로그인 완료 처리는 중복 실행하지 않는다.
- 서버 조회에는 `YPM_SESSION` 쿠키만 고정된 관리자 API로 전달한다. 권한 판단은 Spring이 맡는다. 배포 빌드의 HTML은 `private, no-store`이며 데이터도 `no-store`로 조회한다. 관리 내용을 메타데이터에 넣지 않고 검색 노출을 막는다.
- 관리자 링크는 전체 페이지 이동을 사용한다. 계정 변경·브라우저 뒤로 가기에는 기존 계정 전환 처리와 서버 조회로 이전 내용을 다시 표시하지 않도록 한다.
- 상세에 실패 유형·처리 횟수·수집 위치, 현재 공개 내용과 원본을 표시한다. 원본 문자열을 JSON 숫자로 다시 해석하지 않아 큰 숫자를 보존한다. React의 텍스트 렌더링으로 HTML을 실행하지 않는다. 긴 원본은 내부 스크롤과 키보드 이동을 제공한다.
- 관리 화면은 별도 주소로 접근하며 일반 사용자 주 메뉴에 추가하지 않았다. 페이지 요청 실패는 이 항목 목록에 포함하지 않는다.

## 조회 계약

| 요청 | 응답 |
|---|---|
| `GET /api/v1/admin/collection-exceptions?page=1&pageSize=20` | 실패 항목, 페이지, 페이지 크기, 다음 페이지 여부 |
| `GET /api/v1/admin/collection-exceptions/{runId}/{itemIndex}` | 실패 항목, 저장된 원본 JSON 문자열, 같은 정책번호의 현재 공개 내용 |

- 페이지는 1~1000, 페이지 크기는 1~50이다. 수집 실행 ID는 UUID, 항목 위치는 0~9다. 입력 오류는 `400 INVALID_COLLECTION_QUERY`다.
- 대상은 현재 `INVALID_ITEM`(항목 검증 실패), `STORE_FAILED`(저장 실패)인 항목이다. 구체적인 오류 원인은 저장되어 있지 않아 임의로 추정하지 않는다.
- 최근 처리 시각 역순, 같은 시각에는 수집 요청 순번 역순·항목 위치 순으로 정렬한다. 페이지 크기보다 한 건 더 조회해 다음 페이지 여부를 구한다. 조회 사이에 재처리가 진행되면 목록도 달라질 수 있다.
- 재처리에 성공한 항목은 제외한다. 없는 항목이나 더 이상 실패 상태가 아닌 항목의 상세는 `404 COLLECTION_EXCEPTION_NOT_FOUND`다. DB 조회 장애는 세부 오류를 숨긴 `503 COLLECTION_UNAVAILABLE`로 응답한다.
- 원본의 `plcyNo`가 문자열이고 공백 제거 후 1~100자리 숫자인 경우에만 현재 공개 정책과 연결한다. 제목이 같아도 연결하지 않는다. 정책번호를 확인할 수 없으면 `policyNumber`는 null, 공개 내용이 없으면 `currentPolicy`는 null이다.
- `rawPolicyJson`은 JSONB에 저장된 항목의 JSON 문자열이다. 수신 당시의 공백·키 순서까지 보존한 바이트 원문은 아니다. 외부 텍스트로 취급하며 화면에서는 HTML이나 스크립트로 실행하지 않아야 한다.
- 현재 정책은 조회 시점의 공개 내용이다. 실패 당시의 이전 개정과 동일하다고 보장하지 않는다. 원본·공개 개정·처리 횟수·시도 이력은 조회로 바뀌지 않는다.

## 구현과 남은 범위

`backend/src/main/java/kr/youthpolicymate/admin/`의 컨트롤러·조회 저장소·DTO가 HTTP 계약과 읽기 SQL을 맡는다. `config/AdminAccess.java`는 Spring 설정 바인딩과 요청 권한 판단을 담당한다. 기존 수집 테이블을 사용하므로 DB 변경은 없다. 서버 DTO에서 OpenAPI와 TypeScript를 생성하고 관리자 세션 요구·null 응답을 계약 테스트로 확인한다. 화면은 `frontend/src/app/admin/collection-exceptions/`, 서버 조회·표시는 `frontend/src/features/admin/`에 있다.

남은 범위는 실제 관리자 계정 연결, 페이지 요청 실패(`FETCH_FAILED`, `INVALID_RESPONSE`) 조회, 이전 개정 비교, 근거를 남기는 보정과 재처리다. 이번 조회는 외부 수집·AI 호출을 실행하지 않는다. 기존 운영 CLI의 [수집·재개](policy-range-collection.md)는 그대로 사용할 수 있다.

## 검증

서버는 Java 25.0.3·Docker PostgreSQL 18.6, 화면은 Node 25.4.0에서 확인했다. 서버·API 계약은 화면 연결 중 바뀌지 않아 `df50b0f`의 검증을 재사용했다.

| 명령 | 확인 범위·결과 | 로컬 로그 |
|---|---|---|
| `npm run verify -- check:backend` | `df50b0f`의 전체 서버 테스트·빌드 통과. 관리자 PostgreSQL·접근 경계·명세 포함 | `.local/verification/1788710129886-82ae2edc.log` |
| `npm run verify -- check:api-types` | `df50b0f`의 생성 타입 일치 통과. 이번 화면은 해당 타입을 사용 | `.local/verification/1788710124692-fb7ee6a1.log` |
| `npm run verify -- test:web -- src/features/admin src/features/member/login-destination.test.ts src/components/page-state.test.tsx` | 쿠키 전달·캐시 금지·상태 구분·목록 복귀·원본 보존·로그인 목적지와 공통 상태 표시 통과 | `.local/verification/1788786362643-3f1e6e64.log` |
| `npm run verify -- check:web` | 최종 린트·타입 검사 통과. 사용하지 않는 린트 예외 주석 제거 후 경고 없음 | `.local/verification/1788787086561-bca42bde.log` |
| `npm run verify -- build:web` | 관리자 목록·상세·로그인 서버 렌더링을 포함한 배포 빌드 통과 | `.local/verification/1788786420708-83fe38b7.log` |

배포 빌드 이후 변경은 린트 예외 주석 제거와 문서뿐이다. 동작·의존성·설정은 같아 테스트와 빌드를 반복하지 않았다.

꺼져 있던 로컬 DB를 기존 볼륨으로 다시 실행하고 서버를 복구했다. 상태 조회 `200 UP`, 공개 정책 `200`·40건과 실제 관리자 화면의 로그인 안내를 확인했다. 정기 수집·이메일·마감 알림 실행기는 끈 상태다. 실제 서버 로그는 `/tmp/youth-admin-collection-backend.log`, 개발 웹 로그는 `/tmp/youth-admin-web.log`다.

Playwright로 실제 로그인 안내와 별도 검증용 서버의 성공·오류 화면을 확인했다. 로컬 응답 서버와 배포 빌드를 사용했으며 실제 소셜 제공자에 로그인하거나 관리자 권한을 부여하지 않았다. 로그인 후 목록 복귀·일회성 기록 삭제, 페이지 이동·상세 복귀, 오류 재시도, 권한 변경 후 뒤로 가기의 접근 차단, 긴 숫자 보존·HTML 실행 차단을 확인했다. PC 1280px·모바일 390px 배치, 가로 넘침 없음과 원본 키보드 스크롤도 확인했다. 제목 영역은 요소 캡처와 화면 크기 변경 후 재확인했다.

브라우저 기록과 이미지는 `/tmp/youth-admin-ui/`에 있으며 검증용 브라우저·API·웹 서버는 종료했다. 실제 카카오·네이버 계정의 관리자 접근과 운영 데이터의 실패 사례는 별도 검증이 필요하다.
