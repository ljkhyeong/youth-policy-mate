---
name: youth-policy-rule-data
description: 청년정책메이트의 공고별 질문·선택지·판정표·근거·적용 기간 JSON(PolicyRuleDefinition)을 새로 만들거나 개정하고, rules:policy 명령이나 관리자 화면으로 초안 등록·적용·검증하는 절차.
when_to_use: 새 정책에 질문을 추가하거나 공고 연도·기간·연령·성적·소득 기준이 바뀌어 규칙을 개정할 때, AI 규칙 초안을 검토할 때, 원문 변경·기간 만료로 질문이 중단된 정책을 다시 열 때.
argument-hint: "[정책번호나 정책명]"
---

# 공고별 조건 데이터

대상: $ARGUMENTS

기준은 [ADR-0003](../../../docs/ADR/0003_공고별_조건_데이터.md)과 [규칙 데이터 문서](../../../docs/development/policy-rule-data.md)의 "판정표 형식"·"운영 명령" 절, 관리자 흐름은 [조건 검토·적용](../../../docs/development/policy-rule-review.md)이다. 판정 원칙은 `youth-policy-eligibility` 스킬을 함께 적용한다.

## 원칙

- 공고 값의 변경은 데이터만 바꾼다. 정책별 Java 클래스나 질문 분기를 추가하지 않는다. 현재 형식으로 표현할 수 없는 비교 방식만 공통 코드(`PolicyRuleDefinition`)를 보완한다.
- 적용한 버전은 수정하지 않는다. 수정·되돌리기 모두 새 `ruleVersion`의 초안으로 만든다. DB를 직접 수정하거나 이전 버전을 다시 공개하지 않는다.
- 원문에 없는 기준을 추정하지 않는다. 각 `checks[].evidence`와 `sourceUrl`은 실제 공고에서 확인한 내용이어야 하며, AI 초안은 후보로만 보고 원문과 대조한다.

## 작성 순서

1. 현재 상태를 확인한다: `npm run rules:policy -- --args='status'`. 기존 규칙이 있으면 `status`에 나온 버전 ID로 `export <버전 ID> <저장소 밖 경로>.json`을 실행해 내려받는다. `export`는 기존 파일을 덮어쓰지 않는다.
2. 원문(공개 정책 상세와 공식 공고)에서 대상 연령·기준일·학적·성적·소득·중복지원·참여 제한·예외를 표로 정리한다. 확인하지 못한 항목은 `remainingChecks`로 남긴다.
3. JSON을 고친다. 아래 점검표를 모두 확인한다.
4. 초안 등록: `npm run rules:policy -- --args="draft <파일> '<사유>'"` 또는 관리자 화면의 `새 초안 등록`.
5. 적용: `npm run rules:policy -- --args='publish <초안 UUID> <직전 버전 또는 none>'`. 직전 버전은 `status`에 나온 저장 버전이며 월별 접미사(`-2026-09`)는 붙이지 않는다.
6. `status`와 해당 정책의 질문·결과를 확인한다.

`rules:policy`는 루트 `.env`의 DB에 쓴다. 로컬 DB에서만 실행하고, 운영 DB 연결 설정이 보이면 멈추고 사용자에게 확인한다.

## JSON 점검표

| 항목 | 확인 |
|---|---|
| `ruleVersion` | 새 버전명. 이전 연도 문구·선택지 이름이 남지 않았는지 함께 확인 |
| `contentHash` | 검토한 현재 원문의 해시. 원문이 바뀌면 `publish`가 거부된다 |
| `validFrom`·`validUntil` | 규칙 사용 기간(시작 포함·종료 제외). 서울 자정은 `+09:00`. 실제 접수 기간과 구분 |
| `checks[].cases` | 위에서부터 첫 일치. 충족 행과 확인된 예외 행을 먼저, 명확한 불충족 행을 뒤에 둔다 |
| 예외 미확인 | 예외를 확인하지 못한 조합을 `NOT_MET`으로 두지 않는다. 어느 행에도 맞지 않으면 `UNKNOWN`과 `unknownExplanation` |
| `when`의 `""` | 미응답을 뜻하며 `UNKNOWN` 선택지와 다르다 |
| `birthBinding` | 해당 질문만으로 판단할 수 있는 출생일 범위만 연결한다. 군복무 등 다른 답변이 필요한 조건은 넣지 않는다 |
| `ageBinding` | 만 나이 최소·최대, `referenceDate`가 없으면 서울의 현재 날짜. 병역 예외가 필요한 상한 초과는 확인 대기 선택지로 |
| `monthly` | 월별 실적 기준이면 `true`. 달이 바뀌면 이전 답변을 거부한다 |
| `periodNotice` | 모집 전·접수 중·마감 뒤 안내만 바꾼다. 검색용 모집 상태를 덮어쓰지 않는다 |
| `remainingVariant`·`providedAnswers` | 위치(0부터)와 질문 ID가 실제로 존재하는지 |
| 질문·선택지 ID | 기존 규칙을 개정할 때 식별자를 바꾸지 않는다 |

## 검증

- 형식·참조는 `PolicyRuleDefinitionTest`, 기존 정책의 고정 비교는 `npm run verify -- test:policy-questions`, DB 적용·목록 반영은 `npm run verify -- test:policy-catalog`(Docker 필요)다.
- 새 정책을 테스트 고정 자료에 추가했다면 `backend/src/test/java/kr/youthpolicymate/policy/catalog/PolicyRuleFixtures.java`와 같은 패키지의 `*RulesTest` 형식을 따르고, 충족·불충족·미확인과 결과를 바꾸는 예외만 사례로 둔다.
- 화면에서 질문 시작·자동 입력·비교 결과를 확인해야 하면 `youth-policy-browser-check` 스킬을 사용한다.
- 정책을 추가·개정했으면 HANDOFF의 "조건 질문" 목록과 해당 정책의 `docs/development/<정책>-questions.md`를 갱신한다.
