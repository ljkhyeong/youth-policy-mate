# 명시적 연령 조건 비교

2026-10-07 구현 기준. 제품 요구는 [PRD 5절](../PRD/0001_product-baseline/spec.md#5-자격-안내와-추천-근거), 공고별 기준값 작성은 [공고별 조건 데이터](policy-rule-data.md#판정표-형식), 항목 결과와 상태의 관계는 [판정 결과 집계](eligibility-decision.md)를 따른다.

## 현재 구현 범위

연령은 공고마다 검토한 규칙 데이터(`PolicyRuleDefinition`)의 연결 하나로 비교한다. 비교 결과는 그 규칙의 연령 질문 답변으로 바꾼 뒤 같은 판정표로 판정한다.

| 연결 | 의미 |
|---|---|
| `birthBinding` | 원문이 출생일로 정한 범위(양 끝 포함)를 구간별 선택지로 연결 |
| `ageBinding` | 기준일의 만 나이 최소·최대(양 끝 포함)를 구간별 선택지로 연결. `referenceDate`가 없으면 서울의 오늘 날짜를 기준일로 쓴다 |

연결이 없는 규칙(국가근로장학금·보증료 지원)은 생년월일로 답변을 만들지 않고 기본 조건 목록에서 연령을 판정하지 않는다. 서비스 대상인 19~34세를 기본 범위로 넣지 않으며, 원문에 없는 최소·최대값이나 기준일을 채우지 않는다.

## 비교와 결과

만 나이는 `Period.between(출생일, 기준일).getYears()`, 곧 기준일까지 완전히 경과한 연수다. 2월 29일생은 2000-02-29부터 2021-02-28까지 20년, 2021-03-01부터 21년으로 계산한다. 원문이 다른 윤일 해석을 정하면 이 연결로 표현하지 않는다.

| 상황 | 결과 |
|---|---|
| 출생일이 기준일보다 늦음 | 답변을 채우지 않는다. 기본 조건 목록은 검토한 비교가 없는 경우처럼 온통청년 표기 연령만 안내하고 `UNKNOWN`으로 남긴다 |
| 최소 미만·범위 안·최대 초과 | `below`·`within`·`above` 선택지로 답하고 판정표의 결과(`MET`·`NOT_MET`·`UNKNOWN`)를 따른다 |

병역 연장처럼 다른 확인이 필요한 상한 초과는 `above`를 확인 대기 선택지로 연결해 `UNKNOWN`으로 남긴다. 생년월일에서 복무기간을 추정하지 않는다. `referenceDate`가 없을 때 기준일은 주입한 `Clock`의 시각을 `Asia/Seoul` 날짜로 바꾼 값이며 서울 자정에 바뀐다.

## 사용 위치

- 질문 자동 입력: `POST /api/v1/policies/{number}/question-prefill`이 `prefill()`로 출생일·연령 답변 하나를 만든다.
- 기본 조건 목록: `compareBirth()`가 같은 판정표로 연령 항목을 만든다. `showCalculatedAge`면 표시 값을 `만 N세 (기준일 · 서울)`로 바꾸며 목록의 항목 이름은 "연령"으로 통일한다. 검토한 연결이 없으면 온통청년 표기 연령과 입력한 생년월일의 만 나이(`PolicySourceConditions.completedYears`)를 함께 보여주되 판정하지 않는다.

생년월일 원문을 결과 값에 복사하지 않고 저장·로그에 남기지 않는다.

## 코드와 검증

- `backend/src/main/java/kr/youthpolicymate/policy/catalog/PolicyRuleDefinition.java`: `BirthBinding`·`AgeBinding`과 `prefill`·`compareBirth`.
- `backend/src/main/java/kr/youthpolicymate/policy/catalog/PolicyCheckService.java`: 목록의 연령 항목과 표기 연령 안내.
- `BasicConditionRulesTest`: 정책별 기준일·양 끝 날짜·서울 자정과 병역 연장 미확인.
- `PolicyRuleDefinitionTest.preservesAgeBoundaries`: 윤년·서울 자정·병역 예외 경계에서 자동 입력 후 판정과 기본 비교 결과 일치.

```sh
npm run verify -- test:policy-questions -- --tests 'kr.youthpolicymate.policy.catalog.PolicyRuleDefinitionTest'
```

2026-08-30에 만든 순수 Java 연령 비교기(`AgeCondition`·`AgeConditionEvaluator`)는 실제 판정이 규칙 데이터로 옮겨 간 뒤 만 나이 계산에만 쓰여 2026-10-07 제거했다. 당시 검증 기록은 저장소 이력에서 확인한다.
