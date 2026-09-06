# 접수 상태별 정책 검색

2026-09-06 기준. 공개 목록과 내 조건 결과에서 전체·접수 전·접수 기간·마감·상시·소진 시 마감·기간 미확인을 선택한다. 기본값은 전체다. 접수 상태는 개인 자격과 별도로 취급한다.

## 동작과 저장

- 공개 목록의 검색어·질문 필터·페이지 링크에 `recruitmentStatus`를 유지한다. 상태 변경은 필터 적용 또는 검색으로 제출하며 첫 페이지로 돌아간다.
- 내 조건 결과는 상태를 바꾸면 첫 페이지를 요청한다. 검색·정렬·재시도에 선택을 유지하고 검색·필터 초기화로 전체 결과를 복원한다. 기본 조건은 요청 본문에만 전송하고 저장하지 않는다.
- 두 API는 선택한 `RecruitmentStatus`를 전체 검색에 적용한 뒤 건수와 페이지를 조회한다. 미지정은 전체, 잘못된 상태는 400이다. 조회 횟수는 두 번을 유지하며 모든 원문을 메모리에 올려 필터링하지 않는다.
- `PolicyRecruitmentWindow`는 화면의 `PolicyRecruitment.period`와 같은 해석 결과를 검색용 종류·시작·종료 시각으로 변환한다. 날짜형 종료일은 다음 날 서울 자정의 미포함 경계다. 요청에서 한 번 읽은 시각으로 필터와 표시를 계산하므로 시간이 지나도 재수집 없이 상태가 바뀐다.
- Flyway V18은 `policies`에 검색용 기간 열·제약·종류 인덱스를 추가하고 기존 현재 개정의 원문을 이전한다. 데이터 삭제나 개정 증가는 없다. 수집 시 새 개정과 기간을 같은 트랜잭션에서 반영하며 중복·오래된 응답은 기존 기간을 유지한다.

기간 해석이나 검토된 공고의 시각 보정 규칙을 바꿀 때는 기존 정책의 검색용 기간도 새 Flyway 마이그레이션으로 갱신해야 한다. 적용한 V18은 수정하지 않는다. 기간 원문·시각 근거·미확인 기준은 [접수 상태 표시](policy-recruitment-display.md)를 따른다.

## 검증

코드 리비전 `94463e2`를 확인했다. 이후 변경은 문서뿐이다.

| 범위 | 실행과 결과 | 로그 |
|---|---|---|
| 계약 생성 | `npm run generate:api` 통과 | `/tmp/youth-recruitment-filter-api.log` |
| 서버·DB | `npm run verify -- test:policy-catalog -- --tests kr.youthpolicymate.policy.catalog.PolicyRecruitmentTest --tests kr.youthpolicymate.ingestion.OntongCollectionTest` 통과 | `.local/verification/1788705954067-e6c605b0.log` |
| 관련 웹 테스트 | `npm run verify -- test:web -- src/app/policies src/features/policies src/features/conditions src/app/api/member` 통과 | `.local/verification/1788705949688-5ca9ffd1.log` |
| 린트·타입 | `npm run verify -- check:web` 통과 | `.local/verification/1788706355077-f1b12115.log` |
| 생성 계약 일치 | `npm run verify -- check:api-types` 통과 | `.local/verification/1788705949719-31f5c516.log` |
| 웹 운영 빌드 | `npm run verify -- build:web` 통과 | `.local/verification/1788706360388-0db4b35d.log` |
| 서버 패키징 | `npm run verify -- package:backend` 통과 | `.local/verification/1788706008621-02a84dc0.log` |

PostgreSQL에서 기존 V17 데이터의 이전, 여러 페이지에 걸친 필터·건수·중복 없음, 질문 필터와 검색, 날짜·시각 경계, 원문 변경, 잘못된 상태를 확인했다. 기존 수집의 중복·순서 검증도 통과했다. 관련 웹 테스트 후 바뀐 조건 화면의 레이블 묶음과 스타일은 최종 린트·타입·빌드와 실제 브라우저에서 확인했다.

실제 로컬 40건은 접수 기간 8건·마감 15건·상시 14건·기간 미확인 3건이었다. 각 필터 결과의 상태·건수와 전체 40건의 누락·중복 없음을 확인했다. 이는 수집한 공고의 기간 구분이며 실제 접수창 운영 상태는 아니다.

브라우저에서는 공개 목록의 접수 기간 필터, Enter 검색, 질문 필터 병용, 빈 결과·초기화를 확인했다. 내 조건은 2페이지에서 필터 변경 후 1페이지 이동, 상시 14건에서 K-패스 검색 1건, 최근 수집순 유지·초기화를 확인했다. 모바일에서는 필터 이름과 선택 상자를 함께 배치했으며 화면·문서 너비가 375px로 같았다. 검증용 조건을 모두 지우고 화면 크기 변경을 해제했다.

전체 서버 검사·외부 로그인·이메일 전달·AI 호출·정기 수집은 이번 범위에 없어 실행하지 않았다.

## 재사용할 실행 환경

- JDK: `/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`. 서버 검증은 Docker·Gradle 캐시 접근 권한으로 실행했다.
- 웹 빌드는 내부 처리기의 포트 사용 권한이 필요하다. 제한된 환경의 실패가 Turbopack 캐시에 남아 권한을 바꾼 재시도도 실패했다. `frontend/.next/cache/turbopack`만 `/tmp/youth-recruitment-filter-turbopack-cache-1788706079`로 보관한 뒤 필요한 권한으로 통과했다. 실행 중인 개발 서버의 `.next/dev`는 변경하지 않았다.
- 웹은 기존 127.0.0.1:3000 개발 서버를 사용한다. 변경이 열려 있는 탭에 반영되지 않으면 검증 입력을 지운 뒤 새로고침한다.
- 백엔드는 `backend`에서 같은 JDK의 `bin/java -jar build/libs/youth-policy-mate-0.0.1-SNAPSHOT.jar --spring.profiles.active=local`로 실행했다. `REMINDERS_ENABLED=false EMAIL_ENABLED=false ONTONG_COLLECTION_SCHEDULE_ENABLED=false`를 지정했으며 로그는 `/tmp/youth-recruitment-filter-backend.log`다. 로컬 DB에 V18을 적용했다. 다음 작업에서 프로세스 상태를 확인한다.
