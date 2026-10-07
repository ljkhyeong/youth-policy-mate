# 로컬 개발 환경

이 문서는 로컬 설치·실행 방법을 설명한다. 제품 기능의 완료 상태는 [HANDOFF](../../HANDOFF.md), 제품 요구는 [PRD](../PRD/0001_product-baseline/spec.md), 기술 선택은 [ADR](../ADR/0001_기술스택과_책임_분리.md)를 기준으로 한다. 변경별 검사와 결과 기록은 [검증 절차](verification-workflow.md)를 따른다.

## 1. 현재 구성과 버전

| 구성 | 버전·위치 | 현재 역할 |
|---|---|---|
| 웹 | Next.js 16.3.3, React 19.2.8, `frontend/` | 시작 화면·조건 입력·정책 조회·회원·관리자 화면·공통 상태 안내 |
| 웹 개발 도구 | TypeScript 5.9.3, Tailwind CSS 4.3.3, ESLint 9.39.5, Vitest 4.1.11 | 타입 검사·스타일·린트·입력 및 상태 화면 테스트 |
| API 계약 | springdoc-openapi 3.1.0, openapi-typescript 7.13.0 | 정책·회원·관리자 서버 DTO의 OpenAPI 3.1·TypeScript 생성과 일치 검사 |
| 서버 | Java 25, Spring Boot 4.1.1, `backend/` | 앱 기동·DB 연결·접근 차단·조건 비교·모집 상태 계산·개정 적용·수집·AI 후보/비용·예약 상태 검사 |
| 모듈 구성 | 기능별 Java 패키지 | 회원 `member`, 정책·공고 규칙 판정 `policy`, 조건 결과 값 `eligibility`, 수집·AI `ingestion` |
| DB 접근 | Spring JDBC | JdbcClient·JdbcTemplate과 JDBC 트랜잭션을 사용한다. 페이지는 `LIMIT`·`OFFSET`과 개수 조회로 나누며 JPA/Hibernate·Spring Data·Modulith 의존성은 없다. |
| 빌드 | Gradle Wrapper 9.7.1, npm 잠금 파일 | 백엔드·프런트엔드 빌드 |
| DB | PostgreSQL 18.6 Alpine, `compose.yaml` | 프로젝트 전용 로컬 DB |

정책 수집, OAuth2 회원 API, 이메일 Outbox 구현을 포함한 현재 기능은 HANDOFF에서 확인한다. 외부 로그인·이메일 공급자의 실제 연동 검증과 구현 완료를 구분한다. 정확한 의존성은 빌드 설정·잠금 파일, 원격 검증 범위는 [CI 안내](ci.md)를 따른다.

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

DB 데이터는 [백업과 복구 검증](database-backup.md)의 `db:backup`·`db:verify-backup` 명령으로 확인한다.

DB가 정상 상태가 되면 웹과 다른 터미널에서 서버를 실행한다.

```sh
npm run dev:backend
```

| 항목 | 로컬 주소 |
|---|---|
| 웹 | <http://127.0.0.1:3000> |
| 조건 입력 | <http://127.0.0.1:3000/conditions> |
| 서버 상태 | <http://127.0.0.1:8080/actuator/health> |
| PostgreSQL | `127.0.0.1:55432` |

웹과 로컬 프로필의 서버·DB는 루프백 주소에만 연결한다. 다른 기기에 공개하거나 운영에 배포하기 위한 설정이 아니다. 기본 서버는 GET `/actuator/health`와 공개 정책 목록·상세 GET을 허용한다. 상태 응답에 DB 연결 문자열이나 상세 정보를 노출하지 않는다. 실제 정책은 [조회 안내](policy-catalog.md), 회원 인증은 [회원 기능](member-policy-flow.md), 이메일은 [이메일 설정](member-email-reminders.md)을 참고한다.

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

DB 볼륨은 `youth-policy-mate_postgres_data`이다. PostgreSQL 18의 데이터 경로에 맞춰 컨테이너의 `/var/lib/postgresql`에 마운트했다. 스키마는 서버나 수집 같은 운영 명령이 시작될 때 Flyway가 적용한다. 운영 DB를 만들기 전이라 이전 마이그레이션을 `V1__baseline_schema.sql`(정책·수집·회원·이메일·조건 규칙·AI 기록의 기준 스키마)과 `V2__seed_reviewed_policy_rules.sql`(검토한 공고별 조건 규칙 12건)로 합쳤고, 이후 변경은 다음 번호의 새 파일로 추가한다. Hibernate는 스키마를 자동 생성·수정하지 않는다.

