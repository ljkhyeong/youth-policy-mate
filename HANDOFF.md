# 작업 인계

## 현재 단계

서울 대상 MVP의 제품 범위와 기술 스택을 합의하고 기준 문서·전용 스킬, Next.js·Spring Boot·로컬 PostgreSQL 기본 구성을 작성했다. 시작 화면·비회원 조건 입력 화면과 서버 상태 확인을 제공한다. 서버에 자격 판정 결과 집계 모델과 명시적 연령·거주·단일 취업·소득 구간 비교기를 추가했고 웹·서버 병렬 CI를 구성했다. CI의 로컬 검사와 문법 검사는 통과했지만 원격 푸시·GitHub 실행은 하지 않았다. 정책 API, 업무 DB 스키마, 수집·실제 정책을 연결한 자격 판정·인증·저장·알림 기능은 아직 구현하지 않았다.

인증키 승인 전에도 개발을 진행하기로 했다. `/conditions`에서 생년월일·서울 자치구·주된 취업상태를 입력하고 확인·수정·초기화할 수 있다. 개인정보는 React 화면 상태로만 사용하며 서버 전송·브라우저 저장소 사용은 없다. 새로고침하면 초기화된다. 입력 검사와 정책 자격 판정은 별개이며, 실제 정책이나 가상 추천 결과는 표시하지 않는다. 공통 상태 화면을 추가했고 개발 전용 `/dev/states`에서 로딩·빈 결과·오류 표시를 점검할 수 있다.

취업 조건 설계에 따라 확인한 단일 사실·요구 방향·기준일과 명시적인 답변을 비교하는 순수 Java 모델을 구현했다. 정책·개정·조건 ID·정의·기준일이 다른 답변은 재확인을 요구하고, 모름·답변 누락과 정책 미해석을 구분한다. 주된 상태 하나와 정책 코드 이름만으로 취업 요건을 확정하지 않는다. 추가 질문은 개발 전용 `/dev/employment`에서 인공 예시로 확인할 수 있지만 실제 정책·서버 비교기와 연결하지 않았다. 원천 코드 매핑·복합 취업 조건 해석은 아직 없다.

소득 설계에 따라 정의·대상·기간·적용 기준이 같은 답변의 원화 구간을 비교하는 순수 Java 모델을 구현했다. 사용자 구간 전체가 허용 범위 안이면 충족, 전혀 겹치지 않으면 불충족, 일부만 겹치면 사용자 정보 부족이다. 정책·개정·가구·기준표·기준일이 다른 답변은 재사용하지 않는다. 비교기 작업에서 소득 47건을 포함한 판정 테스트 117건과 서버 `assemble`이 통과했다. 원천 매핑·단위 변환·기준표 조회·보험료 산정은 아직 없다.

앞선 소득 질문 작업에서는 개발 전용 `/dev/income`에 소득 대상·기간·단위, 구간·모름 선택과 확인·수정·삭제를 추가했다. 기간 변경 시 이전 답변을 지우고, 구간 추가 확인은 현재 답변의 판정이 아닌 별도 인공 상황으로 보여준다. 실제 소득 입력·서버 전송·저장·판정 연결은 없다. 웹 17건·린트·타입 검사·빌드, 개발 200·운영 404와 모바일·데스크톱 흐름을 확인했다.

앞선 결과 화면 작업에서는 읽기 전용 자격 결과·근거 컴포넌트와 개발 전용 `/dev/eligibility`를 추가했다. 네 가지 고정 인공 결과로 전체 상태·모집 상태·검토 미완료·항목별 충족/불충족/미확인·근거·기준 정보를 표시한다. 화면에서 상태를 다시 계산하거나 기존 조건 입력을 읽지 않는다. 실제 정책·서버 API 연결은 없다. 웹 24건·린트·타입 검사·빌드, 개발 200·운영 404, 모바일·데스크톱과 예시 전환·근거 펼치기·새로고침 초기화를 확인했다.

