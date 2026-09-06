# 관리자 수집 예외 조회

2026-09-07, 코드 `df50b0f` 기준. [PRD 6.2](../PRD/0001_product-baseline/spec.md#62-자동화-실패와-예외)의 수집 항목 실패 조회 API를 구현했다. 관리 화면과 보정·재처리 API는 아직 없다.

## 접근 설정

- 기존 카카오·네이버 로그인으로 생성된 `members.id`를 `ADMIN_MEMBER_IDS`에 쉼표로 구분해 설정하고 서버를 재시작한다. `.env.example`의 기본값은 비어 있다.
- 회원을 식별한 뒤 해당 UUID만 지정한다. 이메일·닉네임·소셜 제공자의 원래 사용자 번호를 넣지 않는다. 별도 관리자 생성·자동 승격·개발용 우회 로그인은 없다.
- Spring Security는 요청마다 OAuth2 인증, `ROLE_MEMBER`, 설정의 회원 ID를 모두 확인한다. 관리자 설정이 비어 있으면 회원도 접근할 수 없다. UUID 형식이 잘못된 설정은 시작 시 바인딩 오류가 난다.
- 비회원은 `401 LOGIN_REQUIRED`, 권한 없는 회원은 `403 ACCESS_DENIED`를 받는다. 성공·오류 응답에 `Cache-Control: no-store`를 적용한다.
- 현재 Spring 서버 API만 제공한다. Next.js의 관리자 중계와 화면은 미구현이며 실제 소셜 로그인·관리자 계정 연결도 미검증이다.

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

`backend/src/main/java/kr/youthpolicymate/admin/`의 컨트롤러·조회 저장소·DTO가 HTTP 계약과 읽기 SQL을 맡는다. `config/AdminAccess.java`는 Spring 설정 바인딩과 요청 권한 판단을 담당한다. 기존 수집 테이블을 사용하므로 DB 변경은 없다. 서버 DTO에서 OpenAPI와 TypeScript를 생성하고 관리자 세션 요구·null 응답을 계약 테스트로 확인한다.

남은 범위는 관리 화면, 페이지 요청 실패(`FETCH_FAILED`, `INVALID_RESPONSE`) 조회, 이전 개정 비교, 근거를 남기는 보정과 재처리다. 이번 조회는 외부 수집·AI 호출을 실행하지 않는다. 기존 운영 CLI의 [수집·재개](policy-range-collection.md)는 그대로 사용할 수 있다.

## 검증

저장소 루트, Java 25.0.3·Docker PostgreSQL 18.6 환경에서 확인했다. 아래 최종 검사 이후 앱 코드는 `df50b0f`와 같으며 이후 변경은 문서뿐이다.

| 명령 | 확인 범위·결과 | 로컬 로그 |
|---|---|---|
| `npm run verify -- test:admin-collection` | 관리자 접근 경계, 실패 목록·페이지, 원본·현재 내용, 재처리 성공 제외, 조회 무변경·안전한 오류 통과 | `.local/verification/1788709918665-2cbed447.log` |
| `npm run generate:api` | 고정 경로 개수 검사를 수정한 후 명세·타입 생성 통과. 현재 정책의 null 분기와 관리자 세션 요구 확인 | `/tmp/youth-admin-api-generation.log` |
| `npm run verify -- check:backend` | 최종 코드 전체 서버 테스트·빌드 통과. 최초 기능 검사 이후 보완한 명세도 포함 | `.local/verification/1788710129886-82ae2edc.log` |
| `npm run verify -- check:api-types` | 생성 타입 일치 통과 | `.local/verification/1788710124692-fb7ee6a1.log` |
| `npm run verify -- check:web` | 린트·타입 검사 통과 | `.local/verification/1788710124661-6df63fa4.log` |

최종 서버 실행 파일을 로컬에 반영했다. 상태 조회 `200 UP`, 공개 정책 `200`·40건, 비회원 관리자 목록·상세 `401 LOGIN_REQUIRED`와 캐시 금지를 확인했다. 정기 수집·이메일·마감 알림 실행기는 끈 상태이며 서버 로그는 `/tmp/youth-admin-collection-backend.log`다.

관리자 성공 응답과 일반 회원 거절은 실제 PostgreSQL 통합 테스트의 인증 세션으로 검증했다. 실제 카카오·네이버 계정은 사용하지 않았다. 화면·런타임 프런트엔드 코드를 바꾸지 않아 웹 빌드·브라우저 흐름 검사는 반복하지 않았다.
