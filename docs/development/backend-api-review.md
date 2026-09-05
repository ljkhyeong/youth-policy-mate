# Java·Spring API 활용과 중복 검증 검토

2026-09-05, `f8b0122` 기준. Java 25·Spring Boot 4.1.1의 백엔드 구현과 호출부·관련 테스트를 검토했다. 아래는 변경 제안이며 앱 코드는 수정하지 않았다. 정리 효과는 주로 유지보수 부담 감소다.

## 표준 API로 줄일 부분

### 1. SMTP 객체 생성과 설정 — 우선 정리

[SmtpMemberEmailSender.java](../../backend/src/main/java/kr/youthpolicymate/member/SmtpMemberEmailSender.java) 12–35행에서 `JavaMailSenderImpl`을 직접 만들고 호스트·포트·인증·문자 인코딩·타임아웃을 설정한다. 이미 `spring-boot-starter-mail`이 있으므로 Boot의 `spring.mail.*` 설정과 `JavaMailSender` 주입으로 옮길 수 있다. 전송 자체는 이미 Spring API를 사용한다.

발신 주소·기능 활성화·암호화 키 준비 여부는 서비스 설정으로 남긴다. 현재의 기본 비활성화, 587 포트, STARTTLS 필수, 서버 이름 검사, 5초 타임아웃과 인증 사용 조건을 유지해야 한다. 자동 설정의 기본값에 맡기면 기존 동작과 달라진다. [Spring Boot 이메일 설정](https://docs.spring.io/spring-boot/reference/io/email.html)

### 2. 이메일 주소 정규식 — 우선 정리

[MemberEmailStore.java](../../backend/src/main/java/kr/youthpolicymate/member/MemberEmailStore.java) 26–27행은 이메일 형식을 자체 정규식으로 관리한다. [MemberEmailController.java](../../backend/src/main/java/kr/youthpolicymate/member/MemberEmailController.java) 40행에서도 null·254자 제한을 따로 검사한다. 동일한 주소 규칙의 관리 위치가 나뉘어 있다.

`@Email`·`@NotBlank`·`@Size(max = 254)`로 주소 제약을 선언하고, HTTP 입력과 발신 주소 설정에 동일한 기준을 적용하는 편이 낫다. 서비스 직접 호출을 포함해 검증이 실제 실행되는 진입점을 정해야 하며, 어노테이션만 붙인다고 일반 Java 호출이 검증되지는 않는다. `@Email`은 null을 허용하고 세부 허용 형식은 구현체에 따라 다르므로 기존 허용·거절 사례를 비교한 뒤 바꾼다. 확인 코드 전송을 통한 주소 확인은 별도로 유지한다. [Jakarta Validation의 Email](https://jakarta.ee/specifications/bean-validation/3.1/apidocs/jakarta/validation/constraints/email)

### 3. OAuth 로그인 제공자 저장소 — 설정 정리와 함께 변경

[MemberConfiguration.java](../../backend/src/main/java/kr/youthpolicymate/member/MemberConfiguration.java) 57–65행의 `Registrations`는 목록 보관·ID 검색·순회 기능을 직접 구현한다. `InMemoryClientRegistrationRepository`가 같은 기능을 제공한다. `SecurityConfiguration`과 `MemberController`가 이 내부 클래스에 의존하므로 함께 바꾸면 된다.

로그인 키가 없어도 공개 API는 실행되어야 한다. 기본 구현의 List 생성자는 빈 목록을 거절하지만 Map 생성자는 빈 Map을 허용한다. 로컬 Spring Security 7.1.1 바이트코드에서도 확인했다. 변경 불가능한 Map을 전달하고 제공자 존재 여부로 OAuth 로그인을 활성화하면 현재 미설정 상태를 유지할 수 있다. 화면의 제공자 순서도 유지한다. 제공자별 URL·클라이언트 설정은 별도로 Boot 설정에 옮길 수 있지만 카카오·네이버 응답 해석은 남겨야 한다. [기본 저장소 구현](https://github.com/spring-projects/spring-security/blob/main/oauth2/oauth2-client/src/main/java/org/springframework/security/oauth2/client/registration/InMemoryClientRegistrationRepository.java)·[OAuth 설정](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html)

## 삭제하거나 한곳으로 모을 부분

### 4. List.copyOf 이후 null 검사 — 바로 삭제 가능

[AiReservationRecoveryRetryPolicy.java](../../backend/src/main/java/kr/youthpolicymate/ingestion/AiReservationRecoveryRetryPolicy.java)에서 세 군데가 중복이다.

- 70행에서 `List.copyOf(history)` 후 72행에서 원소를 다시 `requireNonNull`로 검사한다.
- 94행에서 `List.copyOf(reviewResumes)` 후 95행에서 원소를 다시 검사한다.
- 149행에서 `List.copyOf(retryDelays)` 후 154행에서 `delay == null`을 검사한다.

`List.copyOf`는 null 원소를 만나면 이미 예외를 던지므로 뒤의 null 검사에는 걸릴 수 없다. 이 세 검사만 제거하고 순번·예약 ID·완료 상태·양수 재시도 간격 검사는 유지한다. 복사 전 `requireNonNull`의 설명 메시지는 별개이며 일괄 삭제할 이유가 없다. [JDK 25 List.copyOf](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection))

### 5. 호출되지 않는 스케줄러 팩터리 — 삭제 가능

[PolicyAiRecoveryHeartbeat.java](../../backend/src/main/java/kr/youthpolicymate/ingestion/PolicyAiRecoveryHeartbeat.java) 129–139행의 `HeartbeatScheduler.scheduled(...)`는 메인 코드·테스트에서 호출되지 않는다. 실제 Spring 구성은 `PolicyAiRecoveryHeartbeatScheduler`를 사용하고 테스트도 관리형 구현 또는 테스트용 구현을 사용한다.

현재는 [heartbeat 문서](ai-reservation-recovery-heartbeat.md) 17행의 호환 어댑터 설명만 남아 있다. 저장소에서 확인한 사용처가 없으므로 이 팩터리와 전용 import·호환 설명을 함께 정리할 수 있다. `HeartbeatScheduler` 인터페이스와 관리형 구현은 계속 필요하다.

### 6. 다섯 정책의 같은 답변 검증 — 작은 공통 함수로 정리

[WorkStudyRules.java](../../backend/src/main/java/kr/youthpolicymate/policy/catalog/WorkStudyRules.java) 39–44행, [ExamFeeRules.java](../../backend/src/main/java/kr/youthpolicymate/policy/catalog/ExamFeeRules.java) 42–47행, [KPassRules.java](../../backend/src/main/java/kr/youthpolicymate/policy/catalog/KPassRules.java) 53–58행, [YouthHousingSavingsRules.java](../../backend/src/main/java/kr/youthpolicymate/policy/catalog/YouthHousingSavingsRules.java) 55–60행, [SeoulYouthNetworkRules.java](../../backend/src/main/java/kr/youthpolicymate/policy/catalog/SeoulYouthNetworkRules.java) 56–61행에 같은 검증이 복사되어 있다.

질문·선택지 목록을 Map으로 만들고, 알 수 없는 질문·선택지·중복 답변을 거절한 뒤 답변 Map을 반환한다. 이 부분만 질문 목록과 답변을 받는 공통 함수로 모으면 된다. 정책별 판정과 안내 문구는 각 클래스에 둔다. 이 검사는 동적인 선택지 검증이라 `@Valid`만으로 대체할 수 없고, 미응답을 일괄 거절해서도 안 된다. 별도 검증 프레임워크나 규칙 상속 구조는 필요하지 않다.

## 유지할 코드

- 이메일 확인 실패 횟수 저장: `MemberEmailStore.confirm`은 실패 횟수를 커밋한 뒤 컨트롤러가 오류 응답을 만든다. 8자리 검사까지 DTO에서 먼저 거절하면 현재 집계되는 형식 오류가 실패 횟수에서 빠진다.
- 발송 직전 동의·저장 상태·개정 확인, DB 행 잠금과 유일성 제약: 최초 입력 검사와 달리 동시 요청·중간 상태 변경을 막는다. `TransactionTemplate`도 외부 SMTP 호출 전후로 DB 작업을 나누는 데 쓰인다.
- 이메일 발송의 트랜잭션 상태 검사: 현재 두 줄로 목적을 수행한다. `@Transactional(NEVER)`로 바꾸려면 `deliverPending → deliver` 내부 호출까지 고려해야 하므로 이 검사를 줄이려고 계층을 추가할 필요는 없다.
- AI 임대 갱신과 종료 처리: 종료 중 새 작업 거절, 기존 갱신 유지, 종료 기한 후 결과 거절을 함께 보장한다. 일반 스케줄러 교체만으로 같은 동작이 보장되지는 않는다. 관련 테스트의 종료·인터럽트 사례는 필요한 검증이다.
- `OntongApiClient`의 JDK HttpClient·응답 크기 제한과 `EmailCrypto`의 JDK Cipher·Mac: 이미 표준 API를 사용한다. 인증키가 포함된 응답 저장 차단과 회원·설정 버전별 암호화 문맥도 목적이 있다.

## 확인 범위와 다음 변경의 검증

소스 검색, 호출부와 기존 테스트 검토, JDK·Spring 공식 문서 대조를 수행했다. 앱 코드 변경이 없어 테스트를 추가하거나 전체 테스트를 다시 실행하지 않았다. 검증 실행 기록의 최근 정책 질문·도구 검사도 검토 시작 시 파일 변경 없이 통과 상태였다.

리팩터링 시에는 삭제한 null 분기 자체를 재현하는 테스트를 만들지 않는다. 기존 재시도 정책 테스트와 정책 질문 테스트를 실행하고, SMTP·OAuth 변경에는 미설정 부팅·TLS 거절·주소 오류·제공자 목록을 확인한다. 이메일 DTO 제약을 바꾸면 OpenAPI 변경 여부도 확인한다.
