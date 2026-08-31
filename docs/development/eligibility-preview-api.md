# 개발 전용 자격 판정 API와 화면 연결

2026-08-31 구현·검증 기준. [자격 판정 모델](eligibility-decision.md)과 [결과 화면](eligibility-result-preview.md)을 고정 인공 규칙·답변으로 연결했다. 제품 정책·비교 규칙은 변경하지 않았다. 실제 정책 추천, 사용자 입력 전송, 저장·로그인 기능은 아니다. 계약 생성은 [ADR-0002](../ADR/0002_서버_DTO_기반_API_계약_생성.md)를 따른다.

## 실행

저장소 루트의 서로 다른 터미널에서 실행한다.

```sh
npm run dev:web
```

```sh
npm run dev:preview-api
```

- [자격 서버 연결 화면](http://127.0.0.1:3000/dev/eligibility/server)
- [서버 없는 고정 화면](http://127.0.0.1:3000/dev/eligibility)
- API: `GET http://127.0.0.1:8081/api/dev/eligibility-examples`
- 명세: `GET http://127.0.0.1:8081/dev/openapi`

DB·Docker·인증키는 필요하지 않다. Java 도구체인과 의존성은 준비되어 있어야 한다. 기존 마감 예시와 같은 `preview` 프로필·8081 서버를 사용하므로 서버를 추가로 띄울 필요는 없다. 이미 실행 중이면 코드 변경 후 재시작한다. 운영 환경에서 `preview`를 활성화하면 안 된다.

## 계산과 API 계약

`backend/src/main/java/kr/youthpolicymate/devpreview/EligibilityPreviewController.java`가 인공 규칙과 답변을 기존 연령·거주·취업·소득 비교기에 전달하고 `EligibilityDecision`으로 집계한다. 최종 상태를 수기로 지정하지 않는다. 시계는 서울 2026-08-31 00:30으로 고정하며 조건별 기준일은 따로 전달한다.

| 인공 상황 | 자격 결과 | 별도 모집 결과 |
|---|---|---|
| 모든 필수 조건 충족 | `ELIGIBLE` | `CLOSED` |
| 소득 구간이 허용 경계에 걸침 | `NEEDS_REVIEW` | `OPEN` |
| 연령 불충족·예외 미확인·취업 정의 미해석 | `NEEDS_REVIEW` | `OPEN` |
| 명확한 연령 불충족·소득 답변 누락 | `INELIGIBLE` | `OPEN` |

모집 상태는 같은 인공 정책·개정·계산 시점으로 `RecruitmentAssessment`에서 별도로 계산한다. 이 고정 예시의 취업 조건은 제한 없음 또는 미해석만 사용한다. 명시적 인공 취업·소득 답변의 전송은 별도 [답변 재판정 화면](eligibility-answer-trial.md)에 연결했다.

- `operationId`: `listDevelopmentEligibilityExamples`
- 요청 본문·사용자 조건·인증키 없음. 응답은 200 JSON, `Cache-Control: no-store`.
- `dataKind: SYNTHETIC`과 예시 ID·이름·설명, 자격 결과와 별도 모집 상태를 반환한다.
- `EligibilityResultResponse`: 전체 상태·설명, 정책 ID·개정·규칙 버전·판정 시점, 검토 상태·미확인 이슈, 항목별 결과·근거.
- `comparedValue`, `referenceDate`, `uncertainty`, `excerpt`는 항상 제공하며 값이 없으면 명시적인 null이다. null을 0원·오늘 날짜·입력 누락으로 바꾸지 않는다.
- `UNKNOWN` 항목만 미확인 원인을 가진다. `MET`·`NOT_MET`의 원인은 null이다. 날짜는 `date`, 판정 시점은 `date-time`으로 구분한다.
- 기본 서버에서는 컨트롤러가 등록되지 않고 접근은 403이다. `preview`의 두 고정 예시 API와 명세는 GET만 허용한다. 별도 `/api/dev/eligibility-trial`은 인공 질문 GET과 저장 없는 계산 POST만 허용한다.

OpenAPI 3.1의 null 허용 enum은 타입과 enum 값 목록 양쪽에 실제 null이 있어야 한다. 현재 도구는 타입에만 null을 생성하므로 `PreviewApiConfiguration`의 명세 보정에서 해당 enum에 null을 추가한다. 생성 파일을 직접 수정하지 않으며 공통 `PreviewApiContractTest`가 이를 검사한다.

## 화면과 실패 처리

- `frontend/src/features/eligibility/eligibility-api-view.ts`는 생성 응답 타입을 사용한다. 인공 조건 ID의 제목과 모집 코드 문구만 붙이며 상태·근거·기준은 다시 계산하지 않는다.
- `frontend/src/app/dev/eligibility/server/load-eligibility-examples.ts`는 고정 루프백 주소를 캐시 없이 조회한다. 리다이렉트는 거부하고 대기는 최대 5초다. `/conditions`나 질문 화면의 개인정보·답변을 읽지 않는다.
- 조회 실패는 공통 오류 화면과 다시 불러오기 버튼으로 표시한다. 불충족·추가 확인 판정이나 오프라인 예시로 바꾸지 않고 내부 오류를 노출하지 않는다.
- `server/layout.tsx`는 로딩 스트리밍 전에 운영 모드를 차단한다. 로딩 페이지 전송 후의 200 응답을 404로 표시하는 문제를 피한다.
- 기존 `EligibilityPreview`는 예시를 props로 받는다. 오프라인·서버 화면이 같은 표시 컴포넌트를 사용하고, 서버 화면에 오프라인 자료를 기본값으로 넣지 않는다.
- 다시 불러오기 버튼은 `frontend/src/components/dev-preview/retry-preview.tsx`에서 마감·자격 화면이 함께 사용한다.

생성 타입은 모든 런타임 JSON을 검사하지 않는다. 현재 변환 경계는 인공 자료 구분·빈 예시와 항목 결과/미확인 원인의 조합을 확인한다. 실제 외부 원천 응답 검증은 수집 작업의 별도 책임이다.

## 검증 결과

```sh
npm run generate:api
./backend/gradlew -p backend build --no-daemon
npm run test:web
npm run check:api-types
npm run check:web
npm run build:web
```

- 서버 총 175건 통과: 기존 도메인 165건, 개발 API·계약 8건, 실제 PostgreSQL·기본 차단 2건. 실패·건너뛰기 없음.
- 새 자격 API 3건은 자격/모집 분리, 조건별 날짜·근거·개정, 미확인 구분과 명시적 null, 쓰기 차단을 검사한다. 기존 규칙의 입력 행렬은 도메인 테스트에서만 유지한다.
- 웹 총 45건 통과: 기존 39건과 자격 표시 변환·서버 조회 6건. 전체 상태 보존, null·근거, 잘못된 미확인 조합, 실패 처리와 운영 호출 차단을 확인했다.
- OpenAPI·생성 타입 검사, 린트·타입 검사·빌드 통과. 웹 타입 검사와 빌드는 차례로 실행했다.
- 실제 개발 화면·API·명세 200, 미허용 API·자격 POST 403. 임시 운영 서버의 자격 서버/고정 화면·마감 서버 화면은 404, `/conditions`는 200이었다. 확인용 3100 서버는 종료했다.
- 브라우저에서 서버 중지 오류 → 재기동 → 다시 불러오기 성공, 네 예시 전환, 근거 펼치기와 예시 변경 시 접힘, 새로고침 초기화를 확인했다.
- 390×844·1280×900에서 표시를 확인했다. 모바일의 긴 소득 설명·근거에 가로 넘침이 없었고 조회한 오류·경고 로그는 없었다.
- 근거 클릭·포커스는 확인했지만 도구의 Enter 입력이 펼침 상태를 바꾸지 않았다. 키보드 전용 전체 흐름은 미확인이다. 도구에 맞춘 별도 이벤트 처리는 추가하지 않았다.

실제 정책 정확도·외부 수집·개인정보 전송·회원 권한·저장·예약·발송·운영 배포 검증은 아니다. Gradle·Docker·빌드·로컬 실행에 권한 확장을 사용했고 기존 Java agent·클래스 공유 경고는 유지했다.

## 다음 작업

고정 인공 정책의 취업 질문·소득 구간 답변 연결은 [인공 답변 재판정](eligibility-answer-trial.md)에서 구현했다. 실제 개인정보·정책·원천 파서 연결은 별도이며 인증된 성공 응답을 확보한 후 원천 계약을 확정한다.
