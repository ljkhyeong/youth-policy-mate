# 내 정책 마감 일정

`a6e807d` 기준. `/my`의 관심 정책과 마감 일정에 공개 정책과 같은 접수 상태를 표시한다. 기존 저장·해제·마감 알림 예약 경로를 사용한다.

## 동작

- 가까운 마감순으로 조회하고 접수 종료 정책은 마지막에 둔다. 날짜가 없는 정책은 종료되지 않은 날짜형 정책 뒤에 표시한다.
- 마감 일정에서 접수 전·접수 기간·마감·상시·소진 시 마감·기간 미확인으로 필터한다. 새로고침과 저장 해제 후에도 선택한 필터를 유지한다. [화면 주소](member-navigation.md)에 탭·필터를 반영해 브라우저 새로고침·뒤로 가기·새 탭에서도 복원한다. 관심 정책 탭은 일정 필터의 영향을 받지 않는다.
- 상시 접수를 마감일 확인 필요로 표시하지 않는다. 날짜가 없는 정책에 임의의 마감일을 만들지 않으며 종료된 정책에 앞으로 마감 알림을 보낸다고 안내하지 않는다.
- 상태는 조회 시점 기준이다. 공고 개정이 없어도 날짜·시각이 바뀌면 새로고침으로 상태를 갱신한다. 실제 접수 가능 여부는 공식 신청처에서 확인한다.
- 필터에 맞는 일정이 없으면 전체 일정으로 돌아갈 수 있다. 저장한 정책 자체가 없는 경우는 별도로 안내한다.

## 서버와 화면

`GET /api/v1/me/policies`의 `SavedPolicy`에 `PolicyRecruitment` 응답을 추가했다. `MemberPolicyStore`가 현재 원본과 기존 `PolicyRecruitment.from` 판정을 사용한다. 브라우저에서 날짜로 접수 종료를 다시 판단하지 않는다. 응답 필드가 추가돼 서버와 웹을 함께 반영한다.

현재 원본을 목록 쿼리에 조인하므로 변경 없는 관심 정책 조회는 기존처럼 SELECT 세 번이다. 기존 회원·정책 잠금과 개정 갱신을 유지하며 마이그레이션은 추가하지 않았다. 저장 목록 조회는 기존과 같이 최신 개정과 알림 예약 갱신을 포함한다.

`MemberPolicyList`는 서버 순서를 유지하고 선택한 접수 상태만 필터한다. 공개 화면의 접수 상태 표시·선택지를 재사용한다. 필터는 화면 상태로만 유지하며 개인정보나 선택값을 브라우저 저장소에 추가하지 않는다.

## 검증

Java 25.0.3·PostgreSQL 18.6에서 관련 검사를 통과했다.

| 명령 | 확인 범위·로그 |
|---|---|
| `npm run generate:api` | 저장 정책 계약·TypeScript 생성. `/tmp/youth-member-calendar-contract.log` |
| `npm run verify -- test:member-flow` | 정렬·상시/미확인/종료·서울 자정·공개 상태 일치·조회 횟수, 기존 회원 분리·저장/해제·마감 변경·알림·이메일 처리. `.local/verification/1789180386578-3f10b728.log` |
| `npm run verify -- test:web -- src/features/member/member-policy-list.test.tsx src/features/policies/policy-recruitment.test.tsx 'src/app/api/member/[...path]/route.test.ts'` | 접수 상태·날짜 표시·빈 필터·관심 정책 탭 분리·기존 중계. `.local/verification/1789180380403-5bd8a5d7.log` |
| `npm run verify -- check:web` | 린트·타입. `.local/verification/1789180380421-e85f67f2.log` |
| `npm run verify -- check:api-types` | 생성 타입 일치. `.local/verification/1789180380436-b90b13a1.log` |
| `npm run verify -- build:web` | 최종 모바일 스타일을 포함한 배포 빌드. `.local/verification/1789180591635-b0865a6b.log` |
| `npm run verify -- package:backend` | 서버 실행 파일 생성. `.local/verification/1789180469770-e59161d9.log` |

부분 검사 후 모바일 계정 메뉴의 클래스·스타일만 조정하고 최종 빌드·브라우저 표시를 다시 확인했다. 서버·판정·필터 동작은 바꾸지 않아 통과한 관련 검사를 재사용했다. 다른 모듈의 전체 서버 검사는 `ee34aca` 기준을 유지한다.

Playwright의 독립 브라우저에서 390px·1280px 가로 넘침, 상태 필터, 새로고침 후 필터 유지, 키보드 저장 해제, 빈 결과와 전체 일정 복귀를 확인했다. 회원 API 응답만 테스트 자료로 대체했으며 실제 회원 데이터는 변경하지 않았다. 브라우저는 종료했고 이미지는 `/tmp/youth-member-calendar-ui/`에 있다. 실제 소셜 제공자 로그인 검증은 별도다.

로컬 서버 상태 조회 200·회원 목록 비회원 401·`/my` 응답 200을 확인했다. 실제 AI 키·모델·요금·한도는 미설정이며 AI 호출·정기 수집·알림·이메일 자동 실행을 켜지 않았다. 이번 브랜치의 원격 CI·배포는 실행하지 않았다.
