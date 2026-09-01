# 청년정책메이트

서울 거주 청년이 내 조건에 맞는 정책과 판단 근거를 확인하고, 관심 정책의 신청 마감과 변경 내용을 관리하는 서비스입니다.

## MVP

- 서울 거주 만 19~34세 청년 대상. 온통청년의 전국 공통·서울시·서울 자치구 정책 제공
- 조건 입력에 따른 3단계 자격 안내와 항목별 근거
- 비회원 검색, 카카오·네이버 로그인 후 조건·관심 정책 저장
- 저장한 정책의 일정과 서비스 내 알림, 별도로 활성화하는 이메일 알림
- 정책 자동 수집·공개, 명확한 조건만 자동 판정, 예외만 선택 검수
- 실제 신청은 공식 사이트에서 진행. 증빙서류 수집과 신청 대행은 제외

## 현재 상태

2026-08-30에 MVP 범위·기술 스택을 확정하고 온통청년 API를 조사한 뒤, Next.js·Spring Boot와 로컬 PostgreSQL의 기본 개발 환경을 구성했습니다. 프런트엔드 빌드, 실제 DB를 사용하는 백엔드 테스트와 로컬 기동을 확인했습니다.

API 인증키 없이 사용할 수 있는 비회원 조건 입력 화면(`/conditions`)을 구현했습니다. 생년월일·서울 자치구·주된 취업상태를 입력하고, 확인·수정·초기화할 수 있습니다. 입력값은 화면 상태로만 사용하며 서버에 보내거나 저장하지 않습니다. 새로고침하면 초기화됩니다.

조건 입력의 로딩 화면과 공통 오류·404 안내를 추가했습니다. 개발 모드의 `/dev/states`에서 로딩·빈 결과·오류를 미리 볼 수 있습니다. 실제 검색 결과가 아닌 화면 점검용이며, 운영 빌드에서는 이 경로를 404로 처리합니다.

서버에는 항목별 결과를 3단계 자격 상태로 합치는 모델과 근거 구조를 추가했습니다. 확인한 연령 범위·거주 지역·단일 취업 요건·소득 구간을 비교합니다. 정책 미해석·예외·사용자 정보 누락을 구분하며, `npm run test:eligibility`로 인증키·DB 없이 단위 테스트를 실행할 수 있습니다.

취업 조건은 [설계](docs/design/employment-condition.md)에 따라 [단일 사실·명시적 답변 비교](docs/development/employment-condition.md)를 구현했습니다. 정책·개정·정의·기준일이 다른 답변은 재사용하지 않습니다. 개발 전용 `/dev/employment`에서 [추가 확인 질문](docs/development/employment-question-preview.md)을 인공 자료로 점검할 수 있습니다. 주된 상태의 자동 변환, 원천 코드 매핑과 실제 정책의 질문·판정 연결은 아직 없습니다.

소득은 [설계](docs/design/income-condition.md)에 따라 [명시적 원화 구간 비교기](docs/development/income-condition.md)를 구현했습니다. 개인·가구, 소득 정의·기간·적용 기준이 맞는 답변만 비교하며 구간 일부만 허용 범위에 겹치면 추가 확인으로 남깁니다. 소득 47건을 포함한 서버 판정 테스트 117건과 빌드가 통과했습니다. 원천 매핑·기준표 계산은 아직 없습니다.

개발 전용 `/dev/income`에서 [소득 질문 미리보기](docs/development/income-question-preview.md)를 제공합니다. 대상·기간·단위를 안내하고 구간·모름 답변의 확인·수정·삭제와 기간 변경 시 초기화를 점검합니다. 구간 추가 확인은 별도 인공 상황이며 현재 답변을 판정한 결과가 아닙니다. 실제 소득을 입력할 필요가 없고 서버 전송·저장·판정 연결은 없습니다. 웹 테스트 17건·린트·타입 검사·빌드와 운영 경로 404를 확인했습니다.

