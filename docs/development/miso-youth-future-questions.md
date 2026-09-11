# 청년 미래이음 대출 조건 질문

2026-09-11 구현, 코드 `6cd4b06`. 정책 `20260421005400112773`의 검토된 개정 1에 질문과 기본 연령 비교를 연결했다. 수집 원문과 DB 데이터는 수정하지 않았다.

## 확인 근거와 범위

- [서민금융진흥원 상품 안내](https://www.kinfa.or.kr/financialProduct/youngFutureLinkLoan.do): 연령·취창업 상태, 세 지원 요건의 선택 관계, 제한 요건과 신용정보 예외를 확인했다. 웹 도구가 시간 초과해 공식 HTML을 직접 읽었다. 사본은 `/tmp/youth-miso-official.html`, 본문은 `/tmp/youth-miso-official.txt`다.
- [금융위원회 2026년 3월 30일 출시 안내](https://www.fsc.go.kr/po010106/86583): 3월 31일 출시, 사회진입 자금 용도·상환 심사·재무상담 연계, 햇살론유스 중복 이용 가능 안내를 대조했다. 같은 보도자료에 있는 다른 미소금융 상품의 요건은 가져오지 않았다.

질문 5개를 결과 3개로 비교한다.

| 결과 | 비교 기준 |
|---|---|
| 대출신청일 연령 | 만 19~34세. 기본 조건 검색은 오늘 서울 날짜의 신청을 가정하고 실제 신청일 재확인을 안내 |
| 취·창업 상태 | 미취업 또는 취·창업 1년 이내. 지점 기준으로 확인한 이력을 사용하며 겸업·시작일·적용 기준이 불명확하면 미확인 |
| 지원 요건 중 하나 충족 | 신용평점 하위 20%, 기초생활수급자·차상위계층 이하, 근로장려금 신청 자격 중 하나. 한 항목이라도 확인된 충족이면 충족, 전부 확인된 비해당이면 불충족, 그 외는 미확인 |

신용점수의 고정 구간이나 근로장려금 자격 계산을 구현하지 않는다. 적용 연도·자격·증빙을 확인한 답변을 받고 과거 수령·현재 근로소득으로 대신하지 않는다. 금융취약계층 생계자금의 신용 하위 50%·연소득 요건도 적용하지 않는다.

전체 자격은 `NEEDS_REVIEW`다. 자금 용도·상환 심사·재무상담·서류, 신용정보·재산·국적·해외체류 등 지원 제한과 예외, 최종 대출 여부·금액·금리·기간은 지점 확인으로 남긴다. 신용정보 등재나 햇살론유스 이용 이력만으로 지원 불가를 단정하지 않는다.

## 연결과 검증

`MisoYouthFutureRules`의 버전은 `miso-youth-future-2026-v1`, 내용 해시는 `c2ba149dd238ced25aab441f1ff595079b0b2fb6b7d25f4d71a97a21ce3d3788`이다. 서울 날짜 2026.3.31.~12.31.의 검토 범위에서만 적용하며 2027년은 재검토가 필요하다. 연말은 사업 종료일을 뜻하지 않는다.

기존 질문 API·DTO·웹 폼·답변 검증을 재사용했다. 질문 제공·기본 연령 비교·SQL 정렬에 같은 등록 기준을 적용한다. 원문 해시 불일치나 적용 기간 밖에서는 질문·기본 연령 비교를 중단하고 이전 답변을 409로 거절한다. 개정·규칙 버전 검사도 유지한다. 현재 질문 제공은 10개 정책, 기본 연령 비교는 8개 정책이다.

저장소 루트에서 `JAVA_HOME=/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`으로 검증했다.

| 명령 | 결과·로그 |
|---|---|
| `npm run verify -- test:policy-catalog -- --tests 'kr.youthpolicymate.policy.catalog.MisoYouthFutureRulesTest'` | 통과, `.local/verification/1789133788438-34e6fdff.log` |
| `npm run verify -- package:backend` | 통과, `.local/verification/1789133829026-eaa810b5.log` |

단위 검사에서 세 요건 각각의 충족, 다른 요건의 미응답·미확인, 모두 비해당, 취창업 유형·미확인, 만 19세·35세·서울 자정과 출시·연도 경계를 확인했다. PostgreSQL API 검사에서 질문 제공·필터·평가·기본 연령 비교와 개정·원문·기간 변경 차단을 확인했다. 기존 정책 API 검사와 실제 생성 OpenAPI 계약 비교를 함께 통과했다.

Playwright로 실제 정책의 질문 5개·결과 3개, 한 요건 충족·다른 요건 미응답, 두 요건 비해당·하나 미확인, 세 요건 비해당과 전체 자격 추가 확인을 확인했다. 답변 수정 시 이전 결과 제거, 초기화, 390px 가로 넘침 없음과 데스크톱 입력 표시를 확인했다. 기본 조건 검색에서는 만 19세·서울 기준일·실제 신청일 재확인과 다른 조건의 미확인 표시를 확인했다. 입력은 URL에 노출되지 않았으며 검증용 답변은 모두 지웠다.

화면·기록은 `/tmp/youth-miso-ui/`, `/tmp/youth-miso-question-flow.log`, `/tmp/youth-miso-basic-flow.log`에 있다. 요소 전체 촬영에 고정 메뉴가 겹쳐 모바일 결과·심사 안내는 별도 뷰포트 이미지로 다시 확인했다. 전용 브라우저는 종료했다.

프런트엔드·API 구조·생성 타입·의존성·설정은 변경하지 않아 `d4405c9`의 [웹 검증](interface-design-review.md#검증)을 재사용했다. 전체 서버 테스트·원격 CI·배포는 이번 범위에서 실행하지 않았다.

## 로컬 실행 상태

- Spring 8080: 위 JDK의 `bin/java -jar backend/build/libs/youth-policy-mate-0.0.1-SNAPSHOT.jar --spring.profiles.active=local`. 로그 `/tmp/youth-miso-backend.log`.
- Next.js 3000: `npm run dev:web`. 로그 `/tmp/youth-design-web.log`.
- 서버의 `REMINDERS_ENABLED=false EMAIL_ENABLED=false ONTONG_COLLECTION_SCHEDULE_ENABLED=false`를 유지했다. DB V20과 실제 정책 데이터는 변경하지 않았다. 다음 작업에서는 프로세스를 확인한다.
