# CI 구성과 검증 범위

2026-08-31 기준. [.github/workflows/ci.yml](../../.github/workflows/ci.yml)에 GitHub Actions 검사를 구성했다. 최초 검증과 이후 생성 계약 검사 추가를 아래에 구분한다. 워크플로를 원격 저장소에 푸시하거나 GitHub에서 실행하지는 않았다.

## 실행 조건과 검사

- `main`에 푸시하거나 `main`을 대상으로 PR을 열고 변경하면 실행하도록 구성했다. 수동 실행도 지원한다.
- 파일 경로 필터를 두지 않아 문서만 변경한 PR도 같은 검사를 받는다.
- 같은 PR·브랜치에 새 실행이 시작되면 이전 실행을 취소한다.
- Ubuntu 24.04에서 두 작업을 병렬로 실행한다. 검사 이름은 `웹 검사`, `서버 검사`다.

| 작업 | 준비 | 검사 순서 | 제한 시간 |
|---|---|---|---|
| 웹 검사 | `.nvmrc`의 Node.js 24, npm 캐시, `npm ci --no-audit --no-fund` | 개발 도구 테스트 → 웹 단위 테스트 → 생성 API 타입 검사 → 린트·타입 검사 → 빌드 | 15분 |
| 서버 검사 | Temurin Java 25, Gradle 캐시·Wrapper 검증, Docker 확인 | 서버 단위·PostgreSQL 통합 테스트와 빌드 | 20분 |

웹은 저장소 루트의 기존 명령을 사용한다.

```sh
npm run check:tools
npm run test:web
npm run check:api-types
npm run check:web
npm run build:web
```

서버는 저장소의 Gradle Wrapper로 실행한다.

```sh
docker info --format '{{.ServerVersion}}'
./backend/gradlew -p backend build --no-daemon
```

통합 테스트는 Testcontainers가 PostgreSQL 18.6 컨테이너를 생성한다. Compose DB나 GitHub Actions의 별도 DB 서비스를 준비하지 않는다. Docker 연결 실패를 테스트 건너뛰기로 바꾸지 않는다.

온통청년 인증키·소셜 로그인·이메일 설정은 필요하지 않다. `check:tools`는 인공 응답으로 개발 도구를 검사하며 실제 온통청년 API를 호출하지 않는다. 의존성·도구·컨테이너 다운로드에는 네트워크가 필요하다.

## 권한·캐시·보고서

- 저장소 토큰은 `contents: read`로 제한하고 checkout 후 Git 인증 정보를 남기지 않는다. 저장소 비밀값을 참조하지 않는다.
- Actions는 전체 커밋 SHA로 고정하고 주석에 해당 버전을 적었다. 버전을 바꿀 때 공식 릴리스와 SHA를 함께 확인한다.
- npm은 루트 잠금 파일을 기준으로 패키지 다운로드 캐시를 사용한다. `node_modules`를 그대로 재사용하지 않고 매번 `npm ci`를 실행한다.
- Gradle은 `basic` 캐시를 사용한다. `main` 외 실행은 캐시를 읽기만 하며, 의존성 그래프 제출과 Build Scan 발행은 끈다.
- 서버 실행이 실패해도 취소된 실행이 아니면 생성된 테스트 보고서를 `backend-test-reports`로 보관하도록 구성했다. 보관 기간은 7일이다.
- 보관 대상은 `backend/build/reports/tests/test/`와 JUnit XML뿐이다. 앱 JAR, `.env`, 원천 API 응답은 업로드하지 않는다. 보고서에 포함되는 테스트 입력·로그에도 실제 개인정보나 인증키를 넣지 않는다.
- 배포, PR 댓글 작성, 브랜치 보호, 유료 서비스 설정은 추가하지 않았다.

## 실제 확인한 결과

macOS arm64에서 다음 검사를 통과했다. 웹은 실행 중인 개발 서버에 영향을 주지 않도록 추적 파일만 복사한 임시 작업 폴더에서 의존성을 새로 설치했다.

