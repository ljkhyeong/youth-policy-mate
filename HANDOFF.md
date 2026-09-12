# 작업 인계

2026-09-12 기준. 개발 이력은 Git과 [개발 문서](docs/development/)에서 확인한다.

## 현재 작업

- 로컬 `main`의 `24c924c`까지 원격에 푸시했고 [웹·전체 서버 CI](https://github.com/ljkhyeong/youth-policy-mate/actions/runs/34684593518)를 통과했다. 이후 작업은 `codex/condition-member-recovery`에 있으며 원격에는 반영하지 않았다.
- `42e8c78`에서 이메일 암호화 키의 사전 점검·일괄 교체 명령을 추가했다. 주소·인증 완료·수신 동의·정책 발송 기록은 유지하며 미완료 확인 코드와 대기 확인 메일만 만료·취소한다. 전용 환경변수 빈 예시를 제공하며 실제 키·운영 DB는 변경하지 않았다. [명령·운영 순서](docs/development/email-key-rotation.md)
- 임시 PostgreSQL에서 점검·교체·손상 주소 롤백·잠금 충돌과 독립 명령을 검증했다. 서버 전체 테스트·배포 JAR 빌드와 JAR 명령 진입을 통과했다. 웹·계약·스키마 변경이 없어 기존 웹 검증을 재사용했다. [범위·로그](docs/development/email-key-rotation.md#검증)
- 검증용 DB와 명령 프로세스는 종료했다. 실제 회원 변경·외부 공급자 호출·이미지 빌드는 없었다. 기존 서버·운영 설정·사용자 창·탭을 변경하지 않았고 브라우저를 실행하지 않았다.
- 정기 수집·AI 자동 처리·이메일·알림은 비활성화다. 실제 OAuth·Resend 연동, 이미지 빌드·공유기·TLS·k3s·백업 운영 설정은 사용자가 진행한다. 백업·외부 공급자 데이터의 보관/삭제와 관리자 기록 보관 기준은 남아 있다.

## 구현·검증 범위

- 정책 조회: 로컬 수집 40건의 검색·상세·원문 링크를 제공한다. 검토된 원문 충돌은 상세에서 별도 안내하며 빈 안내 목록이 검토 완료를 뜻하지 않는다. 서울 대상 전체 정책을 수집한 상태는 아니다. [조회](docs/development/policy-catalog.md)·[수집과 재개](docs/development/policy-range-collection.md)·[충돌 안내](docs/development/policy-source-notices.md)
- 공개 페이지: 운영 주소를 기준으로 검색 허용·대표 주소·공개 공유 제목과 설명을 만든다. 정책 목록의 페이지별 주소와 개인/검색/오류 화면의 검색 제외를 구분한다. [설정과 기본 사이트맵](docs/development/public-page-metadata.md)
- 관리자 수집 예외: `/admin/collection-exceptions`에서 원본·직전 개정 비교와 사유를 남기는 항목 재처리를 제공한다. `/pages`는 페이지 실패, `/replays`는 재처리 이력, `/corrections`는 보정 관리다. 정책명·운영 기관 중 한 항목을 원본과 분리해 보정하고, 새 원본과 충돌하면 현재 내용을 유지한 뒤 관리자가 해소한다. V19·V20에 작업자·사유·적용 개정을 기록하며 같은 요청은 한 번만 처리한다. AI 추출 목록에서는 마지막 시도와 현재 결과·비용 상태를 조회한다. 실제 관리자 계정 연결은 남아 있다. [AI 추출 조회](docs/development/admin-ai-runs.md)·[설정과 계약](docs/development/admin-collection-exceptions.md)·[보정 범위](docs/development/policy-corrections.md)
- 조건 질문: 국가근로장학금·응시료 지원·K-패스·청년주택드림청약통장·서울청년정책네트워크·상반기 이사비 지원·청년내일저축계좌·전세보증금반환보증 보증료 지원·햇살론유스·청년 미래이음 대출·미래 청년 일자리 5월 모집을 제공한다. 정책별 소득·중복지원·참여 제한·보증한도의 미확인을 구분한다. 실제 증빙·기타 제한·선발은 기관 심사가 필요하며 전체 자격은 추가 확인으로 유지한다. [질문 탐색](docs/development/policy-question-discovery.md)·[이사비](docs/development/moving-fee-questions.md)·[저축계좌](docs/development/youth-tomorrow-savings-questions.md)·[보증료](docs/development/guarantee-fee-questions.md)·[햇살론유스](docs/development/haetsalron-youth-questions.md)·[미래이음](docs/development/miso-youth-future-questions.md)·[미래 청년 일자리](docs/development/future-youth-jobs-questions.md)
- 접수 상태: 목록·상세·내 조건에서 날짜형·시각형·상시·마감·미확인을 구분하며 공개 목록과 내 조건을 상태별로 검색한다. 검색·질문 필터·정렬과 함께 전체 결과에 적용한 뒤 페이지를 나눈다. Flyway V18로 기존 정책의 검색용 기간을 이전했다. 기존 마감 알림과 원문 해석을 공유한다. 날짜 충돌·회차·소진 안내를 임의로 접수 중으로 바꾸지 않는다.
- 개인 조건 탐색: 미래 청년 일자리를 포함한 9개 정책의 연령을 비교하고 전체 정책에서 검색·정렬한다. 정책별 기준일·출생일 범위·병역 예외를 구분하며 햇살론유스·청년 미래이음 대출은 오늘 보증·대출신청을 가정한다. 거주·취업·소득은 추가 확인으로 남긴다. 검색·페이지 이동·오류 재시도·모바일 화면을 로컬 실제 정책으로 확인했다.
- 회원 기능: [회원 탈퇴·개인 데이터·전체 세션 삭제](docs/development/member-withdrawal.md), OAuth 로그인 코드, 관심 정책 저장·해제·[저장 상태 오류 복구](docs/development/policy-save-recovery.md)·[저장 당시와 현재 내용 비교](docs/development/saved-policy-changes.md), 일정·[서비스 내 알림 페이지와 미읽음 필터](docs/development/member-notifications.md)를 연결했다. [마감 일정](docs/development/member-calendar.md)에서 접수 상태를 구분하고 가까운 마감순 정렬·상태 필터·새로고침을 제공한다. 테스트 제공자와 실제 HTTP·DB로 코드 교환부터 관리자 접근·세션 종료까지 확인했다. 로컬 카카오·네이버 ID/Secret과 관리자 ID가 없어 실제 제공자 로그인은 미검증이다. [회원 기능](docs/development/member-policy-flow.md)
- 이메일: 주소 확인·동의·암호화 저장·Outbox·SMTP/Resend 어댑터와 서명 웹훅, [관리자 발송 현황](docs/development/admin-email-deliveries.md)·[공급자 상태 조회](docs/development/email-provider-status.md), [로그인 없는 수신 해제](docs/development/email-unsubscribe.md), [설정 오류 복구](docs/development/email-settings-recovery.md)·[키 점검과 교체 명령](docs/development/email-key-rotation.md)을 구현했다. 실제 공급자 계정·발신 도메인·외부 수신 확인과 운영 키 관리는 남아 있다. [이메일 구현과 설정](docs/development/member-email-reminders.md)
- AI: 공고 원문·OpenAI 호출·예산 예약·원 응답·규칙 초안을 연결했다. 응답 유실에는 예약을 유지하고 재호출하지 않는다. 수집된 신규/변경 공고의 자동 추출과 실제 청구·무과금 확인 명령을 제공한다. 공급자 청구 자동 조회·실제 생성 품질 검증은 남아 있다. 모델·요금·월 한도는 설정값으로 받고 미설정 상태에는 호출하지 않는다. [규칙 추출과 설정](docs/development/ai-rule-drafts.md)·[자동 추출](docs/development/ai-rule-automation.md)·[요청 판단](docs/development/ai-request-admission.md)·[복구 실행](docs/development/ai-reservation-recovery-work-runs.md)
- 백업: [수동 백업·임시 DB 복구 검증](docs/development/database-backup.md)을 제공한다. PostgreSQL 기본 도구를 사용하며 원본 DB를 덮어쓰지 않는다. 정기 백업의 주기·보관 기간·외부 보관과 운영 DB 전환은 미정이다.
- 검증 기준: `42e8c78`까지 전체 서버 테스트·빌드와 키 교체 명령을 통과했다. 웹·생성 계약은 `1547da6`에서 바뀌지 않아 기존 검사·빌드·헤드리스 결과를 유지했다. 원격 CI 기준은 `main`의 `24c924c`다. 이후 문서만 변경했으며 미커밋 코드는 없다. 실제 운영 키 교체·bfcache 저장/복원 전체 과정·공급자 송수신/상태 조회·홈서버 이미지 실행·실제 검색 색인과 작업 브랜치의 원격 CI는 미검증이다.
- 검증 도구의 성공·실패·실행 중 변경 시나리오와 기존 도구 검사를 통과했다. 컴파일·패키징 명령의 Gradle 실행 계획에 테스트 실행이 없음을 확인했다. `npm run verify -- status`에서 실행 기록을 확인한다. 검증 도구를 정리할 당시에는 앱 기능을 변경하지 않아 전체 앱 테스트를 재실행하지 않았다.

## 남은 작업

이메일 키 교체를 위한 코드·환경변수 예시·실행 절차를 준비했다. 다음 목록을 근거로 실제 설정 없이 기능을 활성화하거나 운영 키를 바꾸지 않는다.

다음 항목은 실제 연동·운영 설정이 필요한 작업이며, 관련 설정은 사용자가 진행한다.

1. OpenAI 모델·키·입출력 단가·요금 유효기간·월 AI 한도를 `.env`에 설정한 뒤 한 공고의 실제 생성 품질·청구를 확인한다. 확인 후 AI 자동 처리의 일일 한도·간격·시도 횟수를 정해 활성화한다. 초기 미처리 공고에도 같은 한도를 적용한다. 현재는 자동/수동 추출·초안 저장·수동 정산을 제공하며 공급자 청구 자동 조회와 실제 운영 검증은 남아 있다. 이전 공고의 연도만 바꾸거나 AI 결과를 바로 확정하지 않는다. 거주·취업·소득 재사용은 의미·기준일을 맞춘 뒤 확장한다.
2. 카카오 또는 네이버 앱의 ID/Secret·리다이렉트 설정을 확인한 뒤 실제 로그인·로그아웃·관리자 회원 ID 연결을 검증한다. 키는 채팅 대신 루트 `.env`에서 관리한다.
3. 자격·마감 조건이나 미공개 정책의 보정이 필요하면 허용 범위·공개 기준을 먼저 정한다. 현재는 공개 정책의 정책명·운영 기관 한 항목만 보정한다.
4. 사용자가 Resend 계정·발신 도메인·웹훅을 등록한 뒤 실제 수신·반송·수신 해제를 검증한다. 관리자 상태 조회를 사용하려면 `full_access` 권한의 `RESEND_READ_API_KEY`를 별도로 설정하고 발송 ID가 있는 기록으로 확인한다. 수신 해제 헤더가 DKIM 서명에 포함되는지와 실제 메일 서비스의 버튼·본문 링크를 확인한다. 웹훅·수신 해제·공급자 조회 코드는 모의 연동과 PostgreSQL로 검증했다.
5. 실제 수집 한도·범위·주기를 정해 정기 수집을 활성화하고 실패 후 재개를 확인한다.
6. 월 3만 원 예산 안에서 배포·정기 백업의 주기/보관/외부 저장·운영 DB 복구 전환·운영 인증·개인정보 처리 안내·백업/외부 공급자/관리자 기록의 보관·삭제 기준을 정한다. 회원 탈퇴와 현재 DB의 개인 데이터 삭제, 수동 백업·임시 DB 복구 검증·이메일 키 교체 도구는 구현했다. 실제 키 교체 시 모든 API를 중지하고 DB 재암호화 후 새 키로 재시작한다. 이전 백업에 필요한 키의 보관·폐기는 운영자가 정한다. 배포 후 공개 도메인의 검색 메타데이터·robots·사이트맵과 실제 검색 엔진 색인을 확인한다.

## 이어서 작업할 때

- 제품 동작은 [PRD](docs/PRD/0001_product-baseline/spec.md), 기술 선택은 [ADR](docs/ADR/), 적용 스킬은 [AGENTS.md](AGENTS.md)를 따른다.
- 로컬 실행은 [개발 환경](docs/development/local-development.md), 실제 정책 서버 연결은 [조회 문서](docs/development/policy-catalog.md)를 참고한다. 현재 프로세스·브랜치·환경 설정은 실행 시점에 확인한다.
- 최근 검증의 `JAVA_HOME`은 `/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`이다. `test:ai-reservations`는 PostgreSQL 검사로 Docker가 필요하다.
- 웹 빌드는 도구 내부 포트 사용 권한이 필요하다. 이전 제한 실행의 오류가 Turbopack 캐시에 남아 `frontend/.next/cache/turbopack`만 분리해 해결한 적이 있다. `43b85cd`에서는 정식 권한으로 캐시 변경 없이 통과했다. 같은 증상이 없으면 캐시를 지우지 않는다.
- Resend 모듈은 `RestClient.Builder` 구성을 제공하는 `spring-boot-starter-restclient`가 필요하다. 의존성을 모의 빈으로 가린 검사만 재사용하지 않도록 실제 Resend 모듈로 운영 프로필 시작을 검사한다. 이번에 전체 서버 검사를 통과했으며 문서 변경만으로 반복하지 않는다. 공급자 상태 조회는 자동 재시도하지 않고 조회 한도를 별도로 안내한다.
- 수집 필드·미확인 계약은 [온통청년 API 조사](docs/research/ontong-api-contract.md)에 있다. API 키 설정과 실제 응답 확보는 완료했으며 비밀값·전체 캡처는 Git에 넣지 않는다.
- 정기 수집·SMTP·AI 자동 추출·AI 복구 실행기의 기본값은 비활성화다. 외부 연동 검증에는 실제 설정이 필요하다. 운영은 사용자 홈서버 k3s를 대상으로 준비했으며 실제 환경 설정과 백업·외부 공급자·관리자 기록의 보관/삭제 기준은 사용자가 정한다.
