# 명시적 소득 구간 비교

2026-08-30 구현 기준. 제품 동작은 [PRD 4~5절](../PRD/0001_product-baseline/spec.md#4-조건-입력과-로그인), 범위와 원천 확인 한계는 [소득 조건 설계](../design/income-condition.md)를 따른다.

## 현재 구현 범위

`backend/src/main/java/kr/youthpolicymate/eligibility/`에 순수 Java 비교기를 추가했다. 정의·대상·기간·단위가 확인된 사용자 소득 구간과 정책 허용 구간을 비교한다. 인증키·DB·서버 기동 없이 테스트할 수 있다.

원문 해석, 소득 산정, 원천 DTO, API·화면·저장은 구현하지 않았다. 실제 정책이나 `/conditions`·`/dev/employment`의 입력을 연결하지 않는다. 아래 모델은 내부 호출 계약이며 외부 요청을 바로 바인딩할 DTO가 아니다.

| 모델 | 역할 |
|---|---|
| `IncomeBasis` | 정책·개정·조건 ID, 개인·가구, 소득 정의, 대상 기간과 산정 방식, 적용 기준표·실제 기준일 |
| `IncomeRange` | 원화 금액의 하한·상한과 각각의 포함 여부, 명시적인 한쪽 제한 없음 |
| `IncomeAnswer.KnownRange` / `Unknown` | 어떤 비교 기준에 답했는지와 확인한 구간 또는 모름. 미응답은 `Optional.empty()` |
| `IncomeCondition.RangeRequirement` | 비교 기준, 확인한 허용 구간, 원문 단위·변환 근거, 출처 |
| `IncomeCondition.NoRestriction` / `Unresolved` | 본문·예외까지 확인한 소득 제한 없음 또는 정책 미해석 |
| `IncomeConditionEvaluator` | 항목별 `ConditionAssessment` 생성. 최종 자격 집계는 기존 `EligibilityDecision`이 담당 |

## 비교 전제와 입력 재사용

`IncomeBasis` 전체가 같은 답변만 비교한다. 정책·개정·조건 ID, 소득 정의, 대상, 기간·산정 방식, 필요한 가구 조건·기준표·기준일 중 하나가 다르면 `MISSING_USER_INPUT`으로 남기고 이전 금액을 결과에 복사하지 않는다. 문자열로 보존한 정의도 정확히 일치해야 한다. 유사한 문구를 자동으로 같은 뜻이라고 판단하지 않는다.

- 본인 소득은 `Personal`로 표현하며 가구원 수를 받지 않는다.
- `Household`는 정책이 정한 가구원 범위와 필요한 인원 수·유형·산정일을 보존한다. 선택값이 없다는 것은 그 항목을 요구하지 않음을 확인했다는 뜻이다. 필요한 값이 미확인인데 빈 값으로 비교 기준을 완성하지 않는다.
- `Period`는 양 끝 날짜를 포함하는 대상 기간과 합계·평균 등 산정 방식의 정의를 보존한다. 계산 자체는 수행하지 않는다.
- `Standard`는 이미 확인한 금액에 적용한 기준표 이름·연도·개정이다. 표 조회·금액 결정·건강보험료 산정 기능이 아니다. 필요한 표나 산정 방식이 미해석이면 비교 조건을 만들지 않고 `Unresolved`로 남긴다.
- 실제 기준일이 없다면 `referenceDate`를 비워 둔다. 연간 소득의 종료일이나 기준표 연도를 임의의 기준일로 바꾸지 않는다.

금액의 내부 단위는 **원**으로 고정했다. 다른 통화·만원·비율·보험료를 구분 없이 받는 숫자 모델이 아니다. 호출 측에서 정책과 사용자 값의 단위를 확인해야 하며, 원문 단위와 정확한 변환 여부는 `RangeRequirement.amountBasis`와 `SourceEvidence`에 남긴다. `amountBasis`는 근거 설명일 뿐 변환식으로 실행하지 않는다. 현재 원천 매핑이나 단위 변환기는 없으므로, 잘못된 단위의 원천 숫자를 자동 검출·변환했다고 볼 수 없다.

## 구간과 결과

`IncomeRange.Amount`는 `BigDecimal`과 포함 여부를 사용한다. 소수 자릿수의 차이는 정리하되 값은 반올림하지 않는다. 정확한 금액은 `IncomeRange.exact`로 양 끝이 같은 포함 구간을 만든다. 명시적 0원도 유효한 금액이다.

`Unbounded.INSTANCE`는 한쪽 제한이 없음을 확인한 경우만 사용한다. 원천 필드 누락을 대신하지 않는다. 양쪽 모두 제한이 없는 구간은 만들지 않으며, 정책의 소득 제한 없음은 `NoRestriction`, 답변 모름은 `Unknown`으로 구분한다.

| 조건 | 결과 |
|---|---|
| 정책 기준·단위·예외가 미해석이거나 산정 방식 미지원 | `UNKNOWN / UNRESOLVED_POLICY`. 전달된 금액도 사용하지 않음 |
| 본문·예외까지 확인한 소득 제한 없음 | 소득 항목만 `MET`. 답변 요구·복사 없음 |
| 사용자 값 없음·모름·다른 비교 기준의 답변 | `UNKNOWN / MISSING_USER_INPUT` |
| 사용자 구간 전체가 허용 구간에 포함 | `MET` |
| 사용자 구간과 허용 구간이 전혀 겹치지 않음 | `NOT_MET` |
| 일부만 겹침 | `UNKNOWN / MISSING_USER_INPUT`. 경계에 맞춰 더 좁은 구간 확인 안내 |

끝값이 같아도 어느 한쪽에서 그 값을 제외하면 접점만으로 겹친다고 판단하지 않는다. 예를 들어 인공 조건이 `20원 미만`이고 입력이 `20원 이상`이면 두 범위는 겹치지 않는다. 실제 정책 금액 예시가 아니다.

역전된 경계나 포함 금액이 없는 구간은 생성자에서 거절하며 불충족 결과로 바꾸지 않는다. 향후 원천 매핑은 잘못된 정책 구간을 정책 미해석으로, 입력 경계는 잘못된 사용자 구간을 입력 오류로 처리해야 한다. 아직 해당 API 오류 응답·화면은 없다.

항목 결과는 정의·대상·기간·표·금액 근거를 `appliedCondition`, 사용한 구간을 `comparedValue`에 보존한다. 이 문자열을 다시 파싱해 비교하지 않는다. 정책 개정·규칙 버전·판정 시점은 전체 결과의 `EvaluationBasis`와 함께 관리한다. 기존 집계 순서대로 정책 미해석이 우선하며, 소득 충족만으로 다른 조건의 `PolicyReview`를 완료하지 않는다.

## 코드와 검증

추가한 코드는 다음과 같다.

- [IncomeBasis.java](../../backend/src/main/java/kr/youthpolicymate/eligibility/IncomeBasis.java)
- [IncomeRange.java](../../backend/src/main/java/kr/youthpolicymate/eligibility/IncomeRange.java)
- [IncomeAnswer.java](../../backend/src/main/java/kr/youthpolicymate/eligibility/IncomeAnswer.java)
- [IncomeCondition.java](../../backend/src/main/java/kr/youthpolicymate/eligibility/IncomeCondition.java)
- [IncomeConditionEvaluator.java](../../backend/src/main/java/kr/youthpolicymate/eligibility/IncomeConditionEvaluator.java)
- [IncomeConditionEvaluatorTest.java](../../backend/src/test/java/kr/youthpolicymate/eligibility/IncomeConditionEvaluatorTest.java)

저장소 루트에서 이번에 실행한 명령이다. `npm run test:eligibility`로 같은 단위 테스트 범위를 별도로 실행할 수도 있다.

```sh
./backend/gradlew -p backend test --tests 'kr.youthpolicymate.eligibility.*' assemble --no-daemon
```

소득 47건·취업 21건·거주 17건·연령 18건·집계 14건, 총 **117건**이 실패·건너뛰기 없이 통과했고 서버 `assemble`도 통과했다. 소득 검사는 구간 포함·분리·일부 겹침, 양 끝 경계, 소수 정밀도, 0원·미응답·모름, 다른 기준의 금액 재사용 차단, 가구·표 정보, 기존 집계 우선순위와 근거 보존을 확인한다. 테스트 자료는 인공 입력이며 실제 원천 응답이나 공식 기준표를 검증한 것이 아니다.

웹·DB 코드를 바꾸지 않아 프런트엔드 검사와 PostgreSQL 통합 검사는 다시 실행하지 않았다. 입력값·판정 결과를 로그·분석·AI 요청·공용 캐시에 추가하지 않았고 외부 호출도 없다.

## 다음 연결 지점

- 인증키 없이 이어가려면 개발 전용 소득 질문 미리보기에서 정의·기간·원화 단위, 구간·모름·경계 추가 확인 안내를 점검할 수 있다. 이는 후속 작업이며 이번에 화면을 추가한 것은 아니다.
- 실제 API 성공 응답을 확보한 뒤 소득 단위·빈 경계·예외를 확인하고 원천 매핑을 정한다. 필요한 정보가 없으면 무관 코드나 금액 0으로 보완하지 않는다.
- 월·연 환산, 세전·세후 변환, 중위소득 비율·보험료·소득인정액 산정, 기준표 조회, 복합 조건·원문 해석은 아직 미지원이다. 범용 수식 엔진을 추가하지 않는다.
