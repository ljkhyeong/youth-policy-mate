# 개발 전용 마감 계산 API와 화면 연결

2026-08-31 구현·검증 기준. [후보 계산 모델](deadline-reminder-candidates.md)과 [기존 화면](deadline-reminder-preview.md)을 인공 자료로 연결했다. 실제 정책·개인 조건·저장·예약·발송 기능은 아니다. 생성 계약은 [ADR-0002](../ADR/0002_서버_DTO_기반_API_계약_생성.md)를 따른다.

## 실행

저장소 루트에서 웹과 개발 API를 각각 실행한다.

```sh
npm run dev:web
```

다른 터미널:

```sh
npm run dev:preview-api
```

- [서버 연결 화면](http://127.0.0.1:3000/dev/reminders/server): Next.js 서버가 개발 API를 조회한다.
- [서버 없는 고정 화면](http://127.0.0.1:3000/dev/reminders): 기존 오프라인 예시를 유지한다.
- 개발 API: `GET http://127.0.0.1:8081/api/dev/reminder-examples`
- 개발 OpenAPI: `GET http://127.0.0.1:8081/dev/openapi`

API 인증키·DB·Docker가 필요하지 않다. Java 25 도구체인과 최초 의존성 설치는 필요하다. `application-preview.yaml`은 DB 자동 구성을 제외하고 루프백 8081에서 실행한다. 기존 `local` 프로필·8080의 DB 연결 서버와 구분한다. 종료는 실행 터미널에서 `Ctrl+C`로 한다.

## API와 계산

`backend/src/main/java/kr/youthpolicymate/devpreview/`가 개발용 컨트롤러·DTO·보안 설정을 소유한다. 기존 `policy`·`schedule` 모듈은 변경하지 않았다. 컨트롤러는 고정 인공 신청기간 일곱 개를 `DeadlineReminderCandidates.calculate`에 전달한다. 시계는 `2026-08-30T15:30:00Z`, 서울 기준 2026-08-31 00:30으로 고정한다. 후보 결과를 수기로 반환하지 않는다.

| 항목 | 계약 |
|---|---|
| 요청 | GET, 요청 본문·사용자 조건·인증키 없음 |
| operationId | `listDevelopmentReminderExamples` |
| 성공 | 200 JSON, `Cache-Control: no-store` |
| 자료 구분 | `dataKind: SYNTHETIC` |
| 예시 | ID·표시 이름·설명·계산 결과 목록 |
| 결과 | 후보 결과·설명·원래 기간·서울 마감 날짜·모집 상태·후보 목록·근거와 계산 기준 |
| 부재 값 | 마감 날짜·발췌문·해당 기간 종류에서 쓰지 않는 필드는 생략하지 않고 null |
| 날짜 간격 | JSON 정수. 현재 계산기가 7·3·1을 제공하며 문자열 enum으로 바꾸지 않음 |
| 기본 모드 | 컨트롤러 미등록, 접근 403. 오류 본문은 화면에서 사용하지 않음 |

시각형은 원래 순간을 오프셋 포함 문자열로 보내고 원래 시간대 ID를 별도 보존한다. 서울 마감 날짜는 정책 모듈의 값을 사용한다. 모집 상태·오늘 여부·후보 없음 사유를 DTO나 Next.js에서 다시 계산하지 않는다.

`preview`에서 두 GET 경로만 추가 허용한다. 쓰기 요청·다른 API·관리 정보 경로는 차단하고 기본 모드의 OpenAPI는 끈다. 개발 프로필을 운영에서 켜서는 안 되며 실제 회원 인증을 대신하지 않는다.

## 계약 생성과 검사

```sh
npm run generate:api
npm run check:api-types
npm run test:preview-api
```

`generate:api`는 Gradle `exportPreviewOpenApi`로 실제 MockMvc 응답을 [OpenAPI 파일](../../api/openapi.preview.json)에 저장한 뒤 [TypeScript 타입](../../frontend/src/generated/preview-api.d.ts)을 만든다. 라이브 서버·DB 없이 실행한다. 생성 파일은 직접 편집하지 않는다.

일반 서버 테스트는 현재 OpenAPI와 저장본을 비교하고 덮어쓰지 않는다. 웹 CI의 `check:api-types`는 저장된 명세와 생성 타입의 일치를 검사한다. DTO 변경 후 두 생성 파일을 함께 커밋한다. GitHub 원격 실행은 아직 확인하지 않았다.

## 화면 연결과 실패

- `frontend/src/features/reminders/reminder-api-view.ts`: 생성 응답 타입을 기존 표시 모델로 변환한다. 날짜·상태·근거는 보존하고 모집 상태 코드만 문구로 표시한다.
- `frontend/src/app/dev/reminders/server/load-reminder-examples.ts`: 고정 루프백 GET, 캐시 미사용, 리다이렉트 거부, 조회 대기 최대 5초. 사용자 입력으로 URL을 바꾸지 않는다.
- `server/page.tsx`: 성공 시 예시 표시, 실패 시 공통 오류와 다시 불러오기 버튼을 제공한다.
- `server/layout.tsx`: 로딩 화면보다 먼저 운영 모드를 404 처리한다. 비동기 페이지 안에서만 차단하면 스트리밍 때문에 HTTP 200이 될 수 있어 상위에서 막는다.
- `server/retry-preview.tsx`: 조회 중 버튼 중복 클릭을 막는다. 자동 반복 재시도는 없다.

연결 실패·비정상 HTTP·JSON 해석 실패를 후보 없음이나 고정 예시로 바꾸지 않는다. 오류 본문·내부 예외도 표시하지 않는다. 예시 선택은 이미 받은 자료의 표시만 바꾸며 재계산 요청이나 사용자 답변 전송을 하지 않는다. 실제 수집 시각·최신 원문 확인을 뜻하는 표시도 없다.

## 검증 결과와 한계

- `./backend/gradlew -p backend build --no-daemon`: 총 172건 통과. 순수 도메인 165건, 개발 API·계약 5건, 실제 PostgreSQL·기본 차단 2건이다. 실패·건너뛰기 없음.
- API 검사는 날짜형·시각형 직렬화, 서울 날짜·원래 시간대, 숫자 간격, null·빈 목록·사유 보존, 미허용 경로 차단, 생성 계약 일치를 확인했다. 날짜 계산의 전체 행렬은 기존 도메인 테스트에 둔다.
- `npm run test:web`: 총 39건 통과. 기존 32건과 표시 변환·서버 조회 7건이다. 필수 기간 누락·미지원 간격 거부, 조회 실패 시 결과 미제공, 운영 모드에서 호출하지 않음도 확인했다.
- `check:api-types`, 웹 린트·타입 검사·빌드 통과. 타입 검사와 빌드는 순서대로 실행했다.
- 실제 기동에서 개발 API·OpenAPI 200, 미허용 API 403. 서버 연결 화면은 개발 200·운영 404, 운영 조건 입력은 200이었다. 확인용 3100 서버는 종료했다.
- 브라우저에서 서버 중지 상태의 오류 → 서버 기동 → 다시 불러오기 성공, 일곱 예시 전환과 시각형·근거 표시를 확인했다. 조회한 오류·경고 로그는 없었다.
- 1280×900·390×844에서 결과·오류 화면을 확인했다. 모바일 가로 넘침 없음. Enter·Tab 입력이 도구에서 펼침·포커스를 바꾸지 않아 키보드 전용 전체 흐름은 미확인이다.
- Gradle 캐시·Docker 접근과 로컬 실행에 권한 확장을 사용했다. 기존 Java agent·클래스 공유 경고는 유지했다. 실제 정책 정확도·회원 권한·예약·취소·발송·운영 배포 검증은 아니다.

## 다음 작업

같은 생성 계약과 개발 전용 경계로 자격 판정 결과·항목별 근거를 서버 계산에 연결한다. 실제 원천 수집·사용자 조건 저장·회원 인증은 별도다. 성공 응답 확보 전에는 인공 자료 연동을 실제 정책 연동 완료로 취급하지 않는다.
