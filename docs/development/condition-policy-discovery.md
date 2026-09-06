# 개인 조건으로 정책 찾기

2026-09-06 기준. `/conditions`에서 기본 조건을 확인한 뒤 정책명·내용으로 검색하고 연령 조건 충족 우선 또는 최근 수집순으로 정렬한다.

## 비교 범위

| 정책 | 연령 기준 | 추가 확인 |
|---|---|---|
| 국가기술자격 응시료 지원 | 1991.1.1. 이후 출생(당일 포함) | 시험·지원 횟수·예산 등 |
| K-패스 | 현재 서울 날짜 기준 만 19세 이상 | 주소 확인·카드 등록·월별 이용 등 |
| 서울청년정책네트워크 | 1986.1.2.~2007.1.1. 출생 | 상한 초과 시 군복무 연장 확인, 서울 생활권·위원 이력 등 |
| 서울 중개보수·이사비 지원 상반기 | 1986.1.1.~2007.12.31. 출생 | 신청 당시 전입·계약·주택·소득 등 |

정책별 추가 질문과 같은 연령 비교 함수를 사용한다. 기준의 출처는 [응시료](exam-fee-questions.md), [K-패스](kpass-questions.md), [청년정책네트워크](seoul-youth-network-questions.md), [이사비](moving-fee-questions.md)에 있다. 검토된 원문 해시가 일치하고 2026년 안에 있을 때만 적용한다. 검토되지 않은 정책·바뀐 원문·다음 해에는 연령을 미확인으로 남긴다. 청약통장과 국가근로장학금은 기본 조건만으로 비교하지 않는다.

연령 외 조건은 현재 입력만으로 확정하지 않는다. 서울 자치구 입력은 과거 모집 신청 당시의 주소·전입 이력을 증명하지 않으며, 주된 취업상태는 다른 취업·학력·소득 조건을 대신하지 않는다. 모든 항목의 전체 자격은 `NEEDS_REVIEW`다. 마감된 공고는 결과와 질문에 해당 회차·마감을 표시한다.

## 검색·정렬과 개인정보

- `POST /api/v1/policies/checks?page=1&q=&sort=AGE_MATCH`에 기본 조건을 JSON 본문으로 보낸다. 생년월일·자치구·취업상태를 URL이나 저장소에 넣지 않으며 응답은 `no-store`다.
- 기본 순서는 연령 `MET` → `UNKNOWN` → `NOT_MET`이며 동률은 수집 시각 내림차순·정책번호순이다. `RECENT`는 수집 시각·정책번호만 사용한다. 미확인·불충족 정책을 제외하지 않는다.
- `q`는 정책명·설명의 부분 문자열 검색(최대 80자)이다. `%`와 `_`는 와일드카드가 아니다. 전체 결과에 검색·정렬을 적용한 후 20건씩 조회한다.
- `BasicConditionRules`의 비교값을 SQL 정렬과 응답 표시에 함께 사용한다. 정책별 추가 조회 없이 건수와 페이지 조회 두 번으로 처리한다.
- 응답에 항목별 `outcome`, 정책 개정, 연령 규칙 `ruleVersion`, `evaluatedAt`을 제공한다. 비교 기준이 없으면 규칙 버전은 빈 문자열이다.
- 검색·정렬 변경은 1페이지로 돌아간다. 이전 요청을 취소하고 늦은 응답은 무시한다. 실패해도 입력과 검색 조건을 유지하고 재시도할 수 있다.
- 추가 질문으로 이동해도 기본 조건을 자동 답변으로 넣지 않는다.

## 검증

검증한 최종 코드 리비전은 `0392727`이다. 문서 변경만 남은 상태에서 아래 결과를 재사용했다.

| 실행 | 확인 범위·결과 | 로컬 로그 |
|---|---|---|
| `npm run generate:api` | 서버 DTO로 OpenAPI·TypeScript 생성 완료 | `/tmp/youth-condition-api-generation.log` |
| `npm run verify -- test:policy-catalog -- --tests 'kr.youthpolicymate.policy.catalog.*RulesTest'` | PostgreSQL 검색·전체 정렬·페이지·조회 수·버전 변경·공개 권한·계약 검사 통과. 함께 실행한 규칙 검사에서 미응답 표시 1건 실패를 발견해 수정 | `.local/verification/1788699828832-1017594c.log` |
| `npm run verify -- test:policy-questions -- --tests kr.youthpolicymate.policy.catalog.SeoulYouthNetworkRulesTest` | 스크립트의 전체 `*RulesTest` 포함. 수정 후 미응답·정책별 경계·예외·연도 전환 검사 통과 | `.local/verification/1788699959750-a776f713.log` |
| `npm run verify -- test:web -- 'src/app/api/member/[...path]/route.test.ts' src/features/eligibility/policy-questionnaire.test.tsx src/features/conditions` | API 중계의 검색·정렬 전달·개인정보 본문 유지, 조건 입력·질문 화면 관련 검사 통과 | `.local/verification/1788699830882-eafe0977.log` |
| `npm run verify -- check:web` | 린트·타입 검사 통과 | `.local/verification/1788699829674-15911e78.log` |
| `npm run verify -- check:api-types` | 생성 계약 일치 | `.local/verification/1788699868964-22db3f99.log` |
| `npm run verify -- build:web` | Next.js 빌드 종료 코드 0. 실행 중 README·이 문서만 바뀌어 기록 상태는 `changed`이며, 웹 코드·의존성 변경이 없어 빌드는 반복하지 않음 | `.local/verification/1788699873942-f729604b.log` |
| `npm run verify -- package:backend` | 최종 서버 실행 파일 생성. 테스트 재실행 없음 | `.local/verification/1788700008381-641f8cd3.log` |

브라우저에서 로컬 실제 정책 40건을 대상으로 검증용 조건(실제 개인정보 아님)을 사용했다. 서버 미연결 오류 후 입력 유지·재시도 성공, 2페이지 이동 후 검색 시 1페이지 복귀, 정렬 변경 시 검색어 유지, 빈 결과·초기화, 이사비 두 정책 중 검토된 원문에만 질문 제공, 질문 이동·예외 미확인 결과·답변 삭제를 확인했다. 데스크톱과 모바일 크기에서 검색 입력·버튼·초점을 확인했으며 모바일 문서 너비와 화면 너비가 같았다. 검증 후 입력·답변을 비우고 화면 크기 변경을 해제했다.

SQL·규칙·API와 관련 소비 화면에 한정된 변경으로 전체 서버 검사와 외부 OAuth·SMTP·AI 호출은 실행하지 않았다. JDBC·보안 설정·DB 스키마는 변경하지 않았다.

## 로컬 실행 상태

- 웹: 저장소 루트의 `npm run dev:web`, 127.0.0.1:3000, `/tmp/youth-condition-web.log`.
- 서버: `backend`에서 JDK 25의 `bin/java -jar build/libs/youth-policy-mate-0.0.1-SNAPSHOT.jar --spring.profiles.active=local`, 127.0.0.1:8080, `/tmp/youth-condition-backend.log`.
- 서버 시작 시 `REMINDERS_ENABLED=false EMAIL_ENABLED=false ONTONG_COLLECTION_SCHEDULE_ENABLED=false`를 지정했다. `.env`와 수집 원문·회원 데이터는 바꾸지 않았다.
- 사용 JDK는 `/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`이며 서버·DB 접근 검증은 승인된 실행 환경을 사용했다. 프로세스 생존 여부는 다음 작업 시 확인한다.
