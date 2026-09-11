# 화면 문구 정리

2026-09-11 기준. 문구 변경은 `bb501c3`, 기존 연도 경계 테스트 보완은 `c4a6824`에 반영했다. 표현 기준은 [PRD 3.4](../PRD/0001_product-baseline/spec.md#34-화면-문구)를 따른다.

## 적용 범위

홈·정책 목록과 상세·기본 조건·정책별 질문·로그인·내 정책·이메일·관리자 수집 예외 화면을 점검했다. 추상적인 안내, 긴 선택지, 중복된 심사 설명을 줄였다. 공개 화면에서 사용하는 서버의 질문·결과·접수 상태 설명도 함께 수정했다. 개발 전용 예시 화면과 수집한 정책 원문은 유지했다.

| 이전 표현 | 현재 표현 |
|---|---|
| 이 조건으로 정책 확인하기 | 연령 조건 비교 |
| 내 조건으로 정책 찾기 | 연령 조건 비교 결과 |
| 조건 확인 질문 있음 | 질문 있는 정책 |
| 입력한 답변으로 확인 | 조건 비교 |
| 저장 취소 | 저장 해제 |
| 회원가입과 이용 카드 등록을 모두 마쳤어요 | 회원가입·카드 등록 완료 |
| 아직 확인하지 못했어요 | 모르겠어요 |
| 서금원 / 여신심사 | 서민금융진흥원 / 대출심사 |
| 원천 수정 일시 | 온통청년 수정일 |
| 응답 확보 실패 / 발송 시작 | 페이지 수집 실패 / 요청 전송 |
| 사유를 기록하고 재처리 | 재처리 |

- 기본 조건은 현재 연령만 자동 비교한다는 점을 홈·입력·결과에 명시했다. 거주·취업·소득과 최종 신청 자격은 추가 확인으로 유지한다.
- 결과 상단의 공통 심사 안내와 정책별 설명이 반복되지 않도록 공통 문장을 제거했다. 정책별 비교 범위와 최종 자격·남은 확인 사항은 유지한다.
- 이메일 수신 해제 시 취소 대상이 대기 중인 이메일임을 명시했다. 서비스 내 알림 유지와 발송 중 이메일의 취소 제한은 그대로 안내한다.
- 질문 ID·답변 값·판정 기준·API 구조·DB는 변경하지 않았다. 공식 용어, 금액·날짜·예외·출처는 유지했다. 질문 문구만 바뀌었으므로 규칙 버전도 유지한다.

## 검증

기준 리비전은 `bb501c3`이다. 아래 검증 이후 앱 코드는 변경하지 않았으며 문서·커밋만으로 검사를 반복하지 않았다.

| 범위 | 명령과 결과 |
|---|---|
| 웹 | `npm run verify -- test:web`에서 정책 목록의 이전 문구를 검사하는 1건이 실패했다. 기대 문구 수정 후 `npm run verify -- test:web -- src/app/policies/page.test.tsx` 통과. 나머지 테스트의 성공 결과를 재사용했다. |
| 결과·관리자 후속 문구 | `npm run verify -- test:web -- src/features/eligibility/policy-questionnaire.test.tsx`, `npm run verify -- test:web -- src/features/admin/collection-exception-pages.test.tsx` 통과. |
| 정책 질문·접수 상태 | `npm run verify -- test:policy-questions -- --tests 'kr.youthpolicymate.policy.Recruitment*Test'`에서 기존 `BasicConditionRulesTest`의 고정 정책 수 5개만 실패했다. 연도 경계 확인 목적에 맞게 만료 전 결과 존재·만료 후 빈 결과를 검사하도록 보완했다. |
| 실패 범위·API | 아래 Gradle 명령으로 실패 클래스와 문구가 바뀐 API 검증을 실행했다. 첫 실행에서 청약통장 API의 이전 문구 기대값 1개가 남아 있어 수정했고, 해당 메서드와 `PolicyRecruitmentTest`만 재실행해 통과했다. |
| 로컬 반영 | `npm run verify -- package:backend` 통과. 자동 수집·알림·이메일을 끈 로컬 서버에 반영했다. 웹은 기존 개발 서버의 자동 반영을 사용했다. |

```sh
# 기존 npm 스크립트의 전체 RulesTest 선택을 피하고 실패 범위만 재실행했다.
./backend/gradlew -p backend test \
  --tests 'kr.youthpolicymate.policy.catalog.BasicConditionRulesTest' \
  --tests 'kr.youthpolicymate.policy.catalog.PolicyCatalogTest.comparesHousingAgeInBasicConditions' \
  --tests 'kr.youthpolicymate.policy.catalog.PolicyCatalogTest.providesReviewedHaetsalronYouthQuestionsAndAge' --no-daemon

./backend/gradlew -p backend test \
  --tests 'kr.youthpolicymate.policy.catalog.PolicyCatalogTest.comparesHousingAgeInBasicConditions' \
  --tests 'kr.youthpolicymate.policy.catalog.PolicyRecruitmentTest' --no-daemon
```

두 명령의 `JAVA_HOME`은 `/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`이다. 두 번째 실행만으로 전체 서버 검증을 통과했다고 보지 않는다.

- npm 실행 기록: `.local/verification/1789135941413-3e1122c6.log`(웹 최초), `1789136050147-58483e9d.log`(목록), `1789136304354-b76d9d28.log`(결과), `1789136407070-5fce1804.log`(관리자), `1789135961152-9ff28486.log`(규칙 최초), `1789136148747-f03ee795.log`(패키징).
- Gradle 재실행 로그: `/tmp/youth-copy-backend-retry.log`, `/tmp/youth-copy-backend-final.log`. 첫 실행에서 통과한 BasicConditionRulesTest·햇살론유스 API 결과는 재사용했다.
- 브라우저: 홈·목록·조건·로그인·내 정책·관리자 접근 안내를 390px·1280px에서 확인했다. 미래이음 대출 질문·결과·답변 삭제와 기본 조건 입력·연령 비교를 확인했다. K-패스·응시료·청약통장·햇살론유스 질문도 모바일에서 문구 표시와 가로 넘침을 확인했다. 기록과 화면은 `/tmp/youth-copy-ui/`에 있다.
- 실제 회원·관리자 로그인 후 화면과 이메일 외부 전달은 이번 브라우저 검증에 포함하지 않았다. 관리자 화면은 기존 렌더링 테스트로 확인했다. 문구만 변경해 전체 서버 검사·웹 빌드·원격 CI는 재실행하지 않았다.
