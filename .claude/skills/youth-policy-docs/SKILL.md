---
name: youth-policy-docs
description: 청년정책메이트의 PRD·ADR·README·HANDOFF·개발 문서·스킬 문서를 작성하거나 정리하는 기준. 저장소 공통 스킬에 Claude Code 작업 보충과 링크 검사 스크립트를 더한다.
when_to_use: docs/ 아래 문서, README.md, HANDOFF.md, AGENTS.md, skills/·.claude/skills/의 SKILL.md를 수정하거나, 정책·기술 결정이 바뀌어 기준 문서를 먼저 고쳐야 할 때.
---

# 문서 관리

공통 기준은 저장소 `skills/youth-policy-docs/SKILL.md`가 원본이다. 아래에 내용이 보이지 않으면 그 파일을 직접 읽는다. 공통 기준의 상대 링크는 원본 위치 기준이며 `../../docs/`는 저장소 루트의 `docs/`다.

## 공통 기준

!`cat "${CLAUDE_SKILL_DIR}/../../../skills/youth-policy-docs/SKILL.md"`

## Claude Code 보충

### 읽기와 작성

- 긴 문서는 `grep -n '^#' <파일>`로 목차를 확인하고 Read의 `offset`·`limit`로 필요한 절만 읽는다. HANDOFF·PRD·README를 통째로 다시 읽지 않는다.
- 새 ADR은 `docs/ADR/`의 다음 번호로 `NNNN_제목.md`를 만들고 기존 ADR의 머리말(상태·날짜)과 절 구성을 따른다. 채택한 결정을 바꾸면 날짜와 이유를 남기고 이전 결정을 지우지 않는다.
- 기능별 개발 문서(`docs/development/<기능>.md`)의 검증 절에는 실행한 명령·확인 범위·결과·`.local/verification/<id>.log` 경로를 남긴다. 테스트 총개수는 적지 않는다.
- HANDOFF 갱신과 커밋은 `youth-policy-wrap-up` 스킬을 따른다.

### 링크 검사

수정한 Markdown의 상대 경로와 GitHub 제목 앵커를 확인한다. 외부 URL은 확인하지 않는다.

```bash
node "${CLAUDE_SKILL_DIR}/scripts/check-links.mjs" <수정한 .md 파일...>
```

인자가 없으면 Git이 추적하는 모든 `.md`를 검사한다. 종료 코드 1이면 출력된 `파일:줄 → 링크 (사유)`를 고친다. 제목을 바꿨다면 그 제목을 가리키는 다른 문서도 인자 없이 한 번 검사한다.

### 스킬 문서를 고칠 때

- 분야별 판단 기준은 `skills/<이름>/SKILL.md`(Codex·Claude 공통 원본), Claude 전용 보충과 작업 스킬은 `.claude/skills/`에 둔다. 관리 방식은 [스킬 관리 문서](../../../docs/development/skill-reuse.md)를 따른다.
- 공통 원본의 frontmatter에는 Codex 검증기가 허용하는 `name`·`description`만 쓴다. `when_to_use` 같은 Claude 전용 키는 `.claude/skills/`에만 둔다.
- 공통 원본을 고친 뒤 `python3 ~/.codex/skills/.system/skill-creator/scripts/quick_validate.py skills/<이름>`으로 형식을 확인한다.
