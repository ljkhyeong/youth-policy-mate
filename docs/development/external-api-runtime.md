# 외부 API 연동과 홈서버 실행 준비

2026-09-12 기준. 앱 코드·환경변수 예시·실행 파일을 준비했다. 이미지 빌드, 공급자 계정·도메인 등록, 공유기·TLS·k3s·백업 운영 설정은 운영자가 진행한다.

## 외부 API 검토 결과

| 기능 | 현재 연결과 판단 |
|---|---|
| 정책 수집 | 온통청년 API를 이미 사용한다. 공고 개정·누락·호출 한도 처리는 서비스에 필요하므로 유지한다. |
| 소셜 로그인 | Spring Security로 카카오·네이버 OAuth를 연동했다. 직접 비밀번호 인증을 추가할 필요가 없다. |
| 공고 조건 추출 | OpenAI API가 이미 연결돼 있다. 근거 확인·예산 제한·최신 개정 확인은 우리 서비스의 책임이다. |
| 이메일 | **Resend 발송 API와 서명 웹훅을 추가했다.** 수신 서버 전달·반송·신고·차단 결과를 공급자에게 받는다. SMTP도 선택할 수 있다. |
| 마감 일정 | 공고의 날짜를 사용한다. 공휴일 API가 신청 마감일을 대신하지 못하므로 추가하지 않는다. |
| 외부 캘린더 | 현재 서비스 내 일정을 유지한다. 캘린더 구독·Google 계정 연결은 PRD 제외 범위이며, 별도 사용자 수요가 확인되면 검토한다. |
| 자격 판정·동의·알림 생성 | 일반 외부 API로 대체할 수 없는 제품 규칙이다. 전송은 공급자에 맡기고 판단·회원 소유권·취소 처리는 앱에 둔다. |

## Resend 연결

`EMAIL_PROVIDER=resend`를 선택한다. `EMAIL_ENABLED=true`일 때 `EMAIL_ENCRYPTION_KEY`, `EMAIL_FROM`, `RESEND_API_KEY`, `RESEND_WEBHOOK_SECRET`가 필요하다. 암호화 키는 32바이트 난수의 Base64 문자열이며 재시작 때 유지한다. `.env.production.example`에는 실제 키를 넣지 않았다.

관리자에서 [Resend 발송 상태 조회](email-provider-status.md)를 사용하려면 선택 값 `RESEND_READ_API_KEY`를 설정한다. Resend `full_access` 권한이 필요하며, 발송 전용 `RESEND_API_KEY`와 별도로 사용한다. 미설정 시 조회만 사용할 수 없고 발송 설정은 바뀌지 않는다.

