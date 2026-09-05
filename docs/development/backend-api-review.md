# Java·Spring API 활용과 중복 검증 정리

2026-09-05. `104aa2b`에서 기록한 6개 검토 항목을 구현했다. 제품의 자격 판정·권한·동시성·알림 동작을 보존하면서 설정 코드와 중복 검증을 정리했다.

## 적용한 변경

| 대상 | 변경 내용 |
|---|---|
| [SMTP](../../backend/src/main/java/kr/youthpolicymate/member/SmtpMemberEmailSender.java) | Boot가 만든 메일 빈을 주입한다. 호스트·포트·인코딩·TLS·타임아웃을 `spring.mail.*` 설정으로 옮겼다. 사용자명에 따라 인증 사용 여부를 정하는 기존 조건만 코드에 남겼다. |
| [이메일 주소](../../backend/src/main/java/kr/youthpolicymate/member/MemberEmailAddress.java) | 자체 정규식을 제거하고 `@Email`·`@NotBlank`·`@Size(max = 254)`를 선언했다. HTTP 입력은 `@Valid`, 서비스 직접 호출과 발신 주소 설정은 `Validator.validateValue`로 같은 제약을 확인한다. |
| [OAuth 제공자 저장소](../../backend/src/main/java/kr/youthpolicymate/member/MemberConfiguration.java) | 내부 `Registrations` 클래스를 `InMemoryClientRegistrationRepository`로 교체했다. 변경 불가능한 LinkedHashMap으로 카카오·네이버 순서와 제공자가 없는 상태를 유지한다. |
| [재시도 정책](../../backend/src/main/java/kr/youthpolicymate/ingestion/AiReservationRecoveryRetryPolicy.java) | `List.copyOf` 이후의 null 원소 검사 3곳을 제거했다. 예약 ID·순번·완료 상태·재시도 간격 검사는 유지한다. |
| [임대 갱신 스케줄러](../../backend/src/main/java/kr/youthpolicymate/ingestion/PolicyAiRecoveryHeartbeat.java) | 호출되지 않는 `HeartbeatScheduler.scheduled`와 전용 import를 제거했다. 인터페이스와 관리형 스케줄러는 유지한다. |
| [정책 답변 검증](../../backend/src/main/java/kr/youthpolicymate/policy/catalog/PolicyQuestions.java) | `validatedAnswers`에 질문·선택지·중복 답변 검증을 모았다. 국가근로장학금·응시료 지원·K-패스·청년주택드림청약통장·서울청년정책네트워크 5개 규칙이 같은 함수를 사용한다. |

## 설정과 입력 형식

- 기존 `EMAIL_*` 환경변수를 계속 사용한다. 직접 지정한 `app.email.host/port/username/password`도 Boot 메일 설정에 연결한다. 발신 주소·기능 활성화·암호화 키는 서비스 설정으로 남긴다.
- 기본 비활성화, 587 포트, STARTTLS 필수, 서버 이름 검사, 연결·읽기·쓰기 5초 제한을 유지한다. SMTP 자동 설정으로 상태 확인 API가 새 외부 연결을 만들지 않도록 메일 상태 검사는 비활성화했다.
- 이메일 형식은 Jakarta Validation 구현체의 기준을 따른다. 기존 정규식이 허용하던 `.first@example.test`, `first..last@example.test`는 이제 거절한다. `first.last+tag@example.test`는 허용한다. 실제 주소 소유 확인과 수신 동의는 별도다.
- OpenAPI와 TypeScript를 서버 DTO에서 재생성했다. 계약 변경은 주소 필드의 `format: email` 추가이며 필드명·필수 여부·응답 구조는 그대로다.

## 유지한 검증

- 이메일 확인 실패 횟수는 트랜잭션에서 저장한 뒤 컨트롤러가 오류 응답을 만든다. 8자리 코드 검사도 이 흐름에 남겼다.
- 발송 직전 동의·저장 상태·개정 확인, DB 잠금·유일성 제약, 외부 전송 전후의 짧은 트랜잭션을 유지했다.
- 이메일 발송의 트랜잭션 상태 검사도 유지했다. 두 줄을 줄이려고 내부 호출 구조와 트랜잭션 계층을 늘리지 않았다.
- AI 임대 갱신의 종료 중 새 작업 거절·기존 갱신 유지·기한 이후 결과 거절을 유지했다.
- 정책별 자격·예외·안내 문구와 미응답의 추가 확인 처리는 바꾸지 않았다. 동적 선택지 검증은 `@Valid`만으로 대체하지 않았다.
- JDK HttpClient·Cipher·Mac을 사용하는 수집·암호화 코드와 인증키가 포함된 응답 저장 차단은 정리 대상에서 제외했다.

