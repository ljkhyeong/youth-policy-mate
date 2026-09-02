# 로컬 개발 환경

2026-08-31 기준. 이 문서는 현재 실행할 수 있는 개발 골격을 설명한다. 제품 기능의 완료 상태는 [HANDOFF](../../HANDOFF.md), 제품 요구는 [PRD](../PRD/0001_product-baseline/spec.md), 기술 선택은 [ADR](../ADR/0001_기술스택과_책임_분리.md)를 기준으로 한다.

## 1. 현재 구성과 버전

| 구성 | 버전·위치 | 현재 역할 |
|---|---|---|
| 웹 | Next.js 16.3.3, React 19.2.8, `frontend/` | 시작 화면·비회원 조건 입력·공통 상태 안내·개발 전용 질문·자격 결과·마감 후보 표시 |
| 웹 개발 도구 | TypeScript 5.9.3, Tailwind CSS 4.3.3, ESLint 9.39.5, Vitest 4.1.11 | 타입 검사·스타일·린트·입력 및 상태 화면 테스트 |
| API 계약 | springdoc-openapi 3.1.0, openapi-typescript 7.13.0 | 개발 전용 서버 DTO의 OpenAPI 3.1·TypeScript 생성과 일치 검사 |
| 서버 | Java 25, Spring Boot 4.1.1, `backend/` | 앱 기동·DB 연결·접근 차단·조건 비교·모집/알림 후보 계산·개정 적용·수집 진행·AI 후보/비용·예약 상태 검사 |
| 모듈 구성 | Spring Modulith 2.1.1 core | 자격 판정용 `eligibility`, 모집 기간·개정용 `policy`, 알림 후보 날짜용 `schedule`, 수집 진행·AI 후보/비용/예약 상태용 `ingestion` 패키지. 모듈 의존 검증 테스트는 아직 없음 |
| 빌드 | Gradle Wrapper 9.7.1, npm 잠금 파일 | 백엔드·프런트엔드 빌드 |
| DB | PostgreSQL 18.6 Alpine, `compose.yaml` | 프로젝트 전용 로컬 DB |

Spring Batch, OAuth2 공급자, shadcn/ui 컴포넌트, Playwright 자동 테스트, Outbox와 배포 구성은 아직 추가하지 않았다. OpenAPI·생성 TypeScript는 개발 전용 인공 자료 API에 먼저 적용했으며 공개·회원 API 계약은 아직 없다. 웹·서버 CI의 원격 실행 미확인 범위는 [CI 안내](ci.md)를 따른다.

## 2. 필요한 도구

- Node.js 24 LTS와 npm. `.nvmrc`는 24를 지정한다. 현재 패키지는 Node.js 24~26을 허용한다.
- Gradle 실행용 JDK 21 이상. 컴파일·테스트·`bootRun`에는 Java 25 도구체인을 사용한다.
- 실행 중인 Docker와 Docker Compose. 통합 테스트도 실제 PostgreSQL 컨테이너를 사용한다.
- 최초 실행에는 npm·Gradle 의존성, 필요한 JDK와 컨테이너 이미지를 내려받을 네트워크가 필요하다.

Java 25가 없으면 Gradle의 Foojay resolver가 사용자 Gradle 캐시에 준비한다. 시스템 기본 JDK를 변경하지 않는다. Gradle 배포 파일에는 체크섬을 지정했다.

```sh
node --version
npm --version
java -version
docker version
docker compose version
./backend/gradlew -p backend javaToolchains --no-daemon
```

## 3. 설치와 기동

모든 명령은 저장소 루트에서 실행한다.

조건 입력 화면만 확인할 때는 아래 두 명령이면 된다. API 인증키·DB·백엔드가 필요하지 않다.

```sh
npm ci
npm run dev:web
```

백엔드도 실행하려면 DB를 준비한다.

```sh
npm run db:up
```

DB가 정상 상태가 되면 웹과 다른 터미널에서 서버를 실행한다.

```sh
npm run dev:backend
```

DB 없이 서버 계산 결과를 확인하려면 위 DB 연결 서버 대신 `npm run dev:preview-api`를 실행한다. 고정 인공 자료만 계산하는 `preview` 프로필이며 루프백 8081을 사용한다. [개발 API 실행·계약 안내](reminder-preview-api.md)를 따른다. 운영에 이 프로필을 활성화하지 않는다.

