# Resend 발송 상태 조회

2026-09-12, 코드 `1547da6` 기준. 관리자 이메일 발송 목록의 **발송 기록 → Resend 상태 조회**에서 공급자의 최신 이벤트를 확인한다.

## 동작과 범위

- Resend 공급자 발송 ID가 저장된 요청만 조회한다. ID가 없으면 접수 여부를 추정하지 않으며 조회 버튼 대신 사유를 표시한다. SMTP 기록에는 이 기능을 제공하지 않는다.
- 관리자가 버튼을 누를 때만 `GET /emails/{email_id}`를 호출한다. 목록 조회·기록 펼침은 외부 호출을 만들지 않는다. 조회 중 중복 요청을 막고 실패 후 자동으로 재시도하지 않는다.
- 공급자의 최신 이벤트와 조회 시각을 표시한다. 조회 시각은 이벤트 발생 시각이 아니다. 처음 보는 이벤트는 ‘상태 미확인’으로 표시하며 원문 값을 노출하지 않는다. 열람·클릭 이벤트도 실제 사용자 행동을 확정하지 않는다.
- 공급자 조회 결과는 별도 표시한다. 기존 DB 발송 상태·수신 동의·Outbox는 바꾸지 않고 재발송하지 않는다. 기존 웹훅의 상태 갱신과 반송·신고·차단 처리는 유지한다.
- 키 누락·권한 부족·기록 없음·조회 한도·연결 오류를 전달 실패와 구분한다. 다시 조회할 때 이전 결과를 지우고, 로그인 만료 시 관리자 로그인 링크를 제공한다. 화면을 떠난 뒤 이전 응답은 반영하지 않는다.
- 키보드 조회 후 버튼으로 초점을 복원한다. 발송 기록을 접었거나 다른 요소로 이동한 초점은 유지한다.

## 설정

선택 환경변수 `RESEND_READ_API_KEY`를 추가했다. `.env.example`과 `.env.production.example`은 빈 값이며 실제 키는 넣지 않았다. 미설정 상태에서도 앱이 실행되고 기존 발송 기능에 영향을 주지 않는다.