## 검증 결과 — 첫 6개 항목

- `npm run generate:api`: OpenAPI·TypeScript 생성 통과.
- `npm run verify -- check:backend`: 서버 전체 453건, 실패·오류·건너뜀 0, 빌드 통과. 공통 메일·보안 설정 변경을 포함해 한 번 실행했다.
- `npm run verify -- check:api-types`, `npm run verify -- check:web`: 생성 타입 일치·린트·타입 검사 통과.
- 기존 테스트를 활용하고 OAuth 설정·미설정, 이메일 주소의 HTTP·서비스 검증, SMTP 자동 설정 연결을 보완했다. 로컬 SMTP의 평문 전송 차단도 통과했다. 삭제한 null 분기를 반복하는 테스트는 추가하지 않았다.
- 실제 카카오·네이버 로그인과 외부 수신함 전달은 이번 검증 범위에 포함하지 않았다.

표준 API의 동작은 [Spring Boot 이메일 설정](https://docs.spring.io/spring-boot/reference/io/email.html), [Spring Security OAuth 설정](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html), [Jakarta Email 제약](https://jakarta.ee/specifications/bean-validation/3.1/apidocs/jakarta/validation/constraints/email), [JDK List.copyOf](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection))를 확인했다. OAuth Map 생성자의 빈 설정 지원은 로컬 Spring Security 7.1.1 바이트코드와 애플리케이션 테스트에서도 확인했다.

## 추가 정리 — 2026-09-06 적용

`d3ace4e`에서 검토한 추가 4개 항목을 구현했다.

| 대상 | 적용 내용 |
|---|---|
| [조건 확인 조회](../../backend/src/main/java/kr/youthpolicymate/policy/catalog/PolicyCatalogStore.java) | `listForCheck`에서 정책·현재 개정·원문을 JOIN한다. 20건에 SELECT 42회를 호출하던 흐름을 개수·페이지 조회 2회로 줄였다. 페이지 계산은 Spring Data의 `Page`·`PageRequest`를 사용한다. |
| [고정 상태용 객체](../../backend/src/main/java/kr/youthpolicymate/policy/catalog/PolicyCheckService.java) | 항상 같은 값을 얻던 판정 객체 생성을 제거하고 `NEEDS_REVIEW`를 직접 반환한다. 응답의 원문·설명·정책 개정과 실제 정책별 판정 규칙은 유지한다. |
| [AI 종료 상태](../../backend/src/main/java/kr/youthpolicymate/ingestion/AiBudgetReservationState.java) | 6개 클래스의 같은 상태 비교를 `Phase.isTerminal()`로 모았다. 종료 상태의 종류와 상태 전이 조건은 그대로다. |
| [DB 시각 처리](../../backend/src/main/java/kr/youthpolicymate/ingestion/AiDatabaseTime.java) | 7곳의 변환과 6곳의 비교 함수를 수집 패키지 내부의 `AiDatabaseTime`으로 모았다. 마이크로초 절삭·UTC 변환·시각 비교 방식은 그대로다. |

정책 목록의 정렬·페이지·질문 제공 여부와 읽기 일관성을 유지했다. LEFT JOIN 결과에서 현재 개정의 원문이 없으면 기존처럼 오류로 처리하며 정책을 조용히 제외하지 않는다. JSON 본문을 목록과 상세에서 두 번 읽던 처리도 한 번으로 줄었다. 변경 전 42회는 호출 코드로 계산했고, 변경 후 2회는 PostgreSQL 통합 테스트에서 `JdbcClient.sql` 호출 횟수로 확인했다. 응답 시간 개선율을 측정한 것은 아니다.

관련 테스트 188건이 실패·오류·건너뜀 없이 통과했다. 21개 정책과 과거·현재 개정의 원문을 저장한 테스트에서 첫 페이지·마지막 페이지·빈 페이지 각각의 조회 2회, 정렬, 전체 개수, 최신 원문을 확인했다. 기존 API 계약 일치·회원 흐름·AI 예약·복구·재전달 테스트도 함께 통과했다.

실행 명령은 다음과 같다. 공통 설정·프런트엔드·외부 API 계약은 변경하지 않아 전체 서버·웹 검사를 반복하지 않았다.

```sh
npm run verify -- test:ai-recovery-policy -- \
  --tests 'kr.youthpolicymate.ingestion.Ai*Test' \
  --tests 'kr.youthpolicymate.ingestion.PolicyAi*Test' \
  --tests 'kr.youthpolicymate.policy.catalog.PolicyCatalogTest' \
  --tests 'kr.youthpolicymate.member.MemberFlowTest'
```

## 추가 검토 — 2026-09-06, 미적용

`96b1bc6`에서 기존 10개 항목을 제외하고 호출 경로와 설계 문서를 확인했다. 조회 개선 2개와 설계 정리 후보 1개가 남아 있다.

| 우선순위 | 대상과 현재 문제 | 정리 방향 |
|---|---|---|
| 높음 | [관심 정책 목록](../../backend/src/main/java/kr/youthpolicymate/member/MemberPolicyStore.java)의 `saved → refresh`는 정책마다 잠금·상세·저장 개정을 따로 조회한다. 변경이 없는 20건에도 SELECT 63회가 필요하고 본문을 갱신 확인과 목록 표시에서 각각 변환한다. | 저장 개정·현재 개정·표시 필드를 묶어 조회하고, 개정이 달라진 정책만 원문을 읽어 일정과 알림을 갱신한다. 회원 잠금, 정책 번호순 잠금, 최신 개정 확인은 유지한다. |
| 중간 | [질문 조회](../../backend/src/main/java/kr/youthpolicymate/policy/catalog/PolicyQuestionService.java)의 `questionsAt`은 `find`와 `contentHash`로 같은 정책을 두 번 읽는다. 전체 본문·수집 시각을 변환하지만 실제로는 개정·해시·출처 주소만 쓴다. 답변 평가도 같은 경로를 호출한다. | 개정·해시를 한 번에 읽는 내부 조회 결과를 사용한다. 출처 주소는 기존 형식으로 만들고, 미존재·미공개 정책 거절과 질문 제공 기간·해시 검사를 유지한다. SELECT를 2회에서 1회로 줄일 수 있다. |
| 낮음·설계 검토 | [AI 예약 상태 모델](../../backend/src/main/java/kr/youthpolicymate/ingestion/AiBudgetReservationState.java)의 `open`과 인스턴스 상태 전이는 전용 테스트에서만 실행된다. [예약 저장소](../../backend/src/main/java/kr/youthpolicymate/ingestion/AiBudgetReservationStore.java)와 [후속 상태 저장소](../../backend/src/main/java/kr/youthpolicymate/ingestion/AiBudgetReservationLifecycleStore.java)가 예약·정산 규칙을 별도로 구현한다. | 현재 설계가 순수 모델과 DB 구현을 모두 명시하므로 삭제 전에 기준을 정리한다. DB 구현을 기준으로 테스트 범위를 대조한 뒤 미사용 상태 엔진을 제거하거나, 실제로 공유할 전이 규칙을 분리하는 방향을 검토한다. 운영 코드가 쓰는 `Phase`·이벤트 값 타입은 보존한다. |

관심 정책 SELECT 수는 변경이 없는 N건에서 회원 잠금 1회 + 정책 번호 목록 1회 + 정책별 3회 + 최종 목록 1회, 즉 `3N + 3`으로 계산했다. 코드의 호출 횟수이며 실행 시간이나 부하를 측정한 결과는 아니다. 개선 후 조회 횟수는 구현과 통합 테스트에서 확인해야 한다.

짧은 `requireText` 같은 검증 함수를 전부 공통화하는 작업은 제외했다. 새 공통 계층을 추가할 만큼 이득이 크지 않다. DB 잠금 뒤 상태 확인과 발송 직전 동의·개정 확인도 유지 대상이다.

이번에는 코드·테스트·설정 변경 없이 검토 내용과 인계 문서만 수정했다. `npm run verify -- status`에서 최근 관련 검사 이후 문서 2개만 달라진 것을 확인했으며 앱 테스트는 반복하지 않았다.
