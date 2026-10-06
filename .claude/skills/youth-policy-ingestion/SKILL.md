---
name: youth-policy-ingestion
description: 청년정책메이트의 온통청년 수집·원본/개정·정규화·AI 요약/조건 추출·예산 예약과 정산·자동 공개·재처리·관리자 보정 변경 기준. 저장소 공통 스킬에 Claude Code 작업 보충과 외부 호출 명령의 실행 경계를 더한다.
when_to_use: backend의 ingestion 패키지(Ontong*, PolicyAi*, AiBudget*, OpenAi*)나 정책 개정·수집 예외·보정 코드를 바꾸거나, 수집·AI 관련 운영 명령을 실행해야 할지 판단할 때.
---

# 정책 수집

공통 기준은 저장소 `skills/youth-policy-ingestion/SKILL.md`가 원본이다. 아래에 내용이 보이지 않으면 그 파일을 직접 읽는다. 공통 기준의 상대 링크는 원본 위치 기준이며 `../../docs/`는 저장소 루트의 `docs/`다.

## 공통 기준

!`cat "${CLAUDE_SKILL_DIR}/../../../skills/youth-policy-ingestion/SKILL.md"`

## Claude Code 보충

### 코드와 설계 문서

| 영역 | 코드(`backend/.../ingestion/`) | 먼저 볼 문서 |
|---|---|---|
| 온통청년 수집·범위 재개 | `Ontong*` | `docs/research/ontong-api-contract.md`, `docs/development/policy-range-collection.md` |
| 개정 적용 | `policy/catalog/PolicyCatalogStore` | `docs/design/policy-revision-application.md` |
| AI 요청 판단·예산 예약 | `AiRequestBudget`, `AiBudgetReservation*` | `docs/design/ai-request-admission.md`, `docs/design/ai-budget-reservation-lifecycle.md` |
| AI 실행·규칙 초안·자동 처리 | `PolicyAiExecutionCoordinator`, `PolicyAiRule*`, `OpenAiRuleClient` | `docs/design/policy-ai-execution.md`, `docs/development/ai-rule-drafts.md`, `docs/development/ai-rule-automation.md` |
| 관리자 재처리·보정 | `admin/Collection*`, `admin/PolicyCorrection*` | `docs/development/admin-collection-exceptions.md`, `docs/development/policy-corrections.md` |

설계 문서가 길면 목차를 먼저 확인하고 바꿀 상태 전이·잠금 절만 읽는다.

### 외부 호출 명령의 실행 경계

아래 명령은 실제 외부 API를 호출하거나 비용을 쓰거나 로컬 DB의 정책 데이터를 바꾼다. 사용자가 이번 대화에서 명시적으로 요청한 경우에만 실행하고, 실행 전에 대상·범위·한도를 한 줄로 알린다.

| 명령 | 영향 |
|---|---|
| `npm run probe:ontong` | 온통청년 API 호출(`ONTONG_API_KEY`) |
| `npm run collect:policy` | 외부 수집·로컬 DB 정책 반영 |
| `npm run ai:policy-rules` | OpenAI 호출과 비용 발생 |
| `npm run ai:costs` | OpenAI 관리자 키로 비용 조회 |

- 정기 수집·AI 자동 처리·이메일·알림의 기본값은 비활성화(`application.yaml`의 `*_ENABLED:false`)다. 테스트나 로컬 확인을 위해 기본값을 켜지 않는다.
- 외부 호출 코드는 모의 연동과 PostgreSQL 테스트로 검증한다. 실제 응답 확인이 필요하면 미검증으로 남기고 사용자에게 필요한 설정을 알린다.
- `.env`·실제 응답 캡처(`.local/ontong-api/`)·원 응답 본문을 출력하거나 커밋하지 않는다. 필요한 필드만 요약한다.

### 검증

- 수집·AI 테스트 스크립트는 `test:policy-collection`, `test:ai-*`로 나뉜다. 변경 클래스에 맞는 스크립트 선택은 `youth-policy-verify` 스킬을 따른다.
- 동시성·재개·늦은 결과 시나리오는 실제 PostgreSQL 테스트로 확인하고 Docker가 필요하다.