2026-08-31에는 정책 모듈에 확인된 단일 신청기간의 모집 상태 모델을 추가했다. 날짜형은 서울 달력 날짜로, 시각형은 시간대가 확인된 접수 시작·종료 순간으로 비교한다. 상시·소진 시 종료·명시적 마감·기간 미확인에는 가짜 날짜를 만들지 않고 근거·개정과 한 번 읽은 계산 시각을 보존한다. 모집 23건과 기존 자격 판정 117건, 총 140건 및 서버 `assemble`이 통과했다. 실제 API 파싱·화면·알림은 연결하지 않았고 웹·PostgreSQL 통합 검사는 다시 실행하지 않았다.

앞선 후보 날짜 작업에서는 정책 모듈의 서울 마감 날짜 제공과 일정 모듈의 D-7·D-3·D-1 후보 계산을 추가했다. 지난 날짜는 제외하고 오늘 후보는 발송 시각 확인 필요로 남긴다. 후보 없음 사유와 원래 기간·개정·근거·계산 시점을 보존한다. 후보 17건·마감 날짜 제공 8건·기존 모집 상태 23건·자격 판정 117건, 총 165건과 서버 `assemble`이 통과했다. 실제 저장·수신 동의·예약·발송은 없으며 웹·PostgreSQL 통합 검사는 다시 실행하지 않았다.

앞선 고정 화면 작업에서는 읽기 전용 마감·알림 후보 컴포넌트와 개발 전용 `/dev/reminders`를 추가했다. 일곱 가지 고정 인공 자료로 날짜형·시각형 기간, 원문·서울 시간대, 오늘 후보와 후보 없음 사유를 구분한다. 이 경로는 서버·저장·예약·발송과 연결하지 않는다. 웹 32건·린트·타입 검사·빌드, 개발 200·운영 404, 예시 전환·근거·초기화와 반응형을 확인했다.

앞선 마감 API 작업에서는 `preview` 프로필 전용 마감 계산 조회 API와 `/dev/reminders/server`를 연결했다. DB·인증키 없이 고정 인공 신청기간을 기존 서버 모델로 계산한다. DTO 기반 OpenAPI 3.1·생성 TypeScript와 계약 일치 검사를 추가했고 기본 서버의 개발 API 차단을 확인했다. 서버 전체 172건·웹 39건, 생성 타입 검사·린트·타입 검사·빌드가 통과했다. 브라우저에서 서버 중지 오류 → 기동 후 다시 불러오기 복구를 확인했고, 스트리밍 전에 운영 모드를 차단해 실제 404를 확인했다.

앞선 자격 API 작업에서는 같은 `preview` 서버에 자격 예시 조회 API와 `/dev/eligibility/server`를 연결했다. 연령·거주·취업·소득 인공 규칙과 답변을 기존 비교기·집계 모델로 계산하고 전체 상태·항목별 근거·미확인 이유·개정·날짜를 생성 계약으로 전달한다. 모집 상태는 별도 계산한다. null enum 명세를 보완했고 서버 전체 175건·웹 45건, 생성 계약·린트·타입 검사·빌드가 통과했다. 서버 중지 후 복구, 네 예시·근거·반응형·운영 404를 확인했다. 실제 조건 입력·정책·회원·저장·예약·발송은 연결하지 않았다.

앞선 재판정 연결에서는 `/dev/eligibility/interactive`에 인공 취업·소득 답변 변경과 서버 재판정을 연결했다. 질문 GET·계산 POST는 `preview`에만 있고 자유 입력·회원 세션·저장은 없다. 답변 당시 질문의 개정·정의·기준일·소득 기간으로 기존 비교기를 호출하며 오래된 답변은 재사용하지 않는다. 화면은 답변 변경 시 결과를 지우고 늦은 응답을 무시하며, 질문 버전을 바꾸면 답변도 초기화한다. 서버 180건·웹 53건과 생성 계약·린트·타입 검사·빌드, 실패 후 재시도·운영 404를 확인했다. 자세한 범위는 [인공 답변 재판정](docs/development/eligibility-answer-trial.md)을 따른다.

