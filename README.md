# 청년정책메이트

서울 거주 청년이 내 조건에 맞는 정책과 판단 근거를 확인하고, 관심 정책의 신청 마감과 변경 내용을 관리하는 웹앱입니다. 콘텐츠를 읽는 데 그치지 않고 조건·저장·일정·알림 상태를 계속 관리하는 사용 흐름을 기준으로 개발합니다. 설치형 PWA·오프라인 사용·네이티브 앱 패키징·웹 푸시는 아직 확정하지 않았습니다.

현재 제공하는 11개 정책의 질문·판정은 [공고별 조건 데이터](docs/development/policy-rule-data.md)로 관리합니다. 검토한 새 버전을 적용하면 서버 재시작 없이 질문과 조건 비교가 바뀝니다. [관리자 조건 검토·적용](docs/development/policy-rule-review.md)에서 원문 변경·미등록 정책을 찾고 기존 규칙 편집·초안 저장·검토 후 적용을 처리합니다. 파일 등록도 지원합니다. 확인한 생년월일은 9개 정책의 연령 질문에 재사용합니다. [AI 규칙 추출](docs/development/ai-rule-drafts.md)은 운영 명령으로 OpenAI 호출·예산 예약·원 응답 보관·검토용 초안 저장·청구 확인을 처리합니다. [수집 공고 자동 추출](docs/development/ai-rule-automation.md)은 신규·변경 공고를 처리량 제한 안에서 선택하고 중단 후 같은 요청으로 재개합니다. [관리자 AI 추출](docs/development/admin-ai-runs.md)에서 실행 상태·추출 결과·비용 상태를 검색하고 초안을 검토합니다. 실제 모델·키·요금 설정과 생성 품질·청구 검증은 남아 있습니다.

[외부 API 연동과 홈서버 실행 준비](docs/development/external-api-runtime.md)에 Resend 발송·웹훅, 운영 환경변수, Dockerfile 사용법과 Ingress 경로를 정리했습니다. 앱 빌드와 모의 연동은 검증했으며 이미지 빌드·실제 공급자 등록·홈서버 배포는 운영자가 진행합니다.

관리자는 [이메일 발송 현황](docs/development/admin-email-deliveries.md)에서 기간·상태·종류별 내역과 건수를 조회할 수 있습니다. 주소·본문을 노출하지 않으며 조회로 재발송하지 않습니다.

정책 알림 메일은 [본문 링크와 표준 수신 해제 헤더](docs/development/email-unsubscribe.md)를 제공합니다. 로그인 없이 이메일 알림을 끌 수 있으며 관심 정책과 서비스 내 알림은 유지합니다.

[이메일 암호화 키 교체 명령](docs/development/email-key-rotation.md)은 주소와 수신 동의를 유지하며 저장 주소를 재암호화합니다. 사전 점검과 임시 DB 검증을 마쳤으며 실제 교체는 서비스 중지 후 운영자가 진행합니다.

[OpenAI 월 비용 조회](docs/development/openai-costs.md)는 지정 프로젝트의 집계액을 통화별로 확인합니다. 별도 관리자 키를 사용하며 AI 생성이나 DB 정산을 실행하지 않습니다.

운영 주소를 설정하면 [공개 페이지 검색·공유 정보](docs/development/public-page-metadata.md)를 제공합니다. 개인 화면과 검색·필터 결과는 검색에서 제외하며, 개발 환경의 검색 제외도 유지합니다.

## MVP

- 서울 거주 만 19~34세 청년 대상. 온통청년의 전국 공통·서울시·서울 자치구 정책 제공
- 조건 입력에 따른 3단계 자격 안내와 항목별 근거
- 비회원 검색, 카카오·네이버 로그인 후 조건·관심 정책 저장
- 저장한 정책의 [마감순 일정·접수 상태 필터](docs/development/member-calendar.md)와 [알림 페이지·안 읽은 알림 필터](docs/development/member-notifications.md), 별도로 활성화하는 이메일 알림
- 관심 정책의 [저장 당시·현재 내용 비교](docs/development/saved-policy-changes.md)
- 정책 자동 수집·공개, 명확한 조건만 자동 판정, 예외만 선택 검수
- 실제 신청은 공식 사이트에서 진행. 증빙서류 수집과 신청 대행은 제외

## 현재 상태

2026-09-08에 [정책명·운영 기관 보정](docs/development/policy-corrections.md)을 연결했습니다. 수집 원본을 보존하고 보정 사유·작업자·적용 개정을 기록합니다. 새 원본과 충돌하면 현재 공개 내용을 유지하며, 관리자가 보정 유지 또는 원본 적용을 선택합니다. [관리자 수집 예외 관리](docs/development/admin-collection-exceptions.md)의 실패 조회·개정 비교·항목 재처리와 함께 사용할 수 있습니다. 서버·웹·모바일·중복 요청을 검증했으며 실제 관리자 계정 연결은 남아 있습니다.

