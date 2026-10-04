---
name: youth-policy-browser-check
description: 청년정책메이트를 로컬에서 실행(DB·Spring·Next.js)하고 Playwright CLI 헤드리스 브라우저로 대표 사용자 흐름·키보드 초점·모바일 배치를 확인한다. 사용자의 서버·창·탭은 건드리지 않는다.
when_to_use: 화면 상호작용·배치·입력 컨트롤·초점 동작을 바꿔 브라우저 확인이 필요할 때, 앱을 실행해 변경이 실제로 동작하는지 보여달라는 요청을 받았을 때.
argument-hint: "[확인할 화면이나 흐름]"
---

# 로컬 실행과 헤드리스 화면 확인

확인할 흐름: $ARGUMENTS

AGENTS.md에 따라 브라우저 검증은 헤드리스로 실행한다. 사용자가 직접 보겠다고 요청한 경우에만 Claude 내장 브라우저 창이나 `--headed`를 사용한다. 사용자가 실행 중인 서버·브라우저 창·탭은 조작하거나 종료하지 않는다.

## 1. 실행 중인 서버 확인

```bash
lsof -nP -iTCP:3000 -iTCP:3103 -iTCP:8080 -iTCP:8081 -iTCP:55432 -sTCP:LISTEN
```

- 3000·8080에서 실행 중인 서버는 사용자 것으로 보고 재시작·종료하지 않는다. 읽기 전용 확인에는 재사용할 수 있지만, 상태를 바꾸는 흐름은 검증용 서버에서 확인한다.
- 검증용 웹은 3103을 쓴다. 이미 사용 중이면 다른 빈 포트를 고른다.

## 2. 필요한 만큼만 실행

| 확인 대상 | 실행 |
|---|---|
| 화면 컴포넌트·상태 표시만 | 웹만 실행하고 API는 Playwright `route`로 모의 응답 |
| 개발 전용 계산 화면(`/dev/**`) | `npm run dev:preview-api`(8081, DB 불필요) + 웹 |
| 실제 정책 목록·상세·회원 흐름 | `npm run db:up` → `npm run dev:backend`(8080) + 웹 |

- 서버·웹은 Bash `run_in_background`로 실행하고, 준비 여부는 `curl --retry 60 --retry-delay 1 --retry-connrefused -sf -o /dev/null <주소>`로 기다린다. `sleep` 반복은 쓰지 않는다.
- 웹: 배포 빌드를 이미 검증했다면 `npm run start --workspace frontend -- --port 3103`, 아니면 `npm run dev --workspace frontend -- --port 3103`. 같은 폴더의 다른 `next dev` 때문에 시작하지 못하면 `build:web` 후 `start`를 쓴다.
- 웹이 다른 API 주소를 써야 하면 웹 프로세스에 `POLICY_API_BASE_URL=http://127.0.0.1:<포트>`를 지정한다. `frontend/.env.local`은 만들거나 바꾸지 않는다.
- 서버 상태는 `curl -sf http://127.0.0.1:8080/actuator/health`로 확인한다. 실제 OAuth 제공자 로그인·실제 회원 데이터·실제 이메일 발송은 사용하지 않는다. 회원 화면은 `/api/member/**` 응답을 모의한다.

## 3. Playwright CLI 헤드리스 확인

`playwright-cli`는 기본이 헤드리스이고 현재 작업 디렉터리에 `.playwright-cli/` 기록을 만든다. 저장소 밖 작업 폴더(세션 scratchpad, 없으면 `/tmp/ypm-<작업>/`)에서 실행한다.

```bash
mkdir -p "$WORK" && cd "$WORK" && npx --yes --package @playwright/cli playwright-cli -s=ypm-<작업> open http://127.0.0.1:3103/<경로>
```

같은 세션에서 이어서 실행한다.

| 목적 | 명령 |
|---|---|
| 요소 참조 얻기 | `snapshot`, `find "<문구>"` |
| 입력·클릭 | `fill <ref> "<값>"`, `click <ref>`, `press Tab`, `press Enter` |
| 데스크톱·모바일 | `resize 1280 800`, `resize 390 844` |
| 키보드 초점 | `eval "() => document.activeElement?.outerHTML.slice(0, 160)"` |
| 콘솔 오류·요청 중복 | `console error`, `requests` |
| API 모의·지연 응답 | `route "**/api/member/session" --status 200 --body '{"authenticated":false}'`, 늦은 응답은 `run-code`에서 `page.route`로 지연 |
| 반복 가능한 시나리오 | `run-code --filename=<작업 폴더>/<시나리오>.js` (`async page => { ... }`) |

확인 기준:

- 바뀐 흐름의 성공·실패·재시도와, 해당하면 늦은 응답·중복 클릭·계정 전환 뒤 화면을 확인한다.
- 배치나 입력 컨트롤이 바뀌었으면 1280px와 390px에서 줄바꿈·가로 스크롤·겹침을 보고, 키보드만으로 같은 흐름을 끝낼 수 있는지 확인한다.
- 단순 문구 변경은 바뀐 화면의 표시만 확인한다.

## 4. 정리와 기록

- `playwright-cli -s=ypm-<작업> close`로 브라우저를 닫고, 내가 시작한 백그라운드 서버만 종료한 뒤 포트가 비었는지 확인한다. `db:down`은 내가 DB를 시작한 경우에만 실행하고 볼륨은 지우지 않는다.
- 스크린샷·시나리오·로그는 작업 폴더에 두고 저장소에 커밋하지 않는다. 사용자에게 보여줄 스크린샷만 전달한다.
- 기능 개발 문서의 검증 절에 확인한 흐름·화면 너비·헤드리스 사용·모의 여부·결과를 짧게 남기고, 실제 연동을 확인하지 않았다면 미검증으로 적는다.