앞선 개정 작업에서는 [원본 확인·개정 적용 경계](docs/design/policy-revision-application.md)를 설계하고 순수 Java의 `PolicyObservation`·`PolicyRevisionState`를 구현했다. 원본 참조와 비교 내용을 분리하고 같은 내용·재처리·낮은 순번·충돌·실패·비교 방식 변경을 구분한다. 내부 개정을 만든 근거와 최근 정상 확인 기록을 따로 유지한다. 전용 개정 11건·기존 모집 31건 검사와 전체 서버 191건·빌드가 통과했다. 화면·API 계약·DB 스키마는 변경하지 않았다. 실제 원본 취득·저장·순번 발급·DB 동시성 제어는 없으며 [검증 범위](docs/development/policy-revision-application.md)에 남은 작업을 정리했다.

이번에는 별도 `ingestion` 패키지에 [수집 실행 진행 모델](docs/development/collection-run-progress.md)을 추가했다. 페이지·항목별 시도·실패·중단을 보존하고 시작 전·실패·중단 위치만 재처리 대상으로 반환한다. 성공 페이지의 원본·항목 목록을 유지하며 명시적 종료, 검토 필요, 늦은 시도 결과·재전달·충돌을 구분한다. 전용 12건과 전체 서버 203건·빌드가 통과했다. 첫 전체 검사에서 임시 PostgreSQL 연결 오류로 통합 2건이 실패했으나 설정 변경 없는 재실행은 모두 통과했다. 화면·API 계약·DB 스키마는 바꾸지 않았다. 메모리 상태만 다루며 실제 수집·원본 저장·DB 재시작 복구·Spring Batch 실행은 아직 없다.

2026-08-30에 온통청년의 현재 API 명세, 코드 정의서와 공개 정책 사례 2건을 조사했다. 사용자는 인증키를 신청했고 승인 대기 중이다. 인증키를 사용한 성공 응답은 아직 확인하지 못했다. 키 없는 요청의 HTTP 400 HTML 응답을 정상 정책 응답으로 취급하지 않는다.

## 실행 가능한 구성

