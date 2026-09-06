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

## 반복 조회·미사용 모델 정리 — 2026-09-06 적용

`7f5182d`에서 검토한 3개 항목을 구현했다. 조회 개선은 `b4a09e7`, AI 예약 정리는 `8cae9f6`에 커밋했다.

| 대상 | 적용 내용 |
|---|---|
| [관심 정책 목록](../../backend/src/main/java/kr/youthpolicymate/member/MemberPolicyStore.java) | 회원 잠금 뒤 저장 개정·현재 개정·알림 제목을 JOIN하고 정책 번호순으로 `FOR SHARE OF p` 잠금을 잡는다. 변경된 정책만 원문을 읽어 일정·알림을 갱신한다. 최종 목록은 제목·신청 기간만 추출해 본문 전체를 Java 객체로 변환하지 않는다. |
| [질문 조회·평가](../../backend/src/main/java/kr/youthpolicymate/policy/catalog/PolicyQuestionService.java) | 내부 `QuestionVersion`으로 개정·해시를 한 번에 조회한다. 본문·수집 시각 변환을 제거했다. 출처 주소, 미존재·미공개 정책 거절, 기간·해시·답변 버전 검사는 유지한다. |
| [AI 예약 상태](../../backend/src/main/java/kr/youthpolicymate/ingestion/AiBudgetReservationState.java) | 테스트에서만 실행하던 예약 목록·상태 엔진을 제거하고 `Phase`·이벤트 값 타입을 유지했다. [설계](../design/ai-budget-reservation-lifecycle.md)를 DB 저장소 기준으로 정리했다. 순수 모델에만 있던 시간 경계·잘못된 호출 순서 검사를 DB 테스트로 옮기고 종료 결과 충돌 검사를 보완했다. |

변경이 없는 관심 정책 N건의 SELECT는 기존 `3N + 3`회에서 3회로 줄었다. PostgreSQL 통합 테스트에서 20건과 빈 목록 모두 3회, 목록 정렬·표시 필드·회원 분리를 확인했다. 별도 트랜잭션의 정책 갱신이 조회 트랜잭션이 끝날 때까지 차단되는 것도 확인했다. 기존 마감 변경·알림 취소·중복 발송·Outbox 검사는 그대로 통과했다.

질문 조회와 답변 평가는 각각 SELECT 2회에서 1회로 줄었고 호출 횟수를 통합 테스트에서 확인했다. 응답 시간이나 부하 개선율을 측정한 것은 아니다.

`test:ingestion`에서 삭제한 메모리 모델 검사를 제외했다. `test:ai-reservations`는 `test:ai-reservation-db`를 실행하는 별칭이며 Docker가 필요하다. 두 명령을 연달아 실행할 필요는 없다. 짧은 검증 함수, 발송 직전 동의·개정 검사, DB 잠금·유일성 제약은 유지했다.

### 검증 기록

Temurin 25.0.3을 `JAVA_HOME`으로 지정해 다음 범위를 실행했다.

```sh
npm run verify -- test:ingestion -- \
  --tests 'kr.youthpolicymate.ingestion.Ai*Test' \
  --tests 'kr.youthpolicymate.ingestion.PolicyAi*Test' \
  --tests 'kr.youthpolicymate.policy.catalog.PolicyCatalogTest' \
  --tests 'kr.youthpolicymate.member.MemberFlowTest'
```

- 192건 중 191건 통과. 이관한 AI 테스트 1건에서 `IllegalArgumentException`을 기대했으나 Spring 저장소의 예외 변환으로 `InvalidDataAccessApiUsageException`이 발생했다. 거절 동작은 정상이며 테스트 기대값을 원인 예외까지 확인하도록 수정했다.
- `npm run verify -- test:ai-reservations`로 영향받은 DB 테스트 14건을 다시 실행해 실패·오류·건너뜀 없이 통과했다. 앞서 통과한 나머지 178건의 코드·테스트는 변경하지 않아 재사용했다.
- 로그: `.local/verification/1788656647057-b7d789e9.log`, `.local/verification/1788656736335-c7602d24.log`. 두 실행을 합쳐 관련 192건을 확인했다. `verify status`에는 첫 실행의 실패 이력이 남아 있으므로 위 재실행 범위와 함께 판단한다.
- 검증 후 앱 코드는 `8cae9f6`과 동일하며 후속 변경은 문서뿐이다. API 계약 일치 검사는 통과했고 외부 계약·프런트엔드·공통 설정을 바꾸지 않아 API 재생성·전체 서버 빌드·웹 검사는 반복하지 않았다.

