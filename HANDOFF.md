# 작업 인계

2026-09-05 기준. 개발 이력은 Git과 [개발 문서](docs/development/)에서 확인한다.

## 현재 작업

- 개발 브랜치의 누적 변경은 `39f78b5`에서 로컬 `main`에 병합했다. 원격 푸시는 하지 않았다.
- 지시·스킬 정리는 `7c6ec51`에 커밋했다. `codex/verification-workflow`에서 기존 두 작업의 검증 기록을 검토하고 [검증 명령·결과 기록·재실행 기준](docs/development/verification-workflow.md)을 추가했다.
- `codex/backend-api-review`에서 [Java·Spring API 활용과 중복 검증 정리](docs/development/backend-api-review.md) 6개 항목을 구현했다. SMTP 자동 설정·이메일 주소 공통 제약·OAuth 기본 저장소를 적용하고 중복 null 검사·미사용 팩터리·정책별 중복 답변 검증을 정리했다.
- 같은 문서에 조건 확인 반복 조회·고정 결과용 판정 객체·AI 종료 상태 판단·DB 시각 처리의 추가 정리 대상 4개를 기록했다. 이 4개는 아직 구현하지 않았다.

## 구현·검증 범위

- 정책 조회: 로컬 수집 40건의 검색·상세·원문 링크를 제공한다. 서울 대상 전체 정책을 수집한 상태는 아니다. [조회](docs/development/policy-catalog.md)·[수집과 재개](docs/development/policy-range-collection.md)
- 조건 질문: 국가근로장학금·응시료 지원·K-패스·청년주택드림청약통장·서울청년정책네트워크 5개 정책을 제공한다. 일부 요건만 비교하므로 전체 자격은 추가 확인 필요다. [질문 탐색](docs/development/policy-question-discovery.md)·[최근 추가 정책](docs/development/seoul-youth-network-questions.md)
- 회원 기능: OAuth 로그인 코드, 관심 정책 저장·해제, 일정·서비스 내 알림을 연결했다. 실제 카카오·네이버 로그인은 미검증이다. [회원 기능](docs/development/member-policy-flow.md)
- 이메일: 주소 확인·동의·암호화 저장·Outbox·SMTP 어댑터를 구현했다. 실제 공급자·발신 도메인은 미정이며 외부 수신함 전달은 미검증이다. [이메일 구현과 설정](docs/development/member-email-reminders.md)
- AI: 후보 개정 검사·비용 예약·DB 복구 흐름은 내부 모델과 테스트용 공급자로 검증했다. 실제 AI 호출·청구·운영 작업자는 미연결이다. [AI 요청 판단](docs/development/ai-request-admission.md)·[복구 실행](docs/development/ai-reservation-recovery-work-runs.md)
- 최근 앱 검증: 표준 API 리팩터링 후 서버 전체 테스트·빌드, API 재생성과 생성 타입 일치, 웹 린트·타입 검사를 통과했다. [검증 결과](docs/development/backend-api-review.md)에 범위를 기록했다. 정책 질문의 브라우저·키보드 검증은 [이전 개발 기록](docs/development/seoul-youth-network-questions.md)에 있다.
- 검증 도구의 성공·실패·실행 중 변경 시나리오와 기존 도구 검사를 통과했다. 컴파일·패키징 명령의 Gradle 실행 계획에 테스트 실행이 없음을 확인했다. `npm run verify -- status`에서 실행 기록을 확인한다. 검증 도구를 정리할 당시에는 앱 기능을 변경하지 않아 전체 앱 테스트를 재실행하지 않았다.

## 남은 작업

1. 다른 정책의 원문·예외를 검토해 질문 제공 범위를 늘리고 개인 조건 기반 검색·추천을 연결한다.
2. 카카오·네이버 설정 후 실제 로그인·로그아웃·회원 데이터 분리를 확인한다.
3. 이메일 공급자·발신 도메인을 정하고 수신함 전달·반송·수신 해제·결과 미확인 처리를 검증한다.
4. 실제 수집 한도·범위·주기를 정해 정기 수집을 활성화하고 실패 후 재개를 확인한다.
5. AI 공급자와 비용 한도를 정해 실제 요약·조건 추출·과금 확인을 연결한다.
6. 월 3만 원 예산 안에서 배포·백업·운영 인증·개인정보 보관/삭제를 정하고 원격 CI를 확인한다.

## 이어서 작업할 때

- 제품 동작은 [PRD](docs/PRD/0001_product-baseline/spec.md), 기술 선택은 [ADR](docs/ADR/), 적용 스킬은 [AGENTS.md](AGENTS.md)를 따른다.
- 로컬 실행은 [개발 환경](docs/development/local-development.md), 실제 정책 서버 연결은 [조회 문서](docs/development/policy-catalog.md)를 참고한다. 현재 프로세스·브랜치·환경 설정은 실행 시점에 확인한다.
- 수집 필드·미확인 계약은 [온통청년 API 조사](docs/research/ontong-api-contract.md)에 있다. API 키 설정과 실제 응답 확보는 완료했으며 비밀값·전체 캡처는 Git에 넣지 않는다.
- 정기 수집·SMTP·AI 복구 실행기의 기본값은 비활성화다. 외부 연동 검증에는 실제 설정이 필요하다. 운영 장비·클라우드와 개인정보 보관·삭제 정책은 아직 미정이다.