- `frontend/`: Next.js App Router·React·TypeScript·Tailwind. 시작 화면과 비회원 조건 입력 흐름을 제공하며 정책 API를 호출하지 않는다. 이 화면은 백엔드·DB·인증키 없이 `npm run dev:web`으로 실행한다.
- `frontend/src/features/conditions/`: 화면 입력 모델·입력 검사·입력/확인 폼. 취업상태 값은 화면 내부 선택지이며 외부 API 코드나 확정 DTO가 아니다.
- `npm run test:web`: Vitest 테스트 53개. 기존 표시 32개와 마감 API 연결 7개·자격 API 연결 6개·인공 답변 재판정 8개다. 앞선 재판정 작업에서 테스트·생성 타입 검사·린트·타입 검사·프로덕션 빌드를 통과했다. 이번 서버 내부 모델 작업에서는 웹 검사를 다시 실행하지 않았다.
- `frontend/src/components/page-state.tsx`: 공통 로딩·빈 결과·오류·404 표시. `/conditions`의 로딩, 공통 오류·없는 주소 안내에 연결했다. `/dev/states`는 개발 미리보기이며 운영 빌드에서 HTTP 404를 반환한다. 실제 정책 검색·API 실패 복구 검증은 아니다.
- `frontend/src/app/dev/employment/`: 취업 사실의 정의·기준일·근거와 세 가지 답변을 점검하는 개발 전용 화면. 확인·수정·삭제, 예시 개정 변경 시 초기화를 제공한다. 운영 빌드의 404, 데스크톱·모바일과 답변 흐름을 확인했다. 서버 전송·저장·실제 판정은 없고 브라우저 방향키가 반영되지 않아 키보드 전용 전체 흐름은 미확인이다.
- `frontend/src/app/dev/income/`: 소득 구간과 모름을 선택하는 개발 전용 화면. 본인·대상 기간·원화 단위·소득 정의와 경계 포함 여부를 표시하며 구간 추가 확인 예시는 현재 답변과 분리했다. 운영 404, 0원·모름·좁은 구간 확인, 수정·삭제·기간 변경·새로고침 초기화와 반응형을 점검했다. 브라우저 방향키 입력이 반영되지 않아 키보드 전용 전체 흐름은 미확인이다.
- `frontend/src/features/eligibility/`·`frontend/src/app/dev/eligibility/`: 전체 상태를 그대로 표시하는 결과·근거 컴포넌트와 인공 예시. `server/`는 생성 응답 타입을 표시 모델로 변환하고 실패를 판정 결과로 대체하지 않는다. 네 예시·근거·서버 중지 후 복구·반응형·운영 404를 확인했다. 기본 `summary`의 Enter 동작은 도구 입력 한계로 미확인이다. [자격 서버 연결 기록](docs/development/eligibility-preview-api.md)을 따른다.
- `frontend/src/app/dev/eligibility/interactive/`: 인공 질문 버전·정해진 답변 코드만 Server Action으로 전달한다. 서버 판정 결과를 재계산하지 않으며 답변 변경·초기화·질문 버전 변경 후 이전 결과와 늦은 응답을 사용하지 않는다. 실제 개인정보는 받지 않는다.
- `frontend/src/features/reminders/`·`frontend/src/app/dev/reminders/`: 읽기 전용 표시 컴포넌트와 고정 예시. `server/` 경로는 Next.js 서버에서 개발 API를 캐시 없이 조회하고 생성 타입을 표시 모델로 바꾼다. 오류를 후보 없음으로 대체하지 않는다. 운영 404·반응형·연결 복구를 확인했고 실제 예약은 없다. [서버 연결 기록](docs/development/reminder-preview-api.md)에 검증 범위를 정리했다.
- `backend/`: Java 25·Spring Boot·Spring MVC·JPA·Flyway·Spring Modulith core. 기본 모드는 GET `/actuator/health`만 허용하고 기본 로그인 계정을 생성하지 않는다.
- `backend/src/main/java/kr/youthpolicymate/devpreview/`: `preview`에서만 두 고정 예시 API·명세의 GET과 `/api/dev/eligibility-trial`의 질문 GET·인공 계산 POST를 추가 허용한다. 이 계산 경로만 CSRF 검사에서 제외하며 다른 경로·메서드 차단은 유지한다. `npm run dev:preview-api`로 루프백 8081에서 실행하며 DB는 사용하지 않는다. 기본 모드의 세 컨트롤러 미등록·403 차단을 실제 DB 통합 검사와 함께 확인했다.
- `npm run generate:api`: 실제 서버 OpenAPI 응답을 `api/openapi.preview.json`에 내보내고 `frontend/src/generated/preview-api.d.ts`를 생성한다. `npm run check:api-types`와 서버 계약 테스트로 일치를 검사한다. 생성 파일은 직접 수정하지 않는다.
- `npm run test:preview-api`: 마감 API 4개·자격 API 3개·인공 답변 재판정 5개·공통 명세 1개, 총 13개. 이번 전체 서버 빌드에서 도메인 188개와 실제 DB·기본 차단 2개도 함께 실행해 총 203개가 통과했다.
- `backend/src/main/java/kr/youthpolicymate/policy/`: 확인된 날짜·시각 기간의 모집 상태와 근거를 제공한다. `Clock`을 한 번 읽고 서울 날짜를 계산하며 개발 API에만 연결했다. 복수·혼합·충돌 기간의 원문 해석, 실제 정책·저장·예약·발송 연결은 아직 없다.
- 같은 패키지의 `PolicyObservation`·`PolicyRevisionState`: 원본 참조·비교 방식 버전/내용 해시·내부 순번으로 적용 여부와 다음 상태를 계산한다. 수집 실패에도 기존 정상 개정·확인 시각을 보존한다. API·모집·자격·DB에 연결하지 않은 독립적인 순수 모델이다.
- `backend/src/main/java/kr/youthpolicymate/ingestion/`: `CollectionPosition`·`CollectionAttempt`·`CollectionRun`이 페이지·항목별 진행·시도 이력·재처리 위치를 계산한다. 정책 모듈의 원본 참조 값만 사용하며 개정 적용을 호출하지 않는다. 외부 요청·DB·스케줄러와 연결하지 않았다.
- `RecruitmentSchedule.confirmedDeadlineOnSeoul()`: 확인된 마감의 서울 날짜를 제공한다. 날짜형은 날짜 그대로, 시각형은 마감 순간의 서울 날짜를 제공하며 원본 기간을 지우지 않는다.
- `backend/src/main/java/kr/youthpolicymate/schedule/DeadlineReminderCandidates`: 기존 모집 상태와 시계 기준을 사용해 후보 날짜를 계산한다. 지난 날짜는 제외, 오늘 후보는 발송 시각 확인 필요로 표시한다. 빈 목록은 마감일 미확인·모집 마감·남은 후보 없음으로 구분하며 실제 예약 객체가 아니다.
- `backend/src/main/java/kr/youthpolicymate/eligibility/`: 순수 Java 판정 결과·근거 모델. 정책 검토 미완료·조건 미해석을 먼저 보류하고 명확한 불충족·사용자 정보 누락·전체 충족을 구분한다. 개발용 인공 자료 API·화면에만 연결했고 원문 해석·실제 입력 연결은 없다.
- 같은 패키지의 `AgeCondition`·`AgeConditionEvaluator`: 확인한 기준일과 최소·최대 만 나이 조건을 비교한다. 미해석 조건은 보류하며 서비스 대상 19~34세나 오늘 날짜를 기본값으로 쓰지 않는다. 출생연도·연령 연장 등 미지원 해석은 [연령 조건 비교](docs/development/age-condition.md)를 따른다.
- 같은 패키지의 `ResidenceCondition`·`ResidenceConditionEvaluator`·`SeoulResidence`·`SeoulDistrict`: 확인한 전국·서울 전체·자치구 거주 범위와 서울 거주 입력을 비교한다. 정책 기준일과 거주 입력의 기준일이 다르면 해당 날짜의 사용자 정보 부족으로 보류한다. 원천 지역 코드 매핑·거주 기간·직장 소재지 예외는 아직 없으며 [거주 조건 비교](docs/development/residence-condition.md)를 따른다.
- 같은 패키지의 `EmploymentFact`·`EmploymentAnswer`·`EmploymentCondition`·`EmploymentConditionEvaluator`: 확인한 단일 취업 사실과 해당·비해당·모름 답변을 비교한다. 취업 제한 없음은 해당 항목만 충족시키며, 미해석 조건은 답변으로 덮지 않는다. [단일 취업 조건 비교](docs/development/employment-condition.md)에 답변 재사용 범위와 미지원 조건을 정리했다.
- 같은 패키지의 `IncomeBasis`·`IncomeRange`·`IncomeAnswer`·`IncomeCondition`·`IncomeConditionEvaluator`: 소득 구간의 포함 경계, 명시적인 한쪽 제한 없음, 모름·누락·기준 불일치를 구분한다. 내부 단위는 원으로 고정하며 원천 단위 확인·변환은 호출 측의 별도 책임이다. [소득 구간 비교](docs/development/income-condition.md)에 구현 범위와 미지원 산정을 정리했다.
- `npm run test:eligibility`: 소득 47건·취업 21건·거주 17건·연령 18건·집계 14건, 총 117건. 이 명령은 API 인증키·DB·Docker 없이 실행한다. 이번에는 서버 전체 빌드에서 다른 도메인·개발 API·실제 DB 테스트와 함께 다시 확인했다.
- `npm run test:recruitment`: 모집 상태 23건·마감 날짜 제공 8건, 총 31건. 서울 자정·시각 경계, 미확인 이유·근거·개정·원본 기간 보존과 서울 마감 날짜 제공을 확인한다. 실행·설계 범위는 [모집 기간 구현](docs/development/recruitment-period.md)을 따른다.
- `npm run test:policy-revisions`: 개정 적용 11건. 재수집·재처리·낮은 순번·순번 충돌·A→B→A·실패·비교 방식 불일치를 검사한다. 같은 패키지의 다른 테스트가 섞이지 않도록 기존 모집 명령은 `Recruitment*`로 좁혔다. 두 명령을 각각 실행해 통과했다.
- `npm run test:ingestion`: 수집 진행 12건. 부분 실패·명시적 종료·중단·재개·늦은 시도 결과·재전달·충돌·검토 필요를 확인한다. 인증키·DB·Docker 없이 실행한다. 실제 재시작·동시 작업자·중복 정책 반영 검증은 아니다.
- `npm run test:reminders`: 후보 날짜 17건. D-7·D-3·D-1의 달력 날짜, 미래·오늘·지난 날짜, 빈 후보 사유와 개정 변경 후 계산을 확인한다. [후보 날짜 구현](docs/development/deadline-reminder-candidates.md)에 미구현 예약·발송 범위를 함께 정리했다.
- `compose.yaml`: 프로젝트 전용 PostgreSQL 18.6. 호스트 연결은 `127.0.0.1:55432`로 제한한다.
- 업무 테이블과 Flyway SQL은 없다. DB에는 Flyway 관리 테이블만 만들어진다.
- 빌드·타입 검사·린트와 PostgreSQL 통합 테스트 2개가 통과했다. 로컬 서버의 상태 응답 200과 차단 경로 403, 시작 화면도 확인했다.
- 구체적인 버전, 실행·검증 명령과 알려진 경고는 [로컬 개발 안내](docs/development/local-development.md)를 따른다.
- `scripts/ontong-api-probe.mjs`: 인증키 발급 후 목록·상세·지역 필터 응답을 1회씩 확보하는 개발용 점검 명령. `npm run probe:ontong`으로 실행한다. 결과는 Git에서 제외한 로컬 파일에 미검증 상태로 보관하며 DB에 적재하지 않는다.
- `npm run check:tools`의 인공 응답 테스트 7개와 키 누락 시 요청 전 종료를 확인했다. 실제 온통청년 API를 호출하거나 성공 계약을 확인한 것은 아니다.
- `.github/workflows/ci.yml`: `main` 푸시·대상 PR·수동 실행용 `웹 검사`, `서버 검사`. 인증키 없이 기존 검사를 병렬 실행하고 서버 테스트 보고서를 7일 보관하도록 구성했다. actionlint 문법 검사, Node.js 24의 새 의존성 설치·개발 도구 7개·웹 5개 테스트·린트·타입 검사·빌드를 로컬에서 확인했다. GitHub 실행·캐시·보고서 업로드는 미확인이다.