개발 전용 `/dev/eligibility`에서 [자격 결과·근거 미리보기](docs/development/eligibility-result-preview.md)를 제공합니다. 네 가지 고정 인공 결과로 모집 상태와 자격 안내의 분리, 항목별 미확인 이유, 정책 검토 이슈와 근거 펼치기를 점검합니다. 화면은 전체 상태를 다시 계산하지 않습니다. 결과 표시 작업에서 웹 테스트 24건·린트·타입 검사·빌드, 운영 경로 404와 모바일·데스크톱 표시를 확인했습니다. 실제 정책·서버 판정에는 연결하지 않았습니다.

서버에 [모집 기간 상태 계산](docs/development/recruitment-period.md)을 추가했습니다. 확인된 날짜 범위는 서울 날짜로, 시각 범위는 명시된 시간대로 비교합니다. 상시·소진 시 종료·기간 미확인에 가짜 마감일을 만들지 않습니다. `npm run test:recruitment`로 인증키·DB 없이 검사할 수 있습니다. 이 모델을 추가할 때 모집 23건과 기존 자격 판정 117건, 총 140건 및 서버 빌드가 통과했습니다. 현재 개발용 인공 자료만 화면에 연결했고 원천 파싱·실제 정책·알림은 아직 연결하지 않았습니다.

별도 일정 모듈에 [마감 알림 후보 날짜 계산](docs/development/deadline-reminder-candidates.md)을 추가했습니다. 서울 마감 날짜에서 D-7·D-3·D-1을 계산하고, 지난 날짜는 제외하며 오늘 후보는 발송 시각 확인 필요로 남깁니다. `npm run test:reminders`로 17건을 실행할 수 있습니다. 마감 날짜 제공 8건을 포함한 서버 단위 테스트 총 165건과 빌드가 통과했습니다. 실제 저장·수신 동의·예약·발송은 아직 구현하지 않았습니다.

개발 전용 `/dev/reminders`에서 [마감·알림 후보 미리보기](docs/development/deadline-reminder-preview.md)를 제공합니다. 날짜형·시각형 마감, 오늘 후보의 발송 시각 확인 필요, 후보가 없는 이유를 일곱 가지 고정 인공 자료로 표시합니다. 화면은 날짜나 모집 상태를 계산하지 않으며 실제 서버·저장·예약·발송과 연결하지 않았습니다. 웹 테스트 32건·린트·타입 검사·빌드, 운영 경로 404와 모바일·데스크톱 표시를 확인했습니다.

별도 `/dev/reminders/server`에는 [개발 서버 계산](docs/development/reminder-preview-api.md)을 연결했습니다. `npm run dev:preview-api`로 DB·인증키 없이 실행하며 기존 모집·후보 모델의 계산 결과를 받습니다. 서버 DTO → OpenAPI → TypeScript 생성과 계약 일치 검사를 추가했습니다. 서버 172건·웹 39건, 린트·타입 검사·빌드와 연결 실패 후 복구·운영 404를 확인했습니다. 실제 정책·회원·예약·발송 연결은 아닙니다.

GitHub Actions에 웹·서버 병렬 CI를 구성했습니다. 인증키 없이 기존 테스트·린트·타입 검사·빌드를 실행하며 서버는 실제 PostgreSQL 통합 테스트를 포함합니다. 로컬 검사와 CI 문법 검사는 통과했지만, 원격 푸시와 GitHub 실행은 아직 하지 않았습니다. 범위와 남은 확인 사항은 [CI 안내](docs/development/ci.md)를 참고합니다.

`/dev/eligibility/server`에는 [자격 판정·항목별 근거의 서버 계산](docs/development/eligibility-preview-api.md)을 연결했습니다. 고정 인공 규칙과 답변을 기존 비교기·집계 모델로 계산하며 실제 조건 입력은 읽지 않습니다. 서버 175건·웹 45건과 생성 계약·린트·타입 검사·빌드, 연결 실패 후 복구·운영 404를 확인했습니다.

