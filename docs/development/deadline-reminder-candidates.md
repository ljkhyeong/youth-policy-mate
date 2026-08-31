# 마감 알림 후보 날짜 계산

2026-08-31 구현·검증 기준. 제품 기준은 [PRD 7절](../PRD/0001_product-baseline/spec.md#7-관심-정책일정알림), 날짜와 오늘 후보의 처리 범위는 [후보 날짜 설계](../design/deadline-reminder-candidates.md)를 따른다.

## 현재 구현

- `policy/RecruitmentSchedule.confirmedDeadlineOnSeoul()`: 확인된 신청 마감의 서울 날짜를 제공한다. 원본 날짜형·시각형 정보는 기존 `applicationPeriod`에 남긴다.
- `schedule/DeadlineReminderCandidates`: 모집 자료와 주입한 시계를 받아 D-7·D-3·D-1 후보 날짜를 계산한다. 전체 결과에는 원래 모집 자료·근거·정책 개정·계산 시점이 포함된다.
- `DeadlineReminderCandidates.CandidateDate`: 마감 며칠 전인지, 후보 날짜와 미래/오늘 구분만 가진다. 수신자·채널·발송 시각은 없다.

코드는 `backend/src/main/java/kr/youthpolicymate/` 아래에 있다. 정책 모듈이 마감 날짜를 제공하고 일정 모듈은 날짜 차감만 담당한다. 모집 상태와 서울의 기준 날짜는 기존 `RecruitmentAssessment`를 사용하므로 원문 파싱·모집 상태 계산·시계 읽기를 중복하지 않는다.

`DeadlineReminderCandidates.calculate(schedule, clock)`로 계산한다. `dates()`는 지난 날짜를 제외한 변경 불가 목록을 날짜순으로 반환한다. 오늘 후보는 `TODAY_REQUIRES_SEND_TIME_CHECK`, 미래 후보는 `FUTURE_DATE`로 표시한다. 오늘의 실제 발송 시각을 아직 모르므로 즉시 발송하거나 이미 놓친 알림으로 단정하지 않는다.

| 결과 | 의미 |
|---|---|
| `CANDIDATES_AVAILABLE` | 오늘 또는 미래 후보가 있음. 예약·발송 허용을 뜻하지 않음 |
| `NO_CONFIRMED_DEADLINE` | 상시·소진 시 종료·기간 미확인 등 확인된 마감 날짜가 없음 |
| `RECRUITMENT_CLOSED` | 기존 모집 상태가 마감이며 후보를 만들지 않음 |
| `NO_REMAINING_DATES` | 모집은 아직 마감되지 않았지만 D-7·D-3·D-1 날짜는 모두 지남 |

시각형의 서울 마감 날짜가 원문 시간대의 날짜와 다르면 서울 날짜를 사용한다. 날짜를 얻기 위해 원래 마감 시각을 지우거나 바꾸지 않는다. 날짜형은 시각으로 변환하지 않는다. 새 개정·마감 변경·미확인 전환은 새 자료로 다시 계산하며 이전 후보를 가져오지 않는다.

실제 저장·해제·수신 동의·예약·발송·Outbox·DB 연결은 없다. 개발용 인공 자료만 [조회 API와 화면](reminder-preview-api.md)에 연결했다. 최신 자료 선택, 이전 예약 취소, 실제 발송 시각과 이미 지난 시점의 판정은 해당 기능 구현 때 처리한다. 후보 계산의 빈 목록만으로 기존 예약이 취소되었다고 간주하면 안 된다.

## 검증

저장소 루트에서 후보 계산만 검사한다.

```sh
npm run test:reminders
```

정책·자격 판정과 함께 검사하고 서버를 빌드한다.

```sh
./backend/gradlew -p backend test --tests 'kr.youthpolicymate.schedule.*' --tests 'kr.youthpolicymate.policy.*' --tests 'kr.youthpolicymate.eligibility.*' assemble --no-daemon
```

두 명령을 실행해 후보 계산 17건, 마감 날짜 제공 8건, 기존 모집 상태 23건·자격 판정 117건, 총 165건과 서버 `assemble`이 통과했다. 실패·건너뛰기는 없다.

- 월·연도·윤일을 지나는 날짜 차감, D-7·D-3·D-1 순서
- 서울 자정 전후의 미래/오늘/지난 후보 구분과 지난 날짜 보충 금지
- 후보 날짜가 모두 지남과 모집 마감의 구분
- 상시·소진 시 종료·날짜 없는 마감·미확인의 후보 없음과 이유 보존
- 다른 시간대·서울 자정 마감의 날짜 제공, 원본 기간·시간대 보존
- 정책 개정·마감 변경·미확인 전환 시 새 자료 사용
- 모집 시작 전 후보 계산과 예약·발송 미확정 안내

Gradle 캐시 접근을 위해 실행 권한을 확장했다. 기존 모집 상태 테스트에서 사용하는 Mockito의 Java agent·JVM 클래스 공유 경고는 남아 있으며 숨김 옵션은 추가하지 않았다. 웹·PostgreSQL 통합 검사는 다시 실행하지 않았다. 실제 정책 날짜 해석, 회원 저장·해제, 예약 취소·재시도·발송의 검증은 아니다.

## 다음 연결

개발 전용 [고정 미리보기](deadline-reminder-preview.md)와 별도의 [서버 계산 연결](reminder-preview-api.md)을 제공한다. 서버 연결은 고정 인공 신청기간을 이 모델로 계산하고 생성 API 타입을 통해 전달한다. 어느 화면도 날짜를 다시 계산하지 않는다. 실제 정책·회원·발송 연결은 별도다.
