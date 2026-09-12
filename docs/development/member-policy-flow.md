# 조건 확인·회원 저장·서비스 내 알림

2026-09-05 구현 범위다. 제품 동작은 [PRD](../PRD/0001_product-baseline/spec.md), 모듈 책임은 [ADR-0001](../ADR/0001_기술스택과_책임_분리.md)을 따른다.

## 현재 사용할 수 있는 흐름

- `/conditions`: 생년월일·서울 자치구·취업상태를 확인한 뒤 정책 확인 버튼을 누르면 서버에 전송한다. 자동 저장하지 않는다. 현재 수집한 정책의 원문 요건과 검토할 항목을 보여준다.
- `/conditions` 전체 정책 결과는 `NEEDS_REVIEW`다. 별도로 실제 국가근로장학금 상세에 [추가 질문과 공통요건 비교](work-study-questions.md)를 연결했다. 대학별 미검토 요건이 남으므로 최종 신청 가능 판정과 개인별 추천 순위는 제공하지 않는다.
- `/login`: 로그인 방법 조회 실패 시 다시 불러오기를 제공한다. 설정된 카카오·네이버만 표시하며 앱 설정이 없으면 공개 탐색을 안내한다. 로그인 후 현재 링크의 정책번호 또는 관리자 경로로 복귀하고, 이미 로그인한 경우에도 같은 이동 버튼을 제공한다. 일반 로그인은 이전 이동 기록을 지우며 제공자 로그인 실패 후 재시도는 직전 복귀 주소를 유지한다. 로그인만으로 정책을 저장하지 않는다.
- 카카오가 제공한 완전한 양력 생년월일은 빈 입력칸에 넣고 사용자가 확인한다. 기존 입력을 덮지 않는다. 음력·불완전 정보·네이버 생일은 직접 입력한다. 소셜 프로필은 본인확인 완료 정보가 아니다.
- 로그인한 사용자는 확인한 기본 조건을 직접 저장·불러오기·삭제할 수 있다. 관심 정책 저장과 해제는 정책 상세 또는 `/my`에서 한다.
- 조건 화면의 [회원 조회 오류 복구](condition-member-recovery.md)는 조회 실패와 비회원을 구분한다. 직접 입력을 유지하면서 재조회하고, 중복 요청·이전 화면의 응답 반영을 막는다.
- 내 정책의 [요청 처리와 오류 복구](member-dashboard-recovery.md)는 목록 조회·저장 해제·로그아웃·탈퇴의 중복을 막는다. 결과가 불명확하면 개인 상태를 숨겨 재조회하며, 늦은 응답으로 이동한 화면을 바꾸지 않는다.
- 정책 상세의 [저장 상태 오류 복구](policy-save-recovery.md)는 조회 실패와 미저장을 구분한다. 응답 유실 뒤에는 상태부터 다시 조회하고, 중복 요청과 이전 화면의 늦은 응답을 막는다.
- `/my`: 관심 정책, 날짜순 마감 일정, [알림 페이지·안 읽은 알림 필터·읽음 처리](member-notifications.md)를 제공한다. 알림 조회 오류는 관심 정책과 마감 일정에 영향을 주지 않는다. 저장한 뒤 정책 내용이 바뀌면 최신 내용과 변경 표시를 보여주고 [저장 당시·현재 내용 비교](saved-policy-changes.md)를 제공한다.
- 로그아웃은 서버 세션을 삭제하고 전체 화면을 새로 연다. 탭 간 통신을 지원하면 같은 출처의 다른 탭에도 계정 변경을 알려 화면 상태를 초기화한다. 뒤로 가기의 보관 화면 복원은 통신 지원 여부와 무관하게 생년월일 메모리를 지우고 다시 조회한다.

## 인증과 로컬 설정

루트 `.env`에 다음 값을 설정하고 `npm run dev:backend`로 서버를 다시 시작한다. 실제 값은 채팅·커밋에 남기지 않는다.