`/dev/eligibility/interactive`에서는 [인공 취업·소득 답변 재판정](docs/development/eligibility-answer-trial.md)을 점검합니다. 정해진 답변 코드를 기존 서버 비교기로 계산하며 질문 개정·기준 변경 시 답변과 결과를 초기화합니다. 답변 변경 후 늦게 도착한 응답은 무시합니다. 서버 180건·웹 53건, 생성 계약·린트·타입 검사·빌드, 오류 후 재시도·운영 404를 확인했습니다. 실제 개인정보는 받지 않습니다.

서버에 [정책 개정 적용 판단](docs/development/policy-revision-application.md)을 추가했습니다. 원본 참조와 비교 내용을 구분하고 같은 내용의 재수집·낮은 순번의 지연 응답·실패를 현재 개정에 반영할지 계산합니다. `npm run test:policy-revisions`로 11건을 실행할 수 있으며 전체 서버 191건과 빌드가 통과했습니다. DB·수집기·화면에는 연결하지 않은 내부 모델이며 실제 원본 저장이나 DB의 중복 방지 구현은 아닙니다.

별도 수집 모듈에 [실행 진행·실패·재처리 위치 모델](docs/development/collection-run-progress.md)을 추가했습니다. 페이지·항목별 시도 이력을 유지하고 실패 위치만 다시 선택하며, 중단한 시도의 늦은 결과를 무시합니다. 빈 페이지를 종료로 추정하지 않고 검토 필요와 기술적 실패를 구분합니다. 추가 당시 수집 진행 12건과 전체 서버 203건·빌드가 통과했습니다. 메모리의 진행 상태만 다루며 실제 API 수집·DB 저장·서버 재시작 복구는 아직 없습니다.

같은 모듈에 [AI 후보의 개정·버전 검사](docs/development/policy-ai-candidates.md)를 추가했습니다. 정책 개정·원본 근거·생성 방식·AI 요청 순번을 확인하고, 이전 개정의 후보를 현재 결과로 제공하지 않습니다. 실패·한도 보류는 기존 후보와 정책을 지우지 않습니다. 추가 당시 `npm run test:ai-candidates`의 12건과 전체 서버 215건·빌드가 통과했습니다. 실제 AI 호출·본문 품질 검사·자동 공개는 아직 없습니다.

후속 [AI 요청 전 판단](docs/development/ai-request-admission.md)에서는 후보 재사용, 신규·변경 개정과 명시적 재시도, 주어진 예산·예약액·최대 비용을 확인합니다. 예산이나 비용이 미확인이면 보류하고, 잔액이 충분해도 ‘예산 예약 필요’까지만 반환합니다. AI 예산 배분·단가는 정하지 않았으며 실제 예약·정산·과금 차단은 없습니다. 추가 당시 전용 14건과 전체 서버 229건·빌드가 통과했습니다.

[AI 요청 예약·정산 상태](docs/development/ai-budget-reservation-lifecycle.md)에서는 요청별 최대 비용 보유, 외부 호출, 결과 미확인, 실제 비용 정산과 확인된 무과금 해제를 구분합니다. 타임아웃이면 예약액을 유지하고 종료 결과가 충돌하면 기존 확정을 바꾸지 않습니다. 순수 모델 13건과 DB 없는 수집 검사 51건이 통과했습니다.

Flyway V1·V2와 PostgreSQL 예약 저장소도 추가했습니다. 최초 예약은 최신 잔액을 잠가 확인하고, 호출 식별자·결과 미확인·정산·호출 전 취소·확인된 무과금 해제까지 멱등하게 저장합니다. 정산과 해제는 예약 상태와 예산 합계를 한 트랜잭션에서 바꾸며, 미완료 상태는 재시작 후 조회할 수 있습니다. PostgreSQL 전용 12건과 전체 서버 254건·빌드가 통과했습니다. 실제 AI 호출·공급자 청구 조회·외부 비용 상한 검증은 아직 없습니다.

