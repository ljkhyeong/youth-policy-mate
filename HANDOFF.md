# 작업 인계

2026-10-30 기준. 개발 이력은 Git과 [개발 문서](docs/development/)에서 확인한다.

## 현재 작업

- `codex/progressive-policy-discovery`에서 화면 디자인 점검 결과와 내 조건 접수 중 우선 정렬을 반영했다(2026-10-30). 이 브랜치는 원격에 올리지 않았고, 원격 `main`(`b7389ac`) 이후의 지역 필터·단어 검색(`b3011d7`·`10550f5`)과 이번 작업의 커밋이 모두 미반영이다.
- 정렬: 내 조건 결과에 `OPEN_FIRST`(접수 중 우선, 공개 목록과 같은 순서)를 추가해 생년월일을 반영하기 전의 기본값으로 쓴다. `AGE_MATCH`는 같은 연령 그룹 안에서 접수 중 우선 순서를 따르고, `sort`를 생략한 요청은 이전처럼 `AGE_MATCH`다. `RECENT`는 바뀌지 않았다. [조건 탐색 정렬](docs/development/condition-policy-discovery.md#2026-10-30-접수-중-우선-정렬)
- 디자인: Claude Design 디자인 시스템과 어긋난 표현을 바로잡았다. `<button>` 버튼만 보통 굵기이던 원인(레이어 밖 `font: inherit`)을 지우고, 머리말·본문·바닥글 가장자리를 `--page-max`·`--page-gutter`로 맞췄다. 선택 상자·이메일 입력은 2px 각진 칸, 상세 조건 질문은 본문 열에 맞췄고, 확인 안내는 점선 상자, 상태 화면은 위쪽 선, 회원 화면 체크 칸은 볼펜 체크로 바꿨다. 정책 찾기 필터에 이름표와 ‘질문 있는 정책’ 한 칸 켜고 끄기를, 정책 목록·알림에 전체 쪽수(`1 / 5페이지`)를 더했다. 디자인 점검은 서버·API를 바꾸지 않았다. Claude Design 디자인 시스템도 버전 11로 맞췄다(브랜드북·구성 요소 설명과 미리보기·PageState 카드·화면 폭 토큰). [점검 기록](docs/development/interface-design-review.md#2026-10-30-디자인-점검과-기준-정리)
- 전수 점검: 남은 두 결정을 권장안대로 적용하고 화면·상태를 빠짐없이 다시 점검했다.
  - 기본 버튼은 화면마다 대표 행동 하나다. 상세는 `질문에 답하기`, 홈은 `표시한 칸으로 정책 보기`, 내 조건은 `조건 반영`, 정책 찾기는 `검색`(조회 실패 시 `다시 불러오기`)이다.
  - 저장 버튼의 `aria-pressed`를 뺐다.
  - 640px 미만 머리말을 한 줄(51px)로 줄였다.
  - 스타일 없던 문장 속 링크와 공식 안내 링크, 홈 조회 실패 시 빈 칸, 상태 선 폭, 변경 비교의 원천 날짜 형식도 고쳤다.
  - 디자인 시스템은 버전 12로 맞췄다. [전수 점검](docs/development/interface-design-review.md#2026-10-30-전수-점검과-권장안-적용)
- 검증: 디자인은 `test:web`·`check:web`·`build:web`을 통과했고, 로컬 DB 90건·검증용 서버(8180)·웹(개발 3103, 운영 모드 3104)으로 헤드리스 확인했다(1280·390·320px, 밝은·어두운 화면, Tab 순서·질문 필터·체크 칸 키보드 조작, 회원 화면은 응답 모의). 정렬은 `generate:api` 후 `test:policy-catalog`·`test:member-flow`·`check:api-types`·`test:web`·`check:web`·`package:backend`·`build:web`을 통과했고, 새 실행 파일의 서버(8180)·운영 모드 웹(3103)으로 API와 화면(1280·390px)을 확인했다. 검증용 서버·웹·브라우저는 종료하고 DB 컨테이너는 내렸다(볼륨 유지). 전수 점검 뒤 `test:web`·`check:web`·`build:web`을 다시 통과했고, API를 끊은 운영 모드 웹(3105)으로 조회 실패 화면도 확인했다. 실제 화면 낭독기·Safari·Firefox는 확인하지 않았다. 전체 서버 검사(`check:backend`)는 이번에 실행하지 않았다.
- 로컬 DB 볼륨은 2026-10-07에 다시 만들었고 V1~V6이 적용돼 있다. 온통청년 서울 1~4페이지(2026-10-07)와 9·25~28페이지(2026-10-11)를 받아 90건이다(전체 730건). 검토 규칙이 있는 15개 정책 중 12개가 로컬에 있어 질문을 확인할 수 있다. 이전 볼륨은 `.local/backups/`에 백업했다. 정기 수집·AI 자동 처리·이메일·알림은 비활성화다. 실제 OAuth·Resend 연동, 이미지 빌드·공유기·TLS·k3s·백업 운영 설정은 사용자가 진행한다. 백업·외부 공급자 데이터의 보관/삭제와 관리자 기록 보관 기준은 남아 있다.

## 구현·검증 범위

- 정책 조회: 로컬 수집 90건의 단어 검색(띄어쓰기 무시, 제목·설명·운영 기관)·분야 필터·지역(자치구) 필터·상세·원문 링크를 제공한다. 지역 필터는 공고 지역 코드 기준이라 거주 자격 판정이 아니다. 공개 목록은 접수 중 정책을 마감 임박순으로 먼저 보여주고 접수 기간인 정책에 D-day를 표시한다. 상세에 온통청년 표기 조건을 참고로 보여주고, 홈은 “요즘 어떤 상황이세요?”로 상황(분야)을 여러 개 고르게 하고 분야별 정책 수를 함께 표시하며, 목록은 분야 여러 개를 함께 고를 수 있다. 여러 분야로 분류된 정책은 분야마다 포함한다. [화면 개편](docs/development/interface-design-review.md#2026-10-05-화면-개편) 검토된 원문 충돌은 상세에서 별도 안내하며 빈 안내 목록이 검토 완료를 뜻하지 않는다. 서울 대상 전체 정책을 수집한 상태는 아니다. [조회](docs/development/policy-catalog.md)·[지역 필터와 단어 검색](docs/development/policy-region-filter.md)·[수집과 재개](docs/development/policy-range-collection.md)·[충돌 안내](docs/development/policy-source-notices.md)
- 화면: ‘형광펜 공고문’ 디자인, 어두운 화면(기기 설정 추종·바닥글 선택), 탭·iOS 홈 화면용 앱 아이콘을 제공한다. 버튼 글자·화면 폭·입력 테두리·안내 상자·상태 화면은 2026-10-30 점검으로 디자인 시스템 기준에 맞췄다. Claude Design 디자인 시스템에는 밝은 화면 값만 있다. 2026-10-30 변경(버튼·입력·필터 줄·안내 상자·상태 안내·화면 폭 토큰)은 디자인 시스템 버전 11에 반영했다. [화면 기준](docs/design/webapp-interface.md#시각-기준)
- 공개 페이지: 운영 주소를 기준으로 검색 허용·대표 주소·공개 공유 제목과 설명을 만든다. 정책 목록의 페이지별 주소와 개인/검색/오류 화면의 검색 제외를 구분한다. [설정과 기본 사이트맵](docs/development/public-page-metadata.md)
- 관리자 수집 예외: `/admin/collection-exceptions`에서 원본·직전 개정 비교와 사유를 남기는 항목 재처리를 제공한다. `/pages`는 페이지 실패, `/replays`는 재처리 이력, `/corrections`는 보정 관리다. 정책명·운영 기관 중 한 항목을 원본과 분리해 보정하고, 새 원본과 충돌하면 현재 내용을 유지한 뒤 관리자가 해소한다. 재처리·보정 기록 테이블에 작업자·사유·적용 개정을 기록하며 같은 요청은 한 번만 처리한다. AI 추출 목록에서는 마지막 시도와 현재 결과·비용 상태를 조회한다. 실제 관리자 계정 연결은 남아 있다. [AI 추출 조회](docs/development/admin-ai-runs.md)·[설정과 계약](docs/development/admin-collection-exceptions.md)·[보정 범위](docs/development/policy-corrections.md)
- 조건 질문: 국가근로장학금·응시료 지원·K-패스·청년주택드림청약통장·서울청년정책네트워크·상반기 이사비 지원·청년내일저축계좌·전세보증금반환보증 보증료 지원·햇살론유스·청년 미래이음 대출·미래 청년 일자리 5월 모집·K-뉴딜 아카데미·국민취업지원제도·AI학업장려 학자금대출·청년도약 인재양성 부트캠프를 제공한다. 질문 선택지는 신청서 칸(라디오)으로 모두 펼쳐 보여준다. 정책별 소득·중복지원·참여 제한·보증한도의 미확인을 구분한다. 실제 증빙·기타 제한·선발은 기관 심사가 필요하며 전체 자격은 추가 확인으로 유지한다. [질문 탐색](docs/development/policy-question-discovery.md)·[이사비](docs/development/moving-fee-questions.md)·[저축계좌](docs/development/youth-tomorrow-savings-questions.md)·[보증료](docs/development/guarantee-fee-questions.md)·[햇살론유스](docs/development/haetsalron-youth-questions.md)·[K-뉴딜 아카데미](docs/development/k-newdeal-academy-questions.md)·[미래이음](docs/development/miso-youth-future-questions.md)·[미래 청년 일자리](docs/development/future-youth-jobs-questions.md)·[국민취업지원제도](docs/development/employment-support-questions.md)·[AI학업장려 학자금대출](docs/development/ai-study-loan-questions.md)·[부트캠프](docs/development/youth-leap-bootcamp-questions.md)
- 접수 상태: 목록·상세·내 조건에서 날짜형·시각형·상시·마감·미확인을 구분하며 공개 목록과 내 조건을 상태별로 검색한다. 검색·질문 필터·정렬과 함께 전체 결과에 적용한 뒤 페이지를 나눈다. 검색용 기간은 정책 반영 시 계산해 저장한다. 기존 마감 알림과 원문 해석을 공유한다. 날짜 충돌·회차·소진 안내를 임의로 접수 중으로 바꾸지 않는다.
- 개인 조건 탐색: 입력 없이 정책을 둘러보고 생년월일을 반영하면 미래 청년 일자리·K-뉴딜 아카데미·국민취업지원제도·AI학업장려 학자금대출·청년도약 인재양성 부트캠프를 포함한 13개 정책의 연령을 비교해 전체 정책에서 검색·정렬한다. 결과의 기본 정렬은 접수 중 우선이고, 생년월일을 반영하면 연령 조건 충족 우선(같은 그룹은 접수 중 우선)이다. 기본 조건은 모두 선택 입력이며 일부만 저장할 수 있다. [조건 입력](docs/development/guest-conditions.md) 정책별 기준일·출생일 범위·병역 예외를 구분하며 햇살론유스·청년 미래이음 대출은 오늘 보증·대출신청을 가정한다. 거주·취업·소득은 추가 확인으로 남긴다. 검토된 연령 비교가 없는 정책은 온통청년 표기 연령 범위와 만 나이를 참고로 보여준다. 검색·페이지 이동·오류 재시도·모바일 화면을 로컬 실제 정책으로 확인했다.
- 회원 기능: [회원 탈퇴·개인 데이터·전체 세션 삭제](docs/development/member-withdrawal.md), OAuth 로그인 코드, 관심 정책 저장·해제·[저장 상태 오류 복구](docs/development/policy-save-recovery.md)·[저장 당시와 현재 내용 비교](docs/development/saved-policy-changes.md), 일정·[서비스 내 알림 페이지와 미읽음 필터](docs/development/member-notifications.md)를 연결했다. [마감 일정](docs/development/member-calendar.md)에서 접수 상태를 구분하고 가까운 마감순 정렬·상태 필터·새로고침을 제공한다. 테스트 제공자와 실제 HTTP·DB로 코드 교환부터 관리자 접근·세션 종료까지 확인했다. 로컬 카카오·네이버 ID/Secret과 관리자 ID가 없어 실제 제공자 로그인은 미검증이다. [회원 기능](docs/development/member-policy-flow.md)
- 이메일: 주소 확인·동의·암호화 저장·Outbox·Resend 발송(단일 공급자)과 서명 웹훅, [관리자 발송 현황](docs/development/admin-email-deliveries.md)·[공급자 상태 조회](docs/development/email-provider-status.md), [로그인 없는 수신 해제](docs/development/email-unsubscribe.md), [설정 오류 복구](docs/development/email-settings-recovery.md)·[키 점검과 교체 명령](docs/development/email-key-rotation.md)을 구현했다. 실제 공급자 계정·발신 도메인·외부 수신 확인과 운영 키 관리는 남아 있다. [이메일 구현과 설정](docs/development/member-email-reminders.md)
- AI: 공고 원문·OpenAI 호출·예산 예약·원 응답·규칙 초안을 연결했다. 응답 유실에는 예약을 유지하고 재호출하지 않으며, 미확인 예약은 관리 명령으로 정산·해제한다. 신규/변경 공고의 자동 추출·수동 정산·[프로젝트 월 비용 조회](docs/development/openai-costs.md)를 제공한다. 요청별 청구 자동 대사·정산과 실제 생성 품질 검증은 남아 있다. 모델·요금·월 한도는 설정값으로 받고 미설정 상태에는 호출하지 않는다. [규칙 추출과 설정](docs/development/ai-rule-drafts.md)·[자동 추출](docs/development/ai-rule-automation.md)
- 백업: [수동 백업·임시 DB 복구 검증](docs/development/database-backup.md)을 제공한다. PostgreSQL 기본 도구를 사용하며 원본 DB를 덮어쓰지 않는다. 정기 백업의 주기·보관 기간·외부 보관과 운영 DB 전환은 미정이다.
- 검증 기준: 직전에 CI 통과를 확인한 원격 커밋은 `aec34de`(웹·전체 서버, 2026-10-16)다. 최신 로컬 명령·로그는 `npm run verify -- status`와 각 문서의 검증 절에 있다. 실제 비용 조회·운영 키 교체·bfcache 저장/복원 전체 과정·공급자 송수신/상태 조회·홈서버 이미지 실행·실제 검색 색인은 미검증이다.
- 검증 도구의 성공·실패·실행 중 변경 시나리오와 기존 도구 검사를 통과했다. 컴파일·패키징 명령의 Gradle 실행 계획에 테스트 실행이 없음을 확인했다. `npm run verify -- status`에서 실행 기록을 확인한다. 검증 도구를 정리할 당시에는 앱 기능을 변경하지 않아 전체 앱 테스트를 재실행하지 않았다.

## 남은 작업

이메일 키 교체와 OpenAI 월 비용 조회를 위한 코드·환경변수 예시·실행 절차를 준비했다. 다음 목록을 근거로 실제 설정 없이 기능을 활성화하거나 운영 키를 바꾸지 않는다.

다음 항목은 실제 연동·운영 설정이 필요한 작업이며, 관련 설정은 사용자가 진행한다.

1. 질문 규칙을 전체 정책으로 넓히는 주 경로는 AI 초안 자동 생성과 관리자 검토다. 루트 `.env`에 `OPENAI_API_KEY`·`OPENAI_MODEL`·`AI_INPUT_WON_PER_MILLION`·`AI_OUTPUT_WON_PER_MILLION`·`AI_PRICING_VERSION`·`AI_PRICING_VALID_UNTIL`·`AI_MAX_OUTPUT_TOKENS`·`AI_MONTHLY_LIMIT_WON`을 설정하고 `AI_ENABLED`를 켠 뒤 한 공고의 실제 생성 품질·청구를 확인한다(2026-10-05 기준 모두 미설정). OpenAI 모델·키·입출력 단가·요금 유효기간·월 AI 한도를 `.env`에 설정한 뒤 한 공고의 실제 생성 품질·청구를 확인한다. 확인 후 AI 자동 처리의 일일 한도·간격·시도 횟수를 정해 활성화한다. 초기 미처리 공고에도 같은 한도를 적용한다. 월 비용 조회를 사용하려면 별도 명령에 `OPENAI_ADMIN_KEY`·`OPENAI_COSTS_PROJECT_ID`를 주입하고 실제 프로젝트 집계를 확인한다. 집계로 요청별 예약을 해제하지 않으며 자동 대사·정산은 제공하지 않는다. 이전 공고의 연도만 바꾸거나 AI 결과를 바로 확정하지 않는다. 거주·취업·소득 재사용은 의미·기준일을 맞춘 뒤 확장한다.
2. 카카오 또는 네이버 앱의 ID/Secret·리다이렉트 설정을 확인한 뒤 실제 로그인·로그아웃·관리자 회원 ID 연결을 검증한다. 키는 채팅 대신 루트 `.env`에서 관리한다.
3. 자격·마감 조건이나 미공개 정책의 보정이 필요하면 허용 범위·공개 기준을 먼저 정한다. 현재는 공개 정책의 정책명·운영 기관 한 항목만 보정한다.
4. 사용자가 Resend 계정·발신 도메인·웹훅을 등록한 뒤 실제 수신·반송·수신 해제를 검증한다. 관리자 상태 조회를 사용하려면 `full_access` 권한의 `RESEND_READ_API_KEY`를 별도로 설정하고 발송 ID가 있는 기록으로 확인한다. 수신 해제 헤더가 DKIM 서명에 포함되는지와 실제 메일 서비스의 버튼·본문 링크를 확인한다. 웹훅·수신 해제·공급자 조회 코드는 모의 연동과 PostgreSQL로 검증했다.
5. 실제 수집 한도·범위·주기를 정해 정기 수집을 활성화하고 실패 후 재개를 확인한다.
6. 월 3만 원 예산 안에서 배포·정기 백업의 주기/보관/외부 저장·운영 DB 복구 전환·운영 인증·개인정보 처리 안내·백업/외부 공급자/관리자 기록의 보관·삭제 기준을 정한다. 회원 탈퇴와 현재 DB의 개인 데이터 삭제, 수동 백업·임시 DB 복구 검증·이메일 키 교체 도구는 구현했다. 실제 키 교체 시 모든 API를 중지하고 DB 재암호화 후 새 키로 재시작한다. 이전 백업에 필요한 키의 보관·폐기는 운영자가 정한다. 배포 후 공개 도메인의 검색 메타데이터·robots·사이트맵과 실제 검색 엔진 색인을 확인한다.
7. 공개 목록 지역 필터는 구현했다. 전체 수집 범위(5번)를 정한 뒤 자치구 전용 정책의 원문 거주 제한을 확인하고(조사 당시 자치구 전용 2건은 거주 제한 문구가 없었다) 필요하면 정책별 보정 범위를 정한다. 건수가 늘면 `regionCodes` GIN 인덱스와 검색 성능을 검토한다. [지역 필터와 단어 검색](docs/development/policy-region-filter.md)·[지역 필터 조사](docs/research/public-region-filter.md)
8. 조건 질문을 더 넓힌다. 2026-10-11에 국민취업지원제도·AI학업장려 학자금대출, 2026-10-14에 청년도약 인재양성 부트캠프를 추가했다. 로컬 DB(90건)에 남은 접수 중·상시 정책은 대학·기관 지원사업이나 기관별 조건이 크게 다른 사업이 대부분이라, 다음 후보는 로컬에 없는 서울 청년 주요 정책(전체 730건)에서 고르는 편이 낫다. 해당 공고는 `collect:policy -- --args='fetch <페이지>'`로 받는다(외부 호출이라 사용자가 요청할 때 실행, 범위 수집용 일일 한도·호출 간격은 `.env`에 미설정). AI학업장려 학자금대출 질문은 2026-11-18, 부트캠프 질문은 2027-03-01부터 멈추므로 다음 공고·2차년도 기준을 다시 검토한다. 청년예술인 적립계좌는 다음 모집 공고가 나오면 안내·접수 상태 보정을 다시 검토한다. AI 초안을 켜기 전에는 원문과 공식 안내를 대조해 `youth-policy-rule-data` 절차로 직접 추가한다.

## 이어서 작업할 때

- 제품 동작은 [PRD](docs/PRD/0001_product-baseline/spec.md), 기술 선택은 [ADR](docs/ADR/), 적용 스킬은 [AGENTS.md](AGENTS.md)를 따른다.
- 쿼리 매개변수로 enum을 표시 이름으로 받을 때 전역 `Converter`를 쓰지 않는다. Spring 바인더는 변환 실패 뒤 상수 이름으로 다시 변환하므로 컨트롤러에서 `fromLabel`처럼 직접 해석하고, OpenAPI에는 `@Schema(implementation = …)`로 enum을 남긴다.
- 스키마 변경은 다음 번호(V4부터)의 새 Flyway SQL로 추가한다. 운영 DB를 만든 뒤에는 적용한 파일을 고치지 않는다. 설정을 추가할 때는 `Environment.getProperty` 대신 기존 `*Properties` 레코드에 둔다.
- 로컬 실행은 [개발 환경](docs/development/local-development.md), 실제 정책 서버 연결은 [조회 문서](docs/development/policy-catalog.md)를 참고한다. 현재 프로세스·브랜치·환경 설정은 실행 시점에 확인한다.
- 최근 검증의 `JAVA_HOME`은 `/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`이다. `test:ai-reservation-db`는 PostgreSQL 검사로 Docker가 필요하다.
- 웹 빌드는 도구 내부 포트 사용 권한이 필요하다. 이전 제한 실행의 오류가 Turbopack 캐시에 남아 `frontend/.next/cache/turbopack`만 분리해 해결한 적이 있다. `43b85cd`에서는 정식 권한으로 캐시 변경 없이 통과했다. 같은 증상이 없으면 캐시를 지우지 않는다.
- Resend 모듈은 `RestClient.Builder` 구성을 제공하는 `spring-boot-starter-restclient`가 필요하다. 의존성을 모의 빈으로 가린 검사만 재사용하지 않도록 실제 Resend 모듈로 운영 프로필 시작을 검사한다. 이번에 전체 서버 검사를 통과했으며 문서 변경만으로 반복하지 않는다. 공급자 상태 조회는 자동 재시도하지 않고 조회 한도를 별도로 안내한다.
- 수집 필드·미확인 계약은 [온통청년 API 조사](docs/research/ontong-api-contract.md)에 있다. API 키 설정과 실제 응답 확보는 완료했으며 비밀값·전체 캡처는 Git에 넣지 않는다.
- 화면에서 먹색 기본 버튼은 한 화면에 하나만 쓴다. 새 화면을 만들면 대표 행동을 먼저 정하고 나머지는 보조 버튼으로 둔다. 문구가 바뀌는 버튼에는 `aria-pressed`를 함께 쓰지 않는다. 정책 상세 404(`notFound()`)를 개발 모드로 열면 루트 `<head>`의 화면 색 스크립트에 React 경고가 나오지만 동작에는 영향이 없다. `next/script`로 옮기면 첫 화면이 깜빡일 수 있어 그대로 둔다. [전수 점검](docs/development/interface-design-review.md#2026-10-30-전수-점검과-권장안-적용)
- 정기 수집·이메일 발송·AI 자동 추출의 기본값은 비활성화다. 외부 연동 검증에는 실제 설정이 필요하다. 운영은 사용자 홈서버 k3s를 대상으로 준비했으며 실제 환경 설정과 백업·외부 공급자·관리자 기록의 보관/삭제 기준은 사용자가 정한다.
