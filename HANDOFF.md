# 작업 인계

2026-09-12 기준. 개발 이력은 Git과 [개발 문서](docs/development/)에서 확인한다.

## 현재 작업

- 이전 누적 작업은 `1b7fbdd`로 로컬·원격 `main`에 반영했고 [웹·전체 서버 CI](https://github.com/ljkhyeong/youth-policy-mate/actions/runs/34222499053)를 통과했다.
- 현재 `codex/policy-source-notices`의 `ebaad91`에서 관리자 이메일 발송 현황을 추가했다. `/admin/collection-exceptions/email`에서 기간·상태·종류별 내역과 기간 전체 건수를 조회한다. 회원·주소·본문·인증 코드를 반환하지 않으며 조회로 재발송하거나 상태를 바꾸지 않는다.
- 관리자 API의 권한·조회·개인정보 제외·읽기 전용 동작, 관련 웹 테스트·웹 검사·생성 계약·웹 빌드·서버 실행 파일 검증을 통과했다. 별도 Node 24/임시 API/브라우저에서 필터·페이지·오류·키보드·390px 화면을 확인했다. [구현과 검증 로그](docs/development/admin-email-deliveries.md#검증)
- 로컬 Spring은 `/tmp/youth-admin-email-api-runtime.jar` 복사본·PID 82345·로그 `/tmp/youth-admin-email-api-runtime.log`다. Next 개발 서버는 PID 10257·로그 `/tmp/youth-admin-ai-web.log`를 유지했다. 상태 200·관리자 API 비회원 401·새 웹 페이지 200을 확인했다. DB는 V30이며 정책 40건을 유지했다. 임시 웹/API와 브라우저 검증 세션은 종료했다.
- 별도로 수정된 미커밋 파일 6개를 보존했다: `.env.production.example`, `ResendMemberEmailSender.java`, `ResendWebhookController.java`, `MemberFlowTest.java`, `ResendTransportTest.java`, `docs/development/external-api-runtime.md`. Resend User-Agent·웹훅 200 응답·알림 환경변수명 수정이며 이번 커밋에 포함하지 않았다. 현재 실행 파일에는 이 수정이 포함된다. `test:email`의 `.local/verification/1789187521181-d7fd7098.log`가 통과했고 현재 관련 파일 해시와 일치해 재사용했다.
- 실제 회원 설정·이메일·AI 호출은 실행하지 않았다. 자동 추출·정기 수집·이메일·알림은 비활성화다. 이미지 빌드·공급자 등록·공유기·TLS·k3s 설정은 사용자가 직접 수행하며 이번 브랜치는 원격에 반영하지 않았다.

## 구현·검증 범위

- 정책 조회: 로컬 수집 40건의 검색·상세·원문 링크를 제공한다. 검토된 원문 충돌은 상세에서 별도 안내하며 빈 안내 목록이 검토 완료를 뜻하지 않는다. 서울 대상 전체 정책을 수집한 상태는 아니다. [조회](docs/development/policy-catalog.md)·[수집과 재개](docs/development/policy-range-collection.md)·[충돌 안내](docs/development/policy-source-notices.md)
- 관리자 수집 예외: `/admin/collection-exceptions`에서 원본·직전 개정 비교와 사유를 남기는 항목 재처리를 제공한다. `/pages`는 페이지 실패, `/replays`는 재처리 이력, `/corrections`는 보정 관리다. 정책명·운영 기관 중 한 항목을 원본과 분리해 보정하고, 새 원본과 충돌하면 현재 내용을 유지한 뒤 관리자가 해소한다. V19·V20에 작업자·사유·적용 개정을 기록하며 같은 요청은 한 번만 처리한다. AI 추출 목록에서는 마지막 시도와 현재 결과·비용 상태를 조회한다. 실제 관리자 계정 연결은 남아 있다. [AI 추출 조회](docs/development/admin-ai-runs.md)·[설정과 계약](docs/development/admin-collection-exceptions.md)·[보정 범위](docs/development/policy-corrections.md)
- 조건 질문: 국가근로장학금·응시료 지원·K-패스·청년주택드림청약통장·서울청년정책네트워크·상반기 이사비 지원·청년내일저축계좌·전세보증금반환보증 보증료 지원·햇살론유스·청년 미래이음 대출·미래 청년 일자리 5월 모집을 제공한다. 정책별 소득·중복지원·참여 제한·보증한도의 미확인을 구분한다. 실제 증빙·기타 제한·선발은 기관 심사가 필요하며 전체 자격은 추가 확인으로 유지한다. [질문 탐색](docs/development/policy-question-discovery.md)·[이사비](docs/development/moving-fee-questions.md)·[저축계좌](docs/development/youth-tomorrow-savings-questions.md)·[보증료](docs/development/guarantee-fee-questions.md)·[햇살론유스](docs/development/haetsalron-youth-questions.md)·[미래이음](docs/development/miso-youth-future-questions.md)·[미래 청년 일자리](docs/development/future-youth-jobs-questions.md)
- 접수 상태: 목록·상세·내 조건에서 날짜형·시각형·상시·마감·미확인을 구분하며 공개 목록과 내 조건을 상태별로 검색한다. 검색·질문 필터·정렬과 함께 전체 결과에 적용한 뒤 페이지를 나눈다. Flyway V18로 기존 정책의 검색용 기간을 이전했다. 기존 마감 알림과 원문 해석을 공유한다. 날짜 충돌·회차·소진 안내를 임의로 접수 중으로 바꾸지 않는다.
- 개인 조건 탐색: 미래 청년 일자리를 포함한 9개 정책의 연령을 비교하고 전체 정책에서 검색·정렬한다. 정책별 기준일·출생일 범위·병역 예외를 구분하며 햇살론유스·청년 미래이음 대출은 오늘 보증·대출신청을 가정한다. 거주·취업·소득은 추가 확인으로 남긴다. 검색·페이지 이동·오류 재시도·모바일 화면을 로컬 실제 정책으로 확인했다.
- 회원 기능: OAuth 로그인 코드, 관심 정책 저장·해제·[저장 당시와 현재 내용 비교](docs/development/saved-policy-changes.md), 일정·[서비스 내 알림 페이지와 미읽음 필터](docs/development/member-notifications.md)를 연결했다. [마감 일정](docs/development/member-calendar.md)에서 접수 상태를 구분하고 가까운 마감순 정렬·상태 필터·새로고침을 제공한다. 테스트 제공자와 실제 HTTP·DB로 코드 교환부터 관리자 접근·세션 종료까지 확인했다. 로컬 카카오·네이버 ID/Secret과 관리자 ID가 없어 실제 제공자 로그인은 미검증이다. [회원 기능](docs/development/member-policy-flow.md)
- 이메일: 주소 확인·동의·암호화 저장·Outbox·SMTP/Resend 어댑터와 서명 웹훅, [관리자 발송 현황](docs/development/admin-email-deliveries.md)을 구현했다. 실제 공급자 계정·발신 도메인과 외부 수신 확인은 남아 있다. [이메일 구현과 설정](docs/development/member-email-reminders.md)
- AI: 공고 원문·OpenAI 호출·예산 예약·원 응답·규칙 초안을 연결했다. 응답 유실에는 예약을 유지하고 재호출하지 않는다. 수집된 신규/변경 공고의 자동 추출과 실제 청구·무과금 확인 명령을 제공한다. 공급자 청구 자동 조회·실제 생성 품질 검증은 남아 있다. 모델·요금·월 한도는 설정값으로 받고 미설정 상태에는 호출하지 않는다. [규칙 추출과 설정](docs/development/ai-rule-drafts.md)·[자동 추출](docs/development/ai-rule-automation.md)·[요청 판단](docs/development/ai-request-admission.md)·[복구 실행](docs/development/ai-reservation-recovery-work-runs.md)
- 백업: [수동 백업·임시 DB 복구 검증](docs/development/database-backup.md)을 제공한다. PostgreSQL 기본 도구를 사용하며 원본 DB를 덮어쓰지 않는다. 정기 백업의 주기·보관 기간·외부 보관과 운영 DB 전환은 미정이다.
- 검증 기준: 새 관리자 이메일 기능은 `ebaad91`의 관련 API·웹·계약·빌드로 확인했다. 별도 Resend 수정은 위 이메일 테스트 기록을 사용한다. 변경하지 않은 전체 서버의 기준은 `be626f1`이며 이번 조회 추가만으로 전체 서버 검사를 반복하지 않았다. 실제 공급자 송수신·홈서버 이미지 실행·원격 CI는 미검증이다.
- 검증 도구의 성공·실패·실행 중 변경 시나리오와 기존 도구 검사를 통과했다. 컴파일·패키징 명령의 Gradle 실행 계획에 테스트 실행이 없음을 확인했다. `npm run verify -- status`에서 실행 기록을 확인한다. 검증 도구를 정리할 당시에는 앱 기능을 변경하지 않아 전체 앱 테스트를 재실행하지 않았다.

## 남은 작업

1. OpenAI 모델·키·입출력 단가·요금 유효기간·월 AI 한도를 `.env`에 설정한 뒤 한 공고의 실제 생성 품질·청구를 확인한다. 확인 후 AI 자동 처리의 일일 한도·간격·시도 횟수를 정해 활성화한다. 초기 미처리 공고에도 같은 한도를 적용한다. 현재는 자동/수동 추출·초안 저장·수동 정산을 제공하며 공급자 청구 자동 조회와 실제 운영 검증은 남아 있다. 이전 공고의 연도만 바꾸거나 AI 결과를 바로 확정하지 않는다. 거주·취업·소득 재사용은 의미·기준일을 맞춘 뒤 확장한다.
2. 카카오 또는 네이버 앱의 ID/Secret·리다이렉트 설정을 확인한 뒤 실제 로그인·로그아웃·관리자 회원 ID 연결을 검증한다. 키는 채팅 대신 루트 `.env`에서 관리한다.
3. 자격·마감 조건이나 미공개 정책의 보정이 필요하면 허용 범위·공개 기준을 먼저 정한다. 현재는 공개 정책의 정책명·운영 기관 한 항목만 보정한다.
4. 사용자가 Resend 계정·발신 도메인·웹훅을 등록한 뒤 실제 수신·반송·수신 해제를 검증한다. 서명·중복·상태 전이는 모의 연동과 PostgreSQL로 검증했다.
5. 실제 수집 한도·범위·주기를 정해 정기 수집을 활성화하고 실패 후 재개를 확인한다.
6. 월 3만 원 예산 안에서 배포·정기 백업의 주기/보관/외부 저장·운영 DB 복구 전환·운영 인증·개인정보 보관/삭제를 정하고 원격 CI를 확인한다. 수동 백업과 임시 DB 복구 검증은 구현했다.

## 이어서 작업할 때

- 제품 동작은 [PRD](docs/PRD/0001_product-baseline/spec.md), 기술 선택은 [ADR](docs/ADR/), 적용 스킬은 [AGENTS.md](AGENTS.md)를 따른다.
- 로컬 실행은 [개발 환경](docs/development/local-development.md), 실제 정책 서버 연결은 [조회 문서](docs/development/policy-catalog.md)를 참고한다. 현재 프로세스·브랜치·환경 설정은 실행 시점에 확인한다.
- 최근 검증의 `JAVA_HOME`은 `/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`이다. `test:ai-reservations`는 PostgreSQL 검사로 Docker가 필요하다.
- 수집 필드·미확인 계약은 [온통청년 API 조사](docs/research/ontong-api-contract.md)에 있다. API 키 설정과 실제 응답 확보는 완료했으며 비밀값·전체 캡처는 Git에 넣지 않는다.
- 정기 수집·SMTP·AI 자동 추출·AI 복구 실행기의 기본값은 비활성화다. 외부 연동 검증에는 실제 설정이 필요하다. 운영은 사용자 홈서버 k3s를 대상으로 준비했으며 실제 환경 설정과 개인정보 보관·삭제 정책은 사용자가 정한다.
