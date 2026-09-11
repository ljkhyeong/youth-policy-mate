# 작업 인계

2026-09-12 기준. 개발 이력은 Git과 [개발 문서](docs/development/)에서 확인한다.

## 현재 작업

- 이전 누적 작업은 `1b7fbdd`로 로컬·원격 `main`에 반영했고 [웹·전체 서버 CI](https://github.com/ljkhyeong/youth-policy-mate/actions/runs/34222499053)를 통과했다.
- 현재 `codex/policy-source-notices`의 `ef7a25e`에서 관리자 조건 검토 화면을 추가했다. `/admin/collection-exceptions/rules`에서 원문 변경·기간 만료·미등록 정책을 검색하고 직전 개정·원본·질문·초안을 확인한다. 조회만 제공하며 규칙 등록·적용은 기존 운영 명령을 사용한다. 기존 11개 정책의 데이터 기반 질문·판정은 유지한다.
- 전체 서버·DB·OpenAPI 계약, 관련 관리자 웹 테스트·타입 검사·최종 배포 빌드를 통과했다. 별도 테스트 API와 브라우저로 검색·필터·페이지·목록 복귀·키보드·모바일·오류 구분을 확인했다. 명령·로그·수정 이후 검증 범위는 [관리자 조건 검토](docs/development/policy-rule-review.md#검증)에 있다. 이전 정책별 판정·생년월일 재사용 검증은 [공고별 조건 데이터](docs/development/policy-rule-data.md#검증)에 있다.
- 로컬 DB는 V24·정책 40건·적용 규칙 11건이다. Spring은 PID 60653, 로그 `/tmp/youth-rule-review-runtime.log`, Next는 PID 70721로 확인했다. 실제 서버 정상 기동과 새 관리자 API의 비회원 401을 확인했다. 실행 시점에 재확인한다. 이번 브랜치는 원격에 반영하지 않았다.

## 구현·검증 범위

- 정책 조회: 로컬 수집 40건의 검색·상세·원문 링크를 제공한다. 검토된 원문 충돌은 상세에서 별도 안내하며 빈 안내 목록이 검토 완료를 뜻하지 않는다. 서울 대상 전체 정책을 수집한 상태는 아니다. [조회](docs/development/policy-catalog.md)·[수집과 재개](docs/development/policy-range-collection.md)·[충돌 안내](docs/development/policy-source-notices.md)
- 관리자 수집 예외: `/admin/collection-exceptions`에서 원본·직전 개정 비교와 사유를 남기는 항목 재처리를 제공한다. `/pages`는 페이지 실패, `/replays`는 재처리 이력, `/corrections`는 보정 관리다. 정책명·운영 기관 중 한 항목을 원본과 분리해 보정하고, 새 원본과 충돌하면 현재 내용을 유지한 뒤 관리자가 해소한다. V19·V20에 작업자·사유·적용 개정을 기록하며 같은 요청은 한 번만 처리한다. 실제 관리자 계정 연결은 남아 있다. [설정과 계약](docs/development/admin-collection-exceptions.md)·[보정 범위](docs/development/policy-corrections.md)
- 조건 질문: 국가근로장학금·응시료 지원·K-패스·청년주택드림청약통장·서울청년정책네트워크·상반기 이사비 지원·청년내일저축계좌·전세보증금반환보증 보증료 지원·햇살론유스·청년 미래이음 대출·미래 청년 일자리 5월 모집을 제공한다. 정책별 소득·중복지원·참여 제한·보증한도의 미확인을 구분한다. 실제 증빙·기타 제한·선발은 기관 심사가 필요하며 전체 자격은 추가 확인으로 유지한다. [질문 탐색](docs/development/policy-question-discovery.md)·[이사비](docs/development/moving-fee-questions.md)·[저축계좌](docs/development/youth-tomorrow-savings-questions.md)·[보증료](docs/development/guarantee-fee-questions.md)·[햇살론유스](docs/development/haetsalron-youth-questions.md)·[미래이음](docs/development/miso-youth-future-questions.md)·[미래 청년 일자리](docs/development/future-youth-jobs-questions.md)
- 접수 상태: 목록·상세·내 조건에서 날짜형·시각형·상시·마감·미확인을 구분하며 공개 목록과 내 조건을 상태별로 검색한다. 검색·질문 필터·정렬과 함께 전체 결과에 적용한 뒤 페이지를 나눈다. Flyway V18로 기존 정책의 검색용 기간을 이전했다. 기존 마감 알림과 원문 해석을 공유한다. 날짜 충돌·회차·소진 안내를 임의로 접수 중으로 바꾸지 않는다.
- 개인 조건 탐색: 미래 청년 일자리를 포함한 9개 정책의 연령을 비교하고 전체 정책에서 검색·정렬한다. 정책별 기준일·출생일 범위·병역 예외를 구분하며 햇살론유스·청년 미래이음 대출은 오늘 보증·대출신청을 가정한다. 거주·취업·소득은 추가 확인으로 남긴다. 검색·페이지 이동·오류 재시도·모바일 화면을 로컬 실제 정책으로 확인했다.
- 회원 기능: OAuth 로그인 코드, 관심 정책 저장·해제, 일정·서비스 내 알림을 연결했다. 테스트 제공자와 실제 HTTP·DB로 코드 교환부터 관리자 접근·세션 종료까지 확인했다. 로컬 카카오·네이버 ID/Secret과 관리자 ID가 없어 실제 제공자 로그인은 미검증이다. [회원 기능](docs/development/member-policy-flow.md)
- 이메일: 주소 확인·동의·암호화 저장·Outbox·SMTP 어댑터를 구현했다. 실제 공급자·발신 도메인은 미정이며 외부 수신함 전달은 미검증이다. [이메일 구현과 설정](docs/development/member-email-reminders.md)
- AI: 후보 개정 검사·비용 예약·DB 복구 흐름은 내부 모델과 테스트용 공급자로 검증했다. 실제 AI 호출·청구·운영 작업자는 미연결이다. [AI 요청 판단](docs/development/ai-request-admission.md)·[복구 실행](docs/development/ai-reservation-recovery-work-runs.md)
- 검증 기준: 최신 전체 서버·DB·계약 검증은 `ef7a25e`의 서버 구현 기준이다. 관리자 조회 API·생성 타입을 추가했고 공개 정책 API는 유지했다. 웹 검사 이후 검색 폼 배치만 조정해 최종 웹 빌드·브라우저로 확인했다. 원격 CI는 이번 작업에서 실행하지 않았다.
- 검증 도구의 성공·실패·실행 중 변경 시나리오와 기존 도구 검사를 통과했다. 컴파일·패키징 명령의 Gradle 실행 계획에 테스트 실행이 없음을 확인했다. `npm run verify -- status`에서 실행 기록을 확인한다. 검증 도구를 정리할 당시에는 앱 기능을 변경하지 않아 전체 앱 테스트를 재실행하지 않았다.

## 남은 작업

1. 수집·AI 후보를 원문 개정에 연결해 저장하고 검토 가능한 규칙 초안으로 만든다. 기존 11개 정책의 데이터 이전은 완료했다. 현재 형식으로 표현할 수 없는 조건은 추가 확인으로 남기며, 새 공고에 이전 규칙을 자동 이월하거나 AI 결과를 즉시 확정하지 않는다. 실제 공급자 호출·추출 본문 저장·자동 초안 생성은 미연결이다. 생년월일은 9개 정책의 연령 질문에 재사용하며 거주·취업·소득은 의미·기준일을 맞춘 뒤 확장한다.
2. 사용할 카카오 또는 네이버 앱의 ID/Secret과 리다이렉트를 설정한 뒤 실제 로그인·로그아웃·관리자 회원 ID 연결을 확인한다. 사용자에게 설정 여부를 물었으며 아직 답변·키 설정이 없다. 키는 채팅 대신 루트 `.env`에 입력한다.
3. 자격·마감 조건이나 미공개 정책의 보정이 필요하면 허용 범위·공개 기준을 먼저 정한다. 현재는 공개 정책의 정책명·운영 기관 한 항목만 보정한다.
4. 이메일 공급자·발신 도메인을 정하고 수신함 전달·반송·수신 해제·결과 미확인 처리를 검증한다.
5. 실제 수집 한도·범위·주기를 정해 정기 수집을 활성화하고 실패 후 재개를 확인한다.
6. AI 공급자와 비용 한도를 정해 실제 요약·조건 추출·과금 확인을 연결한다.
7. 월 3만 원 예산 안에서 배포·백업·운영 인증·개인정보 보관/삭제를 정하고 원격 CI를 확인한다.

## 이어서 작업할 때

- 제품 동작은 [PRD](docs/PRD/0001_product-baseline/spec.md), 기술 선택은 [ADR](docs/ADR/), 적용 스킬은 [AGENTS.md](AGENTS.md)를 따른다.
- 로컬 실행은 [개발 환경](docs/development/local-development.md), 실제 정책 서버 연결은 [조회 문서](docs/development/policy-catalog.md)를 참고한다. 현재 프로세스·브랜치·환경 설정은 실행 시점에 확인한다.
- 최근 검증의 `JAVA_HOME`은 `/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`이다. `test:ai-reservations`는 PostgreSQL 검사로 Docker가 필요하다.
- 수집 필드·미확인 계약은 [온통청년 API 조사](docs/research/ontong-api-contract.md)에 있다. API 키 설정과 실제 응답 확보는 완료했으며 비밀값·전체 캡처는 Git에 넣지 않는다.
- 정기 수집·SMTP·AI 복구 실행기의 기본값은 비활성화다. 외부 연동 검증에는 실제 설정이 필요하다. 운영 장비·클라우드와 개인정보 보관·삭제 정책은 아직 미정이다.