2026-09-06에 [접수 상태 표시](docs/development/policy-recruitment-display.md)를 연결했습니다. 정책 목록·상세·내 조건 결과에서 접수 기간·접수 전·마감·상시·기간 미확인을 구분하고 계산 근거를 표시합니다. 마감 알림과 원문 해석을 공유합니다.

2026-09-12 기준 [개인 조건 기반 정책 탐색](docs/development/condition-policy-discovery.md)에서 9개 정책의 연령을 비교하고 전체 정책에서 검색·정렬합니다. [미래 청년 일자리 5월 모집 질문](docs/development/future-youth-jobs-questions.md) 추가로 질문 제공 범위는 **11개 정책**입니다. 근로시간·계약기간, 재학·사업자등록 예외를 구분하며 증빙과 최종 선발은 기관 확인으로 남깁니다. 수집 안내와 공식 공고의 신청기간·재학 예외·참고 링크 차이도 표시합니다.

2026-09-05에 [서울청년정책네트워크 하반기 모집 질문](docs/development/seoul-youth-network-questions.md)을 추가했습니다. 공고의 출생일 범위·군복무 연장, 서울 거주 또는 대학·직장 생활권, 연임·위촉 제한을 구분합니다. 이미 마감한 모집임을 질문과 결과에 표시하며, 당시 질문 제공은 **5개 정책**이었습니다. 서버 전체 448건과 빌드가 통과했습니다.

2026-09-05에 [청년주택드림청약통장 가입 질문](docs/development/youth-housing-savings-questions.md)을 추가했습니다. 가입일 연령·본인 무주택·소득서류 기준·소득 금액을 묻고 병역 예외와 미확인 답변을 구분합니다. 당시 질문 제공 범위는 **4개 정책**이었습니다. 은행의 가입·전환 심사와 우대금리·세제·대출 조건은 별도 확인 사항으로 표시합니다. 서버 전체 440건과 빌드가 통과했습니다.

2026-09-05에 [화면 문구](docs/development/ui-wording.md)를 정리했습니다. 질문·결과·이메일 인증 용어를 통일하고 버튼 이름을 실제 동작에 맞췄습니다. 반복 안내를 줄이고 마감 알림을 제공하지 못하는 사유를 구분했습니다. 자격 판단 기준과 이메일 수신 동의 방식은 유지했습니다.

2026-09-05에 [K-패스 가입·이용 공통요건 질문](docs/development/kpass-questions.md)을 추가했습니다. 연령·회원가입과 카드 등록·참여 지역 확인·월 이용 횟수 4개를 비교하고 첫 가입 월의 예외와 처리 대기를 구분합니다. 달이 바뀌면 이전 답변을 다시 받습니다. 당시 질문 제공 범위는 **3개 정책**이었으며 실제 환급액은 계산하지 않습니다. 서버 전체 431건·빌드·실제 모바일 및 데스크톱 흐름을 확인했습니다.

2026-09-05에 [공통요건 질문이 있는 정책 찾기](docs/development/policy-question-discovery.md)를 추가했습니다. 목록의 질문 표시·필터·검색과 기본 조건 결과에서 상세 질문으로 이동할 수 있습니다. 당시 정책 40건 중 질문 제공 2건이었으며 원문 변경·적용 연도에 따라 서버가 제공 여부를 다시 확인합니다. 서버 422건·웹 66건과 계약·린트·타입·빌드, 실제 모바일·데스크톱·키보드 이동을 확인했습니다.

2026-09-05에 [청년 국가기술자격 응시료 지원 공통요건](docs/development/exam-fee-questions.md)을 추가했습니다. 공식 안내의 출생일 범위·대상 시험·큐넷 잔여 횟수를 비교하고 복구 대기·미확인은 추가 확인으로 남깁니다. 당시 국가근로장학금을 포함해 실제 **2개 정책의 공통요건 비교**를 제공했습니다. 서버 전체 420건과 빌드가 통과했으며, 시험 응시자격·실시간 예산·결제 할인 확정은 포함하지 않습니다.

2026-09-05에 [이메일 주소 확인·수신 동의·발송 대기 처리](docs/development/member-email-reminders.md)를 추가했습니다. `/my`의 알림에서 주소 확인과 별도 수신 동의·해제·삭제를 관리합니다. 저장 해제·정책 개정·서울 날짜를 발송 직전에 다시 확인하고, 접수 여부가 불명확한 메일은 자동 재발송하지 않습니다. 기본값은 비활성화이며 실제 SMTP 공급자·발신 주소·암호화 키를 설정해야 사용할 수 있습니다. 실제 외부 메일은 보내지 않았습니다. 서버 전체 413건·웹 62건, 생성 계약·타입·린트·빌드와 모바일·데스크톱의 대표 흐름을 확인했습니다.