| 항목 | 로컬 주소 |
|---|---|
| 웹 | <http://127.0.0.1:3000> |
| 조건 입력 | <http://127.0.0.1:3000/conditions> |
| 상태 미리보기 · 개발 전용 | <http://127.0.0.1:3000/dev/states> |
| 추가 확인 질문 · 개발 전용 | <http://127.0.0.1:3000/dev/employment> |
| 소득 질문 · 개발 전용 | <http://127.0.0.1:3000/dev/income> |
| 자격 결과·근거 · 개발 전용 | <http://127.0.0.1:3000/dev/eligibility> |
| 마감·알림 후보 · 개발 전용 | <http://127.0.0.1:3000/dev/reminders> |
| 마감 후보 서버 연결 · 개발 전용 | <http://127.0.0.1:3000/dev/reminders/server> |
| 자격 판정 서버 연결 · 개발 전용 | <http://127.0.0.1:3000/dev/eligibility/server> |
| 인공 답변 재판정 · 개발 전용 | <http://127.0.0.1:3000/dev/eligibility/interactive> |
| 인공 마감 계산 API · preview 전용 | <http://127.0.0.1:8081/api/dev/reminder-examples> |
| 인공 자격 계산 API · preview 전용 | <http://127.0.0.1:8081/api/dev/eligibility-examples> |
| 인공 질문 GET·재판정 POST · preview 전용 | <http://127.0.0.1:8081/api/dev/eligibility-trial> |
| 생성 OpenAPI · preview 전용 | <http://127.0.0.1:8081/dev/openapi> |
| 서버 상태 | <http://127.0.0.1:8080/actuator/health> |
| PostgreSQL | `127.0.0.1:55432` |

웹과 로컬·preview 프로필의 서버·DB는 루프백 주소에만 연결한다. 다른 기기에 공개하거나 운영에 배포하기 위한 설정이 아니다. 기본 서버는 GET `/actuator/health`만 허용한다. preview는 두 고정 예시 API·명세의 GET, `/api/dev/eligibility-trial`의 질문 GET·인공 계산 POST를 추가 허용한다. 상태 응답에 DB 연결 문자열이나 상세 정보를 노출하지 않는다. 소셜 로그인·실제 정책·회원 업무 API 연동은 아직 없다.

### DB 설정 변경

기본값으로 실행할 때는 `.env`가 없어도 된다. 변경할 때만 루트의 `.env.example`을 `.env`로 복사하고 값을 수정한다. 기존 `.env`가 있다면 덮어쓰지 않는다.

