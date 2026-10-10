# 모집 기간 상태 계산

2026-08-31 구현·검증 기준. 날짜·시각의 포함 경계와 미지원 입력은 [모집 기간 설계](../design/recruitment-period.md)를 따른다. 제품 기준은 [PRD](../PRD/0001_product-baseline/spec.md)이며 기존 자격 판정 규칙은 변경하지 않았다.

## 코드와 책임

`backend/src/main/java/kr/youthpolicymate/policy/`의 순수 Java 값과 `policy/catalog`의 계산 함수로 구성한다. Spring 실행·인증키·DB·외부 API 없이 확인된 신청기간과 계산 시각만 비교한다.

| 코드 | 역할 |
|---|---|
| `ApplicationPeriod` | 날짜 범위·시각 범위·상시·명시적 마감·미확인을 서로 다른 타입으로 표현 |
| `RecruitmentStatus` | 모집 전·접수 기간·마감·상시·기간 미확인 |
| `catalog/PolicyRecruitment` | `of(period, now)`가 상태·안내 문구·서울 마감일·남은 일수를 계산한다. `from(number, hash, raw, now)`는 원문 기간을 해석해 같은 계산을 쓴다. |
| `catalog/PolicyDeadline` | `PolicyRecruitment`의 마감일·상태로 저장 정책 목록의 마감일과 알림 미제공 사유를 만든다. |

호출 측이 주입한 `Clock`에서 한 번 읽은 시각을 넘긴다. 같은 요청의 목록·상세·조건 결과는 그 시각을 공유하며, 시계의 기본 시간대 대신 명시적인 `Asia/Seoul`로 달력 날짜를 계산한다.

날짜형 `Dates`는 포함 의미가 확인된 양 끝 `LocalDate`를 받는다. 끝 날짜까지 달력 범위에 포함되며 정확한 접수 시간은 미확인임을 안내한다. 날짜를 `23:59:59`나 다음 날 자정의 마감 시각으로 변환하지 않는다.

시각형 `Times`는 시간대가 있는 `ZonedDateTime`을 받는다. 시작은 포함, 접수 종료 순간은 제외하는 명시적 입력이며 실제 순간을 기준으로 비교한다. 원문의 포함 경계·시간대 해석을 대신하는 생성자는 아니다. 시작·종료가 역전된 날짜와 비어 있거나 역전된 시각 범위는 거절한다.

신청기간 누락·충돌·복수 차수·시간대 미확인은 `Unresolved`에 이유와 원문을 남긴다. 사업기간을 입력받지 않으므로 사업 종료일로 신청 마감을 보충하지 않는다. 선착순·예산·인원 소진 안내도 `Unresolved`로 남겨 소진 완료 여부를 추정하지 않는다. 상시·명시적 마감은 확인되지 않은 날짜를 만들지 않는다.

모집 안내는 정책 모듈의 값이며 자격 판정 모듈이나 회원 데이터에 의존하지 않는다. `ApplicationPeriod`는 원천 DTO·REST 계약·DB 테이블이 아니며, 응답에는 계산한 `PolicyRecruitment`만 보낸다. 최신 개정 적용과 자료의 최신성 검사는 아직 없다.

마감일은 날짜형이면 종료 날짜, 시각형이면 마감 순간의 서울 날짜다. 이 값은 공개 응답의 `deadlineOnSeoul`에 쓰고, `MemberPolicyStore`가 회원 저장·개정 반영 때 같은 날짜로 D-7·D-3·D-1 예약을 만든다([회원 저장·알림](member-policy-flow.md#마감일과-서비스-내-알림)). 저장 정책 목록의 마감일은 조회 때 `PolicyDeadline.from(recruitment)`으로 다시 계산하고 따로 저장하지 않는다.

## 실행한 검증

저장소 루트에서 모집 기간만 검사한다. 지금은 `PolicyRecruitmentTest`를 실행한다.

```sh
npm run test:recruitment
```

2026-08-31에는 당시 자격 판정 테스트와 함께 `./backend/gradlew -p backend test --tests 'kr.youthpolicymate.policy.*' --tests 'kr.youthpolicymate.eligibility.*' assemble --no-daemon`도 실행했다(당시 자격 판정 단위 테스트는 2026-10-07 판정 집계 모델과 함께 삭제했다). 두 명령을 실행한 결과다. 모집 기간 23건, 기존 자격 판정 117건, 총 140건이 실패·건너뛰기 없이 통과했고 `assemble`도 통과했다.

- 날짜형 서울 자정 직전·정각, 시작일·종료일 포함, 같은 날 시작·종료
- 시각형 시작·마감 직전·정각·직후와 원문 시간대 보존
- 상시·소진 시 종료·명시적 마감의 날짜 경과 후 상태 유지(소진 시 종료 상태는 2026-10-10 삭제)
- 기간 누락·충돌·복수 차수·시간대 미확인 이유와 없는 발췌문 보존
- 정책 개정·근거·계산 시점 보존, 다른 시간대의 시계로 같은 결과
- 계산 중 자정이 넘어가더라도 시계를 한 번만 읽는 동작
- 역전된 날짜·시각과 같은 순간의 빈 시각 범위 거절

첫 시도는 Gradle 캐시의 잠금 파일 접근이 샌드박스에서 차단돼 테스트 전에 종료됐다. 같은 명령을 권한 확장으로 다시 실행해 통과했다. Mockito의 Java agent 동적 로딩과 JVM 클래스 공유 경고가 있었으며 경고를 숨기는 옵션은 추가하지 않았다.

웹·기존 PostgreSQL 통합 검사는 이번 작업에서 다시 실행하지 않았다. 실제 API 파싱·날짜 해석 정확도·모집 종료 수집·UI 연결·알림 예약·발송은 검증한 범위가 아니다. CI의 기존 전체 서버 `build` 명령에는 새 단위 테스트도 포함되지만 이번에 GitHub 실행·푸시는 하지 않았다.

## 다음 연결

마감 날짜 제공 테스트 8건을 더해 당시 `test:recruitment`는 31건을 실행했다. 함께 만든 알림 후보 날짜 모델은 회원 저장 예약과 규칙이 중복되어 2026-10-07 제거했다. 같은 날 운영 경로에서 쓰지 않는 메타데이터(정책 ID·개정·출처·발췌)만 감싸던 `RecruitmentSchedule`·`RecruitmentAssessment`도 삭제하고 계산을 `PolicyRecruitment.of`로 옮겼다. 위 검증 목록 중 메타데이터 보존·시계 한 번 읽기·시계 시간대 검사는 이때 삭제했다. 원문 기간은 `PolicyApplicationPeriod.parse`가 `aplyYmd`의 단일 `yyyyMMdd ~ yyyyMMdd` 범위로 해석하고, 검토한 공고는 `PolicyRecruitment.period`가 내용 해시를 고정해 보정한다. 선착순·소진·회차, 여러 날짜 언급, 형식 불일치는 `Unresolved`로 남긴다.
