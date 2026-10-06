---
name: youth-policy-eligibility
description: 청년정책메이트의 연령·지역·취업·소득 조건 비교, 미확인·예외 처리, 추천 근거와 재판정 변경 기준. 저장소 공통 스킬에 Claude Code 작업 보충을 더한다.
when_to_use: backend의 eligibility 패키지나 policy/catalog의 질문·조건 비교(PolicyQuestionService, PolicyCheckService, BasicConditions, PolicyAgeComparison)를 바꾸거나, 화면의 자격 결과·근거·추가 확인 표시를 바꿀 때. 공고별 질문·판정표 JSON 작성은 youth-policy-rule-data 스킬을 함께 쓴다.
---

# 자격 조건 비교

공통 기준은 저장소 `skills/youth-policy-eligibility/SKILL.md`가 원본이다. 아래에 내용이 보이지 않으면 그 파일을 직접 읽는다. 공통 기준의 상대 링크는 원본 위치 기준이며 `../../docs/`는 저장소 루트의 `docs/`다.

## 공통 기준

!`cat "${CLAUDE_SKILL_DIR}/../../../skills/youth-policy-eligibility/SKILL.md"`

## Claude Code 보충

### 코드 위치

| 역할 | 위치 |
|---|---|
| 조건별 비교 | `backend/.../eligibility/`의 `AgeConditionEvaluator` |
| 전체 결과 결합·상태 | `eligibility/EligibilityDecision`, `EligibilityStatus`, `ConditionAssessment` |
| 공고별 질문·판정표 실행 | `policy/catalog/PolicyRuleDefinition`, `PolicyQuestionService`, `PolicyQuestions` |
| 기본 조건 목록 비교 | `policy/catalog/BasicConditions`, `PolicyCheckService`, `PolicyAgeComparison`, `PolicyCatalogStore` |
| 화면 | `frontend/src/features/eligibility/`, `frontend/src/features/conditions/` |

조건별 판단 근거는 `docs/development/`의 `age-condition.md`·`eligibility-decision.md`, 취업·소득 설계는 `docs/design/`의 `employment-condition.md`·`income-condition.md`에 있다. 바꿀 조건의 문서만 읽는다.

### 구현할 때

- 새 비교 방식을 위해 정책별 Java 클래스를 추가하지 않는다. [ADR-0003](../../../docs/ADR/0003_공고별_조건_데이터.md)에 따라 공고 값은 규칙 데이터로, 새로운 비교 방식만 공통 코드로 보완한다.
- 조건 결과는 `ConditionAssessment.Outcome`(`MET`·`NOT_MET`·`UNKNOWN`)과 미확인 사유 `Uncertainty`, 전체 결과는 `EligibilityStatus`(`ELIGIBLE`·`NEEDS_REVIEW`·`INELIGIBLE`)다. `UNKNOWN`·`NEEDS_REVIEW`를 `NOT_MET`·`INELIGIBLE`로 합치거나, 판정표의 미응답(`""`)과 `UNKNOWN` 선택지를 같은 값으로 다루는 변경이 없는지 diff에서 확인한다.
- 기준일 계산은 주입한 `Clock`에서 `Asia/Seoul` 날짜로 바꾼 값을 사용한다. 테스트는 고정 `Clock`으로 서울 자정 전후를 만든다.

### 검증

- 조건 비교 단위 테스트는 `npm run verify -- test:eligibility`, 공고별 판정표는 `npm run verify -- test:policy-questions`다. 규칙 형식·DB 적용은 `PolicyRuleDefinitionTest`·`PolicyCatalogTest`(Docker 필요)를 직접 지정해 실행한다.
- 테스트 선택과 기록은 `youth-policy-verify` 스킬을 따른다.