- DB 이름·계정 기본값: `youth_policy_mate`
- 기본 비밀번호: `local-only-password` — 로컬 전용 공개 예시이며 운영에서 사용하지 않는다.
- 변경 변수: `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `.env`는 Git에서 제외한다. `KEY=value` 형식으로 작성하고 값을 따옴표로 감싸지 않는다.
- Compose와 Spring의 로컬 프로필이 같은 루트 `.env`를 읽는다. Spring은 Gradle이 사용하는 `backend/` 작업 디렉터리를 기준으로 `../.env`를 읽는다.
- 로컬 프로필을 지정하지 않은 서버에는 예시 DB 접속 정보가 적용되지 않는다. 배포 설정은 별도로 설계해야 한다.

PostgreSQL은 기존 볼륨이 있으면 초기 계정·DB를 다시 만들지 않는다. 따라서 `.env`만 바꿔도 저장된 DB 비밀번호가 변경되는 것은 아니다. 기존 데이터가 있으면 볼륨을 삭제해서 해결하지 말고 계정 설정을 확인한다.

앱 기동에는 API 인증키가 필요하지 않다. 발급 후 별도 개발 명령 `npm run probe:ontong`에서만 `ONTONG_API_KEY`를 읽어 외부 응답을 점검한다. 현재는 승인 대기 상태이며 실제 API 응답은 아직 확인하지 않았다. [인증키 설정·점검 방법](ontong-api-probe.md)을 따르고, 발급된 키는 채팅·저장소·URL 로그에 남기지 않는다.

### 데이터와 종료

DB 볼륨은 `youth-policy-mate_postgres_data`이다. PostgreSQL 18의 데이터 경로에 맞춰 컨테이너의 `/var/lib/postgresql`에 마운트했다. Flyway V1은 AI 예산 `ai_budgets`와 요청 예약 `ai_request_reservations`를 만든다. 정책 원천·회원·알림 테이블은 아직 없다. Hibernate는 스키마를 자동 생성·수정하지 않는다.

웹과 서버는 실행 터미널에서 `Ctrl+C`로 종료한다. DB 컨테이너는 아래 명령으로 종료·제거하되 볼륨은 보존한다.

```sh
npm run db:down
```

`down --volumes`는 사용하지 않는다. 이 옵션은 DB 데이터를 삭제한다.

## 4. 검증 명령과 실제 확인 범위

```sh
npm run test:web
npm run test:eligibility
npm run test:recruitment
npm run test:policy-revisions
npm run test:ingestion
npm run test:ai-candidates
npm run test:ai-candidate-projection
npm run test:ai-admission
npm run test:ai-reservations
npm run test:ai-reservation-db
npm run test:ai-execution
npm run test:ai-recovery
npm run test:ai-recovery-execution
npm run test:ai-recovery-policy
npm run test:ai-recovery-operations
npm run test:ai-recovery-work
npm run test:reminders
npm run test:preview-api
npm run check:api-types
npm run check:web
npm run build:web
npm run check:backend
npm run check:tools
npm audit
```

`test:eligibility`는 순수 Java 집계 14건·연령 비교 18건·거주 비교 17건·취업 비교 21건·소득 비교 47건, 총 117건을 실행한다. API 인증키·DB·Docker 없이 충족·불충족·미확인·예외와 근거 보존, 조건별 범위·기준일·답변 기준 일치를 확인한다. 실제 정책 원문 해석의 정확도 검증은 아니다. 구현 범위는 [자격 판정 결과](eligibility-decision.md), [연령 비교](age-condition.md), [거주 비교](residence-condition.md), [단일 취업 비교](employment-condition.md), [소득 구간 비교](income-condition.md)를 따른다.

`test:recruitment`는 순수 Java 모집 상태 23건과 마감 날짜 제공 8건, 총 31건을 실행한다. 서울 날짜 경계·명시적 접수 종료 시각·상시·소진 시 종료·미확인 이유·근거 보존을 검사하며 API 인증키·DB·Docker가 필요하지 않다. [모집 기간 구현](recruitment-period.md)에 입력 범위와 실제 원문 해석이 아닌 점을 정리했다.

`test:policy-revisions`는 순수 Java 개정 적용 판단 11건을 실행한다. 원본/비교 내용 분리, 같은 결과 재처리, 낮은 순번·순번 충돌, A→B→A, 수집 실패·비교 방식 불일치를 확인한다. 인증키·DB·Docker가 필요하지 않으며 실제 저장·동시성 제어를 검증한 것은 아니다. [개정 적용 판단](policy-revision-application.md)에 구현·미구현 범위를 구분했다. 같은 정책 패키지의 새 테스트가 모집 검사에 섞이지 않도록 `test:recruitment`는 `Recruitment*`만 선택한다.

`test:reminders`는 마감 알림 후보 날짜 테스트 17건을 실행한다. 월·연도·윤일 경계, 오늘 후보 구분·지난 날짜 제외, 후보 없음 사유와 개정 변경 후 계산을 확인한다. 인증키·DB·Docker 없이 실행하며 실제 예약·발송 검증은 아니다. [후보 날짜 구현](deadline-reminder-candidates.md)을 참고한다.

`test:ingestion`은 수집 진행 12·AI 후보 12·실행·복구 결과 연결 8·사전 판단 14·예약 상태 13·복구 재확인 정책 10건, 총 69건을 실행한다. 페이지·항목별 시도, 명시적 종료·부분 실패, 중단·재개·늦은 결과, AI 버전·재사용·실행·복구 응답의 현재 개정 재검사, 사전 비용, 예약·결과 미확인·정산 상태와 재확인 보류를 확인한다. 인증키·DB·Docker가 필요하지 않으며 실제 수집·DB 복구·후보 저장·자동 작업자·과금 차단 검증은 아니다.

`test:ai-candidates`는 AI 후보 모델 12건만 실행한다. 현재 개정·원본·생성 방식·요청 순번 검사, 같은 내용 재사용·A→B→A, 늦은 응답·재전달·충돌과 실패·한도 보류의 기존 후보 유지를 확인한다. 인증키·AI·DB 없이 인공 참조 값으로 검사하며 실제 본문 정확성·비용 차단 검증은 아니다. [AI 후보 구현](policy-ai-candidates.md)을 따른다.

`test:ai-candidate-projection`은 실행·복구 결과 연결 8건만 실행한다. 확인한 응답만 현재 정책·최신 요청과 다시 비교하고 결과 미확인·미실행·청구 전용 복구·미적용 복구를 생략하는지 확인한다. 인증키·AI·DB 없이 실행하며 실제 후보 저장·자동 공개 검증은 아니다. [AI 결과 후보 연결](policy-ai-candidate-projection.md)을 따른다.

`test:ai-recovery-policy`는 복구 재확인 순수 정책 10건만 실행한다. 첫 시도, 활성 임대와 재확인 간격, 확인 완료·실패, 수동 검토, 완료·만료를 포함한 최대 횟수와 종료 예약을 확인한다. 고정 운영값·자동 작업자·DB 대상 선택 검증은 아니며 [AI 복구 재확인 정책](ai-reservation-recovery-retry-policy.md)을 따른다.

`test:ai-recovery-operations`는 실제 PostgreSQL 18.6에서 내부 운영 조회 5건과 작업 배정 5건, 총 10건을 실행한다. 오래된 미완료 예약 컷오프·정렬·최대 조회 수, 전체 이력 기반 판단, 조회 뒤 수동 검토 재확인, 보류 후보 미배정, 동시 배정 한 건, 동일 요청 재전달을 확인한다. Docker가 필요하며 관리자 API·화면·권한과 실제 공급자 확인 검증은 아니다. [AI 복구 내부 운영 조회](ai-reservation-recovery-operations-query.md)와 [작업 배정](ai-reservation-recovery-work-assignment.md)을 따른다.

`test:ai-recovery-work`는 실제 PostgreSQL 18.6과 인공 복구 포트로 제한 목록 4건과 작업 실행 기록 6건, 총 10건을 실행한다. 보류·중단 후보 뒤의 준비된 후보, 조회 뒤 판단 변경, 조회 개수 제한, 후보 단위 외부 확인 실패 뒤 계속 실행, 실행 ID 재전달·충돌·동시 기동, 완료 집계와 전체 실패 기록을 확인한다. Docker가 필요하며 실제 공급자·주기 스케줄러·운영 재확인 값 검증은 아니다. [AI 복구 제한 목록 실행](ai-reservation-recovery-work-runner.md)과 [작업 실행 기록](ai-reservation-recovery-work-runs.md)을 따른다.

`test:ai-recovery-execution`은 실제 PostgreSQL 18.6과 인공 복구 포트로 조정자 14건을 실행한다. 기존 다음 예약 획득과 배정된 시도 실행, 트랜잭션 밖 확인, 정산·무과금·취소·청구 대기, 완료·교체·만료 임대와 변경·종료 예약의 펜싱을 확인한다. Docker가 필요하며 실제 공급자·주기 스케줄러 검증은 아니다. [인공 복구 조정자](policy-ai-recovery-execution.md)를 따른다.

`test:ai-admission`은 사전 판단 14건만 실행한다. 후보 재사용, 신규·변경 개정·명시적 재시도, 예산 미설정·0원·기간, 예약액·소수 최대 비용·잔액 경계, 비용 미확인·만료·다른 요청의 비용을 확인한다. 실제 예약·정산·청구 차단은 없으며 [AI 사전 판단 구현](ai-request-admission.md)을 따른다.

`test:ai-reservations`는 예약 상태 13건만 실행한다. 최대 비용 예약, 재전달·충돌, 외부 호출·결과 미확인, 정산·호출 전 취소·무과금 확인과 예약 초과 비용을 검사한다. 메모리 상태 전이이며 실제 PostgreSQL 동시성이나 공급자 청구 검증은 아니다. [AI 예약 상태 구현](ai-budget-reservation-lifecycle.md)을 따른다.

`test:ai-reservation-db`는 실제 PostgreSQL 18.6에서 Flyway V1과 원자적 예약 5건을 실행한다. 예약·잔액 동시 갱신, 재전달·충돌, 최신 잔액과 한도, 동시 요청 직렬화, DB 제약을 검사한다. Docker가 필요하며 외부 AI나 공급자 청구는 사용하지 않는다.

`check:backend`에 포함된 백엔드 통합 테스트는 Compose DB를 사용하지 않고 Testcontainers가 별도 PostgreSQL을 생성한다. 테스트가 끝나면 테스트용 컨테이너를 정리한다. Docker가 없으면 통합 테스트를 건너뛰지 않고 실패한다.

`check:tools`는 응답 점검 도구의 인공 응답 테스트 7개를 실행한다. API 인증키·Docker·네트워크가 필요하지 않으며 실제 API 계약을 검증하지 않는다.

`test:web`은 기존 조건·상태·질문·결과 표시 32개, 마감 API 연결 7개·자격 API 연결 6개·인공 답변 재판정 8개, 총 53개를 실행한다. 날짜·기준·상태·근거 보존, 오류 원문 비노출, 실패 시 결과 미제공, 늦은 응답 무시와 운영 모드 호출 차단을 포함한다. API 인증키·Docker·네트워크가 필요하지 않다.

`test:preview-api`는 마감 API 4건·자격 API 3건·인공 답변 재판정 5건·공통 계약 1건, 총 13건을 실행하며 DB·Docker가 필요하지 않다. `npm run generate:api`는 실제 생성 OpenAPI와 TypeScript를 갱신하고 `check:api-types`는 타입의 최신 여부만 확인한다. 재생성 절차는 [개발 API 안내](reminder-preview-api.md#계약-생성과-검사)를 따른다.

기본 상태·접근 차단 통합 테스트는 다음 2개이며, AI 예약·실행·복구 저장소 검사는 별도 PostgreSQL Testcontainers 테스트로 실행한다.

1. 실제 PostgreSQL 조회와 상태 응답 `UP`, 상세 정보 비노출.
2. 상태 확인 외 경로와 개발 API의 접근 차단, 기본 모드에서 개발 컨트롤러 미등록.

기동 후 수동 상태 확인:

```sh
curl -i http://127.0.0.1:8080/actuator/health
curl -i http://127.0.0.1:8080/actuator/env
```

첫 요청은 200과 `status: UP`, 두 번째는 403이어야 한다. Spring Boot가 제공하는 상태 그룹 이름은 응답에 포함될 수 있다.

2026-08-30 최초 개발 환경 검증은 macOS arm64, Node.js 25.4.0·npm 11.7.0, Gradle이 준비한 Temurin 25.0.3, Docker 29.7.2에서 진행했다. 이후 Node.js 24에서 실행한 CI 검증 기록은 아래에 구분한다.

조건 입력 추가 후 Vitest 5개, 린트·타입 검사와 프로덕션 빌드를 확인했다. `npm audit`의 알려진 취약점은 0건이다. 브라우저에서 빈 입력 오류·첫 오류 포커스, 확인 화면, 수정 시 값 유지, 초기화와 새로고침 시 값 삭제를 확인했다. 데스크톱 1280px와 모바일 390px 화면에서 시작·입력·확인 화면을 점검했다. 모바일은 브라우저 크기 변경이며 실제 휴대전화 검증이나 저장소에 추가한 E2E 자동 테스트는 아니다. 브라우저 도구의 Tab·Enter 동작이 반영되지 않아 키보드만 사용하는 전체 흐름은 확인하지 못했다. 상세 범위는 [비회원 조건 입력](guest-conditions.md)을 따른다.

판정 결과 집계 모델 추가 후에는 `test:eligibility`의 14건과 `./backend/gradlew -p backend assemble --no-daemon` 빌드를 확인했다. 모두 통과했다. DB·웹 코드는 변경하지 않았으며 기존 PostgreSQL 통합 테스트와 프런트엔드 검사는 이 작업에서 다시 실행하지 않았다.

CI 구성 후에는 macOS arm64의 별도 임시 복사본에서 Node.js 24.20.0·npm 11.19.0으로 `npm ci --no-audit --no-fund`, 개발 도구 테스트 7개, 웹 테스트 5개, 린트·타입 검사·프로덕션 빌드를 모두 통과했다. 서버도 전체 `build`를 실행해 단위 14개·PostgreSQL 통합 2개가 실패·건너뛰기 없이 통과했다. actionlint 1.7.12로 워크플로 문법을 확인했다. GitHub의 Ubuntu 실행·캐시·보고서 업로드는 아직 확인하지 않았다. 자세한 환경과 경고는 [CI 검증 기록](ci.md#실제-확인한-결과)을 따른다.

공통 상태 화면 추가 후에는 Node.js 25.4.0·npm 11.7.0에서 웹 테스트 8개·린트·타입 검사·빌드를 통과했다. 개발 미리보기와 운영 빌드의 404 차단, 데스크톱·모바일 배치를 확인했다. 실제 API 복구와 키보드 전용 흐름 등 미확인 범위는 [상태 화면 검증 기록](page-states.md#접근성과-검증)을 따른다.

연령 비교기 추가 후에는 `npm run test:eligibility`의 32건과 `./backend/gradlew -p backend assemble --no-daemon` 빌드가 통과했다. 생일·윤일 경계와 미해석 조건은 인공 입력으로 검사했다. 실제 정책 API는 호출하지 않았으며 웹·PostgreSQL 통합 검사는 다시 실행하지 않았다. 상세 범위는 [연령 비교 검증 기록](age-condition.md#코드와-검증)을 따른다.

거주 비교기 추가 후에는 `npm run test:eligibility`의 49건과 서버 `assemble`이 통과했다. 전국·서울·자치구 범위, 입력 기준일 차이와 미해석 조건을 인공 입력으로 검사했다. 기존 UI와 서버의 자치구 표시 이름 25개가 같은지도 확인했다. 실제 API 호출·프런트엔드 검사·PostgreSQL 통합 검사는 이번 작업에서 실행하지 않았다. 상세 범위는 [거주 비교 검증 기록](residence-condition.md#코드와-검증)을 따른다.

단일 취업 비교기 추가 후에는 `npm run test:eligibility`의 70건과 서버 `assemble`이 통과했다. 해당·비해당 요구 방향, 모름·누락, 정책 개정·정의·기준일이 다른 답변의 재사용 차단과 기존 집계를 인공 입력으로 검사했다. 실제 API 호출·프런트엔드 검사·PostgreSQL 통합 검사는 이번 작업에서 실행하지 않았다. 상세 범위는 [취업 비교 검증 기록](employment-condition.md#코드와-검증)을 따른다.

추가 확인 질문 미리보기 구현 후에는 웹 테스트 12개·린트·타입 검사·프로덕션 빌드가 통과했다. 개발 경로 200과 운영 경로 404, 답변 확인·수정·삭제와 질문 개정 변경 시 초기화, 데스크톱·모바일 배치를 확인했다. 서버·DB 검사는 다시 실행하지 않았다. 키보드 검증의 한계와 빌드 캐시 처리 기록은 [추가 질문 검증 기록](employment-question-preview.md#검증-결과와-한계)을 따른다.

소득 구간 비교기 추가 후에는 `./backend/gradlew -p backend test --tests 'kr.youthpolicymate.eligibility.*' assemble --no-daemon`으로 판정 117건과 서버 빌드가 통과했다. 포함 경계·일부 겹침·소수 정밀도·0원과 모름 구분·다른 입력 기준의 재사용 차단을 인공 자료로 확인했다. 실제 API·원천 매핑·소득 화면·보험료 산정 검증은 아니며 웹·PostgreSQL 통합 검사는 다시 실행하지 않았다. 상세 범위는 [소득 비교 검증 기록](income-condition.md#코드와-검증)을 따른다.

소득 질문 미리보기 추가 후에는 웹 테스트 17개·린트·타입 검사·프로덕션 빌드가 통과했다. 개발 경로 200·운영 경로 404, 답변 확인·수정·삭제와 기간 변경·새로고침 초기화, 데스크톱·모바일 구간 표시를 확인했다. 키보드 전용 흐름은 도구 입력 한계로 미확인이고 서버·DB 검사는 다시 실행하지 않았다. 생성 캐시 재생성 등 환경 처리와 범위는 [소득 질문 검증 기록](income-question-preview.md#검증-결과와-한계)을 따른다.

결과 표시 컴포넌트와 미리보기 추가 후에는 웹 테스트 24개·린트·타입 검사·프로덕션 빌드가 통과했다. 개발 결과 경로 200·운영 결과 경로 404와 운영 조건 입력 200, 네 예시 전환·근거 펼치기·새로고침 초기화와 반응형 표시를 확인했다. 서버·DB 검사는 다시 실행하지 않았다. 키보드 입력의 검증 한계와 상세 범위는 [결과 화면 검증 기록](eligibility-result-preview.md#검증-결과와-한계)을 따른다.

2026-08-31 모집 기간 모델 추가 후에는 모집 기간 23건과 기존 자격 판정 117건을 함께 실행해 총 140건이 통과했고 서버 `assemble`도 통과했다. 웹·PostgreSQL 통합 검사는 다시 실행하지 않았다. Gradle 캐시 접근 권한 처리와 Java agent 경고, 검증한 경계는 [모집 기간 검증 기록](recruitment-period.md#실행한-검증)을 따른다.

2026-08-31 마감 알림 후보 날짜 추가 후에는 후보 17건·마감 날짜 제공 8건을 포함한 총 165건과 서버 `assemble`이 통과했다. 기존 자격 판정 117건·모집 상태 23건도 함께 실행했다. 웹·PostgreSQL 통합 검사는 다시 실행하지 않았으며, 실제 예약·발송은 연결하지 않았다. [후보 날짜 검증 기록](deadline-reminder-candidates.md#검증)에 상세 범위를 정리했다.

2026-08-31 마감·알림 후보 미리보기 추가 후에는 웹 테스트 32개·린트·타입 검사·프로덕션 빌드가 통과했다. 개발 경로 200·운영 경로 404와 운영 조건 입력 200, 일곱 예시 전환·근거 표시·새로고침 초기화, 데스크톱·모바일 표시를 확인했다. 서버·DB 검사는 다시 실행하지 않았다. 키보드 입력과 새로고침 후 요소 조회의 도구 한계는 [마감 후보 화면 검증 기록](deadline-reminder-preview.md#검증-결과와-한계)에 정리했다.

같은 날 개발 API 연결 후에는 서버 172건(도메인 165·API 5·실제 DB/기본 차단 2)과 전체 빌드, 웹 39건·생성 타입 검사·린트·타입 검사·빌드를 통과했다. 서버 미기동 오류에서 기동 후 다시 불러오기로 복구했고, 개발 200·운영 404와 반응형을 확인했다. 운영 모드에서 로딩 스트리밍 전에 차단하도록 레이아웃을 두었다. [API 연결 검증 기록](reminder-preview-api.md#검증-결과와-한계)에 범위와 한계를 정리했다.

자격 API 연결 후에는 서버 175건(도메인 165·API/계약 8·실제 DB/기본 차단 2)과 전체 빌드, 웹 45건·생성 타입 검사·린트·타입 검사·빌드를 통과했다. null 허용 enum의 실제 명세도 검사하며 서버 중지 후 복구·개발 200·운영 404를 확인했다. [자격 서버 연결 기록](eligibility-preview-api.md)에 미확인 키보드 흐름과 실제 정책 연결 범위를 정리했다.

인공 답변 재판정 연결 후에는 서버 180건(도메인 165·API/계약 13·실제 DB/기본 차단 2)과 전체 빌드, 웹 53건·생성 계약·린트·타입 검사·빌드를 통과했다. 인공 코드만 전송하고 이전 질문 답변은 재사용하지 않는다. 답변 변경·실패·재시도·운영 404와 미확인 범위는 [재판정 검증 기록](eligibility-answer-trial.md#검증)을 따른다.

정책 개정 적용 모델 추가 후에는 전용 개정 11건·모집 31건 검사와 전체 서버 191건(도메인 176·API/계약 13·실제 DB/기본 차단 2) 및 빌드가 통과했다. 화면·API 계약·스키마는 변경하지 않아 웹 검사·브라우저는 다시 실행하지 않았다. 원본 저장·DB 중복 방지·원천 계약은 미구현이며 [개정 적용 검증](policy-revision-application.md#검증-범위)을 따른다.

수집 진행 모델 추가 후에는 전용 12건과 전체 서버 203건(도메인 188·API/계약 13·실제 DB/기본 차단 2), 빌드가 통과했다. 첫 전체 검사에서 임시 PostgreSQL 연결 중 `EOFException`으로 통합 2건이 실패했으나 설정·테스트 변경 없는 재실행으로 통과했다. 원인은 확정하지 않았다. 웹·브라우저는 다시 검사하지 않았으며 [수집 진행 검증 기록](collection-run-progress.md#검사)을 따른다.

AI 후보 개정·버전 검사 추가 후에는 전용 12건과 전체 서버 215건(도메인 200·API/계약 13·실제 DB/기본 차단 2), 빌드가 실패·건너뛰기 없이 통과했다. 이번 DB 검사는 첫 실행에 통과했다. 웹·브라우저·실제 AI는 검사하지 않았고 본문 품질·비용 차단 검증도 아니다. [AI 후보 검증 기록](policy-ai-candidates.md#검증)을 따른다.

AI 요청 전 판단 추가 후에는 전용 14건과 전체 서버 229건(도메인 214·API/계약 13·실제 DB/기본 차단 2), 빌드가 실패·건너뛰기 없이 통과했다. 웹·브라우저·실제 AI·가격 계산·예산 예약은 검사하지 않았다. [사전 판단 검증 기록](ai-request-admission.md#검사)에 확인 범위와 실제 과금 차단이 아닌 점을 구분했다.

AI 예약 상태 추가 후에는 전용 13건과 전체 서버 242건(도메인 227·API/계약 13·실제 DB/기본 차단 2), 빌드가 실패·건너뛰기 없이 통과했다. 웹·브라우저·실제 AI·DB 예약·공급자 청구는 검사하지 않았다. [예약 상태 검증 기록](ai-budget-reservation-lifecycle.md#검사)을 따른다.

PostgreSQL 원자적 예약 추가 후에는 전용 DB 5건과 전체 서버 247건(도메인 227·API/계약 13·PostgreSQL 예약·연결/기본 차단 7), 빌드가 실패·건너뛰기 없이 통과했다. 첫 전용 실행은 Java `Instant`의 SQL 유형을 추론하지 못해 5건이 실패했다. UTC `OffsetDateTime`과 PostgreSQL 마이크로초 정밀도로 매핑한 뒤 전용·전체 검사가 통과했다. 실제 AI 호출·호출 이후 상태·공급자 청구는 검사하지 않았다.

### 알려진 경고와 다음 확인 사항

- ESLint 9.39.5 설치 시 지원 종료 경고가 나온다. 현재 Next.js 린트 설정이 사용하는 React·접근성·import 플러그인의 peer 범위가 ESLint 9까지여서 호환되는 버전을 고정했다. ESLint 10으로 올릴 때 세 플러그인의 지원 범위와 린트 동작을 함께 확인한다. 이는 개발 도구 경고이며 실행 의존성에 포함되지 않는다.
- 테스트 라이브러리가 Java agent의 동적 로딩 경고를 출력할 수 있다. 경고를 숨기기 위한 JVM 옵션은 추가하지 않았다.
- 현재 화면은 공개 제품 화면이 아니므로 `noindex, nofollow`를 적용했다. 실제 공개 정책 페이지를 구현할 때 공개 콘텐츠에 맞는 메타데이터와 검색 노출 정책으로 변경한다.
- Next.js의 `agentRules` 자동 생성을 끄고 저장소의 `AGENTS.md`와 `skills/`를 사용한다. 개발 서버를 실행할 때 별도 `frontend/AGENTS.md`·`CLAUDE.md`가 생겨 작업 지침이 중복되는 것을 막는다.

## 5. 확인한 공식 자료

- [Next.js 설치](https://nextjs.org/docs/app/getting-started/installation)
- [Spring Boot 요구사항](https://docs.spring.io/spring-boot/system-requirements.html)
- [Gradle Java 도구체인](https://docs.gradle.org/current/userguide/toolchains.html)
- [Gradle Foojay resolver](https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention)
- [PostgreSQL 공식 컨테이너와 18 버전 볼륨 경로](https://hub.docker.com/_/postgres)
- [Node.js 릴리스 상태](https://nodejs.org/en/about/previous-releases)
