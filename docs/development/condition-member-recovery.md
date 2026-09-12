# 회원 조건 조회 오류 복구

## 동작

- 회원 조회 중에는 확인 중으로 표시한다. 실패하면 재시도 버튼을 제공하며, 비회원 안내와 구분한다. 직접 조건 입력은 계속 가능하다.
- 저장한 조건의 조회 실패와 빈 결과를 구분하고, 두 경우 모두 현재 입력을 유지한다.
- 조건 불러오기·저장·삭제 중에는 처리 상태를 표시하고 다른 조건 요청을 막는다. 로그인 만료 시 회원 버튼을 숨기고 재조회·로그인 경로를 제공한다.
- 화면을 떠나면 진행 중인 요청을 취소하고 이전 응답을 무시한다. 이미 서버에서 처리한 저장·삭제를 되돌리는 기능은 아니다.
- 재조회 후 버튼 또는 로그인 링크로 키보드 초점을 옮긴다. 소셜 생년월일은 직접 입력한 값을 덮어쓰지 않으며, 안내에서도 입력 완료로 단정하지 않는다.

구현은 `frontend/src/features/member/condition-member-controls.tsx`에 있다. 서버 API·계약·의존성은 변경하지 않았다.

## 검증

2026-09-12, 코드 `65ddd32`. 저장소 루트에서 실행했다.

| 명령·범위 | 결과·로그 |
|---|---|
| `npm run verify -- check:web` | 통과. `.local/verification/1789203869191-2b878031.log` |
| `npm run verify -- test:web -- src/features/conditions/condition-draft.test.ts src/features/conditions/confirmed-birth.test.ts src/features/member/login-destination.test.ts` | 통과. `.local/verification/1789203869191-6b0e0dea.log` |
| `npm run verify -- build:web` | 통과. `.local/verification/1789203917124-eeb5e882.log` |
| Playwright CLI 헤드리스 브라우저 | 아래 대표 흐름 통과. `/tmp/youth-condition-recovery/browser.log` |

브라우저에서는 회원 조회 지연·실패·재시도, 직접 입력 보존, 조건 조회 실패·빈 결과·정상 불러오기, 저장·삭제, 중복 요청, 읽기·저장 중 로그인 만료, 화면 전환 후 늦은 조회·저장 응답, 키보드 초점과 모바일 가로 넘침을 확인했다. 웹 검사에 포함된 단위 테스트는 기존 조건 입력과 로그인 복귀 동작을 확인하며, 이번 비동기 화면 변경은 브라우저로 검증했다.

브라우저 실행 코드는 `/tmp/youth-condition-recovery/browser-flow.js`, 화면 파일은 같은 디렉터리의 `mobile-error.png`·`desktop-page.png`에 있다. 별도 브라우저의 회원 API를 모의 응답으로 대체했고 조건 요청 10건은 모두 모의 처리했다. 실제 회원 변경·외부 공급자 호출은 실행하지 않았으며 검증 브라우저는 종료했다. 실행 명령은 아래와 같다.

```sh
bash /Users/lim/.codex/skills/playwright/scripts/playwright_cli.sh --session youth-conditions-background run-code --filename /tmp/youth-condition-recovery/browser-flow.js
```

브라우저 검증은 창을 띄우지 않는 헤드리스 방식으로 진행한다. 검증 전용 세션에만 모의 응답을 적용하고 사용자 창·탭을 조작하지 않는다.

기준 `main`의 `24c924c`는 원격 푸시 후 [웹·서버 CI](https://github.com/ljkhyeong/youth-policy-mate/actions/runs/34684593518)를 통과했다. 위 변경은 작업 브랜치의 로컬 검증 결과이며, 실제 소셜 로그인·홈서버 배포 검증은 포함하지 않는다.
