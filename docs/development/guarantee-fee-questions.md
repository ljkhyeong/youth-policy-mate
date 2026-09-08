# 전세보증금반환보증 보증료 지원 질문

2026-09-08 구현, 코드 `6e4a2e5`. 정책 `20260527005400113223`의 검토한 원문에 서울 신청자의 공통 조건 질문을 연결했다. 질문 6개로 4개 항목을 비교하며, 보증 가입 심사·실제 지급액은 계산하지 않는다.

## 근거와 비교 범위

- [정부24의 국토교통부 안내](https://www.gov.kr/portal/rcvfvrSvc/dtlEx/161300000103): 보증기관·유효기간·납부, 보증금·유형별 소득 한도, 지원 제외와 소득서류. 최종 수정일 2026.5.8., 2026.9.8. 확인.
- [광진구 2026년 안내](https://www.gwangjin.go.kr/portal/bbs/B0000001/view.do?menuNo=200001&nttId=6206995): 신청일 기준 서울 청년 만 19~39세, 혼인신고 7년 이내 신혼부부, 분양권·입주권과 중복지원 제외. 등록일 2026.5.14.
- [중구 2026년 안내](https://www.junggu.seoul.kr/content.do?cid=1425544719&cmsid=14390&mode=view): 서울의 유형별 소득 기준·유효한 보증·예산 소진 조건을 대조했다. 보도일 2026.2.5.
- [전주시 2026년 안내](https://www.jeonju.go.kr/deokjingu/planweb/board/view.9is?boardUid=ff8080818990c3490189d3dfa75a116d&contentUid=ff8080818990c349018acafdf408297e&dataUid=9be517a89b212afa019cfa597d8b4e10&page=6): 국토부 사업의 신청인·배우자 무주택과 분양권·입주권 포함 기준을 교차 확인했다. 전주시의 접수처·지역 기준은 서울에 적용하지 않았다.

| 결과 항목 | 확인하는 답변 |
|---|---|
| 반환보증 가입·납부 | 신청일에 유효한 HUG·HF·SGI 전세보증금반환보증과 납부 완료. 미가입·다른 보증만 가입·만료·해지·미납은 불충족, 처리 중은 미확인 |
| 임차보증금 | 계약서의 3억 원 이하 여부. 대출 잔액·보증료로 대신하지 않음 |
| 본인·배우자 무주택 | 신청일에 모두 무주택인지 확인. 분양권·입주권 포함, 미혼은 본인만 확인 |
| 유형별 연소득 | 지원 유형·서류 기준·금액 질문을 합쳐 비교. 청년 5천만 원, 청년 외 6천만 원, 신혼부부 합산 7천5백만 원 이하 |

신혼부부는 나이와 관계없이 해당 유형을 선택한다. 그 외 서울 청년은 신청일 만 19~39세로 안내하고, 청년·신혼부부가 아닌 유형도 제공한다. 기본 조건의 생년월일만으로 탈락시키거나 상세 답변을 채우지 않는다. 기본 연령 비교 범위는 6개 정책 그대로다.

소득은 신청처 기준의 합산 범위·대상 연도·증빙을 확인한 답변만 비교한다. 기혼자는 배우자 서류도 필요하다. 기준·유형·금액이 미확인이면 소득이 낮거나 높다는 답변만으로 충족·불충족을 확정하지 않는다. 무소득도 증빙 확인을 생략하지 않는다.

주소·거주, 임차인과 보증서 명의, 국적·재외국민, 등록임대주택·법인 임차인·동일 보증서 중복지원, 기타 제한·서류·예산은 추가 확인으로 남긴다. 확인한 항목이 모두 충족해도 전체 자격은 `NEEDS_REVIEW`다. 보증 가입일에 따른 지원 한도와 특약·실제 납부액·지급 여부를 자동 계산하지 않는다. 원문·모집 상태·수집 데이터는 유지한다.

## 연결과 변경 처리

- 규칙 `GuaranteeFeeRules`, 버전 `guarantee-fee-2026-v1`, 원문 해시 `4dc5e18b6a09b35f00cf00c7be3a608689f6c2d33ee8b191c38a9823dac8cc24`.
- 기존 질문 API·DTO·웹 폼·답변 검증을 재사용한다. `ReviewedPolicyQuestions`의 목록 필터와 내 조건의 질문 표시에도 연결했다. 질문 제공 범위는 8개 정책이다.
- 원문 해시가 다르면 질문을 제공하지 않는다. 개정·규칙 버전 변경과 서울 날짜의 2027년 이후에는 이전 답변을 HTTP 409로 거절한다.
- 답변은 평가 요청 본문으로만 보내며 저장하지 않는다. 입력 수정 시 이전 결과를 지우고 다시 비교한다.

## 검증

저장소 루트에서 `JAVA_HOME=/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`으로 실행했다. `6e4a2e5` 이후 변경은 문서뿐이다.

| 명령 | 결과·로그 |
|---|---|
| `npm run verify -- test:policy-catalog -- --tests 'kr.youthpolicymate.policy.catalog.GuaranteeFeeRulesTest'` | 통과, `.local/verification/1788872974929-2bfaade1.log` |
| `npm run verify -- package:backend` | 통과, `.local/verification/1788873012028-5b58ed17.log` |

규칙 검사에서 유형별 소득 경계·증빙 미확인, 보증 가입 상태·보증금·주택 소유, 미응답과 서울 연도 경계를 확인했다. PostgreSQL API 검사에서 질문·검색 필터·내 조건의 질문 표시, 제출 결과와 개정·규칙·원문·연도 변경 차단을 확인했다. 실제 생성 OpenAPI와 저장 명세의 일치 검사도 포함한다.

Playwright로 실제 로컬 목록 검색 1건과 상세 질문 영역 이동·초점을 확인했다. 인공 답변의 신혼부부 소득 충족, 청년 유형으로 변경 후 불충족, 소득서류 확인 중의 미확인과 답변 수정·전체 삭제를 확인했다. PC와 390px 모바일의 선택지·결과 줄바꿈 및 가로 넘침을 확인했고 검증용 답변은 지웠다. 화면은 `/tmp/youth-admin-ui/guarantee-desktop.png`, `/tmp/youth-admin-ui/guarantee-mobile-input.png`, `/tmp/youth-admin-ui/guarantee-mobile-result.png`에 있다.

API 구조·생성 타입·웹 소스·의존성·설정은 `676fe5d` 이후 같아 [웹 검사·빌드](policy-source-notices.md#검증)를 재사용했다. 전체 서버 검사와 원격 CI는 이번 범위에 실행하지 않았다. DB V20·실제 정책 데이터·인증·외부 연동은 변경하지 않았다.

## 로컬 실행 상태

- Spring 8080: 저장소 루트에서 위 JDK의 `bin/java -jar backend/build/libs/youth-policy-mate-0.0.1-SNAPSHOT.jar --spring.profiles.active=local`, `/tmp/youth-guarantee-backend.log`.
- Next.js 3000: 저장소 루트의 `npm run dev:web`, `/tmp/youth-source-notices-web-server.log`.
- 서버에 `REMINDERS_ENABLED=false EMAIL_ENABLED=false ONTONG_COLLECTION_SCHEDULE_ENABLED=false`를 지정했다. 다음 작업에서는 현재 프로세스를 확인한다.
