# 로컬 개발 환경

2026-08-30 기준. 이 문서는 현재 실행할 수 있는 개발 골격을 설명한다. 제품 기능의 완료 상태는 [HANDOFF](../../HANDOFF.md), 제품 요구는 [PRD](../PRD/0001_product-baseline/spec.md), 기술 선택은 [ADR](../ADR/0001_기술스택과_책임_분리.md)를 기준으로 한다.

## 1. 현재 구성과 버전

| 구성 | 버전·위치 | 현재 역할 |
|---|---|---|
| 웹 | Next.js 16.3.3, React 19.2.8, `frontend/` | 시작 화면·비회원 조건 입력·공통 상태 안내·개발 전용 질문과 결과 표시 |
| 웹 개발 도구 | TypeScript 5.9.3, Tailwind CSS 4.3.3, ESLint 9.39.5, Vitest 4.1.11 | 타입 검사·스타일·린트·입력 및 상태 화면 테스트 |
| 서버 | Java 25, Spring Boot 4.1.1, `backend/` | 앱 기동·DB 연결·상태 확인·접근 차단·판정 결과 집계·명시적 연령·거주·단일 취업·소득 구간 비교 |
| 모듈 구성 | Spring Modulith 2.1.1 core | 자격 판정용 `eligibility` 패키지. 모듈 의존 검증 테스트는 아직 없음 |
| 빌드 | Gradle Wrapper 9.7.1, npm 잠금 파일 | 백엔드·프런트엔드 빌드 |
| DB | PostgreSQL 18.6 Alpine, `compose.yaml` | 프로젝트 전용 로컬 DB |

Spring Batch, OAuth2 공급자, OpenAPI와 생성 TypeScript 계약, shadcn/ui 컴포넌트, Playwright 자동 테스트, Outbox와 배포 구성은 아직 추가하지 않았다. 해당 기능을 구현할 때 필요한 범위로 추가한다. 웹·서버 CI 워크플로는 작성했으며 설정과 원격 실행의 미확인 범위는 [CI 안내](ci.md)를 따른다.

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

| 항목 | 로컬 주소 |
|---|---|
| 웹 | <http://127.0.0.1:3000> |
| 조건 입력 | <http://127.0.0.1:3000/conditions> |
| 상태 미리보기 · 개발 전용 | <http://127.0.0.1:3000/dev/states> |
| 추가 확인 질문 · 개발 전용 | <http://127.0.0.1:3000/dev/employment> |
| 소득 질문 · 개발 전용 | <http://127.0.0.1:3000/dev/income> |
| 자격 결과·근거 · 개발 전용 | <http://127.0.0.1:3000/dev/eligibility> |
| 서버 상태 | <http://127.0.0.1:8080/actuator/health> |
| PostgreSQL | `127.0.0.1:55432` |

웹과 로컬 프로필의 서버·DB는 루프백 주소에만 연결한다. 다른 기기에 공개하거나 운영에 배포하기 위한 설정이 아니다. 서버는 GET `/actuator/health`만 허용한다. 상태 응답에는 DB 연결 문자열이나 구성요소 상세 정보를 노출하지 않는다. 소셜 로그인과 프런트엔드의 업무 API 연동은 아직 없다.

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

DB 볼륨은 `youth-policy-mate_postgres_data`이다. PostgreSQL 18의 데이터 경로에 맞춰 컨테이너의 `/var/lib/postgresql`에 마운트했다. 업무 마이그레이션은 없고, 서버가 처음 연결하면 `flyway_schema_history` 관리 테이블만 만든다. Hibernate는 스키마를 자동 생성·수정하지 않는다.

웹과 서버는 실행 터미널에서 `Ctrl+C`로 종료한다. DB 컨테이너는 아래 명령으로 종료·제거하되 볼륨은 보존한다.

```sh
npm run db:down
```

`down --volumes`는 사용하지 않는다. 이 옵션은 DB 데이터를 삭제한다.

## 4. 검증 명령과 실제 확인 범위

```sh
npm run test:web
npm run test:eligibility
npm run check:web
npm run build:web
npm run check:backend
npm run check:tools
npm audit
```

`test:eligibility`는 순수 Java 집계 14건·연령 비교 18건·거주 비교 17건·취업 비교 21건·소득 비교 47건, 총 117건을 실행한다. API 인증키·DB·Docker 없이 충족·불충족·미확인·예외와 근거 보존, 조건별 범위·기준일·답변 기준 일치를 확인한다. 실제 정책 원문 해석의 정확도 검증은 아니다. 구현 범위는 [자격 판정 결과](eligibility-decision.md), [연령 비교](age-condition.md), [거주 비교](residence-condition.md), [단일 취업 비교](employment-condition.md), [소득 구간 비교](income-condition.md)를 따른다.

`check:backend`에 포함된 백엔드 통합 테스트는 Compose DB를 사용하지 않고 Testcontainers가 별도 PostgreSQL을 생성한다. 테스트가 끝나면 테스트용 컨테이너를 정리한다. Docker가 없으면 통합 테스트를 건너뛰지 않고 실패한다.

`check:tools`는 응답 점검 도구의 인공 응답 테스트 7개를 실행한다. API 인증키·Docker·네트워크가 필요하지 않으며 실제 API 계약을 검증하지 않는다.

`test:web`은 비회원 조건 입력 검사 5개·상태 화면 검사 3개·취업 질문 표시 검사 4개·소득 질문 표시 검사 5개·결과 표시 검사 7개, 총 24개를 실행한다. 필수 항목·날짜·선택지·연령 제한 비적용·서울 자정 경계에 더해 상태 안내 역할, 오류 원문 비노출과 404 복귀 경로, 인공 질문의 답변 의미·모름 처리와 소득 구간 표시를 확인한다. 결과 표시는 전체 상태 유지·미확인 원인·검토 이슈·근거 비실행을 확인한다. API 인증키·Docker·네트워크가 필요하지 않다.

현재 백엔드 통합 테스트는 다음 2개다.

1. 실제 PostgreSQL 조회와 상태 응답 `UP`, 상세 정보 비노출.
2. 상태 확인 외 경로인 `/actuator/env`의 접근 차단.

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

### 알려진 경고와 다음 확인 사항

- 업무 마이그레이션이 없으므로 Flyway가 `No migrations found`를 출력한다. 경고를 없애려고 임시 업무 테이블을 만들지 않는다. 실제 데이터 구조가 확정되면 첫 마이그레이션을 추가한다.
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
