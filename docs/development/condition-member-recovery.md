# 회원 조건 조회 오류 복구

## 동작

- 회원 조회 중에는 확인 중으로 표시한다. 실패하면 재시도 버튼을 제공하며, 비회원 안내와 구분한다. 직접 조건 입력은 계속 가능하다.
- 저장한 조건의 조회 실패와 빈 결과를 구분하고, 두 경우 모두 현재 입력을 유지한다.
- 저장 조건 조회 중 입력을 수정·삭제하면 늦은 응답을 적용하지 않고 안내한다. 수정 후 원래 값으로 되돌려도 해당 요청은 적용하지 않는다. 이후 다시 불러오기를 요청하면 정상 적용하고 이전 전체 삭제 안내를 지운다.
- 조건 불러오기·저장·삭제 중에는 처리 상태를 표시하고 다른 조건 요청을 막는다. 로그인 만료 시 회원 버튼을 숨기고 재조회·로그인 경로를 제공한다.
- 화면을 떠나면 진행 중인 요청을 취소하고 이전 응답을 무시한다. 이미 서버에서 처리한 저장·삭제를 되돌리는 기능은 아니다.
- 재조회 후 버튼 또는 로그인 링크로 키보드 초점을 옮긴다. 소셜 생년월일은 직접 입력한 값을 덮어쓰지 않으며, 안내에서도 입력 완료로 단정하지 않는다.
- 생년월일을 직접 수정·삭제하거나 입력 내용을 모두 지운 뒤에는 늦은 소셜 정보를 적용하지 않는다. 생년월일을 건드리지 않고 다른 항목만 입력한 경우에는 자동 입력을 유지한다.

회원 요청은 `frontend/src/features/member/condition-member-controls.tsx`, 입력 보존은 `frontend/src/features/conditions/condition-form.tsx`에서 처리한다. 불러오기 시작 때 입력 변경 번호를 보관하고 응답 때 대조한다. 입력할 때마다 세션을 다시 조회하거나 입력을 잠그지 않는다. 서버 API·계약·의존성은 변경하지 않았다.

## 검증

2026-09-12, 코드 `f6b63f2`. 저장소 루트에서 실행했다.

| 명령·범위 | 결과·로그 |
|---|---|
| `npm run verify -- check:web` | 통과. `.local/verification/1789210204994-efffa0dc.log` |
| `npm run verify -- test:web -- src/features/conditions/condition-draft.test.ts src/features/conditions/confirmed-birth.test.ts` | 통과. `.local/verification/1789210204995-8612797b.log` |
| Playwright CLI 헤드리스 브라우저 | 입력 충돌 검증: `/tmp/youth-condition-input-race/after.log`. 기존 회원 흐름: 같은 디렉터리의 `existing-flows.log` |

수정 전 `/tmp/youth-condition-input-race/before.log`에서 저장 조건의 직접 입력 덮어쓰기·전체 삭제 복구·삭제한 생년월일의 소셜 재입력을 재현했다. 수정 후 각 항목의 직접 입력, 수정 후 원래 값으로 되돌리기, 전체 삭제, 다시 불러오기, 내용 확인 단계 전환, 늦은 소셜 정보, 초점 유지와 입력 중 추가 세션 조회가 없음을 확인했다.

기존 회원 조회 지연·실패·재시도, 조건 조회 실패·빈 결과, 저장·삭제, 중복 요청, 로그인 만료, 화면 이동 후 늦은 응답, 키보드 초점과 모바일 가로 넘침도 통과했다. 비동기 화면 동작은 브라우저로, 입력 검사와 생년월일 메모리는 기존 단위 테스트로 확인했다.

입력 충돌 검증에서 저장 조건 조회 7건, 기존 회원 흐름에서 조건 요청 10건을 모두 모의 처리했다. 실제 회원 변경·외부 공급자 호출은 실행하지 않았으며 전용 브라우저는 종료했다. 실행 명령은 아래와 같다.

```sh
bash /Users/lim/.codex/skills/playwright/scripts/playwright_cli.sh --session youth-condition-input-race run-code --filename /tmp/youth-condition-input-race/after.js
bash /Users/lim/.codex/skills/playwright/scripts/playwright_cli.sh --session youth-condition-input-race run-code --filename /tmp/youth-condition-recovery/browser-flow.js
```

브라우저 검증은 창을 띄우지 않는 헤드리스 방식으로 진행한다. 검증 전용 세션에만 모의 응답을 적용하고 사용자 창·탭을 조작하지 않는다.

이번 변경은 클라이언트 이벤트 처리에 한정되어 배포 빌드는 반복하지 않았다. 마지막 빌드는 `8d650c9`에서 통과했으며 로그는 `.local/verification/1789209508903-b985fc06.log`다. 현재 코드 전체의 새 빌드 결과로 간주하지 않는다.

기준 `main`의 `24c924c`는 원격 푸시 후 [웹·서버 CI](https://github.com/ljkhyeong/youth-policy-mate/actions/runs/34684593518)를 통과했다. 위 변경은 작업 브랜치의 로컬 검증 결과이며, 실제 소셜 로그인·홈서버 배포 검증은 포함하지 않는다.
