# 관리자 조건 검토

`ef7a25e`에서 공고가 바뀌었거나 질문을 아직 등록하지 않은 정책을 찾는 관리자 조회 화면을 추가했다.

## 사용 흐름

1. `/admin/collection-exceptions/rules`에서 정책명·정책번호와 상태를 검색한다. 기본값은 `검토 필요`다.
2. 정책을 선택해 직전 공개 버전과의 차이, 현재 공개 내용·수집 원본, 등록된 질문·선택지·공식 근거를 확인한다.
3. 원문·기간·예외를 검토한 뒤 [규칙 운영 명령](policy-rule-data.md#운영-명령)으로 새 초안을 등록·적용한다. 화면은 조회만 제공한다.

| 상태 | 판단 기준 |
|---|---|
| 원문 변경 | 현재 지정 규칙의 원문 해시와 공개 정책의 해시가 다름 |
| 기간 만료 | 현재 시각이 규칙 종료 시각 이상 |
| 조건 미등록 | 현재 지정 규칙이 없음. 초안이 있어도 미등록으로 표시 |
| 적용 전 | 현재 시각이 규칙 시작 시각보다 빠름 |
| 적용 중 | 현재 원문과 적용 기간이 일치 |

원문 변경·기간 만료·조건 미등록을 검토 필요로 묶고 이 순서로 표시한다. 같은 상태는 최근 수집 시각·정책번호 순서다. 검색과 필터를 전체 결과에 적용한 뒤 페이지를 나누며 상세 이동·목록 복귀에 검색 조건을 유지한다. 모집 상태와 자격 충족 여부를 이 상태로 대신하지 않는다.

상태는 별도 작업 표에 저장하지 않고 조회 시점의 원문·지정 규칙·주입된 `Clock`으로 계산한다. 개정 비교와 규칙 조회는 하나의 읽기 전용 `REPEATABLE_READ` 트랜잭션에서 수행한다. 전체 건수와 목록도 같은 스냅샷을 사용한다. 별도 배치·외부 호출·DB 마이그레이션은 추가하지 않았다.

## 비교 범위와 권한

- 개정 비교는 정규화한 현재 공개 내용과 **직전 공개 버전** 사이의 차이다. 규칙 검토 당시 원문과의 비교라고 표시하지 않는다. 정규화 화면에 없는 원천 항목은 수집 원본 전체와 공식 공고에서 확인한다.
- 수집 원본과 공개 내용의 보정 여부를 구분한다. 원본·질문·사유는 텍스트로 표시하며 HTML을 실행하지 않는다.
- 현재 지정 버전을 먼저 보여주고 최근 등록 내역을 포함해 최대 21개를 표시한다. 현재 지정·미적용 초안·이전 적용 버전과 현재 원문 일치 여부, 각 적용 기간을 구분한다.
- 관리자 설정에 등록된 회원의 소셜 로그인 세션만 허용한다. 비회원 401·일반 회원 403·없는 정책 404·입력 오류 400·조회 장애 503을 구분하고 응답을 캐시하지 않는다. 관리자 경로에 POST를 허용하지 않는다.
- API는 `GET /api/v1/admin/policy-rule-reviews`, `GET /api/v1/admin/policy-rule-reviews/{number}`다. 첫 API는 `page`, `pageSize`, `filter`, `query`를 받으며 서버 DTO에서 OpenAPI·TypeScript를 생성한다.

실제 관리자 계정 연결, AI 추출 본문 저장·초안 자동 생성, 웹에서 규칙 편집·적용은 남아 있다. 공고별 검토를 완전히 자동화한 상태는 아니다.

## 검증

Java 25.0.3·PostgreSQL 18.6·Node 25.4.0에서 확인했다. 아래 결과는 `ef7a25e`에 적용된다.

| 검증 | 결과와 로그 |
|---|---|
| `./backend/gradlew -p backend test --tests 'kr.youthpolicymate.admin.PolicyRuleReviewApiTest' --no-daemon` | 관리자 권한·미등록 초안·기간 경계·검색·페이지·개정 비교·원본 보존·오류 응답 통과. `/tmp/youth-rule-review-api.log` |
| `npm run generate:api` | 계약 생성 통과. `/tmp/youth-rule-review-contract.log` |
| `npm run verify -- check:backend` | 전체 서버·DB·계약 검사 통과. `.local/verification/1789169033284-9adb41df.log` |
| `npm run verify -- test:web -- src/features/admin/policy-rule-review-pages.test.tsx src/features/admin/load-collection-exceptions.test.ts src/features/admin/collection-exception-pages.test.tsx` | 검색 조건 유지·초안 표시·오류 구분·텍스트 이스케이프·기존 관리자 화면 통과. `.local/verification/1789169033900-93915082.log` |
| `npm run verify -- check:web` | 린트·타입 검사 통과. `.local/verification/1789169033486-6fcc99a0.log` |
| `npm run verify -- check:api-types` | 생성 타입 일치. `.local/verification/1789169033822-99f7720a.log` |
| `npm run verify -- build:web` | 최종 검색 폼 배치까지 배포 빌드 통과. `.local/verification/1789169338773-82f6d1a3.log` |

웹 테스트·타입 검사 후 검색 폼의 컨테이너와 CSS 간격만 조정했다. 최종 빌드와 실제 화면으로 확인했고 같은 서버 검사를 반복하지 않았다. 기존 `test:admin-collection`에도 새 API 테스트를 포함했다. 해당 테스트는 위 전체 서버 검사에서 통과했으며 명령의 선택 범위 추가만으로 재실행하지 않았다.

브라우저 검증은 별도 포트 3100의 배포 웹과 19081의 테스트 API를 사용했다. 검색·상태 필터·페이지 이동·목록 복귀·키보드 검색, 원문과 초안 펼치기, 비회원·권한 없음·조회 장애 구분을 확인했다. 1280px·390px에서 가로 넘침이 없었으며 검색 폼 높이를 각각 130px·278px로 줄였다. 기록과 이미지는 `/tmp/youth-rule-review-ui/`에 보관한다. 실제 소셜 제공자의 관리자 로그인을 검증한 것은 아니다.

실제 로컬 Spring도 새 빌드로 교체해 정상 기동과 새 관리자 API의 비회원 401을 확인했다. 로컬 DB는 V24를 유지한다. 원격 CI·배포·실제 관리자 로그인은 이번 작업에서 실행하지 않았다.
