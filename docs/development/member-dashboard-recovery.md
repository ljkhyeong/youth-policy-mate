# 내 정책의 요청 처리와 오류 복구

## 동작

- 목록 조회·저장 해제·로그아웃·탈퇴 요청을 대시보드에서 하나씩 처리한다. 처리 중 새로고침과 다른 계정 변경을 막는다.
- 저장 해제 성공 후 목록을 다시 읽는 동안에도 변경을 막는다. 변경 성공 안내와 후속 조회 오류를 구분한다.
- 변경 결과가 불명확하면 정책·회원·알림 상태를 숨기고 재조회를 제공한다. 로그인 만료는 로그인 링크를 함께 표시한다. 재조회는 변경 요청을 반복하지 않는다.
- 로그아웃·탈퇴의 응답이 유실된 뒤 재조회에서 비회원 상태를 확인하면 임시 저장 정보를 지우고 다른 탭에 계정 변경을 알린다. 탈퇴 완료 여부가 불명확할 때 완료를 단정하지 않는다.
- 화면을 떠나면 요청을 취소한다. 주소가 먼저 바뀌고 컴포넌트가 정리되기 전인 구간에서도 이전 응답으로 화면 상태나 위치를 바꾸지 않는다.
- 탈퇴 컴포넌트는 삭제 범위·동의 입력만 담당하며 요청은 대시보드가 수행한다. 오류 복구 후에는 삭제 내용을 다시 확인해야 한다.

구현은 `frontend/src/app/my/member-dashboard.tsx`와 `frontend/src/features/member/member-withdrawal.tsx`에 있다. 서버·API 계약·DB 삭제 범위는 변경하지 않았다. 브라우저 요청 취소는 서버에서 이미 처리한 변경을 되돌리지 않는다.

## 검증

2026-09-12, 코드 `31b0366`. 저장소 루트에서 실행했다.

| 명령·범위 | 결과·로그 |
|---|---|
| `npm run verify -- check:web` | 통과. `.local/verification/1789205692229-8eccfad0.log` |
| `npm run verify -- test:web -- src/features/member/member-policy-list.test.tsx src/features/member/login-destination.test.ts` | 통과. `.local/verification/1789205692229-f25df4b4.log` |
| `npm run verify -- build:web` | 통과. `.local/verification/1789205718965-0a162312.log` |
| Playwright CLI 헤드리스 브라우저 | 대표 흐름 통과. `/tmp/youth-dashboard-recovery/after-final.log` |

수정 전에는 로그아웃 요청 후 내 조건으로 이동해도 늦은 응답이 홈으로 다시 이동시켰다. `/tmp/youth-dashboard-recovery/before.log`에서 재현했다. 요청 취소만 적용한 첫 검사에서도 주소 변경과 컴포넌트 정리 사이에 같은 문제가 발생해, 응답 반영 전 현재 경로 확인을 추가하고 해당 흐름을 다시 검증했다.

최종 검사는 조회 오류·재시도·빈 목록 구분, 조회/변경 중 중복 요청, 저장 해제 후 재조회, 해제·로그아웃·탈퇴 응답 유실, 로그인 만료, 화면 이동 후 늦은 응답, 탈퇴 확인·실패·성공, 다른 탭 알림, 키보드 초점과 모바일 표시를 확인했다. 변경 요청 11건은 모두 모의 API에서 처리했고 실제 회원 변경은 없었다. 화면 단위의 비동기 동작은 브라우저로 확인했으며 서버·중계의 기존 검증은 코드가 같아 재사용했다.

브라우저 실행 파일과 화면 파일은 `/tmp/youth-dashboard-recovery/`에 있다. 아래 명령을 별도 헤드리스 세션에서 실행했고 검증 브라우저는 종료했다. 사용자 창·탭, 서버·DB·운영 설정은 변경하지 않았다.

```sh
bash /Users/lim/.codex/skills/playwright/scripts/playwright_cli.sh --session youth-dashboard-recovery run-code --filename /tmp/youth-dashboard-recovery/browser-flow.js
```
