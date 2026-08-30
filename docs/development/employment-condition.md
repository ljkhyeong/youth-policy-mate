# 단일 취업 조건 비교

2026-08-30 구현 기준. 제품 요구는 [PRD 4~5절](../PRD/0001_product-baseline/spec.md#4-조건-입력과-로그인), 입력 의미와 후속 질문은 [취업 조건 설계](../design/employment-condition.md), 전체 상태는 [판정 결과 집계](eligibility-decision.md)를 따른다.

## 현재 구현 범위

`EmploymentConditionEvaluator.evaluate(condition, answer)`는 확인한 단일 취업 요건과 명시적 답변을 비교해 `ConditionAssessment` 하나를 반환한다. 취업 제한 없음과 미해석 조건도 구분한다. 순수 Java 코드이며 인증키·Spring 실행·DB가 필요하지 않다.

현재 `/conditions`의 주된 취업상태 문자열은 입력받지 않는다. 선택지 이름·원천 `jobCd`를 답변으로 변환하는 코드, 실제 정책 해석기, HTTP API와 DB 저장은 없다. [개발 전용 질문 미리보기](employment-question-preview.md)는 별도로 구현했지만 이 비교기와 연결하지 않았다. ‘재직하지 않음’을 복합적인 미취업 정의 전체와 같다고 판단하지 않는다.

## 조건과 답변

| 모델 | 역할 |
|---|---|
| `EmploymentFact` | 정책 ID·개정·조건 ID, 원문에서 확인한 단일 사실의 정의와 기준일 |
| `EmploymentAnswer` | 어떤 `EmploymentFact`에 대한 것인지와 `APPLIES`·`DOES_NOT_APPLY`·`UNKNOWN` 답변 |
| `EmploymentCondition.FactRequirement` | 단일 사실과 `requiredToApply` 요구 방향, 원문 근거 |
| `EmploymentCondition.NoRestriction` | 관련 본문·예외까지 확인한 취업 제한 없음, 알려진 기준일과 근거 |
| `EmploymentCondition.Unresolved` | 해석하지 못한 조건 설명·이유, 알려진 기준일과 근거 |

`requiredToApply=true`는 해당해야 함, `false`는 해당하지 않아야 함이다. 단일 사실의 정의와 답변은 그대로 두고 정책이 요구하는 방향을 비교한다.

| 정책 요구 | 해당함 | 해당하지 않음 |
|---|---|---|
| 해당해야 함 | `MET` | `NOT_MET` |
| 해당하지 않아야 함 | `NOT_MET` | `MET` |

`Optional.empty()`는 답변 누락, `UNKNOWN`은 명시적인 모름 답변이다. 어느 쪽도 비해당이나 불충족으로 바꾸지 않는다. 이 모델은 사용자 진술을 표현할 뿐 재직·미취업 사실을 공적으로 인증하지 않는다.

`EmploymentFact`는 필수 식별자·정의·기준일을 요구한다. 생성자 검사는 내부 모델을 잘못 구성하는 일을 막는 것이며, 원문이나 AI 추출 후보가 정확하다고 검증하지 않는다. 원천 정보가 부족하면 임의의 정의·오늘 날짜를 채우지 말고 `Unresolved`로 남겨야 한다.

## 답변을 재사용할 수 있는 범위

조건의 `EmploymentFact`와 답변이 가리키는 값이 모두 같아야 한다. 정책 ID·정책 개정·조건 ID·정의 문자열·기준일 중 하나라도 다르면 `UNKNOWN / MISSING_USER_INPUT`이다. 정책은 해석했지만 현재 조건에 대한 답변이 없는 것으로 처리한다.

같은 값으로 만든 별도 객체는 비교할 수 있다. 반대로 문구가 같아도 정책 개정이 다르면 재확인을 요구한다. 정의 문자열은 공백·문구를 임의로 정규화하지 않고 그대로 비교한다. 의미가 같은 문구를 자동 판별하거나 변경 없는 답변의 재사용 범위를 넓히는 기능은 없다.

다른 조건의 답변은 결과의 비교값에 넣지 않는다. 현재 조건·기준일·근거와 다시 답변해야 하는 이유만 남긴다. 같은 조건에 대한 모름 답변은 비교값에 ‘모름’을 보존하고, 답변 누락은 비교값도 비워 둔다.

호출자는 실제로 제시한 조건과 그에 대한 답변의 연결을 보존해야 한다. 기존 답변을 새 `EmploymentFact`로 감싸면 이 비교기만으로 재사용 사실을 알아낼 수 없다. 향후 API에서 클라이언트가 보낸 정책 정의·요구 방향을 그대로 신뢰하거나, 주된 취업상태로 답변을 만들어 넣지 않는다.

## 제한 없음과 정책 미해석

`NoRestriction`은 답변 유무와 관계없이 취업 항목만 `MET`으로 처리하고 사용하지 않은 답변은 결과에 복사하지 않는다. 사용자 상태를 비교할 필요가 없는 제한 없음에는 기준일이 없을 수 있으며 오늘 날짜를 넣지 않는다. 기준일에 따라 제한·예외가 달라지는데 이를 모르는 경우는 `NoRestriction`이 아니라 `Unresolved`다.

빈 코드·알 수 없는 코드·파싱 실패는 제한 없음이 아니다. 취업 제한 없음으로 표시된 코드라도 관련 본문·예외를 확인하기 전에는 이 타입에 넣지 않는다.

`Unresolved`는 답변이 있어도 `UNKNOWN / UNRESOLVED_POLICY`를 반환한다. 원문 설명·알려진 날짜·미해석 이유와 근거를 보존한다. 여러 취업 상태의 OR/AND, 수치·기간 비교, 서술형 예외는 현재 지원하지 않는다. 대안 조건을 독립적인 필수 항목으로 쪼개어 기존 집계에 넣지 않는다.

## 집계·시간·개인정보

- 연령·거주·취업 결과를 기존 `EligibilityDecision`에 함께 넣을 수 있다. 취업 비교기가 `PolicyReview`를 자동 완료하지 않는다.
- 취업 항목이 충족돼도 재학 정보가 없거나 정책 예외를 검토하지 못했다면 기존 집계 순서에 따라 처리한다. 모집 상태·공식 자격 인증과도 별개다.
- `EvaluationBasis`의 정책 개정·규칙 버전·판정 시점은 호출자가 관리한다. 이 비교기는 답변과 취업 사실의 일치만 검사하며, 서로 다른 정책의 결과를 하나의 집계에 섞는 것까지 검사하지 않는다.
- 현재 날짜나 기본 시간대를 조회하지 않아 `Clock`이 필요하지 않다. 오늘·신청일을 고르는 계층을 추가할 때 명시적인 시계와 시간대를 사용한다.
- 답변·근거를 보존하지만 로그·분석 이벤트·서버 전송·저장은 새로 추가하지 않았다. 결과 객체 전체를 로그로 출력하지 않는다.

## 코드와 검증

서버 코드는 `backend/src/main/java/kr/youthpolicymate/eligibility/`의 `EmploymentFact.java`, `EmploymentAnswer.java`, `EmploymentCondition.java`, `EmploymentConditionEvaluator.java`에 있다. 테스트는 `backend/src/test/java/kr/youthpolicymate/eligibility/EmploymentConditionEvaluatorTest.java`다.

저장소 루트에서 실행한다.

```sh
npm run test:eligibility
./backend/gradlew -p backend assemble --no-daemon
```

2026-08-30에 취업 비교 21건·거주 비교 17건·연령 비교 18건·집계 14건, 총 70건이 실패·건너뛰기 없이 통과했고 서버 `assemble` 빌드도 통과했다. 취업 테스트는 해당·비해당 요구 방향, 모름·누락, 정책·개정·조건 ID·정의·기준일 불일치, 제한 없음, 정책 미해석과 기존 집계 연결을 확인한다. 웹·DB 코드는 바꾸지 않았으며 프런트엔드 검사·PostgreSQL 통합 검사는 이번 작업에서 다시 실행하지 않았다.

검증 자료는 정의·기준일·요구 방향을 직접 지정한 인공 입력이다. 원천 코드 파서·실제 정책 원문의 정확도·추가 질문 UI·소셜 인증을 검증한 것이 아니다. 복수 답변의 충돌 해결이나 질문 배포·회수도 별도 구현 범위다.
