# 햇살론유스 조건 질문과 연령 비교

현재 질문·판정은 [공고별 조건 데이터](policy-rule-data.md)로 관리한다. 정책별 운영 클래스는 제거했으며 아래 공고 검토 기준과 당시 검증 기록은 유지한다.

2026-09-08 구현, 코드 `5675733`. 정책 `20260724005400113307`의 현재 검토 원문에 질문과 기본 연령 비교를 연결했다. 로컬 개정은 2이며 원문과 DB 데이터는 수정하지 않았다.

## 확인 근거

- [서민금융진흥원 상품 안내](https://www.kinfa.or.kr/financialProduct/hessalLoanYoos.do): 연령·소득·이용 유형, 생애 및 기간·용도별 보증한도, 재신청 제한, 제출서류·심사. 2026.9.8. 직접 받은 HTML의 본문을 확인했다. 웹 검색 도구는 시간 초과여서 `curl --max-time 30`으로 읽었으며 로컬 사본은 `/tmp/youth-kinfa-loan.html`, 텍스트는 `/tmp/youth-kinfa-loan.txt`다.
- [금융위원회의 2026년 정책서민금융 안내](https://www.fsc.go.kr/no040101?cnId=2982): 중소기업 재직 1년 이하 사회초년생, 창업 1년 이하 청년사업자와 소득 한도·생애 한도를 대조했다.
- [금융위원회의 청년사업자 대상 확대 안내](https://www.fsc.go.kr/no040101?cnId=2501): 보증신청일 기준 사업자등록증명원의 개업일로 창업기간을 확인한다. 당시 금리는 이번 규칙에 사용하지 않았다.

서금원 안내에는 개인사업자를 다른 상품으로 안내하는 일반 각주와 청년사업자를 포함한 대상·서류표가 함께 있다. 청년사업자는 금융위의 대상 확대와 2026년 안내를 대조해 반영했다. 모든 개인사업자를 포함하거나 배제하지 않고, 창업기간·겸업·서류 해석이 불명확하면 적용 유형을 확인하게 한다.

## 비교 범위

질문 5개를 결과 4개로 비교한다.

| 결과 | 기준 |
|---|---|
| 보증신청일 연령 | 만 19~34세. 군입대 예정자의 추가 거치기간을 연령 연장으로 적용하지 않음 |
| 이용 대상 | 취업준비생, 중소기업 재직 1년 이하 사회초년생, 창업 1년 이하 청년 개인사업자. 서금원 기준으로 확인한 유형을 사용하며 학적·근로·사업이 겹치거나 기준 확인 중이면 미확인 |
| 본인 연소득 | 증빙·산정 기준 확인과 금액 답변을 함께 비교. 연 3,500만 원 이하이며 월급·매출·가구소득으로 대신하지 않음 |
| 남은 생애 보증한도 | 공식 확인값 기준. 생애 1,200만 원을 상환해도 복원되지 않으며 대출 잔액으로 남은 한도를 계산하지 않음 |

기본 조건에서는 오늘(서울 날짜) 보증신청을 가정한 만 나이만 비교한다. 같은 연령 함수를 질문·검색 결과·정렬에 사용하며 실제 신청일이 다르면 다시 확인하도록 안내한다. 주된 취업상태로 질문을 채우거나 소득·이용 대상을 확정하지 않는다.

전체 자격은 항상 `NEEDS_REVIEW`다. 학적·학점 인정·재직·사업기간·소득 증빙, 재산 보유, 기간별·용도별 한도, 재신청 간격·자금 용도·금융교육과 보증·은행 심사를 남긴다. 실제 승인 여부·대출액·금리·보증료·상환 조건은 계산하지 않는다.

## 연결과 변경 처리

- 규칙 `HaetsalronYouthRules`, 버전 `haetsalron-youth-2026-v1`, 내용 해시 `1fbde72fe6ad25caf843889a0571a62c817ddd8b246df04093bd65710eb22df5`.
- 기존 질문 API·DTO·웹 폼·답변 검증을 재사용한다. `ReviewedPolicyQuestions`와 `BasicConditionRules`에 등록해 질문 제공 9개 정책·기본 연령 비교 7개 정책을 지원한다.
- 원문 해시 불일치 또는 서울 날짜의 2027년 이후에는 질문·연령 비교를 중단한다. 개정·규칙 변경과 적용 연도 만료 후의 이전 답변은 HTTP 409로 거절한다.
- 답변은 평가 본문으로만 보내며 저장하지 않는다. 기본 조건도 URL에 포함하지 않는다. 입력 수정·전체 삭제 시 이전 결과를 지운다.

## 검증

저장소 루트에서 `JAVA_HOME=/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`으로 실행했다. `5675733` 이후 변경은 문서뿐이다.

| 명령 | 결과·로그 |
|---|---|
| `npm run verify -- test:policy-catalog -- --tests 'kr.youthpolicymate.policy.catalog.HaetsalronYouthRulesTest'` | 통과, `.local/verification/1788874741527-3630b5ec.log` |
| `npm run verify -- package:backend` | 통과, `.local/verification/1788874790319-6a621f81.log` |

규칙 검사에서 세 이용 유형·소득 상한·증빙 미확인·생애 한도 소진·미응답, 만 19세·35세 경계와 서울 자정·연도 경계를 확인했다. PostgreSQL API 검사에서 질문·검색 필터·제출, 연령 충족 우선 및 최근 수집순 정렬, 다른 조건 미확인 유지와 개정·규칙·원문·연도 변경 차단을 확인했다. 실제 생성 OpenAPI와 저장 계약의 일치 검사도 포함한다.

Playwright에서 실제 내 조건 검색, 만 19세 경계일 충족·서울 기준일 표시, 상세 질문 이동·초점, 질문 5개·결과 4개를 확인했다. 청년사업자 인공 답변의 충족, 생애 한도 전액 사용의 불충족, 한도 확인 중의 미확인과 입력 수정·전체 삭제를 확인했다. 조건 입력은 URL에 노출되지 않았으며 검증용 답변은 지웠다. PC 결과와 390px 모바일의 선택지·줄바꿈·가로 넘침을 확인했다.

화면은 `/tmp/youth-admin-ui/haetsalron-basic-desktop.png`, `/tmp/youth-admin-ui/haetsalron-result-desktop.png`, `/tmp/youth-admin-ui/haetsalron-input-mobile.png`, `/tmp/youth-admin-ui/haetsalron-result-mobile.png`에 있다. API로 실제 질문 제공 정책 9건도 확인했다.

API 구조·생성 타입·웹 소스·의존성·설정은 `676fe5d` 이후 같아 [웹 검사·빌드](policy-source-notices.md#검증)를 재사용했다. 전체 서버 검사·원격 CI는 이번 범위에 실행하지 않았다. DB V20·기존 데이터·인증·외부 연동은 유지했다.

## 로컬 실행 상태

- Spring 8080: 저장소 루트에서 위 JDK의 `bin/java -jar backend/build/libs/youth-policy-mate-0.0.1-SNAPSHOT.jar --spring.profiles.active=local`, `/tmp/youth-haetsalron-backend.log`.
- Next.js 3000: 저장소 루트의 `npm run dev:web`, `/tmp/youth-source-notices-web-server.log`.
- 서버에 `REMINDERS_ENABLED=false EMAIL_ENABLED=false ONTONG_COLLECTION_SCHEDULE_ENABLED=false`를 지정했다. 다음 작업에서는 현재 프로세스를 확인한다.
