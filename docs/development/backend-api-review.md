# Java·Spring API 활용과 중복 검증 정리

2026-09-05. `104aa2b`에서 기록한 6개 검토 항목을 구현했다. 제품의 자격 판정·권한·동시성·알림 동작을 보존하면서 설정 코드와 중복 검증을 정리했다.

## 적용한 변경

| 대상 | 변경 내용 |
|---|---|
| SMTP 어댑터(`SmtpMemberEmailSender`, 2026-10-07 Resend 단일화로 제거) | Boot가 만든 메일 빈을 주입한다. 호스트·포트·인코딩·TLS·타임아웃을 `spring.mail.*` 설정으로 옮겼다. 사용자명에 따라 인증 사용 여부를 정하는 기존 조건만 코드에 남겼다. |
| [이메일 주소](../../backend/src/main/java/kr/youthpolicymate/member/MemberEmailAddress.java) | 자체 정규식을 제거하고 `@Email`·`@NotBlank`·`@Size(max = 254)`를 선언했다. HTTP 입력은 `@Valid`로 확인한다. 2026-10-07부터 서비스 직접 호출의 중복 재검증을 없앴고, 발신 주소는 `EmailProperties`의 `@Email`·`@Size`로 기동 시 검증한다. |
| [OAuth 제공자 저장소](../../backend/src/main/java/kr/youthpolicymate/member/MemberConfiguration.java) | 내부 `Registrations` 클래스를 `InMemoryClientRegistrationRepository`로 교체했다. 변경 불가능한 LinkedHashMap으로 카카오·네이버 순서와 제공자가 없는 상태를 유지한다. |
| 재시도 정책(`AiReservationRecoveryRetryPolicy`, 2026-10-05 제거) | `List.copyOf` 이후의 null 원소 검사 3곳을 제거했다. 예약 ID·순번·완료 상태·재시도 간격 검사는 유지한다. |
| 임대 갱신 스케줄러(`PolicyAiRecoveryHeartbeat`, 2026-10-05 제거) | 호출되지 않는 `HeartbeatScheduler.scheduled`와 전용 import를 제거했다. 인터페이스와 관리형 스케줄러는 유지한다. |
| [정책 답변 검증](../../backend/src/main/java/kr/youthpolicymate/policy/catalog/PolicyRuleDefinition.java) | `validatedAnswers`에 질문·선택지·중복 답변 검증을 모았다. 모든 공고 규칙이 판정표를 실행할 때 같은 검사를 사용한다. |

## 설정과 입력 형식

- 기존 `EMAIL_*` 환경변수를 계속 사용한다. 발신 주소·기능 활성화·암호화 키는 서비스 설정으로 남긴다. 2026-10-07에 SMTP 설정(`spring.mail.*`, `EMAIL_SMTP_*`, 메일 상태 검사 비활성화)을 어댑터와 함께 삭제하고 `app.email.*`를 `EmailProperties` 하나로 바인딩했다.
- 이메일 형식은 Jakarta Validation 구현체의 기준을 따른다. 기존 정규식이 허용하던 `.first@example.test`, `first..last@example.test`는 이제 거절한다. `first.last+tag@example.test`는 허용한다. 실제 주소 소유 확인과 수신 동의는 별도다.
- OpenAPI와 TypeScript를 서버 DTO에서 재생성했다. 계약 변경은 주소 필드의 `format: email` 추가이며 필드명·필수 여부·응답 구조는 그대로다.

## 유지한 검증

- 이메일 확인 실패 횟수는 트랜잭션에서 저장한 뒤 컨트롤러가 오류 응답을 만든다. 8자리 코드 검사도 이 흐름에 남겼다.
- 발송 직전 동의·저장 상태·개정 확인, DB 잠금·유일성 제약, 외부 전송 전후의 짧은 트랜잭션을 유지했다.
- 이메일 발송의 트랜잭션 상태 검사도 유지했다. 두 줄을 줄이려고 내부 호출 구조와 트랜잭션 계층을 늘리지 않았다.
- AI 임대 갱신의 종료 중 새 작업 거절·기존 갱신 유지·기한 이후 결과 거절을 유지했다.
- 정책별 자격·예외·안내 문구와 미응답의 추가 확인 처리는 바꾸지 않았다. 동적 선택지 검증은 `@Valid`만으로 대체하지 않았다.
- Cipher·Mac을 사용하는 암호화 코드는 정리 대상에서 제외했다. 수집 클라이언트는 이후 관측 없는 `RestClient`와 JDK HTTP 팩토리로 바꾸고 전체 20초·1MiB·리다이렉트 금지·인증키 반사 차단을 유지했다.

## 검증 결과 — 첫 6개 항목

- `npm run generate:api`: OpenAPI·TypeScript 생성 통과.
- `npm run verify -- check:backend`: 서버 전체 453건, 실패·오류·건너뜀 0, 빌드 통과. 공통 메일·보안 설정 변경을 포함해 한 번 실행했다.
- `npm run verify -- check:api-types`, `npm run verify -- check:web`: 생성 타입 일치·린트·타입 검사 통과.
- 기존 테스트를 활용하고 OAuth 설정·미설정, 이메일 주소의 HTTP·서비스 검증, SMTP 자동 설정 연결을 보완했다. 로컬 SMTP의 평문 전송 차단도 통과했다(SMTP 검사는 2026-10-07 어댑터와 함께 삭제). 삭제한 null 분기를 반복하는 테스트는 추가하지 않았다.
- 실제 카카오·네이버 로그인과 외부 수신함 전달은 이번 검증 범위에 포함하지 않았다.

