# 메일에서 이메일 수신 해제

정책 알림 메일의 본문 링크나 메일 서비스의 수신 해제 기능으로 이메일 알림을 끈다. 로그인과 이메일 주소 입력이 필요하지 않으며 관심 정책·서비스 내 알림·주소 인증은 유지한다. 확인 코드 메일에는 넣지 않는다.

## 처리 경로

| 요청 | 동작 |
|---|---|
| 본문 `/email-unsubscribe#<token>` | 브라우저에서 토큰을 읽고 확인 버튼을 표시한다. 조회만으로 설정을 바꾸지 않는다. |
| `GET /api/v1/email-unsubscribe/<token>` | 위 확인 화면으로 303 이동한다. DB 변경은 없다. |
| `POST /api/v1/email-unsubscribe/<token>` | 폼의 `List-Unsubscribe=One-Click`을 확인해 처리하고 빈 200 응답을 반환한다. URL 인코딩 폼과 multipart 폼을 지원한다. |
| 웹 확인 버튼 | Next 중계가 같은 출처의 POST만 받아 표준 폼으로 변환한다. 쿠키·인증 헤더·CSRF·추가 쿼리와 요청 본문은 전달하지 않는다. |

형식 오류는 400, 사용할 수 없는 토큰은 404, 저장소 오류는 503이다. POST 응답은 리다이렉트하지 않는다. 수신 해제·이동 응답은 `no-store`, 확인 화면은 검색 제외·`no-referrer`다. 화면을 바꾸거나 다른 메일 링크를 열면 이전 응답을 무시한다.

응답을 확인하지 못하면 해제 결과를 알 수 없다고 안내하고 재시도 버튼으로 키보드 초점을 옮긴다. 완료나 사용할 수 없는 링크 응답은 해당 안내 제목에 초점을 둔다. 키보드로 다음 링크까지 이동할 수 있다.

사용할 수 없는 링크의 ‘내 알림 설정으로’는 `/my?view=email`에서 이메일 설정을 펼친다. 비회원이나 로그인 만료 상태에서도 [로그인 후 같은 설정으로 복귀](member-navigation.md)할 수 있다.

## 토큰과 동의

- 발송 배정 트랜잭션에서 정책 메일별 32바이트 난수를 만들고 V32의 Outbox 열에 SHA-256 해시만 저장한다. 원래 토큰은 공급자에게 보낼 메일에만 포함한다. 토큰만으로 회원 정보 조회·로그인·주소 변경은 할 수 없다.
- 회원 행을 잠근 뒤 토큰과 주소 설정 버전을 다시 확인한다. 현재 버전이면 기존 동의 해제 경로로 미발송 정책 메일도 함께 취소한다. DB 오류가 나면 동의 변경과 취소를 함께 롤백한다.
- 반복 요청은 같은 200 응답이다. 주소 변경·삭제로 해당 설정이 사라진 링크도 새 주소를 건드리지 않는다. 수신 동의를 껐다가 다시 켜면 예전 토큰을 무효화한다. 이미 켜진 동의를 다시 저장하는 요청은 유효한 링크를 없애지 않는다.
- 탈퇴로 Outbox가 삭제되면 해당 링크는 사용할 수 없다. 이미 발송 중인 메일은 회수하지 않으며, 기존에 발송한 링크 없는 메일은 `/my`의 설정에서 해제한다.
- 해제에는 외부 공급자 요청이나 암호화 키가 필요하지 않는다. 발송을 꺼도 이미 보낸 메일의 해제 요청을 처리한다.

## 공급자 연결과 운영 확인

SMTP는 Spring의 `MimeMessageHelper`, Resend는 발송 API의 `headers`로 `List-Unsubscribe`와 `List-Unsubscribe-Post`를 넣는다. 표준이 HTTPS를 요구하므로 API 공개 주소가 HTTPS일 때만 두 헤더를 넣고, 로컬 HTTP에서는 본문 확인 링크만 제공한다. 새 환경변수나 공급자 계정은 필요하지 않다. 운영 `PUBLIC_APP_URL`과 기존 `/api/v1` Ingress 경로를 사용한다.