| 설정 | 의미 |
|---|---|
| `KAKAO_CLIENT_ID` | 카카오 앱 REST API 키 |
| `KAKAO_CLIENT_SECRET` | 카카오 로그인에서 활성화한 Client Secret |
| `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | 네이버 로그인 앱의 Client ID와 Secret |
| `APP_FRONTEND_URL` | 브라우저 웹 주소, 로컬 기본 `http://127.0.0.1:3000` |
| `APP_BACKEND_URL` | 브라우저가 접근할 로그인 서버 주소, 로컬 기본 `http://127.0.0.1:8080` |
| `APP_COOKIE_SECURE` | HTTPS 쿠키 여부. 운영 기본 `true`, `local` 프로필만 `false` |

각 제공자의 ID와 Secret이 모두 있어야 해당 로그인 연결을 켠다. 리다이렉트 주소를 앱에 정확히 등록한다.

- 카카오: `http://127.0.0.1:8080/login/oauth2/code/kakao`
- 네이버: `http://127.0.0.1:8080/login/oauth2/code/naver`

웹과 서버를 `localhost`와 `127.0.0.1`로 섞지 않는다. 쿠키는 포트가 아닌 호스트를 기준으로 공유된다. 운영에서는 같은 HTTPS 호스트 아래에서 웹과 로그인 경로를 제공하는 역방향 프록시 구성이 필요하다. `/oauth2/authorization/*`, `/login/oauth2/code/*`는 Spring으로 전달한다. 별도 호스트 배포는 현재 쿠키 중계 방식으로 지원하지 않는다.

Next.js에도 `APP_FRONTEND_URL`을 같은 웹 주소로 설정한다. 변경 요청의 Origin을 이 주소와 비교한다. 로컬 기본은 `http://127.0.0.1:3000`이다.

Next.js의 서버 내부 접속 주소는 `POLICY_API_BASE_URL`이다. 로컬 기본은 `http://127.0.0.1:8080`이며 변경할 때는 프런트엔드 프로세스 환경변수 또는 `frontend/.env.local`에 설정한다. 루트 `.env`를 Next.js가 자동으로 읽는다고 가정하지 않는다.

외부 사용자 정보 조회 후 제공자와 안정적인 식별자로 회원을 연결한다. 이메일이 같아도 계정을 합치지 않는다. 연락처·원본 프로필·외부 액세스 토큰은 회원 세션 속성으로 복사하지 않으며 로그인 성공 후 저장된 외부 인증 토큰을 제거한다. Spring Session JDBC가 서버 세션을 보관하며 만료 시간은 12시간이다. Flyway V13은 회원·저장·예약·알림, V14는 Spring Session 테이블을 만든다.

