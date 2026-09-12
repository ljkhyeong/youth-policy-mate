# 회원 탈퇴

`/my` 아래의 **회원 탈퇴**에서 삭제 내용을 확인하고 실행한다. 운영자 설정이나 외부 API 키 없이 동작하며 실제 회원을 삭제하는 운영 명령은 제공하지 않는다.

## 동작과 삭제 범위

- `DELETE /api/v1/me/account`: 로그인한 본인만 탈퇴한다. 대상 회원 ID를 입력받지 않고 기존 세션·CSRF 검사를 사용한다. 완료는 204, 비회원은 401, CSRF 오류는 403, 저장소 장애는 503이다. Next 중계도 같은 출처의 DELETE만 허용하고 쿠키 삭제 응답을 전달한다.
- 현재 서비스 DB의 회원 프로필·소셜 식별자·저장 조건·관심 정책·마감 예약·알림·이메일 설정·확인 코드·발송 이력을 삭제한다. V31은 기존 회원 외래 키 3개에 `ON DELETE CASCADE`를 적용한다. 기존 이메일 외래 키와 함께 삭제를 한 트랜잭션으로 처리하며 마이그레이션 자체는 회원 데이터를 지우지 않는다.
- 회원 삭제의 행 잠금은 기존 조건 저장·알림 배정과 충돌하는 변경을 순서대로 처리한다. 이미 외부 공급자 호출을 시작한 이메일은 회수하지 못한다. 삭제된 Outbox의 뒤늦은 웹훅은 대상 없음으로 처리한다.
- Spring Session의 회원별 조회·삭제 API로 다른 기기의 세션까지 정리하고, Spring Security 로그아웃 처리로 현재 인증과 쿠키를 지운다. 세션 저장소는 별도 트랜잭션을 사용하므로 실패 시 일부 기기는 로그아웃될 수 있지만 회원 데이터 삭제는 롤백한다. [Spring Session 트랜잭션](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)·[Spring Security 로그아웃](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)
- 탈퇴 전에 시작된 OAuth 콜백이 늦게 세션을 저장해도 세션 조회·회원·관리자 API에서 회원 존재 여부를 확인해 인증을 해제한다. DB 장애는 비회원 상태로 숨기지 않고 503으로 응답한다.
- 성공한 화면은 개인 상태·임시 정책 저장·확인한 생년월일을 지우고 다른 탭에 계정 변경을 알린다. 응답이 끊기거나 실패하면 완료를 표시하지 않고 로그인 상태를 다시 확인하도록 안내한다.

다른 회원과 공개 정책·원본·개정은 유지한다. 관리자가 탈퇴해도 정책 변경 이력의 작업자 UUID·사유는 남지만 삭제된 회원 프로필과 연결할 수 없다. 같은 소셜 계정으로 다시 로그인하면 새 UUID로 가입하며 기존 데이터나 관리자 권한을 복원하지 않는다.

## 운영자가 정할 사항

카카오·네이버 계정 자체와 서비스 연결을 자동 해제하지 않는다. 로그인 후 외부 토큰을 보관하지 않는 기존 정책을 유지한다. 개인정보 처리 안내, 관리자 기록·백업·메일 공급자의 보관 기간과 삭제 절차, 백업 복원 시 탈퇴 회원을 되살리지 않는 운영 절차는 별도 결정이 필요하다. 이 기능으로 외부 공급자나 기존 백업의 데이터까지 삭제했다고 안내하지 않는다.


## 검증

코드 기준: `f58f24a`. 실제 회원 삭제·외부 이메일·소셜 제공자 요청은 실행하지 않았다.

| 명령·범위 | 결과·로그 |
|---|---|
| `npm run generate:api` | 서버 계약·TypeScript 생성. `/tmp/youth-withdrawal-contract.log` |
| `npm run verify -- check:backend` | 전체 실행에서 관리자 테스트 준비 3건 실패. 삭제·롤백·OAuth 다중 세션·만료/늦은 세션과 나머지 검사는 통과. `.local/verification/1789188853915-728df67b.log` |
| `npm run verify -- test:member-flow -- --tests 'kr.youthpolicymate.admin.CollectionExceptionApiTest' --tests 'kr.youthpolicymate.ingestion.PolicyAiRuleDraftStoreTest'` | 실패한 테스트의 관리자 회원 준비·DB 장애 주입 범위를 보정하고, 회원 존재 확인 장애도 추가 검증해 통과. `.local/verification/1789188994892-c056141b.log` |
| `npm run verify -- check:api-types` | 통과. `.local/verification/1789188857547-46c5a5d1.log` |
| `npm run verify -- test:web -- 'src/app/api/member/[...path]/route.test.ts'` | 탈퇴 경로·같은 출처·세션/CSRF·회원 ID 제외·쿠키 삭제 중계 통과. `.local/verification/1789188856342-62f5aa26.log` |
| `npm run verify -- check:web` | 최종 화면 타입·린트 통과. `.local/verification/1789189063566-3719c59d.log` |
| `npm run verify -- package:backend` | 실행 JAR 빌드 통과. `.local/verification/1789189068878-adeeb46b.log` |
| `npm run verify -- build:web` | 최종 웹 운영 실행 파일 빌드 통과. `.local/verification/1789189070656-90a64f5e.log` |

별도 브라우저의 모의 응답으로 확인 전 실행 차단, 503 안내, 처리 중 중복 차단, 완료 초점, 다른 탭 초기화, 계정 전환 뒤 늦은 응답 무시, 390px 가로 넘침 없음을 확인했다. 결과·화면은 `/tmp/youth-withdrawal-ui/`에 있다. 실제 서버로 탈퇴 요청을 보내지 않았다.

로컬에는 빌드 JAR 복사본을 반영했다. V31 적용 전후 회원·정책·저장·이메일 건수는 같았고 API 상태·웹 `/my`는 200, 비회원 회원 조회는 401이었다. Docker 이미지 빌드·운영 배포·원격 CI는 실행하지 않았다. 백업·외부 공급자 삭제는 이 검증에 포함하지 않는다.
