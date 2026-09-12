# 이메일 설정 오류 복구

## 변경한 동작

- 최초 조회·재조회 중에는 주소·인증·수신 동의 변경을 막는다. 발송 상태 새로고침도 같은 조회 경로를 사용한다.
- 변경 응답이 유실되면 이전 설정과 변경 버튼을 숨기고 재조회를 제공한다. 조회 재시도는 인증 메일을 재발송하거나 수신 동의를 바꾸지 않는다.
- 서버가 변경을 확인한 뒤 설정 조회만 실패하면 완료 안내와 조회 오류를 구분해 표시한다.
- 인증 코드 오류 후에는 서버에서 만료·사용 가능 상태를 다시 조회한다. 조회에 실패하면 이전 설정으로 인증을 계속하지 않는다. 코드 오류·요청 제한 때 입력값은 유지한다.
- 로그인 만료 시 설정과 주소·코드 입력을 지우고 로그인 링크를 제공한다. 다른 탭으로 이동한 뒤 도착한 응답은 반영하지 않는다.
- 요청은 하나씩 처리하고 완료·복구 후 제목 또는 재시도 버튼으로 키보드 초점을 옮긴다.

구현은 `frontend/src/features/member/member-email-settings.tsx`에 있다. 서버 API·발송·Outbox·동의 규칙·의존성은 변경하지 않았다. 화면 이동 시 요청 취소는 이미 서버가 처리한 변경을 되돌리지 않는다.

## 검증

2026-09-12, 코드 `eaef91c`. 저장소 루트에서 실행했다.

| 명령·범위 | 결과·로그 |
|---|---|
| `npm run verify -- check:web` | 통과. `.local/verification/1789204721693-9a0cbed4.log` |
| `npm run verify -- test:web -- 'src/app/api/member/[...path]/route.test.ts'` | 통과. `.local/verification/1789204669326-4a71101d.log` |
| `npm run verify -- build:web` | 통과. `.local/verification/1789204758111-42542a25.log` |
| Playwright CLI 헤드리스 브라우저 | 대표 흐름 통과. `/tmp/youth-email-recovery/after-final.log` |

수정 전에는 서버에서 수신 동의가 켜진 뒤 응답을 끊으면 화면의 ‘알림 켜기’ 버튼이 다시 활성화됐다. 재현 결과는 `/tmp/youth-email-recovery/before.log`에 있다.

수정 후 최초 조회 오류·재시도, 조회 중 변경 차단, 수신 동의·인증 메일 요청·주소 삭제의 응답 유실, 코드 오류·시도 횟수 소진·후속 조회 실패, 요청 제한, 변경 성공 후 조회 실패, 로그인 만료, 탭 이동 후 늦은 응답, 키보드 초점과 모바일 표시를 확인했다. 최종 회귀 흐름의 변경 요청 12건은 모두 모의 API에서 처리했고 실제 회원 변경·메일 발송은 없었다. 기존 API 중계 테스트는 요청 경로·CSRF·계약 소비를 확인하며 이번 비동기 화면 동작은 브라우저로 검증했다.

최초 정적 검사에서 비동기 조회 함수의 상태 갱신 방식이 검사 규칙에 걸렸다. 상태 갱신을 응답 콜백으로 정리한 뒤 해당 검사와 영향을 받는 브라우저 흐름을 다시 확인했다. 중계 코드는 같아 통과한 테스트 결과를 재사용했다.

브라우저 실행 코드는 `/tmp/youth-email-recovery/browser-flow.js`, 화면 파일은 같은 디렉터리의 `mobile-expired.png`·`desktop-page.png`에 있다. 아래 명령을 헤드리스 전용 세션에서 실행했다. 검증 브라우저는 종료했다. 서버·DB·운영 설정과 사용자 브라우저는 변경하지 않았다.

```sh
bash /Users/lim/.codex/skills/playwright/scripts/playwright_cli.sh --session youth-email-recovery run-code --filename /tmp/youth-email-recovery/browser-flow.js
```