헤더와 POST 형식은 [RFC 8058](https://www.rfc-editor.org/rfc/rfc8058.html), Resend 연결은 [공식 수신 해제 안내](https://resend.com/docs/dashboard/emails/add-unsubscribe-to-transactional-emails)를 따른다. 메일 서비스의 버튼 노출은 실제 메일의 DKIM 서명과 수신 서비스 판단에 달려 있다. 운영자는 발신 도메인 등록 후 서명에 두 수신 해제 헤더가 포함되는지와 실제 버튼·본문 링크를 확인한다. 메일 서비스가 제공하는 연락처 목록과 우리 회원 설정을 중복 관리하지 않는다.

본문 링크의 토큰은 URL 조각(`#`)이라 페이지 요청에 포함되지 않지만, 메일 서비스의 직접 POST 주소에는 토큰이 포함된다. 프록시·접근 로그에서 이 경로의 토큰을 기록하지 않도록 운영자가 설정한다. 실제 메일·도메인·DKIM·Ingress 설정과 Docker 이미지 빌드는 실행하지 않는다.

## 검증

아래는 2026-09-12, 초기 구현 `8bc3fbf`의 검증 결과다. 서버·계약은 유지했으며 최신 화면 검증은 [키보드와 오류 복구 검증](#키보드와-오류-복구-검증)에 기록한다. 로그는 Git에 포함하지 않는 로컬 파일이다.

| 명령·범위 | 결과·로그 |
|---|---|
| `npm run verify -- check:backend` | 전체 서버 테스트·빌드 통과. `.local/verification/1789189862119-4777bf07.log` |
| `npm run verify -- check:web` | 웹 린트·타입 검사 통과. `.local/verification/1789189857883-8053e3fc.log` |
| `npm run verify -- test:web -- 'src/app/api/member/[...path]/route.test.ts'` | 중계 경로·권한·표준 폼·쿠키 제외 검사 통과. `.local/verification/1789189857893-642163f1.log` |
| `npm run generate:api` 후 `npm run verify -- check:api-types` | 생성 계약 일치. `.local/verification/1789189857883-58178074.log` |
| `npm run verify -- build:web` | 웹 운영 빌드 통과. `.local/verification/1789189962862-23c33490.log` |

서버 검사는 PostgreSQL에서 GET 무변경·두 폼 형식·반복 해제·다른 회원 세션·재동의·주소 변경·탈퇴·장애 롤백·발송 비활성 상태를 확인했다. SMTP 메시지와 Resend 모의 요청에 헤더를 확인했으며 인증 코드 메일에는 넣지 않는다. 첫 계약 생성의 테스트 컴파일 오류는 기존 발송 모의 객체의 인자를 수정한 뒤 해소했다.

별도 브라우저에서 확인 버튼·키보드·오류 재시도·중복 방지·늦은 응답 무시·잘못된 링크·완료 초점과 데스크톱/모바일 표시를 확인했다. 처리 요청 4건은 모두 모의 응답이었으며 실제 수신 해제는 하지 않았다. 결과는 `/tmp/youth-unsubscribe-ui/result.log`, 화면은 같은 디렉터리의 `desktop.png`·`mobile.png`·`complete.png`에 있다. 검증 세션은 종료했다.

로컬 JAR 복사본에 V32를 적용한 전후 회원·정책·저장·이메일 건수는 같았다. API 상태·확인 화면은 200, 비회원 조건 조회는 401, 확인 화면 이동은 303이었다. 존재하지 않는 토큰만 사용해 실제 URL 인코딩/multipart POST와 Next 중계의 404 응답을 확인했다. 결과는 `/tmp/youth-unsubscribe-runtime-check.json`, 실행 정보는 [HANDOFF](../../HANDOFF.md)에 있다. 실제 공급자 메일·DKIM·수신 서비스 버튼 표시·Docker 이미지 실행·원격 CI는 미검증이다.

## 키보드와 오류 복구 검증

2026-09-12, 코드 `0761490`. 수신 해제 실패·사용할 수 없는 링크 응답과 [정책 변경 재조회](saved-policy-changes.md#현재-화면-검증)에서 초점이 본문으로 돌아가는 문제를 수정했다. 실패 시 재시도 버튼, 완료·사용 불가 안내와 비교 성공 시 결과에 초점을 둔다. 서버·계약·의존성은 변경하지 않았다.

| 명령·범위 | 결과·로그 |
|---|---|
| `npm run verify -- check:web` | 통과. `.local/verification/1789212792922-e3fb7cf0.log` |
| `npm run verify -- test:web -- 'src/app/api/member/[...path]/route.test.ts' src/features/member/member-policy-list.test.tsx` | 중계·회원 목록 테스트 통과. `.local/verification/1789212792938-e5d1c952.log` |
| 헤드리스 브라우저 | 수정 전 네 가지 초점 유실을 재현하고 같은 흐름에서 복구를 확인했다. `/tmp/youth-member-retry-focus/before.log`·`after-focus.log` |
| 오류 복구와 기존 동작 | 키보드 재시도·응답 유실 안내·확인 전 요청 없음·인증 정보 제외·중복 방지·링크 변경 후 이전 응답 무시·다음 버튼 접근·모바일을 통과했다. `/tmp/youth-member-retry-focus/recovery.log` |

브라우저 코드는 `/tmp/youth-member-retry-focus/before.js`와 `recovery-flow.js`다. 후자는 수신 해제와 정책 변경 조회의 기존 동작도 함께 확인한다. 전용 헤드리스 세션에서 다음 명령으로 실행했다.

```sh
bash /Users/lim/.codex/skills/playwright/scripts/playwright_cli.sh --session youth-member-retry-focus run-code --filename /tmp/youth-member-retry-focus/before.js
bash /Users/lim/.codex/skills/playwright/scripts/playwright_cli.sh --session youth-member-retry-focus run-code --filename /tmp/youth-member-retry-focus/recovery-flow.js
```

회원 API는 모의 응답이며 실제 수신 해제·회원 변경·외부 공급자 호출은 없었다. 검증 세션은 종료했고 사용자 창·탭은 조작하지 않았다. 서버 렌더링·라우팅 변경이 없어 배포 빌드는 반복하지 않았다. 마지막 빌드는 `8d650c9`의 `.local/verification/1789209508903-b985fc06.log`이며 현재 코드의 새 빌드 결과는 아니다.