표준 API의 동작은 [Spring Boot 이메일 설정](https://docs.spring.io/spring-boot/reference/io/email.html), [Spring Security OAuth 설정](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html), [Jakarta Email 제약](https://jakarta.ee/specifications/bean-validation/3.1/apidocs/jakarta/validation/constraints/email), [JDK List.copyOf](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection))를 확인했다. OAuth Map 생성자의 빈 설정 지원은 로컬 Spring Security 7.1.1 바이트코드와 애플리케이션 테스트에서도 확인했다.

## 추가 정리 — 2026-09-06 적용

`d3ace4e`에서 검토한 추가 4개 항목을 구현했다.

| 대상 | 적용 내용 |
|---|---|
| [조건 확인 조회](../../backend/src/main/java/kr/youthpolicymate/policy/catalog/PolicyCatalogStore.java) | `listForCheck`에서 정책·현재 개정·원문을 JOIN한다. 20건에 SELECT 42회를 호출하던 흐름을 개수·페이지 조회 2회로 줄였다. 페이지 계산은 `LIMIT`/`OFFSET`과 개수 조회로 한다. |
| [고정 상태용 객체](../../backend/src/main/java/kr/youthpolicymate/policy/catalog/PolicyCheckService.java) | 항상 같은 값을 얻던 판정 객체 생성을 제거하고 `NEEDS_REVIEW`를 직접 반환한다. 응답의 원문·설명·정책 개정과 실제 정책별 판정 규칙은 유지한다. |
| AI 종료 상태(`AiBudgetReservationState`) | 6개 클래스의 같은 상태 비교를 `Phase.isTerminal()`로 모았다. 종료 상태의 종류와 상태 전이 조건은 그대로다. |
| DB 시각 처리(`AiDatabaseTime`) | 7곳의 변환과 6곳의 비교 함수를 수집 패키지 내부의 `AiDatabaseTime`으로 모았다. 마이크로초 절삭·UTC 변환·시각 비교 방식은 그대로다. |

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
| AI 예약 상태(`AiBudgetReservationState`) | 테스트에서만 실행하던 예약 목록·상태 엔진을 제거하고 `Phase`·이벤트 값 타입을 유지했다. [설계](../design/ai-budget-reservation-lifecycle.md)를 DB 저장소 기준으로 정리했다. 순수 모델에만 있던 시간 경계·잘못된 호출 순서 검사를 DB 테스트로 옮기고 종료 결과 충돌 검사를 보완했다. |

변경이 없는 관심 정책 N건의 SELECT는 기존 `3N + 3`회에서 3회로 줄었다. PostgreSQL 통합 테스트에서 20건과 빈 목록 모두 3회, 목록 정렬·표시 필드·회원 분리를 확인했다. 별도 트랜잭션의 정책 갱신이 조회 트랜잭션이 끝날 때까지 차단되는 것도 확인했다. 기존 마감 변경·알림 취소·중복 발송·Outbox 검사는 그대로 통과했다.

질문 조회와 답변 평가는 각각 SELECT 2회에서 1회로 줄었고 호출 횟수를 통합 테스트에서 확인했다. 응답 시간이나 부하 개선율을 측정한 것은 아니다.

`test:ingestion`에서 삭제한 메모리 모델 검사를 제외했다. `test:ai-reservations`는 `test:ai-reservation-db`를 실행하는 별칭이며 Docker가 필요하다. 두 명령을 연달아 실행할 필요는 없다. 별칭은 2026-10-05에 삭제했으며 같은 검사는 `test:ai-reservation-db`로 실행한다. 짧은 검증 함수, 발송 직전 동의·개정 검사, DB 잠금·유일성 제약은 유지했다.

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
| 발송 시작 | 당시 `startDispatch`의 첫 제어 행 `FOR UPDATE` 조회에서 예약 실행 ID를 읽는다. `JdbcClient`의 `singleRow`로 행 존재를 확인하고 예약 ID가 없는 상태도 처리한다. 한도 설정 경로에서 해당 SELECT는 2회에서 1회로 줄었다. |

저장 원본 재처리·부분 실패·현재 상태 확인과 예약 교체 거절·발송 시작 중복 차단·호출 간격 갱신은 유지했다. 첫 항목은 원문을 가져오는 횟수의 개선이며 전체 SELECT 수를 3회에서 1회로 줄인다는 뜻은 아니다. 실행 시간·전송량 개선율은 측정하지 않았다.

Temurin 25.0.3을 사용해 `npm run verify -- test:policy-collection`을 실행했다. 수집 HTTP 클라이언트·원문 변환·페이지 수집·범위 재처리·스케줄러·정책 조회와 API 계약 검사 46건이 실패·오류·건너뜀 없이 통과했다. 기존 `OntongSweepTest`에서 원문 조회 함수 1회와 당시 제어 행 SELECT 1회를 확인했다. 제어 행은 이후 advisory 잠금과 페이지 기록 조회로 바뀌어 지금은 발송 시작의 잠금·순번 확인 SELECT 2회를 확인한다. 별도 테스트 사례를 추가하지 않고 기존 재처리·예약 교체 사례를 보완했다.

로그는 `.local/verification/1788658608359-66a5a149.log`다. 검증한 앱 코드는 `22a8120`이며 이후 변경은 문서뿐이다. 수집 내부 조회만 변경해 전체 서버 빌드·웹 검사·실제 온통청년 호출은 실행하지 않았다.

## 미사용 수집 모델 정리 — 2026-09-06 적용

`1b1a207`에서 검토한 초기 메모리 수집 모델을 `211307b`에서 제거했다. `CollectionRun`·`CollectionAttempt`·`CollectionPosition` 세 파일과 전용 `CollectionRunTest`를 삭제했다. 다른 실행 코드에서는 참조하지 않았으며 실제 Spring Batch·DB 수집 코드는 변경하지 않았다.

초기 모델의 설계·개발 문서도 삭제하고 연결을 [한 페이지 수집](limited-policy-collection.md)과 [범위 수집](policy-range-collection.md)으로 정리했다. `test:ingestion`은 당시 AI 후보·결과 연결·사전 판단·복구 재확인 정책만 검사했다(2026-10-05 대상 코드와 함께 삭제). 실제 수집 검사는 `test:policy-collection`을 사용한다.

초기 모델의 검증은 다음 기준으로 정리했다.

| 확인할 동작 | 현재 수집 검증 |
|---|---|
| 중복 실행·빈 페이지·범위 종료·부분 실패·재처리 | 기존 `OntongCollectionTest`·`OntongSweepTest`가 확인한다. |
| 늦은 이전 응답·동시 재처리·원본 없는 실패의 재호출 차단 | 요청 순번·항목 잠금·저장 원본을 사용하는 기존 DB 검사를 유지한다. |
| 확정 원문 보존·같은 페이지의 중복 정책 번호 | 기존 실행 이력 테스트에 원문 덮어쓰기 거절을 추가하고, 중복 번호 항목만 보류하며 정상 항목을 반영하는 DB 사례를 추가했다. |

가상 페이지 커서 순환·메모리 이력 목록의 불변성 등 삭제한 모델에만 필요한 검사는 이관하지 않았다. 실제 수집의 상태·재시도 규칙을 초기 모델에 맞춰 바꾸지도 않았다.

Temurin 25.0.3에서 다음 명령으로 52건이 실패·오류·건너뜀 없이 통과했다.

```sh
npm run verify -- test:ingestion -- --tests 'kr.youthpolicymate.ingestion.OntongCollectionTest'
```

로그는 `.local/verification/1788663376550-bb48e6d9.log`다. 변경한 검증 명령과 실제 수집 DB 사례를 실행했으며 삭제 타입의 코드·빌드 설정 참조가 없음을 확인했다. 변경하지 않은 범위 수집·HTTP·파서·스케줄러·정책 조회는 이전 `22a8120` 검증 결과를 재사용했다. 전체 서버 빌드·웹·실제 온통청년 호출은 실행하지 않았다. 검증 후 앱 코드는 `211307b`와 같으며 후속 변경은 문서뿐이다.

## 목록 조회·수집 저장 정리 — 2026-09-06 적용

`e1b297c`에서 검토한 2개 항목을 `62aff07`에서 구현했다. 앱 응답 시간·전송량 개선 폭은 측정하지 않았다.

| 대상 | 적용 내용 |
|---|---|
| [정책 목록](../../backend/src/main/java/kr/youthpolicymate/policy/catalog/PolicyCatalogStore.java)의 `list` | 본문 전체 대신 제목·설명·분류·기관·신청 기간 5개 JSON 필드를 SQL에서 추출해 `PolicySummary`에 매핑한다. 상세 문단·링크·지역 코드의 조회와 `PolicyContent` 변환을 제거했다. SELECT 2회와 정렬·검색·질문 제공 여부·페이지 계약은 유지했다. |
| [수집 항목 준비](../../backend/src/main/java/kr/youthpolicymate/ingestion/OntongCollectionStore.java)의 `prepare` | 항목별 `JdbcClient.update()`를 `NamedParameterJdbcTemplate.batchUpdate` 호출로 묶었다. 페이지 잠금과 항목 저장·READY 전환의 단일 트랜잭션은 유지했다. 페이지당 최대 10건이므로 개선 범위는 작다. |

Spring은 일괄 갱신에 [JDBC 배치 API](https://docs.spring.io/spring-framework/reference/data-access/jdbc/advanced.html)를 제공한다. 배치 호출로 묶어도 저장하는 행과 INSERT 작업 수가 줄어드는 것은 아니며, 여기서는 개별 실행 호출을 줄이는 개선이다. 항목별 정책 반영·재처리 트랜잭션까지 한 배치로 합치지는 않는다.

다음 후보는 정리 대상에서 제외했다.

- 같은 내용 해시의 본문 갱신: `OntongPolicyCapture`는 `lastMdfcnDt`를 해시에서 제외하지만 `PolicyContent.sourceModifiedAtText`에는 담는다. 해시가 같다는 이유로 갱신을 생략하면 표시 정보가 오래된 값으로 남을 수 있다.
- 항목마다 페이지를 훑는 중복 정책 번호 검사: 최대 10건이라 전체 비교도 최대 100회다. 별도 색인과 전달 인자를 늘릴 만큼 효과가 크다고 보기 어렵다.
- 아직 운영 호출이 연결되지 않은 AI 실행·복구 조정자: 공급자 연결을 위한 구현·DB 검증 경로가 있으므로 삭제한 초기 수집 모델과 같은 미사용 코드로 분류하지 않았다.

기존 목록 응답 검사에서 5개 표시 필드와 수집 시각을 확인하도록 보완했다. 페이지의 READY 전환을 DB 제약으로 실패시켜 배치 저장 항목도 모두 롤백되는지, 제약을 제거한 뒤 같은 원문을 순서대로 재처리하는지 확인하는 사례를 추가했다.

Temurin 25.0.3에서 `npm run verify -- test:policy-collection`을 실행해 48건이 실패·오류·건너뜀 없이 통과했다. 기존 검색·정렬·질문 필터·빈 페이지·부분 실패·중복 수집·동시 재처리와 API 계약 일치 검사도 포함한다. 로그는 `.local/verification/1788677061574-2dd6ff10.log`다.

검증한 앱 코드는 `62aff07`이며 이후 변경은 문서뿐이다. 전체 서버 빌드·웹 검사·실제 온통청년 호출은 실행하지 않았다. 변경하지 않은 회원·AI 검사는 이전 결과를 재사용한다.

## 미사용 프레임워크 정리 — 2026-09-06 적용

프로젝트 점검 범위를 프런트엔드·개발 도구·빌드 의존성까지 넓혔다. Java 소스·테스트에서 JPA 엔티티·저장소와 Modulith 검증·이벤트 사용처가 없음을 확인해 `c8cfc33`에서 다음을 정리했다.

- JPA 스타터를 JDBC 스타터로 바꾸고, 당시 페이지 처리에 쓰던 Spring Data Commons를 명시했다(2026-10-07 제거). 기존 JdbcClient·JdbcTemplate·`@Transactional` 코드는 유지하며 Spring Boot의 JDBC 자동 설정을 사용한다.
- Spring Modulith 스타터와 전용 BOM, 사용하지 않는 `spring.jpa` 설정을 제거했다. 기능별 패키지 구조는 유지한다.
- JPA 예외 변환을 기대하던 테스트 2곳은 실제 입력 오류인 `IllegalArgumentException`을 확인하도록 변경했다. 음수 청구·역전된 시각·예산 잔액·상태 보존 검사는 유지했다.

JPA·ORM·Modulith 라이브러리가 실행 JAR에서 빠졌으며 JDBC·Spring Data Commons(2026-10-07 제거)와 입력 검증용 Hibernate Validator는 남아 있음을 확인했다. 별도의 연결·트랜잭션 래퍼는 추가하지 않았다. [기술 결정](../ADR/0001_기술스택과_책임_분리.md)과 [로컬 구성](local-development.md)을 실제 의존성에 맞췄다.

Temurin 25.0.3에서 `npm run verify -- check:backend`로 서버 전체 테스트 436건과 빌드를 통과했다. 실패·오류·건너뜀은 없으며 DB 잠금·롤백·동시 수집·회원·알림·AI 예약/복구와 API 계약 검사를 포함한다. 로그는 `.local/verification/1788690272938-2917da10.log`다. 검증한 앱 코드는 `c8cfc33`이며 후속 변경은 문서뿐이다.

프런트엔드는 의존성·CSS 클래스 사용처를 확인하고 `tsc -p frontend/tsconfig.json --noEmit --noUnusedLocals --noUnusedParameters --incremental false`로 미사용 식별자를 점검해 오류가 없었다. 실제 사용 중인 개발 예시 화면·타입 변환·개인 상태 보호 코드는 유지했다. 프런트엔드·개발 도구는 변경하지 않아 웹 빌드·브라우저·도구 테스트는 다시 실행하지 않았다. 실제 외부 API·로그인·메일 발송 검증은 이번 범위에 포함하지 않았다.

## 미사용·중복 코드 정리 — 2026-10-05 적용

2026-09-06 이후 추가된 관리자·규칙 데이터·AI·이메일 코드를 중심으로 서버·웹·빌드 설정을 다시 점검했다. 다른 작업이 수정 중인 조건 탐색 파일(`BasicConditions`·`PolicyCatalogStore`·`PolicyCheckService`·조건 입력 화면)은 범위에서 뺐다. 출력·HTTP 상태·헤더·마크업·검증 순서는 바꾸지 않았다.

| 대상 | 변경 |
|---|---|
| 서버 | `PolicyRecruitment`에서 결과에 쓰이지 않는 공고별 출처·위치 덮어쓰기를 삭제했다. `PolicyQuestionService`의 중계 메서드와 실행되지 않던 분기를 확인 함수 하나로 합쳤다. 관리자 컨트롤러 6개의 no-store 오류 응답을 `PolicyApiError.noStore`로 모았다(2026-10-07에 [공통 오류 처리기](#웹-계층보안-설정-정리--2026-10-07-적용)로 대체). 수집 저장소의 일일 요청 수 쿼리와 AI 월 예산 ID 형식을 한 곳에서 만든다. 테스트에서만 쓰던 `AiBudgetReservationLifecycleStore.unresolved`와 읽지 않는 `Call` 구성요소를 삭제했다. |
| 서버 테스트 | 8개 클래스에 복사된 `dbTime`을 `AiDatabaseTime.dbTime`으로 바꿨다. |
| 빌드·스크립트 | `build.gradle`의 운영 명령 6개와 계약 생성 2개를 표에서 등록한다. 작업 이름·설명·main 클래스는 같다. `test:ai-reservations` 별칭을 삭제했고 같은 검사는 `test:ai-reservation-db`로 실행한다. |
| 웹 관리자 | 페이지 이동 7곳, 검색 폼·필터 해석 2곳, 목록 오류 화면 2곳을 `collection-exception-view.tsx`의 공통 컴포넌트로 합쳤다. |
| 웹 개발 화면 | `/dev`의 운영 404 차단과 noindex를 `app/dev/layout.tsx` 하나로 모았다. 예시 선택 버튼 5곳과 개발 API 로더 2개를 합치고 모집 상태 라벨을 공유한다. |
| 웹 기타 | 서울 날짜·시각 형식, 회원 API 중계의 쿼리 전달, 저장 버튼의 오류 상태 계산 중복을 정리했다. `RuleDraftForm`의 미사용 속성을 삭제했다. |

추적 파일 기준으로 약 280줄이 줄었다.

### 유지한 것

- 조건 입력 화면의 진행 중인 변경으로 사용처가 없어진 `globals.css`의 진행 표시·확인 요약 스타일 약 120줄은 그 변경과 함께 정리한다.
- 관리자 저장소의 null 허용 시각 변환(약 5줄)과 회원 행 잠금 중복(약 4줄)은 공통 헬퍼·생성자 의존성을 늘리는 비용이 더 커서 유지했다. 2026-10-07에 이메일·관심 정책 쪽 잠금을 모두 `MemberIdentityStore.lock` 정적 메서드로 옮겼다. 두 새로고침 버튼은 문구·스타일이 달라 합치지 않았다.
- 목적이 다른 크기·잠금 검사, 의존성·환경변수 예시는 모두 사용 중이거나 동작이 달라져 유지했다. 컨트롤러별 `CacheControl.noStore()`와 관리자 보안 경로 목록은 2026-10-07에 Spring Security 기본 캐시 금지 헤더와 `/api/v1/admin/**` 단일 규칙으로 바꿨다.

### 결정이 필요한 코드

AI 예약 복구 작업과 후보 상태 모델(`AiReservationRecovery*`, `PolicyAiRecovery*`, `PolicyAiCandidateState`, `PolicyAiCandidateResultProjector`, `PolicyAiRequestAdmission`, `PolicyRevisionState`)은 main 19개 파일 3,452줄, 테스트 13개 3,971줄이다. 진입점인 `AiReservationRecoveryWorkRunCoordinator`·`PolicyAiCandidateResultProjector`를 참조하는 운영 코드가 없고, 하트비트 설정은 YAML에 없는 `app.ai-recovery.heartbeat.enabled`가 있어야 생성된다. 운영 코드는 `ReservationRequired`·`AppliedRevision`·복구 상태 타입만 사용한다. 제거로 결정해 [AI 예약 복구 코드 제거](#ai-예약-복구-코드-제거--2026-10-05-적용)에서 처리했다.

### 검증

| 명령 | 결과 | 로그 |
|---|---|---|
| `npm run verify -- test:web` | 통과 | `.local/verification/1791126119461-5002c8a3.log` |
| `npm run verify -- check:web` | 린트·타입 검사 통과 | `.local/verification/1791126913469-36ec8a35.log` |
| `npm run verify -- build:web` | 프로덕션 빌드 통과 | `.local/verification/1791126919558-b6141c54.log` |
| `npm run verify -- check:backend` | 서버 전체 테스트·빌드 통과 | `.local/verification/1791126913363-0185d9ce.log` |

- `exportPreviewOpenApi`·`exportPolicyOpenApi`를 실행해 두 계약 파일의 해시가 실행 전과 같음을 확인했다.
- 3103 포트의 운영 모드 웹에서 `/dev` 화면 8개는 HTTP 404, `/conditions`와 관리자 AI 추출·조건 검토·이메일 발송 화면은 200이었다. 확인 후 서버를 종료했고 기존 3000 개발 서버는 변경하지 않았다.
- 테스트 이후에는 이 문서만 추가했다.

## AI 예약 복구 코드 제거 — 2026-10-05 적용

[결정이 필요한 코드](#결정이-필요한-코드)를 운영 경로에 연결하지 않고 제거하기로 정했다.

- 진입점(`AiReservationRecoveryWorkRunCoordinator`·`PolicyAiCandidateResultProjector`)을 호출하는 운영 코드가 없었고, 하트비트는 설정 파일에 없는 속성을 켜야 생성됐다. 실제로 실행된 적이 없는 코드다.
- 운영 AI 요청은 결과가 미확인이면 예약을 유지하고 재호출하지 않는다. 운영자가 청구·무과금을 확인한 뒤 `ai:policy-rules`의 `settle`·`no-charge`로 정산·해제한다([규칙 추출](ai-rule-drafts.md)). 단일 홈서버·AI 비활성 상태에서 임대·하트비트·다중 작업자 조정은 쓰이지 않는다.
- 자동 재확인이 필요해지면 제거 직전 커밋 `31451b3`의 코드·문서를 참고해 다시 설계한다.

| 대상 | 변경 |
|---|---|
| 서버 | 예약 복구(`AiReservationRecovery*`·`PolicyAiRecovery*`), 후보 상태(`PolicyAiCandidateState`·`PolicyAiCandidateResultProjector`), 요청 전 판단(`PolicyAiRequestAdmission`), 개정 적용(`PolicyRevisionState`)의 main 19개와 테스트 13개를 삭제했다. |
| 옮긴 타입 | 운영 코드가 쓰던 `ReservationRequired`는 `AiRequestBudget`, `AppliedRevision`은 `PolicyAiResult`로 옮겼다. 필드·검증은 같다. `AiRequestBudget`은 중첩 타입만 담는 클래스가 됐다. |
| 예약 수명주기 | `AiBudgetReservationLifecycleStore`의 `*UnderRecovery` 메서드·복구 시도 펜스·`RECOVERY_*` 결과를 삭제했다. 운영 메서드의 시그니처·트랜잭션·동작은 같다. 삭제한 모델만 쓰던 `PolicyObservation.Failed`도 삭제했다. |
| DB | V3–V9 테이블은 유지한다. 적용한 마이그레이션을 고치지 않았고 테이블 삭제 마이그레이션도 추가하지 않았다. |
| 스크립트 | `test:ingestion`·`test:policy-revisions`·`test:ai-candidates`·`test:ai-candidate-projection`·`test:ai-admission`과 `test:ai-recovery*` 6개를 삭제했다. 이 문서 앞부분의 과거 기록에 있는 해당 명령은 더 실행할 수 없다. |
| 문서·스킬 | 삭제한 코드의 구현 기록 14개와 복구 설계 문서를 삭제했다. 요청 판단·AI 후보·개정 적용 설계 문서는 AI 처리를 켤 때의 설계 근거로 남기고 상태만 고쳤다. README·로컬 개발·실행·예약 문서와 수집·검증 스킬의 참조를 정리했다. |

추적 파일 기준 약 8,700줄이 줄었다.

### 검증

| 명령 | 결과 | 로그 |
|---|---|---|
| `npm run verify -- compile:backend` | 통과 | `.local/verification/1791169237913-ff86b905.log` |
| `npm run verify -- check:backend` | 서버 전체 테스트·빌드 통과(실패·건너뜀 없음). 삭제한 테스트만큼 건수가 줄고 실행 시간은 약 250초에서 90초로 줄었다 | `.local/verification/1791169605696-d336b067.log` |

- 변경 검토에서 검증 스킬의 함께 볼 검사, 설계 문서의 현재형 서술, 과거 기록의 삭제된 명령 안내를 찾아 고쳤다.
- 배포된 DB의 V3–V9 테이블에 행이 남았는지는 확인하지 않았다. 코드가 운영 경로에 연결된 적이 없어 비어 있을 것으로 본다.

## AI 예약·실행 계층 통합 — 2026-10-07 적용

운영 DB를 만들기 전이라 보존할 AI 예약 데이터가 없다. 규칙 추출 한 경로만 쓰던 예약·실행 계층을 Spring·PostgreSQL 기본 기능으로 줄였다. 운영 DB 생성 전 기존 마이그레이션을 제자리 수정하는 기준은 [백엔드 스킬](../../skills/youth-policy-backend/SKILL.md)을 따른다.

| 대상 | 변경 |
|---|---|
| 스키마 | V27의 `policy_ai_rule_calls`를 제자리 수정해 예산 ID·가격 버전·최대 비용·요금 유효기간·단계·발송/결과 미확인/완료 시각·확인 ID·실제 비용을 호출 행에 두었다. 단계별 필수 열·시간 순서는 CHECK, 전송 내용·비용 예약·저장한 응답의 변경 금지는 트리거로 막는다. 요청 근거 13개 열을 다시 저장하던 `ai_request_reservations`와 `reservation_id` 형 변환 조인을 쓰지 않는다. |
| 예약 | `PolicyAiRuleCallStore.reserve`가 정책 행 잠금 뒤 예산 행의 조건부 `UPDATE` 한 번으로 최신 잔액을 검사한다. 미리 읽은 잔액과 비교하던 방식은 다른 공고의 예약이 먼저 커밋되면 잔액이 충분해도 `STALE_BALANCE`로 실패했다. 한도 불일치·요금 만료·한도 부족 문구는 같다. |
| 수명주기 | 발송·결과 미확인·정산·취소·무과금 해제를 단계 조건이 있는 `UPDATE`와 CTE 한 문장으로 바꿨다. 항상 상수였던 호출·미확인 식별자와 사유 열을 없앴고, 재생 판정은 운영자가 입력한 확인 ID·시각·금액에만 둔다. `settle`·`no-charge` 명령은 `APPLIED`·`OVER_RESERVATION`·`REPLAYED`·`CONFLICT`를 출력한다. 역전된 시각·음수 예약액은 DB 제약 위반으로 거절한다. |
| 실행 | 구현이 하나뿐인 실행 포트·조정자와 만든 뒤 버리던 결과 타입 대신 `PolicyAiRuleGenerationService`에 예약 → 발송 기록 → 트랜잭션 밖 호출 → 응답 커밋 → 초안 저장을 직선으로 적었다. 요청마다 하던 예산 행 재잠금·16개 항목 재비교·JVM/DB 시각 혼합 비교가 없어졌다. |
| 설정 | `OpenAiRuleClient`·자동 실행기·관리자 조회가 환경 변수를 직접 읽고 파싱하던 코드를 `app.ai` 설정 레코드(`AiProperties`)로 바꿨다. 환경 변수 이름은 같다. 빈 값은 설정하지 않은 것으로 보고 기본값을 쓴다. 숫자·시각 형식 오류는 서버·운영 명령 기동 실패, 누락·범위 밖·요금 만료는 호출 직전 보류다. API 키는 `toString`에서 가린다. |
| 조회 | 자동 실행 선택·만료·최근 목록과 관리자 AI 추출 조회가 호출 행의 `phase`를 직접 읽는다. 관리자 목록과 자동 실행 최근 목록은 명시 열과 `JdbcClient.query(레코드)`로 매핑한다. API 계약은 같다. |
| 프롬프트 | 판정 항목 라벨이 서로 달라야 한다는 지시를 추가하고 생성 방식 버전(`PROMPT_VERSION`)을 `openai-rule-v2`로 올렸다. |
| 테스트·스크립트 | `test:ai-reservation-db`를 새 `PolicyAiRuleCallStoreTest`로 바꾸고 `test:ai-execution`을 삭제했다. 조정자 테스트가 보던 트랜잭션 밖 실행·결과 미확인·재호출 차단·예산 거절은 `PolicyAiRuleGenerationTest`가 확인하며, 예상하지 못한 호출 예외에서 `DISPATCHED`를 유지하는 검사를 추가했다. |

유지한 성질: 외부 호출을 DB 트랜잭션 밖에서 수행, `HELD`→`DISPATCHED` 전환에 성공한 실행만 호출, 응답 우선 커밋, 결과 미확인 예약액 유지, 예약·해제와 예산 합계의 원자성, 월 예산 덮어쓰기 금지, 관리자 API 계약과 화면.

운영·현재 테스트 경로에서 쓰지 않게 된 다음 파일을 삭제했다.

- main: `ingestion/AiBudgetReservationStore`, `AiBudgetReservationLifecycleStore`, `AiBudgetReservationState`, `AiRequestBudget`, `AiDatabaseTime`, `PolicyAiExecutionCoordinator`, `PolicyAiExecutionPort`, `PolicyAiResult`, `policy/PolicyObservation`
- 테스트·문서: `AiBudgetReservationStoreTest`, `PolicyAiExecutionCoordinatorTest`, `docs/development/policy-ai-execution.md`
- 마이그레이션: 당시 V1의 `ai_request_reservations`와 보조 인덱스, V2, V3–V9 복구 테이블

V27을 제자리 수정했으므로 기존 로컬 DB는 Flyway 검증이 실패한다. [볼륨을 다시 만든다](local-development.md#데이터와-종료).

### 검증

Temurin 25.0.3·PostgreSQL 18.6 Testcontainers에서 실행했다. 실제 OpenAI 호출과 로컬 DB 볼륨 재생성은 하지 않았다.

| 명령 | 확인 범위 | 로그 |
|---|---|---|
| `npm run verify -- test:ai-reservation-db` | 예약·발송·정산·취소·무과금, 동시 예약·종료, DB 제약 | `.local/verification/1791334649147-4d7bce34.log` |
| `npm run verify -- test:ai-rule-generation` | 생성 서비스·초안 저장·트리거·설정 바인딩 | `.local/verification/1791334627087-f351fd38.log` |
| `npm run verify -- test:ai-rule-auto` | 자동 선택·재개·스케줄러 등록 | `.local/verification/1791334657633-d0eaa01f.log` |
| `npm run verify -- test:admin-ai` | 관리자 AI 추출 조회 매핑·권한 | `.local/verification/1791333865766-9725caa1.log` |
| `npm run verify -- test:runtime`, `test:email-key-rotation` | `app.ai` 바인딩을 포함한 서버·운영 명령 기동 | `.local/verification/1791333874746-4aa7add2.log`, `.local/verification/1791333883535-5363b222.log` |
| `npm run verify -- test:ai-costs` | 비용 조회 명령 회귀 | `.local/verification/1791332639922-922111db.log` |

파일 삭제 뒤 `compile:backend`와 AI 관련 테스트를 다시 실행했다. 전체 서버 검사는 마지막 통합 단계에서 실행한다.

## 웹 계층·보안 설정 정리 — 2026-10-07 적용

운영 전이라 보존할 응답 형식 소비자가 화면뿐이다. 컨트롤러마다 반복하던 오류 변환·캐시 헤더·회원 ID 변환·보안 경로 목록을 Spring MVC·Security 기본 기능으로 줄였다. 화면이 코드로 분기하는 이메일 오류(`EMAIL_*`, `EMAIL_PROVIDER_*`), 보안 오류(`LOGIN_REQUIRED`·`ACCESS_DENIED`, 세션 확인 필터의 `MEMBER_UNAVAILABLE`), `{code,message}` 본문은 그대로다. 상태 코드는 아래 표에 적은 경로만 바뀌었다.

| 대상 | 변경 |
|---|---|
| 오류 응답 | 정책·회원 처리기 2개와 관리자 컨트롤러 6곳의 `@ExceptionHandler` 약 30개를 `config/ApiExceptionHandler` 하나로 합쳤다. 요청 오류는 `ApiException`으로 던진다. 영역별 코드는 `INVALID_REQUEST`(400)·`NOT_FOUND`(404)·`CONFLICT`(409)·`SERVICE_UNAVAILABLE`(503) 공통 값이 됐다. 회원 API 처리 중 저장소 장애도 `MEMBER_UNAVAILABLE` 대신 `SERVICE_UNAVAILABLE`이다. 공통 처리기가 모든 컨트롤러에 적용돼 회원·정책 API의 `DuplicateKeyException`은 503에서 409로, 처리기가 없던 Resend 웹훅의 DB 장애는 500에서 503 JSON으로 바뀌었다. 흐름 제어용 빈 예외 클래스 7개(`PolicyNotFoundException`, `PolicyChangedException`, `CollectionReplays.Changed`, `PolicyCorrections.Changed`·`Invalid`, `PolicyRuleActions.Invalid`·`Changed`·`Missing`)와 `MemberEmailStore.EmailException`을 없앴다. |
| 내부 오류 | `IllegalArgumentException`을 통째로 400으로 바꾸던 처리를 없앴다. 생년월일·질문 답변 검사는 `ApiException.invalid()`를 던지고, 나머지 내부 오류는 500이 된다. 오류 재디스패치(`DispatcherType.ERROR`)를 허용해 처리하지 못한 예외가 403으로 가려지지 않는다. 실서버에서 `sendError`로 끝나는 응답(예: 조건 비교의 415, 관리자 경로의 404·405)도 403 대신 해당 상태의 Boot 기본 오류 JSON이 된다. 트랜잭션 시작 실패(`CannotCreateTransactionException`)도 503으로 응답해 정책 목록·조건 비교의 DB 연결 실패가 질문 API와 같아졌다. |
| 캐시 헤더 | 직접 붙이던 `Cache-Control: no-store`를 지웠다. Spring Security 기본 헤더가 모든 응답에 `no-cache, no-store, max-age=0, must-revalidate`와 `Pragma`·`Expires`를 붙인다. 세션 확인 필터를 `HeaderWriterFilter` 뒤로 옮겨 503 응답도 같은 헤더를 받는다. 성공 응답은 본문 타입을 직접 반환하고, 상세 조회의 404는 `orElseThrow(ApiException::notFound)`로 바꿨다. |
| 보안 설정 | 관리자 경로 17개 나열을 `/api/v1/admin/**` 한 줄과 `AuthorizationManager`를 구현한 `AdminAccess`로 바꿨다. 관리자가 GET 전용 경로에 POST하면 403 대신 405다. 진입점·거부 처리기·세션 확인 필터는 JSON 문자열 대신 `PolicyApiError.writeTo`로 쓴다. 로그아웃은 `HttpStatusReturningLogoutSuccessHandler`, 로그인 리다이렉트는 `AppUrls`를 쓰고, 기본값과 같던 세션 무효화·쿠키 삭제 설정과 사용자 없는 `UserDetailsService`를 지웠다. |
| 회원 ID | `MemberController.member(OAuth2User)`와 관리자 `UUID.fromString(principal.getName())`을 `@CurrentMember UUID` 메타 애너테이션으로 바꿨다. `/api/v1/session`은 비로그인도 받으므로 `OAuth2User`를 유지한다. 관심 정책 저장소의 회원 행 잠금은 `MemberIdentityStore.lock`을 쓴다. |
| OpenAPI | 관리자 컨트롤러의 `@SecurityRequirement`·401·403 선언을 지우고 `MemberApiConfiguration`이 회원·관리자·로그아웃 경로에 세션·401·403·CSRF 헤더를 한 규칙으로 붙인다. 관리자 POST 5개에 빠져 있던 `X-CSRF-TOKEN`이 계약에 추가됐다. 필드별 nullable 보정 3곳은 `config/OpenApiContractConfiguration`의 규칙 하나(`@Schema(types = {..., "null"})` 표시 필드를 `anyOf`·enum null로 변환)로 바꿨고, 그 결과 nullable enum 3개(`district`·`employmentStatus`·`deliveryIssue`)에 null이 더해졌다. 생성 TypeScript 타입은 오류 응답 미디어 타입과 관리자 POST의 CSRF 헤더만 바뀌었다. |

클래스나 메서드에 `@ApiResponse`를 하나라도 두면 `@ApiResponse(responseCode = "200")`(본문 없는 응답은 해당 코드)도 함께 선언한다. 그렇지 않으면 springdoc이 선언한 코드만 문서에 넣고 반환 타입에서 200 응답을 추론하지 않는다. 내용은 반환 타입에서 추론하므로 `AdminSlice<T>`의 제네릭 스키마 이름도 유지된다.

삭제한 파일: `policy/catalog/PolicyApiExceptionHandler`, `PolicyNotFoundException`, `member/MemberApiExceptionHandler`, `admin/AdminApiConfiguration`.

유지한 성질: 회원 데이터 소유권 검사, CSRF, 외부 호출을 DB 트랜잭션 밖에서 수행, 이메일 주소 AAD 암호화, 요청마다 하는 관리자 판정, 확인 코드 실패 횟수 커밋 뒤 오류 응답, OAuth 성공 시 인가 정보 제거.

### 검증

Temurin 25.0.3·PostgreSQL 18.6 Testcontainers에서 실행했다. 실제 OAuth 제공자·외부 API 호출과 로컬 DB 조작은 하지 않았다.

| 명령 | 확인 범위 | 로그 |
|---|---|---|
| `npm run verify -- check:backend` | 서버 전체 테스트 330건(실패·건너뜀 없음)과 빌드. 생성 계약 비교, 실서버 로그인·로그아웃 쿠키 삭제·관리자 없는 경로 404, 저장소 장애 503과 내부 오류 비변환 포함 | `.local/verification/1791356930180-8648a158.log` |
| `npm run verify -- test:runtime` | 운영 프로필의 `AppUrls`·로그인 실패 리다이렉트 | `.local/verification/1791356688833-5a3d06a3.log` |
| `npm run generate:api` 후 `npm run verify -- check:api-types`, `check:web` | 생성 계약·TypeScript 타입과 웹 타입 검사 | `.local/verification/1791356578312-0f599659.log`, `.local/verification/1791356579342-47b25bf2.log` |

