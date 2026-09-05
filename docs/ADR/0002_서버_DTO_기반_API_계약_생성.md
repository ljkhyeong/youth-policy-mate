# ADR-0002 서버 DTO 기반 API 계약 생성

- 상태: 채택
- 결정일: 2026-08-31
- 상위 결정: [ADR-0001](0001_기술스택과_책임_분리.md)
- 첫 적용: [개발 전용 마감 계산 API](../development/reminder-preview-api.md)

## 배경과 결정

서버 계산과 화면을 연결하면서 날짜·정확한 시각·시간대·null·후보 상태를 보존해야 한다. 서버의 이름 있는 DTO와 컨트롤러를 계약의 관리 기준으로 삼고, springdoc-openapi로 OpenAPI 3.1을 만든 뒤 openapi-typescript로 TypeScript 타입을 생성한다. 생성 파일은 Git에 보관하되 손으로 수정하지 않는다.

최초 적용 당시 온통청년 성공 응답이 없어 개발 전용 인공 자료에 한정했다. 이 계약을 실제 원천 DTO나 공개 정책 API 확정으로 취급하지 않는다.

| 대상 | 관리 방식 |
|---|---|
| 경로·메서드·operationId·응답 | 서버 컨트롤러·DTO에서 정의 |
| 항상 있는 필드 | 서버 스키마의 `requiredProperties`로 명시 |
| null 가능 필드 | OpenAPI 3.1의 복수 타입으로 정의하고 JSON에도 null 포함 |
| 날짜 | `LocalDate`를 `date` 문자열로 전송. 시각으로 바꾸지 않음 |
| 정확한 시각 | `Instant`·`OffsetDateTime`을 `date-time`으로 전송. 원래 지역 시간대 ID는 별도 보존 |
| OpenAPI | MockMvc에서 받은 실제 생성 응답을 내보냄 |
| TypeScript | 생성 응답 타입을 사용하고 별도 표시 모델은 어댑터로 변환 |
| 계약 검사 | 서버의 현재 OpenAPI와 저장본 비교, 웹의 생성 타입 최신 여부 확인 |

신청기간 응답은 종류에 해당하지 않는 필드도 null로 제공한다. 화면은 해당 종류에 필요한 필드만 표시 모델로 옮긴다. OpenAPI가 기간 종류별 필드 조합을 모두 검증한다고 주장하지 않는다.

생성 TypeScript는 컴파일 시 계약 확인용이며 런타임의 모든 JSON을 검증하지 않는다. 현재 경계는 인공 자료 구분·빈 예시·필수 기간 값·지원 간격, 자격 항목의 결과/미확인 원인 조합을 확인한다. 외부 원천 응답 검증은 별도 수집 작업에서 구현한다.

[자격 예시 API](../development/eligibility-preview-api.md)에도 같은 방식을 적용했다. null 허용 enum은 타입과 enum 값 목록 모두에 실제 null을 포함한다. 현재 생성기가 enum 목록에서 null을 빠뜨리는 필드는 서버의 명세 보정과 계약 테스트로 맞춘다.

## 개발 전용 경계

개발 조회·인공 답변 계산과 보안 허용 설정은 `preview` 프로필에서만 등록한다. 기본 모드는 기존 접근 차단과 OpenAPI 비활성화를 유지한다. `preview`는 DB 자동 구성을 제외하고 루프백에서 실행한다. 운영 배포에서 이 프로필을 활성화하면 안 되며 프로필 분리는 운영 인증을 대신하지 않는다.

Next.js 서버 컴포넌트가 고정 루프백 주소를 `no-store`로 조회한다. 브라우저 직접 요청·CORS·회원 토큰 전달은 추가하지 않는다. 운영 모드 차단은 로딩 스트리밍보다 앞선 레이아웃에서 수행한다. 조회 실패는 자격 판정·후보 없음이나 오프라인 예시로 바꾸지 않는다.

[인공 답변 재판정](../development/eligibility-answer-trial.md)은 Next.js Server Action이 질문 버전·답변 코드만 고정 주소에 POST한다. 서버가 enum과 필수 값을 검사하고, 누락·지원하지 않는 코드는 입력값을 돌려주지 않는 400 응답으로 거절한다. 실제 개인정보·회원 세션·저장 기능은 없다. 개발용 `/api/dev/eligibility-trial`의 POST만 허용하며 이 경로만 CSRF 검사에서 제외한다. 그 밖의 경로·메서드 차단과 CSRF 보호는 유지한다. 이 예외를 회원 API로 확대하지 않는다.

화면은 답변 변경 시 이전 결과를 지우며, 현재 요청 번호와 다른 응답은 무시한다. 질문 내용이 달라지면 답변·결과를 초기화한다. 서버는 답변 당시의 질문 버전으로 사실·기간을 복원해 기존 비교기로 현재 조건과 비교한다. 버전 불일치를 현재 기준으로 바꿔 계산하지 않는다. 두 개의 고정 예시 버전은 실제 정책의 개정 저장·조회 계약을 대신하지 않는다.

## 결과와 비용

DTO·생성 명세·소비 타입의 차이를 자동 검사할 수 있다. 반면 DTO 변경 뒤 재생성이 필요하고, 명세의 nullable·enum이 실제 JSON과 맞는지 대표 직렬화 검사도 필요하다. 첫 연동에서 정수의 문자열 enum 생성을 발견해 정수 DTO와 설명으로 수정했다.

Swagger UI·MCP 노출·범용 API 클라이언트·실서비스 오류 응답 형식은 추가하지 않는다. 공개·회원 API의 인증·오류 계약은 해당 기능을 만들 때 정한다.

## 확인한 도구

2026-08-31 기준 springdoc-openapi 3.1.0과 openapi-typescript 7.13.0을 고정했다. Spring Boot 4 지원과 Swagger UI 없는 명세 모듈은 [springdoc 공식 문서](https://springdoc.org/), 타입 생성·검사는 [openapi-typescript CLI 문서](https://openapi-ts.dev/cli)를 확인했다. 프로젝트에서 실제 생성·직렬화·빌드도 통과했다.

## 공개 정책 API 적용 — 2026-09-05

같은 생성 방식을 공개 목록·상세에도 적용했다. `api/openapi.policy.json`과 `frontend/src/generated/policy-api.d.ts`를 생성하며 `npm run generate:api`가 개발·공개 계약을 함께 갱신한다. 공개 계약 생성에는 PostgreSQL 테스트 컨테이너가 필요하다. 생성용 명세 경로는 테스트 설정에서만 허용하며 기본 서버의 OpenAPI 비활성화는 유지한다.

공개 조회는 기본 서버의 `/api/v1/policies`와 `/api/v1/policies/{policyNumber}` GET으로 제공한다. Next.js 서버가 `POLICY_API_BASE_URL`(기본 `http://127.0.0.1:8080`)로 조회하며 브라우저에는 온통청년 인증키를 전달하지 않는다. [구체적인 계약·오류·저장 범위](../development/policy-catalog.md)를 따른다.
