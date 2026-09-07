# 정책 표시 보정과 수집 충돌

2026-09-08, 코드 `152e331` 기준. 공개된 정책의 정책명·운영 기관을 원본과 분리해 보정한다. 제품 기준은 [PRD 6.2](../PRD/0001_product-baseline/spec.md#62-자동화-실패와-예외), 관리자 권한 설정은 [수집 예외 관리](admin-collection-exceptions.md)를 따른다.

## 사용 흐름

1. `/admin/collection-exceptions/corrections`의 ‘보정 관리’에서 정책번호로 공개 정책을 조회한다. 실패 상세의 ‘정책 보정 관리’에서도 이동할 수 있다.
2. 정책명 또는 운영 기관의 보정 값과 근거를 입력한다. 한 정책에 진행 중인 보정은 하나만 허용하며, 값·사유는 공백을 제외하고 필수이고 각각 최대 500자다.
3. 수집 원본에서 보정 대상 값이 같으면 보정을 유지하고 다른 변경을 반영한다. 대상 값이 달라지면 공개 내용·개정을 유지하고 ‘새 원본 확인 필요’로 표시한다.
4. 충돌 이력에서 새 원본 값과 함께 반영될 내용을 확인한 뒤 ‘보정 유지’ 또는 ‘보정 해제 · 원본 적용’을 선택하고 사유를 남긴다. 충돌이 없는 보정도 해제할 수 있다.
5. 충돌로 남은 수집 항목은 실패 상세에서 재처리해 수집 상태를 갱신한다. 보정 처리만으로 과거 항목의 시도 이력을 바꾸지 않는다.

응답을 받지 못하면 입력을 잠그고 같은 요청 ID와 내용으로 결과를 재확인한다. 상태가 바뀐 요청은 차단하고 최신 이력을 다시 열도록 안내한다. 보정 입력을 브라우저 저장소에 보관하지 않는다.

## 저장과 동시 처리

- V20의 `policy_corrections`에 대상 필드, 기준 원본, 보정 값, 작업자, 사유, 요청·적용 개정과 해제 이력을 보관한다. `policy_revisions.correction_id`는 적용한 보정을 가리키며 해제 후 개정은 null이다. 원본 JSON은 수정하지 않는다.
- 보정 적용·해제·유지는 기존 `PolicyCatalogStore` 반영 경로를 사용한다. 보정과 공개 개정을 하나의 트랜잭션으로 저장하며 어느 쪽이든 실패하면 함께 롤백한다.
- 수집과 보정은 정책 행을 먼저 잠근다. 관리자 처리는 잠금 뒤 최신 개정을 별도로 읽는다. 조인 조회 중 잠금을 기다리다가 이전 개정에 묶이는 경합을 피한다.
- 화면이 조회한 공개 개정과 검토 원본 ID가 같을 때만 처리한다. 같은 요청 ID·입력·작업자는 기존 결과를 반환한다. 동시 재전송도 새 개정을 중복 생성하지 않는다.
- 충돌 중에도 최신 수집 요청 순번을 기록해 오래된 수집의 덮어쓰기를 막는다. 순번 없는 개발용 캡처는 수신 시각을 비교한다. 더 새 원본이 도착하면 검토 원본을 갱신하고, 이미 열린 화면의 해소 요청은 거절한다.
- 보정 유지는 이전 보정을 해제하고 최신 원본을 기준으로 새 보정 이력을 만든다. 원본 적용은 보정을 해제한다. 표시 값이 같더라도 보정 적용 관계가 바뀌면 새 개정을 남긴다.
- 수집 항목·시도·관리자 재처리 결과에 `CORRECTION_CONFLICT`를 추가했다. 실패 목록과 범위 수집의 미완료 판단에 포함하며, 해결 전 재처리는 충돌 결과를 기록한다.
- 자격·기간 계산 코드는 바꾸지 않는다. 보정으로 내용 해시가 바뀌면 기존에 검토한 해시에 한정된 질문·연령 비교는 안전하게 중단될 수 있다.

## API 계약

경로의 공통 접두사는 `/api/v1/admin/policy-corrections`다. 모든 요청은 지정한 관리자 소셜 세션을 검사하고 응답을 캐시하지 않는다. POST는 Spring CSRF 토큰이 필요하며 작업자는 세션에서 정한다.

| 요청 | 내용 |
|---|---|
| `GET ?page=1&pageSize=20` | 보정·충돌·해제 이력. 기록 시각 역순, 같은 시각에는 보정 ID 순 |
| `GET /policies/{number}` | 현재 공개 내용·개정·보정 ID와 직전 개정 |
| `POST` | `requestId`, `policyNumber`, `expectedRevision`, `field`(`TITLE`/`ORGANIZATION`), `value`, `reason` |
| `POST /{id}/resolutions` | `requestId`, `expectedRevision`, `reviewSnapshotId`, `action`(`KEEP`/`USE_SOURCE`), `reason` |

페이지는 1~1000, 크기는 1~50이다. 생성·해소의 성공 응답은 `PolicyCorrectionItem`이다. 입력 오류는 400, 상태·개정·검토 원본·요청 ID 충돌은 409, DB 실패는 503이다. 공개 정책 조회의 없는 정책은 404다. OpenAPI와 TypeScript는 서버 DTO에서 생성한다.

Next.js는 보정 생성·해소의 고정된 POST 경로만 기존 개인 API 중계에 추가했다. 다른 출처의 요청을 차단하고 세션 쿠키와 CSRF만 고정된 Spring 서버로 전달한다. 원본·보정 사유·링크 값은 React 텍스트로 표시한다.

## 검증

Java 25.0.3·Docker PostgreSQL 18.6·Node 25.4.0에서 확인했다. 아래 결과는 `152e331`의 기능 코드에 적용된다. 마지막 수정은 성공 응답의 OpenAPI 주석과 계약 확인이며, 생성 검사·타입 검사·서버 패키징으로 확인했다. 동작이 같은 전체 서버 테스트와 웹 빌드는 반복하지 않았다.

| 명령 | 범위·결과 | 로컬 로그 |
|---|---|---|
| `npm run verify -- test:admin-collection -- --tests kr.youthpolicymate.admin.PolicyCorrectionApiTest.serializesSameRequest` | 기존 관리자 검사와 보정·원본 보존·충돌·롤백·동시 요청 통과. Gradle의 기존 테스트 필터에 추가되므로 단일 테스트 실행은 아님 | `.local/verification/1788822699185-702d4984.log` |
| `npm run verify -- check:backend` | 전체 서버 테스트·마이그레이션·계약·빌드 통과 | `.local/verification/1788822786646-2227b8a4.log` |
| `npm run verify -- test:web -- src/features/admin src/app/api/member` | 보정 화면·안전한 원본 표시·조회 경로·POST 중계 통과 | `.local/verification/1788822782775-c4b9d43a.log` |
| `npm run verify -- check:web` | 최종 린트·타입 검사 통과 | `.local/verification/1788823297629-ec8c32af.log` |
| `npm run verify -- check:api-types` | 최종 생성 계약 일치 통과 | `.local/verification/1788823297616-81967b2c.log` |
| `npm run verify -- build:web` | 새 보정 경로의 배포 빌드 통과 | `.local/verification/1788822846422-2253e138.log` |
| `npm run verify -- package:backend` | 최종 서버 실행 파일 생성 통과 | `.local/verification/1788823302239-728ecdad.log` |

최초 동시 보정 테스트의 409 실패는 정책 잠금과 개정 조회를 분리해 수정했다. 이후 관련 검사와 전체 서버 검사를 통과했다. 최종 `npm run generate:api`는 성공 응답·관리자 인증 계약도 확인했으며 로그는 `/tmp/youth-corrections-generate-final.log`다.

Playwright와 임시 API·배포 웹에서 정책 조회·키보드 보정 실행, 보정 생성과 충돌 유지 각각의 응답 유실 후 같은 ID 재확인, 이력 중복 없음, 409 이후 재실행 차단을 확인했다. PC 1280px·모바일 390px 이미지와 원본 상세를 검토했으며 가로 넘침이나 원문 스크립트 실행이 없었다. 검증용 503·409와 favicon 404 외 앱 오류는 없었다. 기록과 이미지는 `/tmp/youth-admin-ui/`에 있고 검증용 브라우저·API·웹은 종료했다.

로컬 실제 서버는 V20 적용, 상태 조회 `200 UP`, 공개 정책 40건, 보정 API 비회원 401을 확인했다. 서버 로그는 `/tmp/youth-policy-corrections-backend.log`, 개발 웹 로그는 `/tmp/youth-admin-web.log`다. 정기 수집·이메일·마감 알림은 비활성화했다. 실제 정책 보정·실제 소셜 로그인·관리자 권한 부여는 실행하지 않았다.

## 제외한 범위

정책 식별자·자격·마감 조건의 직접 보정, 미공개 정책의 최초 보정 공개, 여러 필드의 동시 보정은 포함하지 않는다. 새 원본이 항목 검증을 통과하지 못하면 기존 `INVALID_ITEM`으로 남으며 보정으로 검증을 우회하지 않는다. 실제 관리자 계정 연결과 운영 데이터 사례는 별도 검증이 필요하다.
