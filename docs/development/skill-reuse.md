# 프로젝트 지시·스킬 관리

## 관리 원본

- 공통 작업 규칙과 스킬 선택은 [AGENTS.md](../../AGENTS.md), 분야별 판단 기준은 저장소의 [skills/](../../skills/)에 둔다.
- 제품 정책은 [PRD](../PRD/0001_product-baseline/spec.md), 기술 결정은 [ADR](../ADR/)을 기준으로 한다. 스킬에는 작업 중 놓치기 쉬운 조건과 필요한 참조만 남긴다.
- 사용자 스킬 폴더 `~/.codex/skills/youth-policy-*`는 저장소의 같은 이름 폴더를 가리키는 심볼릭 링크다. 저장소 원본을 수정하면 연결된 스킬에도 반영된다.

## Claude Code 스킬

Claude Code는 저장소의 `.claude/skills/`를 사용한다. Codex 검증기는 frontmatter를 `name`·`description` 등으로 제한하므로, Claude 전용 키(`when_to_use`·`argument-hint`·`context` 등)는 공통 원본에 넣지 않고 이 폴더에만 둔다.

| 종류 | 스킬 | 구성 |
|---|---|---|
| 분야 기준 | `youth-policy-docs`·`backend`·`api-contract`·`frontend`·`ingestion`·`eligibility`·`reminders` | 본문의 `` !`cat …` ``이 호출 시점에 `skills/<이름>/SKILL.md` 원본을 불러오고, 아래에 코드 위치·재사용 코드·실행 경계 같은 Claude 보충을 둔다. 원본 내용을 복사하지 않는다. |
| 작업 절차 | `youth-policy-verify` | 변경 위치별 `test:*` 선택표와 현재 검증 기록 |
| | `youth-policy-browser-check` | 로컬 실행과 Playwright CLI 헤드리스 화면 확인 |
| | `youth-policy-rule-data` | 공고별 규칙 JSON 작성·등록·적용 |
| | `youth-policy-review` | 반복된 오류 유형 점검표로 변경 검토(별도 서브에이전트에서 실행) |
| | `youth-policy-wrap-up` | 검증 확인·비밀값 점검·HANDOFF 갱신·분류 커밋 |

- 분야 판단 기준이 바뀌면 공통 원본을 고친다. Claude 보충에는 Claude가 작업 중 찾기 어려운 코드 위치·명령·실행 경계만 둔다.
- 작업 절차 스킬은 [검증 절차](verification-workflow.md)·[규칙 데이터](policy-rule-data.md) 같은 기준 문서를 링크하고 세부 규칙을 복사하지 않는다.
- 문서 스킬의 `scripts/check-links.mjs`는 Markdown 상대 경로와 GitHub 제목 앵커를 확인한다.

## 2026-09-05 정리

[OpenAI의 GPT-6 Astra 프롬프팅 가이드](https://developers.openai.com/api/docs/guides/latest-model?model=gpt-6-astra#prompting-best-practices)를 참고했다. 지시 충돌과 불필요한 승인 대기를 줄이고, 응답과 검증 범위를 작업에 맞췄다.

- 문서·백엔드·API·프런트엔드·수집·자격·알림 7개 스킬의 역할과 자동 선택 설정을 유지했다.
- 반복된 문서 읽기·일반 개발 원칙은 공통 지시로 모았다. 다른 프로젝트의 제외 목록과 구현 이전의 안내, 고정된 전체 검증 절차는 삭제했다.
- 사용자 요청을 다시 승인받게 할 수 있는 표현을 고쳤다. 필요한 검증을 통과하면 새 근거 없이 반복하지 않도록 했다.
- 미확인 자격·계정별 데이터 분리·개정 순서·알림 취소·외부 전달 결과 미확인 등 실제 오류를 막는 규칙은 남겼다.
- HANDOFF의 누적 이력과 오래된 현재 상태를 제거했다. 현재 기능·검증 범위·남은 작업만 두고 상세 기록은 기존 개발 문서와 Git에서 확인한다.
- 기존 작업 기록을 대조해 [검증 절차와 결과 기록](verification-workflow.md)을 추가했다. 컴파일·부분 테스트·패키징·전체 검사를 구분하고 결과와 파일 상태를 다음 작업에서 확인한다.

## 수정 후 확인

- 스킬의 이름·설명·참조 경로를 확인하고 시스템 `skill-creator`의 `scripts/quick_validate.py`를 실행한다.
- `agents/openai.yaml`의 UI 설명·기본 프롬프트를 스킬 역할에 맞추고 기존 도구 의존성과 자동 선택 설정을 보존한다.
- 저장소를 옮기면 사용자 스킬 링크를 갱신한다. 같은 이름의 다른 파일·디렉터리를 덮어쓰지 않는다.
- Claude 스킬은 폴더 이름과 `name`이 같은지, 본문의 `` !`…` `` 명령이 `${CLAUDE_SKILL_DIR}`를 실제 경로로 바꿨을 때 종료 코드 0으로 끝나는지 확인한다. 실패하면 스킬 호출 전체가 중단된다. 새 스킬 폴더는 Claude Code 세션을 다시 시작해야 목록에 나타난다.
- 수정한 Markdown은 `node .claude/skills/youth-policy-docs/scripts/check-links.mjs <파일...>`로 링크를 확인한다.