| 검사 | 환경·결과 |
|---|---|
| 워크플로 문법 | actionlint 1.7.12 통과 |
| 의존성 설치 | Node.js 24.20.0·npm 11.19.0에서 `npm ci --no-audit --no-fund` 통과 |
| 개발 도구 테스트 | 7개 통과, 실패·건너뛰기 없음 |
| 웹 테스트 | 5개 통과, 실패·건너뛰기 없음 |
| 웹 린트·타입 검사·빌드 | 모두 통과. `/`는 정적, `/conditions`는 동적 렌더링 |
| 서버 테스트·빌드 | Temurin 25.0.3 도구체인·Docker 29.7.2에서 통과. 단위 14개와 PostgreSQL 통합 2개, 실패·건너뛰기 없음 |

Node.js와 actionlint는 공식 배포 파일의 SHA-256을 확인한 뒤 사용했다. 시스템 Node.js·JDK 설정이나 기존 웹 개발 서버는 변경하지 않았다.

설치 시 기존 ESLint 9 지원 종료 경고와 npm의 일부 설치 스크립트 승인 관련 경고가 나왔다. 스크립트를 일괄 승인하거나 의존성을 변경하지 않았으며 검사·빌드는 통과했다. 서버 테스트의 Java agent 관련 경고도 이번 작업에서 숨기지 않았다.

이 결과는 로컬 명령 검증이다. GitHub의 Ubuntu 실행 환경, Actions 자체의 실행, 캐시 저장·복원과 보고서 업로드까지 확인한 것은 아니다. 실제 정책 API 계약·판정 정확도·소셜 인증·알림 발송도 검증하지 않는다.

2026-08-31에는 개발 API 연결과 함께 생성 타입 최신 여부 검사를 추가했다. 서버 테스트는 실제 생성 OpenAPI와 저장본을 비교한다. 로컬에서 서버 172건·빌드, 웹 39건·생성 타입 검사·린트·타입 검사·빌드를 통과했다. 변경 워크플로는 Ruby YAML 파서로 구문을 확인했으며 actionlint·Node.js 24 새 설치·개발 도구 테스트는 이번에 다시 실행하지 않았다. 자세한 범위는 [개발 API 검증](reminder-preview-api.md)을 따른다.

자격 API 연결 후에는 같은 CI 명령으로 로컬 서버 175건·웹 45건, 생성 계약·린트·타입 검사·빌드를 통과했다. CI 설정·외부 연동·원격 실행은 변경하거나 검증하지 않았다. [자격 API 검증](eligibility-preview-api.md)을 참고한다.

## 첫 원격 실행에서 확인할 사항

1. 워크플로를 푸시한 뒤 `웹 검사`, `서버 검사`가 모두 실행·통과하는지 확인한다.
2. 서버 보고서에 단위 테스트와 실제 PostgreSQL 통합 테스트가 포함되는지 확인한다.
3. 캐시 저장·복원과 테스트 보고서 업로드가 정상인지 확인한다.

브랜치 보호의 필수 검사 지정은 아직 하지 않았다. 적용 여부를 정한 뒤 위 검사 이름을 사용한다. 운영 배포 준비와 CI 검증은 별도 작업이다.

## 확인한 공식 자료

- [checkout v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1)
- [setup-node v7.0.0](https://github.com/actions/setup-node/releases/tag/v7.0.0)
- [setup-java v6.0.0](https://github.com/actions/setup-java/releases/tag/v6.0.0)
- [Gradle setup-gradle v6.3.0 설정](https://github.com/gradle/actions/blob/v6.3.0/docs/setup-gradle.md)
- [upload-artifact v7.0.1 입력](https://github.com/actions/upload-artifact/blob/v7.0.1/action.yml)
- [actionlint v1.7.12](https://github.com/rhysd/actionlint/releases/tag/v1.7.12)
