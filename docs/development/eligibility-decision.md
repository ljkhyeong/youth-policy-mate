# 자격 판정 결과 집계

2026-10-07 구현 기준. 제품 규칙은 [PRD 5절](../PRD/0001_product-baseline/spec.md#5-자격-안내와-추천-근거), 서버 책임은 [ADR-0001](../ADR/0001_기술스택과_책임_분리.md), 공고별 판정표는 [ADR-0003](../ADR/0003_공고별_조건_데이터.md)을 따른다.

## 결과 값

| 타입 | 값 |
|---|---|
| `eligibility/ConditionOutcome` | 항목 하나의 결과. `MET`·`NOT_MET`·`UNKNOWN` |
| `eligibility/EligibilityStatus` | 상태. `ELIGIBLE`·`INELIGIBLE`·`NEEDS_REVIEW` |

항목 결과는 판정표가 정한다. 불충족을 바꿀 수 있는 예외를 확인하지 못했으면 판정표에서 `NOT_MET` 대신 `UNKNOWN` 행이나 확인 대기 선택지로 표현한다. 미응답(`""`)과 `UNKNOWN` 선택지는 판정표에서 구분하지만 일치하는 행이 없으면 둘 다 `UNKNOWN`이다. 작성 기준은 [공고별 조건 데이터](policy-rule-data.md#판정표-형식)를 따른다.

## 공통요건 상태

공고별 질문 응답(`PolicyRuleDefinition.evaluate`)은 판정 항목 결과로 `commonCriteriaStatus`를 계산한다(`commonStatus`).

| 순서 | 조건 | 결과 |
|---|---|---|
| 1 | 판정 항목이 없음 | `NEEDS_REVIEW` |
| 2 | 불충족 항목이 하나 이상 | `INELIGIBLE` |
| 3 | 불충족은 없고 미확인 항목이 있음 | `NEEDS_REVIEW` |
| 4 | 모든 항목 충족 | `ELIGIBLE` |

다른 항목이 미확인이라는 이유로 명확한 불충족을 숨기지 않고, 미확인을 충족으로 바꾸지 않는다. 판정 항목 이름(`label`)은 규칙 안에서 서로 달라야 하며 등록할 때 검사한다.

## 전체 상태

응답의 `status`는 항상 `NEEDS_REVIEW`다. 모든 규칙에 기관 심사·참여 제한 등 남은 확인(`remainingChecks`, 비울 수 없음)이 있어 서비스가 최종 자격을 확정하지 않는다. 기본 조건 목록(`POST /api/v1/policies/checks`)의 항목 상태도 `NEEDS_REVIEW`다. 목록은 연령만 검토한 규칙으로 비교하고 거주·취업·소득·추가 조건은 `UNKNOWN`으로 남긴다. 연령 비교는 [연령 조건 비교](age-condition.md)를 따른다.

## 근거·시간·개인정보

- 항목 결과에는 표시 값·설명·원문 근거를 담고, 응답에는 정책 개정·규칙 버전·판정 시각을 함께 준다. 개정이나 규칙 버전이 바뀌면 이전 답변은 409로 거절한다.
- 시간 계산은 주입한 `Clock`과 서울 날짜를 사용한다.
- 답변·생년월일은 저장하지 않는다. 결과 객체 전체를 로그·분석 이벤트에 넣지 않는다.
- 모집 상태는 이 결과에 넣지 않는다. 별도 [모집 기간 상태 계산](recruitment-period.md) 값이며, `ELIGIBLE`만으로 지금 신청할 수 있다고 안내하지 않는다.

## 코드와 검증

- `backend/src/main/java/kr/youthpolicymate/eligibility/`: `ConditionOutcome`, `EligibilityStatus`, 기본 조건 입력용 `SeoulDistrict`.
- `backend/src/main/java/kr/youthpolicymate/policy/catalog/`: `PolicyRuleDefinition`(`evaluate`·`commonStatus`), `PolicyQuestions`, `PolicyCheckService`.
- 정책별 `*RulesTest`(`npm run verify -- test:policy-questions`)가 충족·불충족·미확인과 공통요건 상태를, `PolicyCatalogTest`가 API 응답을 확인한다.

취업·소득 입력 의미는 [취업 조건 설계](../design/employment-condition.md)·[소득 조건 설계](../design/income-condition.md)에 있으며 구현은 없다.

2026-08-30에 만든 순수 Java 집계 모델(`EligibilityDecision`·`ConditionAssessment`·`PolicyReview`·`EvaluationBasis`·`SourceEvidence`)은 실제 판정이 공고별 판정표로 옮겨 간 뒤 상태 계산에만 쓰여 2026-10-07 `commonStatus`로 바꾸고 제거했다. 같은 시기의 거주·단일 취업·소득 구간 비교기와 개발 전용 자격 API도 이미 제거했다. 당시 검증 기록은 저장소 이력에서 확인한다.