## 다음 작업 진입점

- 제품 동작: [PRD-0001](docs/PRD/0001_product-baseline/spec.md)
- 기술 선택: [ADR-0001](docs/ADR/0001_기술스택과_책임_분리.md)
- API 계약 생성: [ADR-0002](docs/ADR/0002_서버_DTO_기반_API_계약_생성.md)
- 최초 승인 기록: [서울 MVP 합의 개정 1](docs/alignments/seoul-mvp.html)
- 적용할 스킬: [전용 스킬과 출처](docs/development/skill-reuse.md)
- 조사 결과·미확인 계약: [온통청년 API 조사](docs/research/ontong-api-contract.md)
- 인증키 설정·응답 확보 명령: [온통청년 응답 점검](docs/development/ontong-api-probe.md)
- 성공 응답 확인 전 설계: [정책 수집·판정 데이터 구조 초안](docs/design/policy-data-model.md)
- 원본/내용·개정·순번과 실제 저장 원자성: [개정 적용 설계](docs/design/policy-revision-application.md)
- 내부 적용 판단과 미구현 수집·저장 경계: [개정 적용 모델](docs/development/policy-revision-application.md)
- 페이지·항목 시도·재처리 위치와 실제 저장 조건: [수집 진행 설계](docs/design/collection-run-progress.md)
- 내부 진행·중단·재개와 검증 범위: [수집 진행 모델](docs/development/collection-run-progress.md)
- 현재 화면 범위·후속 연결 지점: [비회원 조건 입력](docs/development/guest-conditions.md)
- 판정 결과 집계·근거 구조·검증: [자격 판정 결과](docs/development/eligibility-decision.md)
- 기준일·연령 범위 비교·미해석 처리: [연령 조건 비교](docs/development/age-condition.md)
- 전국·서울·자치구 거주 비교·기준일 처리: [거주 조건 비교](docs/development/residence-condition.md)
- 취업 입력의 한계·추가 질문·향후 검증 시나리오: [취업 조건 설계](docs/design/employment-condition.md)
- 단일 취업 요건·명시적 답변 비교·정책 개정과 기준일 확인: [단일 취업 조건 비교](docs/development/employment-condition.md)
- CI 설정·로컬 검증·첫 원격 실행 확인: [CI 안내](docs/development/ci.md)
- 상태 화면·개발 미리보기·검증 한계: [공통 상태 화면](docs/development/page-states.md)
- 추가 질문·근거·답변 확인과 변경 점검: [추가 확인 질문 미리보기](docs/development/employment-question-preview.md)
- 소득 정의·단위·기간과 구간 경계·미확인 처리: [소득 조건 설계](docs/design/income-condition.md)
- 소득 구간 비교·입력 기준 일치·검증: [소득 비교 구현](docs/development/income-condition.md)
- 소득 정의·기간·구간 답변과 추가 확인 표시: [소득 질문 미리보기](docs/development/income-question-preview.md)
- 전체·항목별 자격 안내와 근거·검토 이슈 표시: [결과·근거 미리보기](docs/development/eligibility-result-preview.md)
- 신청기간의 날짜·시각 경계·상시·미확인 표현: [모집 기간 설계](docs/design/recruitment-period.md)
- 정책 모듈의 모집 상태 계산·시계·검증: [모집 기간 구현](docs/development/recruitment-period.md)
- 마감 알림의 달력 날짜와 오늘 후보 구분: [후보 날짜 설계](docs/design/deadline-reminder-candidates.md)
- 후보 날짜 계산·빈 목록 사유·검증: [후보 날짜 구현](docs/development/deadline-reminder-candidates.md)
- 날짜·시간대·오늘 후보와 빈 후보 안내: [마감·알림 후보 미리보기](docs/development/deadline-reminder-preview.md)
- 서버 계산·생성 계약·실패 복구 연결: [개발 전용 마감 API](docs/development/reminder-preview-api.md)
- 자격 계산·조건 근거·미확인 원인 전송: [개발 전용 자격 API](docs/development/eligibility-preview-api.md)
- 인공 답변 변경·질문 버전·늦은 응답 처리: [인공 답변 재판정](docs/development/eligibility-answer-trial.md)

