---
name: youth-policy-verify
description: 청년정책메이트에서 변경 파일에 맞는 최소 검증을 고르고 npm run verify로 실행·기록한다. 이전 검증 기록과 현재 파일 차이를 함께 보여준다.
when_to_use: 코드·설정을 수정한 뒤 어떤 테스트·검사를 돌릴지 정할 때, 테스트를 실행하거나 실패 원인을 나눌 때, 작업을 이어받아 이전 검증이 아직 유효한지 확인할 때.
argument-hint: "[검증할 범위나 스크립트]"
allowed-tools:
  - Bash(npm run verify:*)
---

# 검증 범위 선택과 기록

기준은 [검증 절차](../../../docs/development/verification-workflow.md)다. 이 스킬은 그 절차를 변경 위치별 명령으로 구체화한다. 요청 범위: $ARGUMENTS

## 현재 상태

작업 트리:

!`git status --short`

최근 검증 기록(현재 파일과의 차이 포함):

!`npm run verify -- status`

## 순서

1. 위 기록에서 **통과했고 그 뒤 바뀐 파일과 관계없는 검증**은 다시 실행하지 않는다. 사용자가 이번 작업 전에 만든 변경과 내 변경을 구분한다.
2. 아래 표에서 바뀐 파일에 해당하는 가장 좁은 스크립트를 고른다. 알려진 검토(`youth-policy-review`)와 테스트 보완을 먼저 마친다.
3. 관련 검사를 통과한 뒤 넓은 영향이 있을 때만 전체 검사(`check:backend`, `build:web`)를 한 번 실행한다.
4. 실패하면 로그에서 코드·환경·명령 오류를 구분한다. 원인을 고친 뒤 실패한 범위부터 다시 실행하고, 원인이 그대로인 명령을 반복하지 않는다.
5. 결과를 보고할 때 실행한 명령·통과/실패·로그 경로를 적는다. 부분 테스트·컴파일·패키징을 전체 테스트 통과로 표현하지 않는다.

## 변경 위치별 스크립트

명령은 모두 `npm run verify -- <스크립트>` 형식이다. 서버 경로는 `backend/src/main/java/kr/youthpolicymate/` 기준이다.

| 바뀐 위치 | 기본 스크립트 | 함께 볼 것 |
|---|---|---|
| `eligibility/**` | `test:eligibility` | 판정표에 영향이 있으면 `test:policy-questions` |
| `policy/catalog/` 질문·판정표(`PolicyRuleDefinition`, `PolicyQuestion*`, `BasicConditions`) | `test:policy-questions` | 규칙 형식은 `PolicyRuleDefinitionTest`, DB 적용·목록은 `test:policy-catalog` |
| `policy/catalog/` 목록·상세·저장소(`PolicyCatalogStore`, `*Response`) | `test:policy-catalog` | 응답 형식이 바뀌면 API 계약 행 |
| `policy/Recruitment*`, `ApplicationPeriod` | `test:recruitment` | `catalog/PolicyRecruitment*`·`PolicyDeadline*`은 해당 테스트 클래스 |
| `ingestion/Ontong*` | `test:policy-collection` | |
| `ingestion/AiBudgetReservation*`, `AiRequestBudget`, `PolicyAiResult`, `policy/PolicyObservation` | `test:ai-reservation-db` | 요청·결과 타입이 바뀌면 운영 규칙 추출 경로를 포함한 `test:ai-rule-generation`도 |
| `ingestion/PolicyAiExecutionCoordinator` | `test:ai-execution` | |
| `ingestion/PolicyAiRule*`, `OpenAiRuleClient` | `test:ai-rule-generation` | 스케줄러는 `test:ai-rule-auto`, 초안 저장소만이면 `test:ai-rule-drafts` |
| `ingestion/OpenAiCosts*` | `test:ai-costs` | |
| `member/SocialMemberService`, 세션·로그인 | `test:member-login` | 보안 설정이 바뀌면 `check:backend` |
| `member/MemberEmail*`, `*EmailSender`, `Resend*` | `test:email` | |
| `member/EmailCrypto`, `EmailKeyRotation*` | `test:email-key-rotation` | |
| `member/` 저장 정책·계정·알림 | `test:member-flow` | |
| `admin/Collection*`, `PolicyCorrection*`, `PolicyRule*` | `test:admin-collection` | |
| `admin/PolicyAiRun*` | `test:admin-ai` | |
| `admin/EmailDelivery*` | `test:admin-email` | |
| `config/SecurityConfiguration`, `AdminAccess` | `test:admin-ai`(AdminAccessTest 포함) | 공통 보안 영향이 넓으면 `check:backend` |
| `application-prod.yaml`, 운영 프로필 구성 | `test:runtime` | |
| Flyway SQL·Java 마이그레이션 | 해당 기능의 DB 테스트 | 여러 기능 테이블에 영향이 있으면 `check:backend` |
| `backend/build.gradle`, 공통 `application.yaml` | `check:backend` | |
| 컴파일 확인만 필요 | `compile:backend` | 실행 파일만 필요하면 `package:backend` |
| `frontend/src/**` 동작 | `test:web -- <frontend 기준 테스트 경로...>` + `check:web` | 라우팅·서버 렌더링·메타데이터·의존성은 `build:web` |
| 서버 DTO·`api/*.json`·`frontend/src/generated/**` | `npm run generate:api`(직접 실행) 후 `check:api-types` + `check:web` | |
| `scripts/*.mjs` | `check:tools` | |
| `package.json`·잠금 파일 | 영향받는 앱의 전체 검사 | |
| 문서·스킬만 | 앱 검사 없음 | `youth-policy-docs` 스킬의 링크 검사, 수정한 스킬 형식 |

표에 없는 서버 테스트 클래스 하나만 필요하면 `./backend/gradlew -p backend test --tests '<FQCN>' --no-daemon`을 직접 실행한다. 이 명령은 verify 기록에 남지 않으므로 보고에 명령과 결과를 적는다.

## Claude Code 실행 요령

- Gradle 테스트와 `build:web`은 수 분이 걸린다. Bash `timeout`을 600000으로 지정하거나, `check:backend`처럼 더 긴 검사는 `run_in_background`로 실행하고 완료 알림을 기다린다. 완료를 기다리려고 `sleep`이나 반복 조회를 하지 않는다.
- verify는 성공 시 요약, 실패 시 로그 끝 20줄만 출력한다. 전체 로그는 출력된 `.local/verification/<id>.log`에서 `grep -n -E 'FAILED|Error|error:' <로그>`로 위치를 찾은 뒤 필요한 범위만 읽는다.
- PostgreSQL 테스트 전 `docker info --format '{{.ServerVersion}}'`로 Docker를 확인한다. 실행 중이 아니면 환경 문제로 보고하고 코드 실패로 다루지 않는다.
- 종료 코드 2(`실행 중 파일 변경`)는 검사 도중 파일이 바뀌었다는 뜻이다. 내가 바꾼 파일이면 그 범위만 다시 실행한다.
- `build:web`이 이전 제한 실행의 Turbopack 캐시 오류를 보이면 [HANDOFF](../../../HANDOFF.md)의 "이어서 작업할 때" 기록을 확인한다. 같은 증상이 없으면 캐시를 지우지 않는다.