[AI 요청 실행 포트와 인공 실행기](docs/development/policy-ai-execution.md)도 추가했습니다. 예약과 호출 식별 정보를 저장한 뒤 DB 트랜잭션 밖에서 공급자 독립 포트를 실행하고, 결과 미확인·청구 대기·확인된 비용·무과금을 기존 상태에 반영합니다. 인공 실행 7건과 전체 서버 261건·빌드가 통과했습니다. 실제 공급자·정책 본문·프롬프트·후보 DB 저장은 아직 없습니다.

[AI 미완료 예약 복구 저장소](docs/development/ai-reservation-recovery.md)는 `HELD`·`DISPATCHED`·`OUTCOME_UNKNOWN` 예약의 확인 작업자를 제한된 시간 동안 한 명만 선택하고, 만료·완료 시도의 시작/종료 단계를 보존합니다. 복구 전용 7건과 전체 서버 268건·빌드가 통과했습니다. 실제 복구 작업자·공급자 조회·임대 갱신·상태 전이 펜싱은 아직 없습니다.

온통청년 API 인증키는 신청 후 승인 대기 중입니다. 인증된 성공 응답, 정책 조회·수집, 실제 정책을 연결한 자격 판정, 로그인·저장·알림 기능은 아직 구현하지 않았습니다. 서버 계산 연결은 개발용 인공 자료에 한정합니다. 조건 입력 검사는 신청 자격 판정이 아니며 실제 정책이나 가상 추천 결과를 표시하지 않습니다.

발급 후 사용할 단건 응답 점검 명령 `npm run probe:ontong`을 준비했습니다. 인증키를 노출하지 않고 미검증 JSON 응답을 로컬에 보관하는 개발 도구이며, 운영 수집기나 실제 API 검증 완료를 뜻하지 않습니다.

주요 스택은 Next.js 16·React 19·TypeScript, Java 25·Spring Boot 4.1 모듈러 모놀리스, PostgreSQL 18입니다. 전체 선택과 책임 경계는 ADR을 기준으로 합니다. 월 운영비 상한은 3만 원이며 운영 장비·클라우드는 아직 정하지 않았습니다.

## 문서

