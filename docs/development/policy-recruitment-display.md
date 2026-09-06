# 정책 접수 상태 표시

2026-09-06 기준. 공개 목록·상세·내 조건 결과에서 같은 서버 접수 상태와 설명을 표시한다. 개인 자격이나 연령 정렬은 바꾸지 않는다.

## 계산과 근거

- 기존 `RecruitmentAssessment`와 `ApplicationPeriod`를 사용한다. 날짜만 있으면 서울 날짜 기준으로 시작일·종료일을 포함하며, 정확한 접수 시각은 미확인이라고 안내한다.
- `PolicyApplicationPeriod`는 기존 마감일 추출 코드를 공유하도록 분리한 원문 해석기다. 온통청년의 신청기간 코드·단일 날짜 구간·추가 안내를 읽으며, 날짜 형식은 Java의 `BASIC_ISO_DATE`로 엄격하게 해석한다.
- 코드·날짜 안내가 함께 있어 의미가 충돌하거나, 잘못된 날짜·역전 구간·복수 회차·선착순·소진·본문의 다른 날짜가 있으면 미확인으로 남긴다. 날짜가 없는 상시·명시적 마감은 별도 상태다. 사업기간을 신청기간으로 대신 쓰지 않는다.
- [서울청년정책네트워크](seoul-youth-network-questions.md)와 [상반기 이사비 지원](moving-fee-questions.md)은 검토한 정책번호·원문 해시가 일치할 때만 기존 규칙의 정확한 접수 시각을 적용한다. 원문이 바뀌면 이전 시각을 중단한다. 해당 공고의 과거 마감 사실은 연도가 바뀌어도 유지하며 새 모집에 적용하지 않는다.
- `PolicyDeadline`도 같은 해석기를 사용한다. 시각을 새로 만들거나 상시·미확인 자료에 마감 날짜를 추가하지 않는다. 기존 알림 예약·발송 로직은 유지한다.

## API와 화면

`PolicySummary`, `PolicyDetailResponse`, `PolicyCheckItem`에 `recruitment`를 추가했다. 상태·설명·평가 시각을 제공한다. 목록·개인 조건 결과는 한 요청의 시각을 공유한다. 공개 목록은 건수·페이지 조회 두 번, 상세는 한 번으로 처리하며 목록에는 기간 해석에 필요한 원문 필드만 가져온다.

화면은 생성 TypeScript 타입의 공통 컴포넌트를 사용한다. `OPEN`은 ‘접수 기간’으로 표시해 실제 신청 가능·자격 충족으로 오인하지 않게 한다. 다른 상태는 ‘접수 전·마감·상시·소진 시 마감·기간 미확인’이다. 기존 신청기간 원문과 공식 안내 링크를 함께 남긴다. 공개 페이지와 조건 조회의 기존 `no-store`를 유지한다. 화면을 오래 열어둔 동안 상태를 자동 갱신하지는 않는다.

## 검증

최종 코드 리비전 `811d400`을 아래 범위로 확인했다. 이후 변경은 문서뿐이다.

- `npm run generate:api`: 서버 DTO에서 OpenAPI·TypeScript 생성 완료. `/tmp/youth-recruitment-api.log`.
- `npm run verify -- test:policy-catalog -- --tests kr.youthpolicymate.policy.catalog.PolicyRecruitmentTest --tests kr.youthpolicymate.policy.catalog.PolicyDeadlineTest --tests 'kr.youthpolicymate.policy.Recruitment*Test' --tests kr.youthpolicymate.policy.catalog.SeoulYouthNetworkRulesTest --tests kr.youthpolicymate.policy.catalog.MovingFeeRulesTest --tests kr.youthpolicymate.member.MemberFlowTest`: 통과. 기존 목록·수집 원문·계약 검사와 모집 상태·마감·회원 저장/알림 흐름을 함께 확인했다. `.local/verification/1788703051063-62e78ae7.log`.
- `npm run verify -- test:web -- src/app/policies src/features/policies src/features/conditions`: 통과. `.local/verification/1788703047098-c85e39c4.log`.
- `npm run verify -- check:web`: 린트·타입 검사 통과. `.local/verification/1788703047098-8d32b0bf.log`.
- `npm run verify -- check:api-types`: 생성 계약 일치. `.local/verification/1788703047098-7096cca0.log`.
- `npm run verify -- build:web`: 웹 운영 빌드 통과. `.local/verification/1788703173722-1e63776c.log`.
- `npm run verify -- package:backend`: 서버 실행 파일 생성. 테스트를 반복하지 않았다. `.local/verification/1788703154708-c5654de5.log`.

실제 로컬 수집 40건은 확인 시점에 접수 기간 8건·마감 15건·상시 14건·미확인 3건으로 표시됐다. 이는 수집한 기간 기준이며 실제 접수창 운영이나 전체 청년정책의 현황을 뜻하지 않는다.

브라우저에서 목록과 국가근로장학금 상세의 접수 상태·설명 일치를 확인했다. 검증용 조건으로 내 조건 결과의 연령 충족/최종 자격/접수 마감 분리, Enter 제출·검색과 결과 제목 초점을 확인했다. PC·모바일에서 표시를 확인했으며 모바일 문서 너비와 화면 너비가 같았다. 접수 전·윤년·서울 자정·정확한 마감 시각·원문 변경·불명확 기간은 서버 테스트로 확인했다. 검증용 입력을 지우고 화면 크기 변경을 해제했다.

전체 서버 검사·외부 로그인·SMTP 전달·AI 호출·정기 수집은 이번 변경 범위에 없어 실행하지 않았다. DB 스키마와 `.env`를 변경하지 않았다.

## 로컬 반영

웹은 기존 127.0.0.1:3000 개발 서버를 사용했다. 백엔드는 확인한 이전 프로세스를 정상 종료하고 새 실행 파일로 127.0.0.1:8080에 시작했다. 현재 서버 로그는 `/tmp/youth-recruitment-backend.log`다.

`backend`에서 JDK `/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`의 `bin/java -jar build/libs/youth-policy-mate-0.0.1-SNAPSHOT.jar --spring.profiles.active=local`로 실행했다. `REMINDERS_ENABLED=false EMAIL_ENABLED=false ONTONG_COLLECTION_SCHEDULE_ENABLED=false`를 지정했다. 다음 작업에서 프로세스 상태를 확인하고 같은 코드의 검증을 반복하지 않는다.