2026-09-08 확인 시 로컬의 카카오·네이버 ID/Secret과 `ADMIN_MEMBER_IDS`는 미설정이고, 실제 서버의 로그인 제공자 목록은 비어 있다. 실제 동의 화면과 등록한 리다이렉트의 검증은 앱 설정 후 진행해야 한다. 로그인으로 생성된 본인의 회원 UUID를 확인한 뒤 [관리자 접근 설정](admin-collection-exceptions.md#접근-설정)을 적용한다.

## 마감일과 서비스 내 알림

정책 모듈이 단일 신청 날짜 구간의 종료 날짜를 제공한다. 날짜를 임의의 자정이나 23:59로 바꾸지 않는다. 상시는 마감일을 두지 않고, 소진·회차별 접수·본문의 다른 날짜·파싱 실패는 추가 확인으로 남긴다. [내 정책 마감 일정](member-calendar.md)은 공개 정책과 같은 접수 상태를 표시하고 가까운 마감순 정렬·접수 상태 필터를 제공한다. 원문 전체를 의미적으로 검토하는 기능은 아니므로 실제 공급 자료에서 누락·상충이 없는지 추가 확인해야 한다.

저장과 D-7·D-3·D-1 예약은 같은 트랜잭션이다. 저장 해제는 미전달 예약을 취소하고, 다시 저장하면 별도 저장 식별자를 사용한다. 최신 개정으로 일정과 예약을 다시 만들며, 같은 저장의 동일 날짜·동일 D 알림을 이미 전달했다면 본문 변경만으로 재전달하지 않는다. 지난 날짜의 예약은 `SKIPPED`, 서비스 내 전달은 `DELIVERED`, 해제·이전 개정은 `CANCELED`로 남긴다. 알림과 예약 상태 변경을 한 번에 커밋하므로 중간 실패로 절반만 반영되지 않는다.

| 설정 | 로컬 기본 | 설명 |
|---|---|---|
| `REMINDERS_ENABLED` | `true` | 로컬에서만 기본 실행. 다른 프로필 기본은 `false` |
| `REMINDER_DELIVERY_TIME` | `09:00` | 서울 날짜의 당일 알림 처리 시작 시간 |
| `REMINDER_POLL_MS` | `60000` | 처리 대상 재확인 간격과 최초 대기 시간 |

오전 9시는 로컬 확인용 기본값이다. 운영 시각·재확인 간격을 정한 뒤 명시적으로 활성화한다. 서버가 시작 시간 이후에 켜지면 당일 알림만 처리하고 지난 날짜를 몰아서 보내지 않는다. 반복 실행에서 최대 100명의 대상을 조회하고 회원별로 짧게 처리한다. 저장 목록 조회 시에도 최신 개정 반영이 일어나므로 이 조회는 DB 변경을 포함한다.

서비스 내 알림과 [이메일 주소 확인·수신 동의·Outbox·SMTP 어댑터](member-email-reminders.md)를 구현했다. 실제 이메일 공급자·발신 도메인·수신함 전달은 미검증이며 브라우저 푸시는 제공하지 않는다.

## API와 검증

공개 `POST /api/v1/policies/checks`와 `/api/v1/policies/{number}/evaluation`은 저장 없는 조건 확인이므로 CSRF 예외다. 나머지 회원 변경에는 `/api/v1/session`에서 받은 `csrfToken`을 `X-CSRF-TOKEN`에 넣는다. `/api/v1/me/**`는 회원 역할과 서버 세션의 소유자 식별자를 사용한다. 로그아웃은 `POST /api/v1/logout`이다. Next.js 중계는 허용한 경로만 고정된 서버로 보내고 세션 쿠키·CSRF 외의 인증 헤더를 전달하지 않는다.

응답 계약은 `api/openapi.policy.json`, 웹 타입은 `frontend/src/generated/policy-api.d.ts`다. 저장된 조건이 없는 경우 `conditions: null`, 마감이 불명확하면 `deadline.date: null`을 그대로 보낸다. 개인 응답과 조건 확인 응답은 `no-store`로 처리한다. 생년월일을 URL이나 정책 확인 응답에 넣지 않는다.

```bash
./backend/gradlew -p backend test --tests 'kr.youthpolicymate.member.*' --tests 'kr.youthpolicymate.policy.catalog.PolicyDeadlineTest' --no-daemon
npm run generate:api
npm run check:web
npm run test:web
npm run check:api-types
npm run build --workspace frontend -- --webpack
```

대표 검증은 실제 PostgreSQL에서 계정 소유권, CSRF, 동시 저장, 해제·재저장, 개정 변경, 동일 날짜 중복 전달 방지, 서울 자정, 전체 롤백을 확인한다. 소셜 입력 후보와 연락처 제외, 날짜 보류, Next.js 중계의 출처·쿠키·본문 크기를 별도 확인한다. 서버 전체 385건·웹 59건과 린트·타입·계약 검사·Webpack 빌드가 통과했다. 실제 브라우저에서 20건 원문 조회·상세 이동·비회원 로그인 안내를 확인했다. 회원 일정·알림 읽음·다른 탭 로그아웃 초기화는 테스트 응답으로 확인했고, 이후 응답 교체를 제거했다. 실제 소셜 제공자 연동과 이메일 발송의 성공을 이 테스트로 대신하지 않는다.

## 로그인 화면 검증

2026-09-12, 코드 `a6586c5`. 로그인 화면과 복귀 주소 처리만 변경했으며 서버·API 계약·외부 제공자 설정은 유지했다. 저장 버튼은 정책번호를 로그인 주소에 전달하고, 제공자를 선택할 때 복귀 기록을 저장한다. 숫자 정책번호와 고정 관리자 경로만 허용한다.

| 명령·범위 | 결과·로그 |
|---|---|
| `npm run verify -- test:web -- src/features/member/login-destination.test.ts` | 통과. `.local/verification/1789206820599-77852135.log` |
| `npm run verify -- check:web` | 통과. `.local/verification/1789207280324-63c6f9ce.log` |
| `npm run verify -- build:web` | 통과. `.local/verification/1789207284591-ed134025.log` |

Playwright CLI의 별도 헤드리스 세션에서 조회 실패·재시도·미설정, 정책 링크의 새 탭 복귀, 로그인 상태의 복귀 버튼, 관리자 우선순위, 잘못된/중복 정책번호, 이전 기록 정리, 제공자 실패 후 정책·관리자 복귀, 키보드 초점·모바일·늦은 응답을 확인했다. 모의 제공자와 세션만 사용했으며 회원 변경 요청은 없었다. 실제 카카오·네이버 인증은 미검증이다.

```sh
bash /Users/lim/.codex/skills/playwright/scripts/playwright_cli.sh --session youth-login-notification-recovery run-code --filename /tmp/youth-login-notification-recovery/login-flow.js
```

결과는 `/tmp/youth-login-notification-recovery/login-result.log`에 있다. 로그인 검증 뒤 변경한 모바일 스크롤 여백은 [알림 화면](member-notifications.md#검증)에서 확인했고, 복귀 주소 로직·테스트는 같아 다시 실행하지 않았다. 위 웹 검사·빌드는 스크롤 수정까지 포함한다. 검증 브라우저를 종료했으며 사용자 창·탭은 조작하지 않았다.

## 계정 전환과 보관 화면 복원 검증

2026-09-12, 코드 `72c8013`. `AccountTransitions`의 복원 이벤트 등록을 `BroadcastChannel` 지원 여부에서 분리했다. 계정 변경 알림과 보관 화면 복원은 같은 함수로 확인한 생년월일을 지우고 화면을 다시 조회한다. 일반 페이지 표시와 관계없는 알림은 입력을 유지한다.

| 명령·범위 | 결과·로그 |
|---|---|
| `npm run verify -- check:web` | 통과. `.local/verification/1789212037363-9b621b44.log` |
| `npm run verify -- test:web -- src/features/conditions/confirmed-birth.test.ts src/features/member/login-destination.test.ts` | 통과. `.local/verification/1789212037353-db961274.log` |
| 통신 미지원 상태의 복원 이벤트 | 수정 전 이전 계정명·입력이 남는 문제를 재현했고 수정 후 재조회·초기화를 확인했다. `/tmp/youth-account-restore/before.log`·`after.log` |
| 헤드리스 브라우저의 탭 간 전환 | 로그인·로그아웃 후 다른 탭 초기화, 이전 조건 응답 무시, 중복 새로고침 방지, 통신 지원 상태의 복원 이벤트를 통과했다. `/tmp/youth-account-restore/cross-tab.log` |

브라우저 실행 코드는 `/tmp/youth-account-restore/restore-flow.js`와 `cross-tab-flow.js`다. 전용 헤드리스 세션에서 다음 명령으로 실행했다.

```sh
bash /Users/lim/.codex/skills/playwright/scripts/playwright_cli.sh --session youth-account-restore run-code --filename /tmp/youth-account-restore/restore-flow.js
bash /Users/lim/.codex/skills/playwright/scripts/playwright_cli.sh --session youth-account-restore run-code --filename /tmp/youth-account-restore/cross-tab-flow.js
```

복원 검증은 `pageshow` 이벤트의 `persisted` 값을 지정해 수행했다. 브라우저가 실제 페이지를 bfcache에 보관하고 복원하는 과정 전체를 검증한 것은 아니다. Next.js의 자체 복원에서도 주소 상태 변경 이벤트가 발생하므로 문서 재조회 요청과 입력·계정 표시로 결과를 판단했다.

회원 API는 모의 응답이며 실제 계정 변경·외부 공급자 호출은 없었다. 검증 탭과 세션을 모두 종료했고 사용자 창·탭은 조작하지 않았다. 클라이언트 이벤트 처리만 변경해 배포 빌드는 반복하지 않았다. 마지막 빌드는 `8d650c9`의 `.local/verification/1789209508903-b985fc06.log`이며 현재 코드의 새 빌드 결과는 아니다.

## OAuth 전체 흐름 검증

2026-09-08, 테스트 코드 `be4a912` 기준. `OAuthLoginFlowTest`는 임시 제공자 HTTP 서버와 실제 Spring 웹 서버·PostgreSQL을 사용한다. 운영 등록 설정의 인증 방식·프로필 식별자는 유지하고, 테스트 설정에서 제공자 주소와 임시 웹 서버의 리다이렉트 주소만 교체한다. 인증된 사용자를 직접 주입하지 않고 Spring의 OAuth 필터·코드 교환·사용자 조회·세션 저장을 실행한다.

- 카카오·네이버 로그인 성공, 제공자와 고정 식별자에 따른 회원 연결, 일반 회원의 관리자 접근 거절을 확인한다.
- 익명 세션 쿠키 교체, 이전 쿠키와 로그인 전 CSRF 토큰 차단, 로그인 완료 후 외부 토큰 제거를 확인한다. JDBC에서 읽은 회원 세션에는 필요한 속성만 남는다.
- 올바른 CSRF 토큰으로 로그아웃하면 저장된 세션을 제거하고 이전 로그인 쿠키로도 접근하지 못한다.
- `state` 불일치·동의 거절·토큰 교환 실패·식별자 없는 프로필은 실패 화면으로 돌아가며 회원 인증을 만들지 않는다. 사용한 인가 요청의 재전송도 거절한다.

`npm run verify -- test:member-login`으로 위 통합 테스트와 기존 소셜 프로필 테스트를 통과했다. 로그는 `.local/verification/1788824303225-d91f0042.log`다. 운영 코드·화면·의존성을 바꾸지 않았으므로 전체 서버 검사·웹 검사·빌드·실제 서버 재시작은 반복하지 않았다. 실제 카카오·네이버 서버, 동의 화면, 등록한 리다이렉트와 관리자 계정 연결은 미검증이다.

## 다음 작업

1. 로그인 앱의 리다이렉트·동의 항목 설정 후 실제 두 제공자 로그인을 확인한다.
2. 국가근로장학금 공통요건에 이어 다른 정책의 원문 기준일·조건·예외를 검토하고 추가 질문을 확장한다.
3. 이메일 공급자·발신 도메인을 정하고 [구현한 주소 확인·동의·Outbox](member-email-reminders.md)의 실제 수신함 전달·반송을 검증한다.
4. [범위·정기 수집](policy-range-collection.md)의 실제 호출 한도·주기와 페이지 범위를 확인한 뒤 활성화한다.
5. [회원 탈퇴·전체 세션 정리](member-withdrawal.md)는 구현했다. 공개 전 개인정보 처리 안내와 백업·외부 공급자 보관/삭제, 운영비·배포 기준을 확정한다.