- [PRD-0001: MVP 기준과 완료 조건](docs/PRD/0001_product-baseline/spec.md)
- [ADR-0001: 기술 스택과 책임 분리](docs/ADR/0001_기술스택과_책임_분리.md)
- [ADR-0002: 서버 DTO 기반 API 계약 생성](docs/ADR/0002_서버_DTO_기반_API_계약_생성.md)
- [최초 제품 합의 기록](docs/alignments/seoul-mvp.html)
- [전용 스킬과 재사용 출처](docs/development/skill-reuse.md)
- [로컬 개발 환경과 검증 명령](docs/development/local-development.md)
- [CI 구성과 검증 범위](docs/development/ci.md)
- [비회원 조건 입력의 구현 범위](docs/development/guest-conditions.md)
- [공통 상태 화면과 개발 미리보기](docs/development/page-states.md)
- [추가 확인 질문과 답변 미리보기](docs/development/employment-question-preview.md)
- [소득 구간 질문과 추가 확인 미리보기](docs/development/income-question-preview.md)
- [자격 결과·근거 표시와 개발 미리보기](docs/development/eligibility-result-preview.md)
- [자격 판정 결과 집계와 근거 구조](docs/development/eligibility-decision.md)
- [명시적 연령 조건 비교와 미지원 범위](docs/development/age-condition.md)
- [명시적 거주 조건 비교와 기준일 처리](docs/development/residence-condition.md)
- [단일 취업 조건 비교와 답변 재사용 제한](docs/development/employment-condition.md)
- [명시적 소득 구간 비교와 입력 기준 확인](docs/development/income-condition.md)
- [모집 기간·마감 상태의 날짜와 시각 설계](docs/design/recruitment-period.md)
- [모집 기간 상태 구현과 경계 검증](docs/development/recruitment-period.md)
- [마감 알림 후보 날짜 설계](docs/design/deadline-reminder-candidates.md)
- [마감 알림 후보 계산과 검증](docs/development/deadline-reminder-candidates.md)
- [마감·알림 후보 표시와 개발 미리보기](docs/development/deadline-reminder-preview.md)
- [개발 전용 마감 계산 API·생성 계약·서버 연결](docs/development/reminder-preview-api.md)
- [개발 전용 자격 판정 API·근거·서버 연결](docs/development/eligibility-preview-api.md)
- [인공 취업·소득 답변 변경과 서버 재판정](docs/development/eligibility-answer-trial.md)
- [온통청년 인증키 설정과 응답 점검](docs/development/ontong-api-probe.md)
- [온통청년 API 조사와 확인할 계약](docs/research/ontong-api-contract.md)
- [정책 수집·판정 데이터 구조 초안](docs/design/policy-data-model.md)
- [원본 확인·개정 적용·저장 원자성 설계](docs/design/policy-revision-application.md)
- [정책 개정 적용 판단 모델과 검증](docs/development/policy-revision-application.md)
- [수집 실행·부분 실패·재처리 위치 설계](docs/design/collection-run-progress.md)
- [수집 진행 모델과 검증·저장 전제](docs/development/collection-run-progress.md)
- [정책 개정·생성 버전과 AI 후보 재사용 설계](docs/design/policy-ai-candidates.md)
- [AI 후보 수용·늦은 결과 차단 모델과 검증](docs/development/policy-ai-candidates.md)
- [AI 요청 전 재사용·비용 확인 설계](docs/design/ai-request-admission.md)
- [AI 사전 판단과 실제 예약·과금 차단의 경계](docs/development/ai-request-admission.md)
- [AI 요청별 예산 예약·결과 미확인·정산 설계](docs/design/ai-budget-reservation-lifecycle.md)
- [AI 예약·정산 상태 모델과 실제 DB 경계](docs/development/ai-budget-reservation-lifecycle.md)
- [AI 실행 순서와 공급자 분리 설계](docs/design/policy-ai-execution.md)
- [AI 실행 포트와 인공 실행기 검증](docs/development/policy-ai-execution.md)
- [AI 미완료 예약 복구 소유권 설계](docs/design/ai-reservation-recovery.md)
- [AI 미완료 예약 복구 저장소 검증](docs/development/ai-reservation-recovery.md)
- [취업 조건 비교 범위와 추가 확인 설계](docs/design/employment-condition.md)
- [소득 입력 의미와 구간 비교 설계](docs/design/income-condition.md)
- [현재 작업 인계](HANDOFF.md)

## 개발 시작

[AGENTS.md](AGENTS.md)와 HANDOFF를 읽고 요청에 맞는 `skills/`의 전용 스킬을 적용합니다. 웹은 Node.js 24 LTS와 npm, 서버는 Gradle 실행용 JDK 21 이상이 필요합니다. 앱에 필요한 Java 25는 Gradle 도구체인으로 준비합니다. DB 연결 서버·통합 테스트에는 Docker가 필요하지만 개발 전용 인공 자료 API는 DB·Docker 없이 실행할 수 있습니다.

조건 입력 화면은 인증키·DB·백엔드 없이 실행할 수 있습니다. 저장소 루트에서 실행합니다.

```sh
npm ci
npm run dev:web
```

인공 자료의 서버 계산은 다른 터미널에서 `npm run dev:preview-api`를 실행한 뒤 [마감 서버 연결](http://127.0.0.1:3000/dev/reminders/server), [자격 서버 연결](http://127.0.0.1:3000/dev/eligibility/server) 또는 [답변 재판정](http://127.0.0.1:3000/dev/eligibility/interactive)을 엽니다. DB 연결 서버도 확인하려면 DB를 준비하고 다른 터미널에서 백엔드를 실행합니다.

```sh
npm run db:up
npm run dev:backend
```

- 웹: <http://127.0.0.1:3000>
- 조건 입력: <http://127.0.0.1:3000/conditions>
- 백엔드 상태 확인: <http://127.0.0.1:8080/actuator/health>
- 설정 변경·검증·종료 방법: [로컬 개발 안내](docs/development/local-development.md)