## 수집 조회 정리 — 2026-09-06 적용

`31a9126`에서 검토한 2개 항목을 `22a8120`에 구현했다. 새 라이브러리 없이 기존 JDBC 조회를 정리했다.

| 대상 | 적용 내용 |
|---|---|
| [수집 페이지 조회](../../backend/src/main/java/kr/youthpolicymate/ingestion/OntongCollectionStore.java) | 상태 확인은 내부 `PageStatus`로 페이지 번호·상태·원문 존재 여부·오류·건수만 읽는다. 원문 조회의 `Page`에서도 사용하지 않는 상태 필드를 제거했다. 정상 재처리에서 원문은 실제 해석 단계인 `applyStored`만 읽어 3회에서 1회로 줄었다. |
| 발송 시작 | `startDispatch`의 첫 `FOR UPDATE` 조회에서 예약 실행 ID를 읽는다. `JdbcClient`의 `singleRow`로 행 존재를 확인하고 예약 ID가 없는 상태도 처리한다. 한도 설정 경로에서 해당 SELECT는 2회에서 1회로 줄었다. |

저장 원본 재처리·부분 실패·현재 상태 확인과 예약 교체 거절·발송 시작 중복 차단·호출 간격 갱신은 유지했다. 첫 항목은 원문을 가져오는 횟수의 개선이며 전체 SELECT 수를 3회에서 1회로 줄인다는 뜻은 아니다. 실행 시간·전송량 개선율은 측정하지 않았다.

Temurin 25.0.3을 사용해 `npm run verify -- test:policy-collection`을 실행했다. 수집 HTTP 클라이언트·원문 변환·페이지 수집·범위 재처리·스케줄러·정책 조회와 API 계약 검사 46건이 실패·오류·건너뜀 없이 통과했다. 기존 `OntongSweepTest`에서 원문 조회 함수 1회와 제어 행 SELECT 1회를 확인했다. 별도 테스트 사례를 추가하지 않고 기존 재처리·예약 교체 사례를 보완했다.

로그는 `.local/verification/1788658608359-66a5a149.log`다. 검증한 앱 코드는 `22a8120`이며 이후 변경은 문서뿐이다. 수집 내부 조회만 변경해 전체 서버 빌드·웹 검사·실제 온통청년 호출은 실행하지 않았다.

## 미사용 수집 모델 검토 — 2026-09-06, 미적용

`950311f`에서 추가로 확인한 정리 대상은 초기 메모리 수집 모델 1개다. [CollectionRun](../../backend/src/main/java/kr/youthpolicymate/ingestion/CollectionRun.java)·[CollectionAttempt](../../backend/src/main/java/kr/youthpolicymate/ingestion/CollectionAttempt.java)·[CollectionPosition](../../backend/src/main/java/kr/youthpolicymate/ingestion/CollectionPosition.java)는 서로를 참조하지만 다른 실행 코드에서는 사용하지 않는다. 앱 코드 329줄이며 유일한 외부 사용처는 전용 `CollectionRunTest` 260줄이다. `test:ingestion`도 이 모델의 검사를 계속 실행한다.

[당시 설계](../design/collection-run-progress.md)는 API 연결 전의 진행·중단·재개 모델을 정의한다. 실제 수집은 이후 추가한 Spring Batch 작업과 `OntongCollectionStore`·`OntongSweepStore`로 실행한다. 두 구현의 상태·재시도 방식이 완전히 같지는 않으므로 초기 모델의 테스트를 현재 수집 검증으로 볼 수 없다.

정리 방향은 세 모델 파일과 전용 테스트를 제거하고, 현재 수집 동작에 필요한 사례만 기존 `OntongCollectionTest`·`OntongSweepTest`와 대조해 보완하는 것이다. 페이지 커서 순환처럼 현재 숫자 페이지 수집에 해당하지 않는 가상 사례까지 옮기지는 않는다. 설계·README·검증 명령에서도 이전 모델과 실제 구현의 관계를 정리해야 한다. 우선순위는 중간이며, 실행 성능보다 유지보수 범위를 줄이는 작업이다.

이번에는 사용처·호출 경로·기준 문서를 확인하고 검토·인계 문서만 변경했다. 앱 코드와 테스트는 수정하거나 실행하지 않았다.
