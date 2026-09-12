# 관리자 AI 추출 조회

`280a12b` 기준. `/admin/collection-exceptions/ai`에서 자동 추출을 시도한 요청을 조회한다. 수동 준비만 한 요청이나 아직 처리하지 않은 공고는 이 목록에 포함하지 않는다. 실행·재호출·정산 버튼은 제공하지 않는다.

## 표시와 조회 기준

- 정책명·정책번호 검색과 마지막 시도 상태 필터를 제공한다. 요청별 마지막 시도만 선택한 뒤 필터·전체 건수·페이지를 같은 DB 조회 시점으로 계산한다. 최근 시작 순이며 시도 횟수를 함께 표시한다.
- 마지막 시도 상태와 현재 추출 결과·비용 상태를 구분한다. 시도가 끝난 뒤 응답이나 초안이 저장되거나 비용이 정산됐을 수 있다.
- 실행 중인 기록의 기한이 지났으면 `실행 시간 초과`로 표시한다. DB의 실행 상태·종료 시각·시도 횟수는 변경하지 않는다.
- 추출 기준 개정과 현재 내부 개정, 원문 변경·후속 요청 여부를 표시한다. 이전 결과가 최신 공고의 조건을 확인했다는 뜻은 아니다.
- 초안 생성 결과는 기존 [조건 검토](policy-rule-review.md) 화면으로 연결한다. 생성 완료와 서비스 적용은 별개다.
- 자동 실행 스위치를 켰는지 표시한다. 켜짐이 키·모델·요금·예산·일일 한도의 충족이나 작업자 정상 실행을 뜻하지 않는다. 상세 설정과 복구 기준은 [자동 추출](ai-rule-automation.md)을 따른다.
- 빈 결과·로그인 필요·권한 없음·조회 장애를 구분한다. 조회가 실패하면 자동 실행 설정이나 처리 결과를 추정하지 않는다.

## API와 접근

`GET /api/v1/admin/policy-ai-runs?page=1&pageSize=20&filter=ALL&query=`

- `page`: 1~1000, `pageSize`: 1~50, 검색어: 100자 이하.
- 필터: `ALL`, `RUNNING`, `COMPLETED`, `RETRY_PENDING`, `INTERRUPTED`, `REVIEW_REQUIRED`, `SUPERSEDED`, `LEASE_EXPIRED`.
- 서버 DTO `PolicyAiRuns`에서 OpenAPI와 TypeScript를 생성한다. 관리자 소셜 세션의 회원 ID를 기존 설정으로 확인하며 응답과 Next.js 조회를 캐시하지 않는다.
- 응답에는 정책·요청·시도와 처리 상태만 포함한다. AI 요청·응답·후보 본문, 작업자, 인증키는 제공하지 않는다.
- 새 테이블·마이그레이션 없이 V26~V28의 기존 기록을 읽는다. 조회로 AI 호출·규칙 적용·비용 변경을 실행하지 않는다.

## 검증

Java 25.0.3·PostgreSQL 18.6에서 확인했다. 실제 제공자 대신 DB 테스트 자료와 별도 포트의 테스트 API를 사용했다.

| 명령 | 확인 범위·로그 |
|---|---|
| `npm run generate:api` | 서버 계약과 타입 생성. `/tmp/youth-admin-ai-contract.log` |
| `npm run verify -- test:admin-ai` | 권한·변경 요청 차단·검색·마지막 시도 선택·시간 초과·늦은 결과·공고 변경·응답 비노출·조회 부작용 없음. `.local/verification/1789177236504-7485d459.log` |
| `npm run verify -- test:admin-collection` | 기존 수집·재처리·보정·조건 검토·관리자 권한 회귀. `.local/verification/1789177279859-d9eea957.log` |
| `npm run verify -- test:web -- src/features/admin/policy-ai-runs-pages.test.tsx src/features/admin/load-collection-exceptions.test.ts src/features/admin/policy-rule-review-pages.test.tsx src/features/admin/collection-exception-pages.test.tsx` | 화면·검색 조건·오류·세션 전달. `.local/verification/1789177232427-0cf0d143.log` |
| `npm run verify -- check:web` | 린트·타입. `.local/verification/1789177232444-fd62286e.log` |
| `npm run verify -- check:api-types` | 생성 타입 일치. `.local/verification/1789177232431-db09aae2.log` |
| `npm run verify -- build:web` | 새 경로 배포 빌드. `.local/verification/1789177283371-0451a893.log` |
| `npm run verify -- package:backend` | 검증한 서버 실행 파일 생성. `.local/verification/1789177362352-b77508ba.log` |

최초 계약 생성은 Docker가 꺼져 실패했다. Docker 시작 후 생성과 관련 DB 검사를 통과했다. 자동 추출·과금 처리 코드는 바꾸지 않아 `ee34aca`의 전체 서버 검사 결과를 해당 범위에 재사용했다. 이번 검사 뒤 변경은 문서뿐이다.

Playwright로 390px·1280px 가로 넘침, 상태 검색, 빈 결과·조회 장애, 키보드 내역 펼치기·재시도 초점, 페이지 이동·조건 검토 연결을 확인했다. 별도 웹 3100·테스트 API 19082·브라우저 세션은 종료했다. 기록은 `/tmp/youth-admin-ai-browser/`에 있으며 실제 관리자 로그인을 확인한 것은 아니다.

로컬 Spring의 상태 조회 200·관리자 API 비회원 401, Next.js 새 화면 200을 확인했다. 정책 40건·적용 규칙 11건·AI 요청/호출/자동 실행 0건을 유지했다. 실제 AI 생성·청구·관리자 제공자 로그인·원격 CI·배포는 미검증이다. 자동 추출·정기 수집·이메일·알림은 비활성화했다.
