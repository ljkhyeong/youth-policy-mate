# 작업 인계

2026-09-12 기준. 개발 이력은 Git과 [개발 문서](docs/development/)에서 확인한다.

## 현재 작업

- 이전 누적 작업은 `1b7fbdd`로 로컬·원격 `main`에 반영했고 [웹·전체 서버 CI](https://github.com/ljkhyeong/youth-policy-mate/actions/runs/34222499053)를 통과했다.
- 현재 `codex/policy-source-notices`의 `130d8de`에서 미래 청년 일자리(서울영커리언스 점프업)의 2026년 5월 모집 질문과 기본 연령 비교를 추가했다. 근로·재학·사업자등록 예외를 구분하고 수집 안내와 공식 공고의 차이를 표시한다. V21은 검토한 정책의 검색용 접수 시작일만 수정한다.
- 관련 서버 규칙·PostgreSQL 정책 API·계약 일치·마이그레이션·웹 검사·린트·타입 검사와 PC·모바일 흐름을 확인했다. 같은 출처의 여러 안내가 중복 항목으로 처리되던 문제는 `1c988f3`에서 수정했다. 명령·로그·재사용·미실행 범위는 [미래 청년 일자리 검증](docs/development/future-youth-jobs-questions.md#연결과-검증)에 있다. 미커밋 변경 없이 인계하며 이번 브랜치는 원격에 반영하지 않았다.

## 구현·검증 범위

- 정책 조회: 로컬 수집 40건의 검색·상세·원문 링크를 제공한다. 검토된 원문 충돌은 상세에서 별도 안내하며 빈 안내 목록이 검토 완료를 뜻하지 않는다. 서울 대상 전체 정책을 수집한 상태는 아니다. [조회](docs/development/policy-catalog.md)·[수집과 재개](docs/development/policy-range-collection.md)·[충돌 안내](docs/development/policy-source-notices.md)
- 관리자 수집 예외: `/admin/collection-exceptions`에서 원본·직전 개정 비교와 사유를 남기는 항목 재처리를 제공한다. `/pages`는 페이지 실패, `/replays`는 재처리 이력, `/corrections`는 보정 관리다. 정책명·운영 기관 중 한 항목을 원본과 분리해 보정하고, 새 원본과 충돌하면 현재 내용을 유지한 뒤 관리자가 해소한다. V19·V20에 작업자·사유·적용 개정을 기록하며 같은 요청은 한 번만 처리한다. 실제 관리자 계정 연결은 남아 있다. [설정과 계약](docs/development/admin-collection-exceptions.md)·[보정 범위](docs/development/policy-corrections.md)
- 조건 질문: 국가근로장학금·응시료 지원·K-패스·청년주택드림청약통장·서울청년정책네트워크·상반기 이사비 지원·청년내일저축계좌·전세보증금반환보증 보증료 지원·햇살론유스·청년 미래이음 대출·미래 청년 일자리 5월 모집을 제공한다. 정책별 소득·중복지원·참여 제한·보증한도의 미확인을 구분한다. 실제 증빙·기타 제한·선발은 기관 심사가 필요하며 전체 자격은 추가 확인으로 유지한다. [질문 탐색](docs/development/policy-question-discovery.md)·[이사비](docs/development/moving-fee-questions.md)·[저축계좌](docs/development/youth-tomorrow-savings-questions.md)·[보증료](docs/development/guarantee-fee-questions.md)·[햇살론유스](docs/development/haetsalron-youth-questions.md)·[미래이음](docs/development/miso-youth-future-questions.md)·[미래 청년 일자리](docs/development/future-youth-jobs-questions.md)
- 접수 상태: 목록·상세·내 조건에서 날짜형·시각형·상시·마감·미확인을 구분하며 공개 목록과 내 조건을 상태별로 검색한다. 검색·질문 필터·정렬과 함께 전체 결과에 적용한 뒤 페이지를 나눈다. Flyway V18로 기존 정책의 검색용 기간을 이전했다. 기존 마감 알림과 원문 해석을 공유한다. 날짜 충돌·회차·소진 안내를 임의로 접수 중으로 바꾸지 않는다.
- 개인 조건 탐색: 미래 청년 일자리를 포함한 9개 정책의 연령을 비교하고 전체 정책에서 검색·정렬한다. 정책별 기준일·출생일 범위·병역 예외를 구분하며 햇살론유스·청년 미래이음 대출은 오늘 보증·대출신청을 가정한다. 거주·취업·소득은 추가 확인으로 남긴다. 검색·페이지 이동·오류 재시도·모바일 화면을 로컬 실제 정책으로 확인했다.
- 회원 기능: OAuth 로그인 코드, 관심 정책 저장·해제, 일정·서비스 내 알림을 연결했다. 테스트 제공자와 실제 HTTP·DB로 코드 교환부터 관리자 접근·세션 종료까지 확인했다. 로컬 카카오·네이버 ID/Secret과 관리자 ID가 없어 실제 제공자 로그인은 미검증이다. [회원 기능](docs/development/member-policy-flow.md)
- 이메일: 주소 확인·동의·암호화 저장·Outbox·SMTP 어댑터를 구현했다. 실제 공급자·발신 도메인은 미정이며 외부 수신함 전달은 미검증이다. [이메일 구현과 설정](docs/development/member-email-reminders.md)
- AI: 후보 개정 검사·비용 예약·DB 복구 흐름은 내부 모델과 테스트용 공급자로 검증했다. 실제 AI 호출·청구·운영 작업자는 미연결이다. [AI 요청 판단](docs/development/ai-request-admission.md)·[복구 실행](docs/development/ai-reservation-recovery-work-runs.md)
- 검증 기준: `1b7fbdd`의 전체 CI 이후 정책 질문·기본 연령 비교와 화면 구성을 개선했다. 최신 기능 `130d8de`는 [미래 청년 일자리 검증](docs/development/future-youth-jobs-questions.md#연결과-검증), 앞선 문구 정리는 [문구 검증](docs/development/ui-wording.md) 범위에서 확인했다. API 구조·생성 타입·의존성·설정은 유지했고 로컬 DB는 V21이다. 미변경 범위의 성공 결과를 재사용했으며 전체 CI와 웹 빌드는 재실행하지 않았다.
- 검증 도구의 성공·실패·실행 중 변경 시나리오와 기존 도구 검사를 통과했다. 컴파일·패키징 명령의 Gradle 실행 계획에 테스트 실행이 없음을 확인했다. `npm run verify -- status`에서 실행 기록을 확인한다. 검증 도구를 정리할 당시에는 앱 기능을 변경하지 않아 전체 앱 테스트를 재실행하지 않았다.

## 남은 작업

1. 개별 정책 전용 코드 추가보다 공고 변경을 데이터로 반영하는 구조를 우선한다. 현재 공통 조건 비교 모듈은 있지만 실제 질문 서비스는 정책별 코드·연도·원문 해시에 연결되어 있고, AI 후보 본문 저장과 실제 추출 연결은 미구현이다. 공고별 적용 기간·기준일·조건·예외·근거를 버전으로 저장하고 기존 비교 모듈과 질문 화면에 연결한다. 기존 정책 2~3개를 먼저 이전해 조건 값·연도 변경에 코드 배포가 필요 없는지 검증한다. 공통 입력은 의미·기준일이 같은 경우만 재사용하고 정책별 예외만 추가로 묻는다. 새 공고에 이전 규칙을 무조건 이월하거나 AI 추출을 즉시 확정 판정에 사용하지 않는다. 이 구조가 검증되기 전에는 개별 정책 추가를 확대하지 않는다. 아직 구현 전인 다음 개발 우선순위다.
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