2026-09-05에 [지정 범위 수집·재개·정기 실행](docs/development/policy-range-collection.md)을 추가했습니다. 페이지별 진행 위치를 저장하고 호출 간격·서울 날짜별 요청 한도·중복 발송 차단을 적용합니다. 실제 3~4페이지를 수집해 로컬 정책은 **40건**으로 늘었습니다. 서버 전체 403건과 빌드가 통과했습니다. 정기 실행은 운영 한도와 주기를 설정한 뒤 켜야 하며 현재 비활성화입니다.

2026-09-05에 [국가근로장학금 추가 질문·공통요건 비교](docs/development/work-study-questions.md)를 실제 정책 상세에 연결했습니다. 국적·학적·성적·학자금 지원구간과 기준 적용 제외 여부를 확인합니다. 답변 수정 시 결과를 지우고, 정책이 바뀌면 최신 질문을 다시 받습니다. 서버 전체 393건·웹 61건, API 계약·린트·타입 검사와 Webpack 빌드가 통과했습니다.

**조건 비교는 정책별 일부 요건만 확인합니다.** 대학·기관·은행의 증빙 확인, 추가 참여 제한과 선발 심사는 남아 있어 최종 신청 자격은 추가 확인 필요로 표시합니다. 다른 정책의 규칙 검토·개인별 추천 순위·이메일 공급자 실연동·정기 수집의 실제 운영 설정은 남은 작업입니다.

[조건 확인·회원 저장·서비스 내 알림](docs/development/member-policy-flow.md)도 제공합니다. `/conditions`에서 실제 원문 요건을 확인하고, 설정된 소셜 로그인 후 기본 조건과 관심 정책을 저장할 수 있습니다. `/my`는 마감 일정·변경 알림·읽음 처리와 [회원 탈퇴·개인 데이터 삭제](docs/development/member-withdrawal.md)를 제공합니다. 카카오·네이버 앱 키가 로컬에 없어 실제 소셜 제공자 로그인은 미확인입니다.

2026-09-05에 **실제 정책 20건의 저장·검색·상세 조회**를 연결했습니다. `/policies`에서 제목·설명을 검색하고 지원 내용·신청 안내·공식 링크·수집 시각을 확인할 수 있습니다. 당시 온통청년 서울 필터 20건으로 시작했으며 범위 수집 후 현재 40건입니다. 전체 정책 목록이나 개인별 자격 판정은 아닙니다. [조회 실행 방법](docs/development/policy-catalog.md)을 참고합니다.

Spring Batch와 PostgreSQL로 [한 페이지 수집·실행 이력·저장 원본 재처리](docs/development/limited-policy-collection.md)를 추가했습니다. `npm run collect:policy -- --args='fetch 2'`는 서울 필터의 지정 페이지를 최대 10건만 한 번 요청합니다. 실패 항목은 보관한 원본으로 재처리하며 정상 정책은 유지합니다. 이번 서버 전체 373건과 빌드가 통과했습니다. 이후 범위·정기 수집 코드를 추가했으며, 정기 실행은 아직 켜지 않았습니다.

## 앞선 단계의 구현 기록

아래 검증 수와 미구현 표시는 각 작업 당시의 기록입니다. 현재 이용 범위는 위 ‘현재 상태’를 따릅니다.

2026-08-30에 MVP 범위·기술 스택을 확정하고 온통청년 API를 조사한 뒤, Next.js·Spring Boot와 로컬 PostgreSQL의 기본 개발 환경을 구성했습니다. 프런트엔드 빌드, 실제 DB를 사용하는 백엔드 테스트와 로컬 기동을 확인했습니다.

API 인증키 없이 사용할 수 있는 비회원 조건 입력 화면(`/conditions`)을 구현했습니다. 홈은 설명보다 조건 입력 시작을 우선하는 앱 화면으로 구성했고, 모바일 하단 탭과 데스크톱 상단 메뉴에서 홈·내 조건을 이동할 수 있습니다. 생년월일·서울 자치구·주된 취업상태를 입력하고, 확인·수정·초기화할 수 있습니다. 입력 중에는 화면 상태로만 사용하고, 정책 확인 버튼을 눌렀을 때만 서버에 보냅니다. 계정 저장은 로그인 후 별도 버튼으로 선택합니다. 화면 입력은 새로고침하면 초기화됩니다. 화면 기준은 [웹앱 인터페이스](docs/design/webapp-interface.md)에 정리했습니다.

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