웹과 서버는 실행 터미널에서 `Ctrl+C`로 종료한다. DB 컨테이너는 아래 명령으로 종료·제거하되 볼륨은 보존한다.

```sh
npm run db:down
```

`down --volumes`(`-v`)는 평소에 사용하지 않는다. 이 옵션은 DB 데이터를 삭제한다. 예외로 기준 스키마로 합치기 전에 만든 볼륨은 서버·명령 기동 시 Flyway 검증(checksum·누락 파일)이 실패한다. 로컬 데이터를 버려도 되는지 확인한 뒤 볼륨을 다시 만들고 정책을 다시 수집한다.

```sh
npm run db:down -- -v
npm run db:up
npm run collect:policy -- --args='fetch 1'
```

- `npm run db:down -- -v`는 `docker compose down -v`와 같다. 컨테이너를 이미 내렸다면 `docker volume rm youth-policy-mate_postgres_data`로 볼륨만 지운다.
- 새 볼륨에는 검토 규칙 12건만 들어 있다. 회원·관심 정책·관리자 보정·운영 명령으로 등록한 규칙 버전 등 로컬에서 만든 데이터는 다시 만들어야 한다.
- 수집에는 `.env`의 `ONTONG_API_KEY`가 필요하다. `fetch`는 한 페이지(최대 10건)만 가져오므로 페이지 번호를 바꿔 반복하거나 [범위 수집](policy-range-collection.md#설정과-실행)을 사용한다. 검토 규칙은 수집한 원문의 내용 해시가 검토 당시와 같을 때만 질문을 제공한다.

## 4. 검증 명령과 실제 확인 범위

```sh
npm run test:web
npm run test:recruitment
npm run test:ai-reservation-db
npm run check:api-types
npm run check:web
npm run build:web
npm run check:backend
npm run check:tools
npm audit
```

`test:recruitment`는 `PolicyRecruitmentTest`로 모집 상태·마감일·저장 정책 마감 사유를 검사한다. 서울 날짜 경계·명시적 접수 종료 시각·상시·소진 시 종료·미확인 이유·검토한 공고의 보정 기간을 확인하며 API 인증키·DB·Docker가 필요하지 않다. [모집 기간 구현](recruitment-period.md)에 입력 범위와 실제 원문 해석이 아닌 점을 정리했다.

`test:ai-reservation-db`는 PostgreSQL 18.6에서 AI 호출 기록의 예약·잔액 동시 갱신, 요금·기간·한도 경계, 발송·결과 미확인·정산·취소·무과금 해제와 재생·충돌, 동시 종료와 DB 제약을 검사한다. Docker가 필요하며 외부 AI나 공급자 청구는 사용하지 않는다. [AI 예약·정산 구현](ai-budget-reservation-lifecycle.md)을 따른다.

`check:backend`에 포함된 백엔드 통합 테스트는 Compose DB를 사용하지 않고 Testcontainers가 별도 PostgreSQL을 생성한다. 테스트가 끝나면 테스트용 컨테이너를 정리한다. Docker가 없으면 통합 테스트를 건너뛰지 않고 실패한다.

`check:tools`는 응답 점검 도구의 인공 응답 테스트 7개를 실행한다. API 인증키·Docker·네트워크가 필요하지 않으며 실제 API 계약을 검증하지 않는다.

`test:web`은 웹 화면·API 중계·상태 처리 Vitest 테스트를 실행한다. 날짜·기준·상태·근거 보존, 오류 원문 비노출, 실패 시 결과 미제공과 늦은 응답 무시를 포함한다. API 인증키·Docker·네트워크가 필요하지 않다.

`npm run generate:api`는 실제 생성 OpenAPI(`api/openapi.policy.json`)와 TypeScript를 갱신하고 `check:api-types`는 타입의 최신 여부만 확인한다. 계약 생성에는 PostgreSQL Testcontainers가 필요하다. 생성 방식은 [ADR-0002](../ADR/0002_서버_DTO_기반_API_계약_생성.md)를 따른다.

기본 상태·접근 차단 통합 테스트는 다음을 확인하며, AI 예약·실행 저장소 검사는 별도 PostgreSQL Testcontainers 테스트로 실행한다.

1. 실제 PostgreSQL 조회와 상태 응답 `UP`, 상세 정보 비노출.
2. 상태 확인 외 운영 점검·명세 경로(`/actuator/env`, `/v3/api-docs`)의 접근 차단.

기동 후 수동 상태 확인:

```sh
curl -i http://127.0.0.1:8080/actuator/health
curl -i http://127.0.0.1:8080/actuator/env
```

첫 요청은 200과 `status: UP`, 두 번째는 403이어야 한다. Spring Boot가 제공하는 상태 그룹 이름은 응답에 포함될 수 있다.

2026-08-30 최초 개발 환경 검증은 macOS arm64, Node.js 25.4.0·npm 11.7.0, Gradle이 준비한 Temurin 25.0.3, Docker 29.7.2에서 진행했다. 이후 Node.js 24에서 실행한 CI 검증 기록은 아래에 구분한다.

조건 입력 추가 후 Vitest 5개, 린트·타입 검사와 프로덕션 빌드를 확인했다. `npm audit`의 알려진 취약점은 0건이다. 브라우저에서 빈 입력 오류·첫 오류 포커스, 확인 화면, 수정 시 값 유지, 초기화와 새로고침 시 값 삭제를 확인했다. 데스크톱 1280px와 모바일 390px 화면에서 시작·입력·확인 화면을 점검했다. 모바일은 브라우저 크기 변경이며 실제 휴대전화 검증이나 저장소에 추가한 E2E 자동 테스트는 아니다. 브라우저 도구의 Tab·Enter 동작이 반영되지 않아 키보드만 사용하는 전체 흐름은 확인하지 못했다. 상세 범위는 [비회원 조건 입력](guest-conditions.md)을 따른다.

CI 구성 후에는 macOS arm64의 별도 임시 복사본에서 Node.js 24.20.0·npm 11.19.0으로 `npm ci --no-audit --no-fund`, 개발 도구 테스트 7개, 웹 테스트 5개, 린트·타입 검사·프로덕션 빌드를 모두 통과했다. 서버도 전체 `build`를 실행해 단위 14개·PostgreSQL 통합 2개가 실패·건너뛰기 없이 통과했다. actionlint 1.7.12로 워크플로 문법을 확인했다. GitHub의 Ubuntu 실행·캐시·보고서 업로드는 아직 확인하지 않았다. 자세한 환경과 경고는 [CI 검증 기록](ci.md#실제-확인한-결과)을 따른다.

공통 상태 화면 추가 후에는 Node.js 25.4.0·npm 11.7.0에서 웹 테스트 8개·린트·타입 검사·빌드를 통과했다. 개발 미리보기와 운영 빌드의 404 차단, 데스크톱·모바일 배치를 확인했다. 실제 API 복구와 키보드 전용 흐름 등 미확인 범위는 [상태 화면 검증 기록](page-states.md#접근성과-검증)을 따른다.

2026-08-31까지 추가한 판정 결과 집계 모델·연령 비교기(`test:eligibility`), 거주·취업·소득 비교기, 마감 알림 후보 날짜, `/dev` 미리보기 화면과 preview API의 검증 기록은 해당 코드를 2026-10-07 제거하면서 함께 삭제했다. 필요하면 저장소 이력에서 확인한다.

2026-08-31 모집 기간 모델 추가 후에는 모집 기간 23건과 기존 자격 판정 117건을 함께 실행해 총 140건이 통과했고 서버 `assemble`도 통과했다. 웹·PostgreSQL 통합 검사는 다시 실행하지 않았다. Gradle 캐시 접근 권한 처리와 Java agent 경고, 검증한 경계는 [모집 기간 검증 기록](recruitment-period.md#실행한-검증)을 따른다.

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

## 실행 파일과 빌드 분리

빌드 디렉터리의 JAR을 계속 실행한 상태에서 다시 빌드하면 실행 중인 서버가 일부 클래스를 읽지 못할 수 있다. `package:backend` 또는 서버 빌드 후 실행 파일을 별도 경로에 복사해 실행한다. 이미 실행 중인 복사본은 덮어쓰지 않고 새 경로를 사용한다.

```sh
runtime_jar="/tmp/youth-policy-api-$(git rev-parse --short HEAD)-$(date +%s).jar"
cp backend/build/libs/youth-policy-mate-0.0.1-SNAPSHOT.jar "$runtime_jar"
java -jar "$runtime_jar" --spring.profiles.active=local --app.email.enabled=false --app.reminders.enabled=false --app.ontong.schedule.enabled=false --app.ai.auto.enabled=false
```

기존 프로세스와 포트 사용을 확인한 뒤 교체한다. 운영 실행은 [외부 API와 홈서버 준비](external-api-runtime.md)를 따른다.
