# 작업 인계

2026-09-07 기준. 개발 이력은 Git과 [개발 문서](docs/development/)에서 확인한다.

## 현재 작업

- 이전 코드 정리 작업은 `d6ef9d2`까지 로컬 `main`에 병합했다. 원격 푸시는 하지 않았다.
- `codex/condition-policy-discovery`의 `a03b02d`에서 청약통장의 기본 조건 연령 비교를 추가했다. 개인 조건 검색·정렬, 이사비 질문, 접수 상태 표시·필터가 포함된 브랜치이며 아직 `main`에 병합하지 않았다.
- 비교 범위·개인정보 처리와 최근 검증 명령·로그·실행 경로는 [개인 조건 탐색](docs/development/condition-policy-discovery.md)에 있다. 이후 변경은 문서뿐이다.

## 구현·검증 범위

- 정책 조회: 로컬 수집 40건의 검색·상세·원문 링크를 제공한다. 서울 대상 전체 정책을 수집한 상태는 아니다. [조회](docs/development/policy-catalog.md)·[수집과 재개](docs/development/policy-range-collection.md)
- 조건 질문: 국가근로장학금·응시료 지원·K-패스·청년주택드림청약통장·서울청년정책네트워크·상반기 이사비 지원 6개 정책을 제공한다. 일부 요건만 비교하므로 전체 자격은 추가 확인 필요다. [질문 탐색](docs/development/policy-question-discovery.md)·[최근 추가 정책](docs/development/moving-fee-questions.md)
- 접수 상태: 목록·상세·내 조건에서 날짜형·시각형·상시·마감·미확인을 구분하며 공개 목록과 내 조건을 상태별로 검색한다. 검색·질문 필터·정렬과 함께 전체 결과에 적용한 뒤 페이지를 나눈다. Flyway V18로 기존 정책의 검색용 기간을 이전했다. 기존 마감 알림과 원문 해석을 공유한다. 날짜 충돌·회차·소진 안내를 임의로 접수 중으로 바꾸지 않는다.
- 개인 조건 탐색: 청약통장을 포함한 5개 정책의 연령을 비교하고 전체 정책에서 검색·정렬한다. 청약통장은 오늘 가입 기준의 나이와 병역기간 차감 미확인을 구분한다. 거주·취업·소득은 추가 확인으로 남긴다. 검색·페이지 이동·오류 재시도·모바일 화면을 로컬 실제 정책으로 확인했다.
- 회원 기능: OAuth 로그인 코드, 관심 정책 저장·해제, 일정·서비스 내 알림을 연결했다. 실제 카카오·네이버 로그인은 미검증이다. [회원 기능](docs/development/member-policy-flow.md)
- 이메일: 주소 확인·동의·암호화 저장·Outbox·SMTP 어댑터를 구현했다. 실제 공급자·발신 도메인은 미정이며 외부 수신함 전달은 미검증이다. [이메일 구현과 설정](docs/development/member-email-reminders.md)
- AI: 후보 개정 검사·비용 예약·DB 복구 흐름은 내부 모델과 테스트용 공급자로 검증했다. 실제 AI 호출·청구·운영 작업자는 미연결이다. [AI 요청 판단](docs/development/ai-request-admission.md)·[복구 실행](docs/development/ai-reservation-recovery-work-runs.md)
- 최근 검증: `a03b02d`의 관련 PostgreSQL·정책 규칙·계약 검사와 서버 패키징을 완료했다. 실제 청약통장으로 연령 충족·병역 미확인·검색·상시 필터·PC/모바일 표시를 확인했다. 웹 변경이 없어 `94463e2`의 웹 검사·빌드는 재사용했다. [명령·범위·결과](docs/development/condition-policy-discovery.md#검증)를 참고한다. 이전 전체 서버 검사는 `c8cfc33`의 코드 정리 시점이며 최근 기능의 전체 서버 검사로 간주하지 않는다.
- 검증 도구의 성공·실패·실행 중 변경 시나리오와 기존 도구 검사를 통과했다. 컴파일·패키징 명령의 Gradle 실행 계획에 테스트 실행이 없음을 확인했다. `npm run verify -- status`에서 실행 기록을 확인한다. 검증 도구를 정리할 당시에는 앱 기능을 변경하지 않아 전체 앱 테스트를 재실행하지 않았다.

## 남은 작업

1. 다른 정책의 원문·예외를 검토해 질문과 연령 비교 범위를 늘린다. 거주·취업·소득 비교는 기준일·증빙·예외를 확정한 정책부터 연결한다.
2. 카카오·네이버 설정 후 실제 로그인·로그아웃·회원 데이터 분리를 확인한다.
3. 이메일 공급자·발신 도메인을 정하고 수신함 전달·반송·수신 해제·결과 미확인 처리를 검증한다.
4. 실제 수집 한도·범위·주기를 정해 정기 수집을 활성화하고 실패 후 재개를 확인한다.
5. AI 공급자와 비용 한도를 정해 실제 요약·조건 추출·과금 확인을 연결한다.
6. 월 3만 원 예산 안에서 배포·백업·운영 인증·개인정보 보관/삭제를 정하고 원격 CI를 확인한다.

## 이어서 작업할 때

- 제품 동작은 [PRD](docs/PRD/0001_product-baseline/spec.md), 기술 선택은 [ADR](docs/ADR/), 적용 스킬은 [AGENTS.md](AGENTS.md)를 따른다.
- 로컬 실행은 [개발 환경](docs/development/local-development.md), 실제 정책 서버 연결은 [조회 문서](docs/development/policy-catalog.md)를 참고한다. 현재 프로세스·브랜치·환경 설정은 실행 시점에 확인한다.
- 최근 검증의 `JAVA_HOME`은 `/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`이다. `test:ai-reservations`는 PostgreSQL 검사로 Docker가 필요하다.
- 수집 필드·미확인 계약은 [온통청년 API 조사](docs/research/ontong-api-contract.md)에 있다. API 키 설정과 실제 응답 확보는 완료했으며 비밀값·전체 캡처는 Git에 넣지 않는다.
- 정기 수집·SMTP·AI 복구 실행기의 기본값은 비활성화다. 외부 연동 검증에는 실제 설정이 필요하다. 운영 장비·클라우드와 개인정보 보관·삭제 정책은 아직 미정이다.
