# 미래 청년 일자리 조건 질문

현재 질문·판정은 [공고별 조건 데이터](policy-rule-data.md)로 관리한다. 정책별 운영 클래스는 제거했으며 아래 공고 검토 기준과 당시 검증 기록은 유지한다.

2026-09-12, 코드 `130d8de` 기준. 정책 `20260722005400213264`의 서울영커리언스 점프업에 **2026년 5월 모집** 질문과 기본 연령 비교를 연결했다. 이후 2차 모집에는 적용하지 않는다.

## 공식 근거와 적용 범위

- [서울시 공고 제2026-1444호](https://youth.seoul.go.kr/bbs/view.do?key=2303300002&pstSn=2605040004), [첨부 공고문](https://youth.seoul.go.kr/atch/fileDown.do?cnncSn=2605040004&cnncTy=bbs&ordr=1)의 신청자격·참여제한·구비서류를 확인했다.
- 검토한 내용 해시는 `0d98b50fc87fc4e319676be23e6a900304a4434215e997004ed3e1f47bb8dfea`, 규칙 버전은 `future-youth-jobs-2026-may-v1`이다. 공고일인 2026년 5월 4일부터 연말까지 같은 원문에서만 질문을 제공한다. 정책 개정·규칙 버전·원문이 바뀌면 이전 답변을 거부한다.
- 공고의 나이 표현을 오늘의 만 나이로 환산하지 않고 명시된 출생일 범위와 의무복무 제대군인의 연장 표를 사용한다. 복무기간·증빙이 없으면 연령 상한 초과를 확정하지 않는다.

| 질문 | 비교 기준과 예외 |
|---|---|
| 연령 | 1986.1.1.~2007.12.31. 출생. 의무복무 제대군인은 복무기간 1년 미만·1~2년 미만·2년 이상에 따라 각각 1985·1984·1983년생까지 연장하며 인정 여부를 확인한다. |
| 거주 | 모집 신청 당시 서울 주민등록. 서울 소재 학교·직장만으로 대신하지 않으며 등록 형태가 불명확하면 확인을 남긴다. |
| 근로 | 신청서 제출일에 미취업 또는 주 30시간 이하 **또는** 근로계약기간 3개월 미만. 주 30시간·계약기간 3개월의 포함 여부를 구분한다. |
| 재학 | 대학·대학원 재학·휴학은 원칙적으로 제한. 수료·졸업예정·졸업유예와 방송통신·사이버·야간대학(원) 재학 예외는 증빙 인정 확인값으로 비교한다. |
| 사업자등록 | 등록이 없어야 하나, 실제 미영업 또는 근로자·임대사무실 없는 부동산임대업 증빙 예외를 구분한다. |
| 일자리 사업 | 다른 정부·서울시 일자리 창출 사업 참여 여부. 교육·수당만 받는 경우 사업 유형을 확인한다. |

졸업예정은 공고일 기준 마지막 학년 2학기 재학 또는 이수 여부를 확인한다. 예외의 증빙 인정 여부가 미확인이면 불충족으로 단정하지 않는다. 모든 결과에 직무별 법정 제한·근로 가능 여부·서류·가점·교육·면접 등 기관 확인 사항을 남긴다. 전체 자격은 추가 확인이며 민감한 이력이나 증빙은 입력받지 않는다.

## 원문 차이와 접수 기간

수집된 안내는 그대로 표시하고 별도 안내에서 차이를 설명한다.

- 수집된 `5월 4~31일`은 공고기간이며 실제 접수는 **5월 18~31일 23:59(서울)**다. 질문·결과는 해당 모집의 마감을 표시한다.
- ‘대학생 참여 불가’에는 위 재학 예외가 생략되어 있다.
- 수집된 ‘추가 참고 안내’는 다른 정책으로 연결된다. 별도 안내에서 확인한 모집 공고로 이동할 수 있다.

`PolicyRecruitment`가 검토 해시가 일치하는 경우 실제 접수 기간을 사용한다. 목록 필터와 상세의 기준을 맞추기 위해 V21에서 같은 정책번호·해시의 검색용 기간만 수정한다. 원본·본문·개정·회원 데이터는 변경하지 않는다. 이후 다른 원문은 기존 기간 해석 경로를 따른다. 마감 날짜는 기존과 같은 5월 31일이므로 마감 알림의 날짜는 바뀌지 않는다.

## 연결과 검증

`FutureYouthJobsRules`를 질문 제공·답변 평가·목록 질문 필터와 기본 연령 비교에 등록했다. 웹은 기존 질문·결과·원문 차이 컴포넌트를 사용한다. API 구조와 생성 타입은 변경하지 않는다.

다음 검증 이후 앱 코드는 변경하지 않았다. 웹의 동일 출처 안내 구분 수정은 `1c988f3`에 있다.

```sh
./backend/gradlew -p backend test \
  --tests 'kr.youthpolicymate.policy.catalog.FutureYouthJobsRulesTest' \
  --tests 'kr.youthpolicymate.policy.catalog.BasicConditionRulesTest' \
  --tests 'kr.youthpolicymate.policy.catalog.PolicyRecruitmentTest' \
  --tests 'kr.youthpolicymate.policy.catalog.PolicyCatalogTest' bootJar --no-daemon

./backend/gradlew -p backend test \
  --tests 'kr.youthpolicymate.policy.catalog.PolicyCatalogTest.migratesOnlyReviewedFutureYouthJobsPeriod' --no-daemon

npm run verify -- test:web -- src/app/policies/policy-content.test.tsx src/features/eligibility/policy-questionnaire.test.tsx
npm run verify -- check:web
```

- 서버 첫 실행 통과: 예외·미응답·날짜 경계, 원문·개정·규칙 변경 후 제출 거부, 기존 정책 API·생성 계약 일치와 패키징을 확인했다. 로그: `/tmp/youth-future-jobs-test.log`.
- 마이그레이션 검사에 실제 원본·개정 관계와 목록 상태 확인을 보완하고 해당 메서드만 다시 실행해 통과했다. 다른 정책번호·다른 해시의 기간과 원본·개정 수를 유지한다. 로그: `/tmp/youth-future-jobs-migration-test.log`.
- 웹 검사·린트·타입 검사 통과. 기록: `.local/verification/1789164221321-94fcc2ed.log`, `1789164221304-59695402.log`. 다른 웹 파일은 이전 전체 검사와 같아 재사용했다. 화면 구조·라우팅·의존성과 API 구조는 유지해 웹 빌드·전체 서버 검사·원격 CI는 반복하지 않았다.
- 실제 브라우저에서 1280px·390px의 질문 여섯 개와 세 안내를 확인했다. 근로·재학·사업 예외의 조건 충족, 재학 미확인의 추가 확인, 전체 자격의 추가 확인 유지, 답변 삭제를 검증했다. 처음 사용한 결과 선택자가 실제 화면과 달라 수정한 뒤 최종 실행을 마쳤다. 가로 넘침과 새 콘솔 오류는 없었다. 로그: `/tmp/youth-future-jobs-ui/flow-final.log`.
- 1280px에서 기본 조건 입력→점프업 검색→연령 충족·다른 조건 미확인·마감 표시→상세 질문 이동을 확인했다. 상세 질문은 빈 답변으로 시작하며 시험 기본 조건도 지웠다. 로그: `/tmp/youth-future-jobs-ui/age-flow.log`. 화면은 같은 디렉터리의 `questions-390.png`, `result-1280.png`, `age-search-1280.png` 등에 있다.

`JAVA_HOME`은 `/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`이다. 로컬 서버는 생성한 JAR를 `--spring.profiles.active=local`로 실행하며 `REMINDERS_ENABLED=false EMAIL_ENABLED=false ONTONG_COLLECTION_SCHEDULE_ENABLED=false`를 적용했다. 서버 로그는 `/tmp/youth-future-jobs-backend.log`, 웹은 기존 `npm run dev:web` 프로세스를 사용한다.

로컬 PostgreSQL에 V21을 적용했다. 대상 정책의 개정 2·내용 해시·마감일은 같고 접수 시작일만 5월 18일로 바뀐 것을 확인했다. 실제 회원 로그인·외부 신청·이메일 발송은 이번 검증 범위에 포함하지 않는다. 원격에는 반영하지 않았다.