[AI 요청 예약·정산](docs/development/ai-budget-reservation-lifecycle.md)에서는 요청별 최대 비용 보유, 외부 호출, 결과 미확인, 실제 비용 정산과 확인된 무과금 해제를 구분합니다. 타임아웃이면 예약액을 유지하고 종료 결과가 충돌하면 기존 확정을 바꾸지 않습니다. 예약·정산 규칙은 PostgreSQL 저장소에서 구현·검증하며 별도 메모리 상태 엔진은 유지하지 않습니다.

Flyway V1·V2와 PostgreSQL 예약 저장소도 추가했습니다. 최초 예약은 최신 잔액을 잠가 확인하고, 호출 식별자·결과 미확인·정산·호출 전 취소·확인된 무과금 해제까지 멱등하게 저장합니다. 정산과 해제는 예약 상태와 예산 합계를 한 트랜잭션에서 바꾸며, 미완료 상태는 재시작 후 조회할 수 있습니다. PostgreSQL 전용 12건과 전체 서버 254건·빌드가 통과했습니다. 실제 OpenAI 호출은 AI 규칙 추출 경로에 연결했습니다. 공급자 청구 자동 조회·외부 비용 상한 검증은 남아 있습니다.

[AI 요청 실행 포트와 인공 실행기](docs/development/policy-ai-execution.md)도 추가했습니다. 예약과 호출 식별 정보를 저장한 뒤 DB 트랜잭션 밖에서 공급자 독립 포트를 실행하고, 결과 미확인·청구 대기·확인된 비용·무과금을 기존 상태에 반영합니다. 인공 실행 7건과 전체 서버 261건·빌드가 통과했습니다. OpenAI 규칙 추출을 이 실행기에 연결했으며 원 응답과 초안은 V26·V27에 보관합니다.

AI 예약 복구 작업자·후보 상태·요청 전 판단·개정 적용 순수 모델은 운영 경로에 연결되지 않아 2026-10-05 제거했습니다. 결과가 미확인인 예약은 관리 명령으로 정산·해제합니다. [결정 기록](docs/development/backend-api-review.md#ai-예약-복구-코드-제거--2026-10-05-적용)

온통청년 인증키와 목록·상세 성공 응답 확보 이후 실제 정책 조회·지정 페이지 수집·회원 저장·서비스 내 알림·국가근로장학금 공통요건 비교로 확장했습니다. 현재 범위와 외부 설정 제약은 문서 상단을 따릅니다.

실제 응답을 확보하는 점검 명령 `npm run probe:ontong`을 준비했습니다. 인증키를 노출하지 않고 미검증 JSON 응답을 로컬에 보관하는 개발 도구이며, 자동 운영 수집기는 아닙니다. 확인한 성공 계약과 남은 항목은 API 조사 문서에서 구분합니다.

주요 스택은 Next.js 16·React 19·TypeScript, Java 25·Spring Boot 4.1 모듈러 모놀리스, PostgreSQL 18입니다. 전체 선택과 책임 경계는 ADR을 기준으로 합니다. 월 운영비 상한은 3만 원이며 운영 장비·클라우드는 아직 정하지 않았습니다.

로컬 DB는 [백업과 복구 검증](docs/development/database-backup.md) 명령을 제공합니다. 백업 파일을 만든 뒤 별도 임시 DB에 실제 복원해 확인합니다.

## 문서

- [PRD-0001: MVP 기준과 완료 조건](docs/PRD/0001_product-baseline/spec.md)
- [ADR-0001: 기술 스택과 책임 분리](docs/ADR/0001_기술스택과_책임_분리.md)
- [ADR-0002: 서버 DTO 기반 API 계약 생성](docs/ADR/0002_서버_DTO_기반_API_계약_생성.md)
- [최초 제품 합의 기록](docs/alignments/seoul-mvp.html)
- [프로젝트 지시·스킬 관리](docs/development/skill-reuse.md)
- [변경별 검증 범위와 실행 기록](docs/development/verification-workflow.md)
- [로컬 개발 환경과 검증 명령](docs/development/local-development.md)
- [CI 구성과 검증 범위](docs/development/ci.md)
- [모바일 우선 웹앱 인터페이스 기준](docs/design/webapp-interface.md)
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
- [정책 개정·생성 버전과 AI 후보 재사용 설계](docs/design/policy-ai-candidates.md)
- [AI 요청 전 재사용·비용 확인 설계](docs/design/ai-request-admission.md)
- [AI 요청별 예산 예약·결과 미확인·정산 설계](docs/design/ai-budget-reservation-lifecycle.md)
- [AI 예약·정산 상태 모델과 실제 DB 경계](docs/development/ai-budget-reservation-lifecycle.md)
- [AI 실행 순서와 공급자 분리 설계](docs/design/policy-ai-execution.md)
- [AI 실행 포트와 인공 실행기 검증](docs/development/policy-ai-execution.md)
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