### 인증키 없이 이어갈 작업

- 원본·개정의 최소 적용 규칙과 저장 시 필요한 원자성 조건을 정리하고 순수 모델로 검증했다. 원천 성공 응답 전에는 운영 수집기·확정 DTO·Flyway DDL을 만들지 않는 기존 기준을 유지했다. 실제 저장·재시작·동시성 검증을 완료했다고 취급하지 않는다.
- 수집 실행·항목 실패·재처리 위치의 내부 계약과 순수 모델도 구현했다. 단일 실행의 이력은 메모리에만 있으며 실제 원천 요청·정규화·원본 보관·재시작 복구는 성공 계약과 보관 기준 확인 후 연결한다.
- 키 미발급 상태의 다음 독립 작업 후보는 정책 개정과 AI 요약·추출 후보의 버전 일치 검사다. 이전 개정의 늦은 후보를 현재 정책에 적용하지 않는 규칙만 먼저 검증할 수 있다. 실제 AI 공급자 호출·자동 공개·DB 저장을 함께 구현했다고 취급하지 않는다.
- 질문 조회와 재판정 실패 후 재시도 복구를 확인했다. 키보드 전용 전체 흐름은 도구의 Tab·Enter 입력이 반영되지 않아 미확인이다.
- CI를 원격에 푸시할 때 첫 GitHub 실행과 캐시·테스트 보고서를 확인한다. 이번 작업에서 푸시나 브랜치 보호 변경은 하지 않았다.

