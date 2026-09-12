# 회원 알림

2026-09-12, 코드 `b92d2cc` 기준. 알림 탭에서 페이지 조회·미읽음 필터·개별 읽음·모두 읽음을 제공한다. [화면 주소와 로그인 복귀](member-navigation.md)

## 동작

- 최신순으로 20건씩 표시하고 이전·다음 페이지, 전체/안 읽은 필터, 한국 시간의 받은 시각을 제공한다. 탭의 숫자는 회원 전체의 미읽음 수다.
- 페이지·필터는 주소에 반영해 새로고침·뒤로 가기·새 탭·로그인 후 복원한다. 필터를 바꾸면 첫 페이지로 이동한다.
- ‘모두 읽음’은 현재 페이지와 관계없이 본인의 미읽음 전체에 적용한다. 미읽음이 없으면 버튼을 비활성화한다. 갱신 시작 뒤 도착한 알림은 미읽음으로 남고, 이미 읽은 시각·알림 내용·이메일 발송 상태는 유지한다.
- 개별·전체 읽음 후 목록과 미읽음 수를 다시 조회한다. 안 읽은 목록은 필터를 유지하고 첫 페이지 주소로 교체하며, 전체 목록은 현재 페이지를 유지한다. 관심 정책·마감 일정은 다시 조회하지 않는다.
- 조회·읽음·변경 후 재조회 중에는 필터·페이지·읽음 버튼을 잠근다. 결과가 불명확하면 이전 목록과 미읽음 수를 숨기고 다시 불러오기를 제공한다. 재조회는 변경 요청을 반복하지 않는다. 로그인 만료 시 현재 화면으로 복귀하는 로그인 링크를 제공한다.
- 재시도나 읽음 후 오류 버튼 또는 필터로 초점을 돌린다. 다른 탭이나 영역으로 옮긴 초점은 유지하고, 늦은 응답이 현재 화면을 바꾸지 않도록 한다.
- 이메일 설정은 펼침 메뉴로 제공한다. 서비스 내 읽음 처리와 이메일 수신 동의는 별개다.

## API와 데이터

| 요청 | 동작 |
|---|---|
| `GET /api/v1/me/notifications` | `page` 기본 1, `pageSize` 기본 20·최대 50, `filter`는 `ALL`/`UNREAD` |
| `POST /api/v1/me/notifications/{id}/read` | 본인 알림 한 건 읽음, 성공 시 204 |
| `POST /api/v1/me/notifications/read-all` | 본인 미읽음 전체 처리, 본문 없이 요청하고 성공 시 204 |

조회 응답은 `items`, `page`, `pageSize`, `total`, `hasNext`, `unreadCount`다. `total`은 선택한 필터의 전체 개수, `unreadCount`는 필터와 무관한 미읽음 수다. 잘못된 조회 조건은 400으로 처리한다.

인증된 회원 ID만 사용하고 변경 요청에는 CSRF 검증을 적용한다. 비회원은 401, CSRF 누락은 403이다. Next 중계는 고정된 회원 API로만 전달하며 알림 조회의 세 쿼리 외에는 전달하지 않는다. 회원 응답은 공용 캐시에서 제외하고 OpenAPI·웹 타입은 서버에서 생성한다. 새 경로를 포함한 서버와 웹을 함께 반영한다.

`MemberPolicyStore.notifications`는 읽기 전용 `REPEATABLE_READ` 트랜잭션에서 개수와 목록을 조회한다. 필터를 적용한 뒤 `created_at DESC, id DESC`로 정렬하며 페이지 간 데이터 시점까지 고정하지는 않는다.

`readAll`은 `member_id`와 `read_at IS NULL` 조건으로 한 번 갱신한다. [PostgreSQL의 갱신 대상 조회 규칙](https://www.postgresql.org/docs/18/transaction-iso.html#XACT-READ-COMMITTED)에 따라 쿼리 시작 시 이미 커밋된 알림을 처리한다. 화면 조회 이후라도 갱신 시작 전에 도착한 알림은 포함한다. 새 컬럼·별도 배치·개별 API 반복 호출은 추가하지 않았다.

## 검증

저장소 루트에서 실행했다. Java는 `/Users/lim/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.3+9/Contents/Home`을 사용했다.

| 명령 | 확인 범위·로그 |
|---|---|
| `npm run verify -- test:member-flow` | PostgreSQL 회원 통합 검사. 모든 페이지의 본인 미읽음 처리·인증/CSRF·다른 회원과 최초 읽은 시각 보존·갱신 중 도착한 알림 보존. `.local/verification/1789219264340-aa455341.log` |
| `npm run generate:api` | OpenAPI·TypeScript 생성. `/tmp/youth-notifications-read-all-contract.log` |
| `npm run verify -- check:api-types` | 생성 계약 일치. `.local/verification/1789219420161-f0809eb8.log` |
| `npm run verify -- test:web -- 'src/app/api/member/[...path]/route.test.ts'` | 중계 경로·메서드·출처·쿠키/CSRF·쿼리 제한. `.local/verification/1789219265493-114dd6c6.log` |
| `npm run verify -- check:web` | 웹 린트·타입 검사. `.local/verification/1789219420179-7c9f8609.log` |
| `npm run verify -- build:web` | 배포용 웹 빌드. `.local/verification/1789219560663-975a1d21.log` |
| `npm run verify -- package:backend` | 테스트 재실행 없는 서버 실행 파일 빌드. `.local/verification/1789219566420-88e0b149.log` |

전용 헤드리스 세션 `youth-notifications-read-all`에서 `/tmp/youth-notifications-read-all/flow.js`를 실행했다. 결과는 같은 폴더의 `result-final.log`다. 전체 페이지 처리·중복 차단·변경 후 재조회·새 미읽음 표시·현재 페이지/검색 필터 보존·응답 유실 시 조회만 재시도·로그인 만료·다른 탭의 초점·개별 읽음·키보드·빈 상태·390px/1280px 화면을 통과했다. 화면은 `screen-390.png`, `screen-1280.png`로 확인했다.

첫 실행은 탭 이동이 완료되기 전에 새로고침한 검증 스크립트 때문에 대기가 끝났다. 주소 변경을 기다리도록 스크립트를 수정한 뒤 통과했으며 이 실패로 앱 코드를 변경하지 않았다. 브라우저의 읽음 요청 7건은 모의 API에서만 처리했고 실제 회원 변경·외부 발송은 없었다. 기존 3000 포트 서버를 사용했으며 전용 브라우저는 종료했다.

관련 서버·웹 검사는 통과했지만 전체 서버 검사를 재실행한 것은 아니다. 실제 소셜 제공자 로그인·외부 이메일 송수신·홈서버 이미지 실행과 운영 설정은 이번 검증 범위에서 제외했다.