이 변수는 **조회에만 사용하는 키**다. Resend의 발송 전용 `sending_access` 키로는 조회할 수 없으므로 공급자에서 `full_access` 권한이 필요하다. 이 권한은 공급자 측 읽기 전용 권한이 아니다. 발송은 기존 `RESEND_API_KEY`, 조회는 `RESEND_READ_API_KEY`만 사용하며 서로 대체하지 않는다. [Resend 키 권한](https://resend.com/docs/api-reference/api-keys/create-api-key)

발송을 꺼도 조회용 키가 있으면 기존 Resend 기록을 확인할 수 있다. 공급자를 SMTP로 바꾼 뒤에도 과거 Resend 기록은 조회할 수 있다. 실제 키 발급·운영 Secret 반영·권한 확인·실제 공급자 조회는 사용자가 진행한다.

## API와 구현

`GET /api/v1/admin/email-deliveries/{id}/provider-status`

`id`는 서비스의 발송 요청 ID다. 서버가 DB에 저장된 공급자 발송 ID를 조회해 사용하므로 브라우저가 임의의 외부 발송 ID를 전달하지 않는다. 응답은 생성 계약 `AdminEmailProviderStatus`의 `event`, `checkedAt` 두 필드다. 주소·본문·제목·회원 ID와 공급자 원문 오류는 반환하지 않는다.

| 응답 | 의미 |
|---|---|
| 200 | 공급자 최신 이벤트 조회 완료. 모르는 이벤트는 `UNKNOWN` |
| 400 | 요청 ID 형식 오류 |
| 401 / 403 | 서비스 로그인 필요 / 관리자 권한 없음 |
| 404 | 조회 가능한 Resend 발송 ID가 기록되지 않음 |
| 503 | `EMAIL_PROVIDER_NOT_CONFIGURED`, `ACCESS_DENIED`, `NOT_FOUND`, `RATE_LIMITED`, `UNAVAILABLE`에 해당하는 조회 실패. 코드에는 모두 `EMAIL_PROVIDER_` 접두사가 붙음 |

공급자 키의 401·403은 서비스 로그인 오류와 구분해 503과 `EMAIL_PROVIDER_ACCESS_DENIED`로 응답한다. 공급자의 404도 발송 실패나 미접수의 증거로 취급하지 않는다. 응답은 `no-store`이며 Next는 관리자 GET 경로·회원 쿠키만 중계한다.

`EmailDeliveryStore`에서 발송 ID를 읽고 DB 트랜잭션 밖에서 `ResendEmailLookup`을 호출한다. Spring `RestClient`와 JDK HTTP 클라이언트를 사용하며 연결 제한은 2초, 응답 제한은 5초다. [Resend 조회 계약](https://resend.com/docs/api-reference/emails/retrieve-email)과 [이벤트 종류](https://resend.com/docs/dashboard/emails/manage-emails)를 기준으로 필요한 필드만 읽는다.

`ce62bd4`에서 누락된 `spring-boot-starter-restclient`를 추가했다. 기존 Resend 발송 모듈도 `RestClient.Builder`를 주입받으므로 이 구성이 필요하다. 운영 프로필 테스트는 실제 Resend 모듈을 구성하고 발송 비활성 상태로 시작하는지 확인한다. [Spring의 RestClient 구성](https://docs.spring.io/spring-boot/reference/io/rest-client.html)

## 검증

저장소 루트에서 실행했다. Java는 `/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`을 사용했다.

| 명령 | 확인 범위·로그 |
|---|---|
| `npm run verify -- check:backend` | 전체 서버 테스트와 실행 파일 빌드. 공급자 계약·관리자 권한·읽기 전용·운영 Resend 구성 포함. `.local/verification/1789220899213-b02b4d59.log` |
| `npm run generate:api` | 최종 OpenAPI·TypeScript 생성. `/tmp/youth-email-provider-contract-complete.log` |
| `npm run verify -- check:api-types` | 생성 계약 일치. `.local/verification/1789220906036-cee8e60b.log` |
| `npm run verify -- test:web -- src/features/admin/email-deliveries-pages.test.tsx 'src/app/api/member/[...path]/route.test.ts'` | 화면·중계 관련 검사. `.local/verification/1789220781668-5d4de643.log` |
| `npm run verify -- test:web -- src/features/admin/email-deliveries-pages.test.tsx` | 초점 수정 후 영향받은 화면 검사. `.local/verification/1789221134746-39301e95.log` |
| `npm run verify -- check:web` | 최종 웹 린트·타입 검사. `.local/verification/1789221054855-eb4c933f.log` |
| `npm run verify -- build:web` | 최종 배포용 웹 빌드. `.local/verification/1789221064973-24cd8a7f.log` |

헤드리스 결과는 `/tmp/youth-email-provider/browser-passed.log`, 스크립트는 같은 폴더의 `flow.js`다. 명시적 GET 조회·대상 제한·중복 차단·키보드·기존 기록 유지·키 누락/권한/한도/기록 없음/연결 실패·서비스 로그인/권한·재조회·이벤트·서울 조회 시각·390px/1280px·기록 접기·화면 이동 후 늦은 응답을 확인했다. 화면 파일은 `screen-390.png`, `screen-1280.png`다. 공급자 조회 13건은 모의 응답이며 실제 회원 변경·Resend 호출·발송은 없었다.

검증 중 실제 앱의 REST 클라이언트 의존성 누락과 조회 후 키보드 초점 유실을 수정했다. 반복 오류를 설정하던 Mockito 테스트는 `doThrow`로 고쳤다. 모바일 검사는 화면 크기 변경 직후의 스크롤 위치 대신 실제 Tab 이동으로 확인하고, 이동 대상인 내 조건 화면의 로그인 상태 조회도 모의 처리에 포함했다.

검증용 웹은 빌드 후 `.next/standalone/frontend/server.js`로 실행했고 정적 파일은 빌드 결과의 `frontend/.next/static`을 해당 서버의 `.next/static`으로 복사했다. 임시 API에만 연결했으며 기존 3000 포트 서버를 바꾸지 않았다. 전용 브라우저·3103 포트 웹·임시 API를 종료했다. Docker 이미지 빌드·운영 환경변수 변경·실제 공급자 연동은 실행하지 않았다.