### 인증키 발급 후 이어갈 수집 작업

작은 목록·상세 성공 응답을 확보하는 데서 시작한다. 키 값은 채팅·저장소·요청 URL 로그에 남기지 않는다. 조사 문서 8절의 최소 확인 후 원천 DTO·정규화·저장 구조를 확정하고 작은 기능 흐름을 구현한다. 인증키가 준비되기 전에는 API의 지역 필터나 JSON 구조를 검증 완료로 표시하지 않는다. 현재의 조건 입력 화면을 정책 목록·상세·판정 구현 완료로 취급하지 않는다.

키를 발급받으면 루트 `.env`의 `ONTONG_API_KEY`에 직접 설정한다. 먼저 `npm run probe:ontong`으로 받은 미검증 응답을 확인하고, 응답에 있는 정책번호로 상세를 점검한다. 점검 결과의 `UNVERIFIED` 표시는 정상 수집 성공을 뜻하지 않는다.

## 이번 조사에서 주의할 사항

- 현재 주소는 `/go/ythip/getPlcy`, 인증 파라미터는 `apiKeyNm`이다. 제공목록의 청년정책API 탭을 선택하기 전 나오는 옛 예시를 복사하지 않는다.
- 코드 정의서와 명세의 `bizPrdSeCd`/`bizPrdSecd`, `sBizCd`/`sbizCd` 표기가 다르다. 실제 응답으로 확인한다.
- 학력 제한없음과 대학생 참여 불가 문구가 함께 있는 공개 사례가 있다. 코드만으로 자격을 확정하지 않는다.
- 신청기간과 사업기간, 전국 분류와 실제 신청 주체를 구분한다. 구체적인 사례·정책번호는 조사 문서를 참고한다.

## 현재 제약

- 운영비 상한은 월 3만 원이며 운영 장비·클라우드는 사용자 요청으로 나중에 정한다.
- AI·이메일 공급자와 실제 호출 한도, 개인정보 보관·삭제 및 배포 준비는 PRD의 공개 전 확인 사항이다.
- 현재 검증 범위는 개발 환경·DB 연결·접근 차단, 비회원 입력·인공 예시 표시, 서버의 조건 비교·모집·후보 계산, 개발용 자격·마감 API와 생성 계약·화면 연결·실패 복구, 내부 개정 적용 판단·수집 진행 모델이다. 실제 수집·원본 저장·DB 중복 방지·정책 정확도·외부 연동·소셜 인증·예약·발송 검증으로 확대해서 보고하지 않는다.
- CI 워크플로는 작성했지만 GitHub 실행은 아직 확인하지 않았다. 배포·운영 인증 설정은 없다. 로컬 DB 계정과 비밀번호를 운영 환경에 재사용하지 않는다.
- 이 작업에서 스킬 원본은 `skills/`에 보관한다. 사용자 스킬 폴더의 링크 상태는 설치 위치에서 직접 확인한다.
