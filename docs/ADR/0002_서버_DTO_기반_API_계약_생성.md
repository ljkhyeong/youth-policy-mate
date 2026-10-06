# ADR-0002 서버 DTO 기반 API 계약 생성

- 상태: 채택
- 결정일: 2026-08-31
- 상위 결정: [ADR-0001](0001_기술스택과_책임_분리.md)
- 적용: [공개 정책 API](../development/policy-catalog.md)와 회원·관리자 API

## 배경과 결정

서버 계산과 화면을 연결하면서 날짜·정확한 시각·시간대·null·후보 상태를 보존해야 한다. 서버의 이름 있는 DTO와 컨트롤러를 계약의 관리 기준으로 삼고, springdoc-openapi로 OpenAPI 3.1을 만든 뒤 openapi-typescript로 TypeScript 타입을 생성한다. 생성 파일은 Git에 보관하되 손으로 수정하지 않는다.

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

해당하지 않는 null 가능 필드도 응답에 null로 넣는다. 화면은 필요한 필드만 표시 모델로 옮기며, OpenAPI가 필드 조합 규칙까지 검증한다고 주장하지 않는다.

생성 TypeScript는 컴파일 시 계약 확인용이며 런타임의 모든 JSON을 검증하지 않는다. 외부 원천 응답 검증은 수집 작업에서 구현한다.

null 허용 enum은 타입과 enum 값 목록 모두에 실제 null을 포함한다. 현재 생성기가 enum 목록에서 null을 빠뜨리는 필드는 서버의 명세 보정과 계약 테스트로 맞춘다.

## 결과와 비용

DTO·생성 명세·소비 타입의 차이를 자동 검사할 수 있다. 반면 DTO 변경 뒤 재생성이 필요하고, 명세의 nullable·enum이 실제 JSON과 맞는지 대표 직렬화 검사도 필요하다. 첫 연동에서 정수의 문자열 enum 생성을 발견해 정수 DTO와 설명으로 수정했다.

Swagger UI·MCP 노출·범용 API 클라이언트·실서비스 오류 응답 형식은 추가하지 않는다. 공개·회원 API의 인증·오류 계약은 해당 기능을 만들 때 정한다.

## 확인한 도구

2026-08-31 기준 springdoc-openapi 3.1.0과 openapi-typescript 7.13.0을 고정했다. Spring Boot 4 지원과 Swagger UI 없는 명세 모듈은 [springdoc 공식 문서](https://springdoc.org/), 타입 생성·검사는 [openapi-typescript CLI 문서](https://openapi-ts.dev/cli)를 확인했다. 프로젝트에서 실제 생성·직렬화·빌드도 통과했다.

## 공개 정책 API 적용 — 2026-09-05

같은 생성 방식을 공개 목록·상세에도 적용했다. `api/openapi.policy.json`과 `frontend/src/generated/policy-api.d.ts`를 생성하며 `npm run generate:api`가 이 계약을 갱신한다. 공개 계약 생성에는 PostgreSQL 테스트 컨테이너가 필요하다. 생성용 명세 경로는 테스트 설정에서만 허용하며 기본 서버의 OpenAPI 비활성화는 유지한다.

공개 조회는 기본 서버의 `/api/v1/policies`와 `/api/v1/policies/{policyNumber}` GET으로 제공한다. Next.js 서버가 `POLICY_API_BASE_URL`(기본 `http://127.0.0.1:8080`)로 조회하며 브라우저에는 온통청년 인증키를 전달하지 않는다. [구체적인 계약·오류·저장 범위](../development/policy-catalog.md)를 따른다.

## 개발 전용 계약 제거 — 2026-10-07

이 결정은 처음에 온통청년 성공 응답이 없어 `preview` 프로필의 인공 자료 API(`/api/dev/**`)와 개발 전용 계약(`api/openapi.preview.json`, `preview-api.d.ts`)에 먼저 적용했다. 실제 판정은 [ADR-0003](0003_공고별_조건_데이터.md)의 공고별 판정표, 마감 알림 예약은 회원 저장 흐름으로 동작하게 되면서 이 API를 쓰는 제품 기능이 없어졌다. 실운영 전에 프로필 분기와 계약 파일을 유지하는 비용만 남아 `preview` 프로필·개발 전용 계약·`/dev` 화면을 제거했다. 서버 DTO 기반 생성 방식은 정책·회원·관리자 계약(`api/openapi.policy.json`)에만 적용한다. `/api/dev/**`를 포함해 허용 목록에 없는 경로는 기본 보안 설정이 계속 거절한다.
