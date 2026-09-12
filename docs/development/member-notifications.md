# 회원 알림 조회

`f189078` 기준. `/my`에서 이전 알림까지 페이지로 조회하고 안 읽은 알림을 따로 볼 수 있다.

## 동작

- 화면은 최신순으로 20건씩 표시한다. 최근 100건 제한을 없애고 이전·다음 페이지를 제공한다.
- ‘알림’ 탭의 숫자는 회원 전체의 안 읽은 알림 수다. 전체·안 읽은 알림 필터와 받은 시각을 표시하며 시각은 한국 시간으로 변환한다.
- 안 읽은 목록에서 읽음 처리하면 필터를 유지하고 첫 페이지로 이동한다. 읽음 처리로 목록이 줄어들 때 다음 항목을 건너뛰지 않도록 한다. 전체 목록은 현재 페이지를 유지한다.
- 알림 조회·읽음 처리로 관심 정책과 마감 일정을 다시 조회하지 않는다. 알림 오류는 해당 영역에 표시하고 재시도를 제공한다. 빈 목록과 오류를 구분하고 늦게 도착한 이전 조회 결과는 반영하지 않는다.
- 이메일 알림 설정은 펼침 메뉴로 제공한다. 키보드로 열고 닫을 수 있으며 기존 인증·동의 기능을 유지한다.

## API와 데이터

`GET /api/v1/me/notifications`는 `page`(기본 1, 최소 1), `pageSize`(기본 20, 1~50), `filter`(`ALL` 또는 `UNREAD`, 기본 `ALL`)를 받는다. 응답은 `items`, `page`, `pageSize`, `total`, `hasNext`, `unreadCount`다. `total`은 선택한 필터의 전체 개수이며 `unreadCount`는 필터와 무관하다.

Spring 요청 검증으로 잘못된 페이지·크기·필터를 400으로 처리한다. 인증된 회원 ID만 사용하고 응답은 `no-store`다. 프런트 중계는 세 쿼리만 전달하며 타입은 서버 OpenAPI에서 생성한다. 응답 필드와 기본 조회 개수가 바뀌므로 서버와 웹을 함께 반영한다.

`MemberPolicyStore`는 읽기 전용 `REPEATABLE_READ` 트랜잭션에서 개수와 목록을 조회한다. 필터를 적용한 뒤 `created_at DESC, id DESC`로 정렬한다. 같은 시각의 알림도 순서가 고정된다. 페이지 간 데이터 시점까지 고정하지는 않는다. 마이그레이션과 기존 알림 생성·발송 경로는 변경하지 않았다.

읽음 처리는 기존 `POST /api/v1/me/notifications/{id}/read`를 사용한다. 본인 알림에만 적용하고 CSRF 검증을 거친다. 반복 요청으로 최초 읽은 시각을 바꾸지 않는다.

## 검증

| 명령 | 확인 범위·로그 |
|---|---|
| `npm run generate:api` | OpenAPI·TypeScript 생성. `/tmp/youth-notifications-contract.log` |
| `npm run verify -- test:member-flow` | PostgreSQL에서 105건 페이지 조회·동시각 정렬·전체 미읽음 수·필터·잘못된 입력·회원 소유권·CSRF·읽음 중복 요청과 기존 회원 흐름. `.local/verification/1789181368554-d92b815d.log` |
| `npm run verify -- test:web -- 'src/app/api/member/[...path]/route.test.ts' src/features/member/member-policy-list.test.tsx` | 허용 쿼리·쿠키·캐시 중계와 기존 마감 일정. `.local/verification/1789181369734-ca3d0531.log` |
| `npm run verify -- check:web` | 최종 화면 린트·타입. `.local/verification/1789181635944-e5d887d4.log` |
| `npm run verify -- check:api-types` | 생성 계약 일치. `.local/verification/1789181372106-033d5a53.log` |
| `npm run verify -- build:web` | 이메일 설정 펼침 메뉴를 포함한 최종 배포 빌드. `.local/verification/1789181646134-5291945d.log` |
| `npm run verify -- package:backend` | 검증한 서버 실행 파일 생성. `.local/verification/1789181410965-baca36f5.log` |

브라우저는 별도 `member-notifications` 세션과 테스트 회원 응답으로 확인했다. 100건 이후 이동·미읽음 수·키보드 읽음 처리·탭 간 필터 유지·불필요한 관심 정책 조회 방지·503 후 복구·늦은 응답 무시·빈 목록·390px/1280px 표시를 확인했다. 결과와 화면은 `/tmp/youth-member-notifications-ui/`에 있다. 대기는 `page.waitForTimeout`을 사용하고 오류 검사는 ‘서비스 알림’ 영역으로 한정한다.

통합 검사 이후 수정은 이메일 설정의 화면 배치와 스타일이며 최종 웹 검사·빌드·브라우저로 확인했다. 서버 코드·테스트·의존성은 동일해 DB 검사를 반복하지 않았다. 이후 변경은 문서뿐이다. 로컬 서버 상태 200·비회원 알림 API 401·내 정책 화면 200을 확인했다. 실제 소셜 제공자 로그인과 외부 이메일 전달은 검증하지 않았다.
