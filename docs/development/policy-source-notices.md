# 정책 원문 충돌 안내

2026-09-08 구현. 코드 기준은 `676fe5d`다.

## 적용 범위

정책 상세에서 수집 안내의 충돌과 확인할 공식 공고를 질문 영역보다 먼저 표시한다. `PolicySourceNotice`가 정책번호와 현재 내용 해시를 대조하고, `PolicyCatalogStore`가 상세 응답의 `sourceNotices`에 넣는다. 추가 DB 조회나 마이그레이션은 없다.

- 대상: 청년내일저축계좌 `20260430005400113009`, 검토 당시 개정 1.
- 검토한 해시: `f3709a60376cdaf861ee232c1fc411292f2d3bdcb28c807ca87190a19698733c`.
- 수집 내용: ‘추가 신청 자격’은 가구 소득인정액 기준 중위소득 100% 이하, ‘소득 안내’는 50% 이하로 서로 다르다.
- 확인 근거: [복지로 2026년 신청 안내](https://www.bokjiro.go.kr/ssis-tbu/cms/pc/customer/notice/1309680_1141.html)(2026-04-24 등록, 2026-09-08 재확인)는 가구 소득인정액이 기준 중위소득 50% 이하인 대상으로 안내한다.

원본 스냅샷·공개 본문·정책 개정은 변경하지 않는다. 이 안내는 수동 검토 결과이며 자동 충돌 탐지가 아니다. 자격 질문은 계속 미제공이고, 안내만으로 자격을 확정하지 않는다. 지원금·예외·증빙 기준의 전체 검토는 남아 있다.

## 계약과 변경 처리

`GET /api/v1/policies/{policyNumber}`의 `sourceNotices`는 항상 배열이다. 각 항목은 `title`, `description`, `sourceLabel`, `sourceUrl`을 제공한다. 웹은 서버에서 생성한 타입을 사용한다. 새 웹을 반영하기 전에 서버가 이 필드를 제공해야 한다.

해시나 정책번호가 다르면 빈 배열을 반환한다. 제목·운영 기관 보정으로 해시가 바뀌어도 재검토가 필요하다. 빈 배열은 검토 완료나 충돌 해소를 뜻하지 않는다. 모든 상세에 수집 출처와 일반적인 추가 확인 안내를 유지한다. 2026년 공고 링크는 해당 연도를 명시해 다음 모집 기준으로 오해하지 않게 한다.

질문 상태 확인은 `GET /api/v1/policies/{policyNumber}/questions`를 사용한다.

## 검증

저장소 루트에서 실행했다. 서버 명령의 `JAVA_HOME`은 `/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`이며 PostgreSQL 테스트에는 Docker를 사용했다.

| 명령 | 결과·로그 |
|---|---|
| `npm run generate:api` | 통과, `/tmp/youth-source-notices-api.log` |
| `npm run verify -- test:policy-catalog` | 통과, `.local/verification/1788868749592-4ee0c3fa.log` |
| `npm run verify -- test:web -- src/app/policies/policy-content.test.tsx` | 통과, `.local/verification/1788868750612-6e2b7b72.log` |
| `npm run verify -- check:api-types` | 통과, `.local/verification/1788868754851-d6eed6f3.log` |
| `npm run verify -- check:web` | 통과, `.local/verification/1788868776760-835e0dbe.log` |
| `npm run verify -- package:backend` | 통과, `.local/verification/1788868812556-a0beefc5.log` |
| `npm run verify -- build:web` | 통과, `.local/verification/1788868813920-a8a7d6e8.log` |

통합 테스트는 원본·공개 본문 보존, 변경된 내용과 다른 정책의 안내 제외, 질문 미제공 유지를 확인한다. 웹 테스트는 안내·링크의 표시 순서, 원문 유지와 안내가 없는 상태를 확인한다.

로컬 Spring 8080·Next.js 3000과 실제 저장 정책으로 상세 응답을 확인했다. Playwright에서 데스크톱·390px 모바일의 배치와 가로 넘침 없음, 공고 링크의 키보드 새 창 열기, 질문 미제공 안내, 이사비 정책에 충돌 안내가 붙지 않는 것을 확인했다. 화면은 `/tmp/youth-admin-ui/source-notices-desktop.png`, `/tmp/youth-admin-ui/source-notices-mobile.png`다. 기존 `favicon.ico` 404 외 화면 오류는 없었다.

현재 로컬 서버 로그는 `/tmp/youth-source-notices-backend.log`, `/tmp/youth-source-notices-web-server.log`다. 정기 수집·알림·이메일 발송은 비활성화했다. 실제 수집 데이터·DB 스키마는 변경하지 않았다.

기준 `main`의 `1b7fbdd`는 [원격 웹·전체 서버 CI](https://github.com/ljkhyeong/youth-policy-mate/actions/runs/34222499053)를 통과했다. 이번 변경은 상세 조회·표시 범위의 위 검증만 실행했으며 전체 서버 검사와 원격 CI는 다시 실행하지 않았다. 이후 문서 변경·커밋만으로 앱 검사를 반복하지 않는다.