- 발송: `POST https://api.resend.com/emails`. Outbox ID를 `Idempotency-Key: email/{id}`와 `outbox_id` 태그에 넣는다. Resend의 멱등 키 보관 기간은 24시간이므로 무기한 중복 방지를 보장하지 않는다. [공식 발송 API](https://resend.com/docs/api-reference/emails/send-email)·[멱등 키](https://resend.com/docs/dashboard/emails/idempotency-keys)
- API 요청에는 공급자가 요구하는 `User-Agent: youth-policy-mate/1.0`을 명시한다. [API 공통 요구사항](https://resend.com/docs/api-reference/introduction)
- 처리: 10초 간격으로 인증 메일을 우선하며 Resend는 한 번에 최대 5건, SMTP는 최대 50건을 배정한다. 연결 제한은 5초, Resend 응답 제한은 10초다. 운영 초기에는 API 한 인스턴스를 기준으로 한다. 공급자 계정 전체의 실제 호출·일/월 한도는 운영자가 확인한다. [공급자 한도](https://resend.com/docs/api-reference/rate-limit)
- 발송 전 회원·주소 설정 버전·동의·저장 정책·최신 개정을 재확인한다. 외부 호출 중에는 DB 트랜잭션을 열지 않는다.
- 명확한 요청 거절은 `FAILED`, 응답 단절·타임아웃·불확실한 오류는 `UNKNOWN`이다. 자동 재발송하지 않으며, Resend 웹훅으로 결과를 보완한다. 서비스 내 알림은 유지한다.
- 발송 키와 웹훅 키는 다르다. `EMAIL_ENABLED=false`여도 Resend와 웹훅 키가 설정돼 있으면 이미 발송한 이메일의 결과를 받는다.
- 정책 메일에는 [로그인 없는 수신 해제](email-unsubscribe.md)를 제공한다. 기존 공개 주소·Ingress를 사용하며, 운영자는 DKIM 서명과 실제 수신 서비스의 버튼·본문 링크를 확인한다.

운영자가 등록할 웹훅 주소는 **`https://<공개 도메인>/api/v1/webhooks/resend`**다. 이벤트는 `email.sent`, `email.delivered`, `email.delivery_delayed`, `email.failed`, `email.bounced`, `email.complained`, `email.suppressed`를 선택한다. 열람·클릭 추적은 사용하지 않는다. [공식 이벤트](https://resend.com/docs/webhooks/event-types)

| 웹훅 처리 | 동작 |
|---|---|
| 인증 | 원문 본문과 `svix-id`, `svix-timestamp`, `svix-signature`를 Svix Java 라이브러리로 검증한다. 서명 오류·5분을 벗어난 요청은 401이다. |
| 입력 | 최대 64KiB, 잘못된 이벤트 값은 400, 초과 본문은 413이다. 키가 없으면 404다. |
| 저장 | 우리 `outbox_id` 태그가 있는 발송의 결과만 적용한다. 이메일 ID·상태·발생 시각만 추가 저장하며 주소·제목·본문·원문 웹훅은 저장하지 않는다. |
| 순서·중복 | 동일 시각의 재전송은 상태를 바꾸지 않는다. API 응답보다 먼저 온 웹훅을 보존하며, 뒤늦은 접수·지연 이벤트가 전달 완료를 되돌리지 않는다. |
| 반송·신고·차단 | 해당 주소 설정 버전의 동의·인증·코드를 해제하고 미발송 요청을 취소한다. 다시 받으려면 주소를 재인증한다. 이전 주소 이벤트는 새 설정을 변경하지 않는다. |
| 응답 | 반영·중복·대상 없는 이벤트는 공급자 계약에 맞춰 200이다. DB 오류는 성공으로 응답하지 않아 공급자가 재시도할 수 있다. |

웹훅의 `DELIVERED`는 **수신 메일 서버 접수**를 뜻하며 사용자의 열람이나 받은편지함 도착을 보장하지 않는다. 공개 회원 API 계약은 생성 OpenAPI를 사용하고, 외부 웹훅 본문은 [Resend 계약](https://resend.com/docs/webhooks/emails/delivered)을 따른다. [서명 검증](https://resend.com/docs/webhooks/verify-webhooks-requests)

## 실행 환경변수

[운영 예시](../../.env.production.example)를 참고해 k3s 환경변수로 주입한다. 앱은 예시 파일을 자동으로 읽지 않는다. 웹 컨테이너에는 DB·OAuth·이메일·AI 비밀값을 전달하지 않는다.

| 대상 | 필요한 값 |
|---|---|
| 웹·API 공통 | `PUBLIC_APP_URL=https://<공개 도메인>` — 마지막 `/` 없이 같은 주소 사용 |
| 웹 | `POLICY_API_BASE_URL=http://<API Service>:8080`, `HOSTNAME=0.0.0.0`, `PORT=3000` |
| API | `SPRING_PROFILES_ACTIVE=prod`, `DB_HOST`, `DB_PORT=5432`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` |
| 로그인 | 사용할 공급자의 `KAKAO_CLIENT_ID/SECRET`, `NAVER_CLIENT_ID/SECRET` |
| 관리자 | `ADMIN_MEMBER_IDS`에 실제 가입한 회원 UUID 등록 |
| 이메일 | 위 Resend 값. 기존 SMTP는 `.env.example` 참고 |
| 수집·AI·알림 | 예시의 관련 키·한도·주기를 설정한 뒤 각각 활성화 |

초기 예시에서 정기 수집·AI·이메일·알림은 모두 꺼져 있다. 빈 키나 `example.com` 주소로 공급자 연동이 되는 것은 아니다. 이메일을 켜도 서비스 내 정기 알림은 `REMINDERS_ENABLED`를 별도로 켜야 한다. 기존 DB의 이메일 암호화 키를 바꾸면 저장한 주소를 읽을 수 없다.

웹의 `PUBLIC_APP_URL`은 [공개 페이지 검색·공유 정보](public-page-metadata.md)에도 사용한다. 운영 모드에서 HTTPS 루트 주소를 지정하면 공개 화면의 검색을 허용하고 robots·기본 사이트맵의 주소를 만든다. 빌드 때 도메인을 넣지 않고 실행 시 전달한다. 개발 모드나 주소 미설정 상태에서는 검색을 제외한다.

## 프록시·상태 확인 연결

운영자가 구성할 Ingress는 같은 HTTPS 호스트에서 다음 경로를 보낸다. 경로를 잘라내거나 바꾸지 않는다.

| 외부 경로 | 내부 대상 |
|---|---|
| `/api/v1`, `/oauth2`, `/login/oauth2` | API Service:8080 |
| `/` 및 나머지 웹 경로 | 웹 Service:3000 |

카카오·네이버 Redirect URI는 각각 `https://<공개 도메인>/login/oauth2/code/kakao`, `/login/oauth2/code/naver`다. 웹훅 경로에는 로그인·브라우저용 CSRF 검사를 두지 않고 서명을 검증한다. 다른 회원 변경 API는 기존 로그인·CSRF 검사를 유지한다.

- API는 `0.0.0.0:8080`에서 실행하며 전달된 HTTPS 헤더를 처리하고 `Secure; HttpOnly; SameSite=Lax` 세션 쿠키를 사용한다. 프록시가 전달 헤더를 설정·덮어쓰고 API 포트는 내부에서만 접근하도록 운영자가 구성한다.
- API 시작·생존 확인: `/actuator/health/liveness`. 준비 확인: `/actuator/health/readiness`이며 DB 연결을 포함한다. DB 장애를 생존 검사에 넣어 재시작을 반복하지 않는다. `/actuator`는 외부 Ingress에 연결하지 않는다.
- 웹 시작·생존 확인: `/healthz`. API·DB 상태는 API의 준비 검사에서 판단한다.
- API 종료 대기 시간은 30초다. Pod의 종료 유예 시간은 이보다 길게 설정한다. DB는 PostgreSQL 18 계열을 기준으로 검증했으며 Flyway가 V32까지 적용한다.
- 앱은 DB 세션을 사용한다. 초기 API 인스턴스는 1개를 기준으로 하고, 여러 인스턴스의 수집·외부 공급자 호출량은 별도 검증 후 늘린다.

Spring 기본 [상태 확인 기능](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)을 사용한다. 라우터·인증서·Ingress·PVC·자원 제한·백업 주기는 이 저장소에서 운영 적용하지 않았다.

## 사용자가 실행할 이미지 빌드

저장소 루트가 빌드 컨텍스트다. Dockerfile은 비밀값 없이 빌드하며 `.dockerignore`가 `.env`, 로컬 백업·로그·Git 등을 제외한다. 실행 환경변수는 이미지에 넣지 않고 컨테이너 시작 시 전달한다.

```sh
docker build -f backend/Dockerfile -t youth-policy-api:local .
docker build -f frontend/Dockerfile -t youth-policy-web:local .
```

API는 JDK 25로 `bootJar`를 만들고 JRE 25의 일반 UID로 실행한다. 웹은 Node 24로 Next 단독 실행 파일을 만들고 일반 사용자로 실행한다. 웹 산출물에는 `.next/static`도 포함한다. [Next 단독 실행](https://nextjs.org/docs/app/api-reference/config/next-config-js/output)

위 Docker 이미지 빌드·k3s 배포는 실행하지 않았다. 홈서버 CPU 구조에 맞는 이미지 빌드·레지스트리 또는 이미지 반입은 운영자가 수행한다.

## 검증

- `npm run verify -- test:email`: Resend 요청·웹훅 서명·중복·순서 역전·이전 주소 격리·기존 회원 발송 경계 통과.
- `npm run generate:api`로 계약 생성 후 `check:api-types`, `check:web`, 개인 API 중계 `test:web` 통과.
- `npm run verify -- check:backend`: 전체 서버 테스트·V30·HTTPS 로그인/쿠키·DB 준비 검사·실행 파일 빌드 통과.
- `npm run verify -- build:web`: 웹 단독 실행 빌드 통과. Node 24.21.0에서 상태·세션·정책 조회 200, 다른 출처 변경 요청 403을 확인했다. 내부 API 비회원 요청은 401이었다.
- 별도 브라우저 응답으로 반송·신고·전달·지연 안내와 390px 화면의 가로 넘침 없음을 확인했다. 실제 회원·메일 공급자를 수정하거나 호출하지 않았다.
- 외부 Resend 발송·공급자 웹훅 재전송, 실제 OAuth 등록, 공개 HTTPS/Ingress, 홈서버 이미지 실행은 운영 설정 이후 확인해야 한다. 코드·모의 연동 검증을 실제 공급자 검증으로 간주하지 않는다.

로컬 로그: 이메일 `.local/verification/1789187521181-d7fd7098.log`, 전체 서버 `.local/verification/1789187847384-dafe088b.log`, 웹 검사 `1789186100144-43cf39eb.log`, 중계 검사 `1789186100144-35232bb2.log`, 계약 `1789186100148-99c3e1f5.log`, 웹 빌드 `1789186141173-80c8f43c.log`. 브라우저 결과와 화면은 `/tmp/youth-resend-ui/`에 있다. 웹 빌드 이후 Dockerfile에서 실제로 존재하지 않는 `public` 디렉터리 복사를 제거했으며 앱 코드·빌드 산출물은 그대로다.
