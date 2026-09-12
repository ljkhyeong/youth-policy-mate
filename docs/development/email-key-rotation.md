# 이메일 암호화 키 교체

2026-09-12 기준. `42e8c78`에서 사전 점검과 일괄 교체 명령을 추가했다. 임시 PostgreSQL에서 검증했으며 실제 DB·키·운영 설정은 변경하지 않았다.

## 처리 범위

| 명령 | 처리 |
|---|---|
| `check` | 현재 키로 모든 저장 주소를 복호화하고 주소·확인 코드·대기 확인 메일 건수를 출력한다. 읽기 전용 트랜잭션이며 DB를 변경하지 않는다. |
| `apply` | 현재 키로 주소를 복호화한 뒤 새 키로 다시 암호화한다. 확인 코드 해시·만료 시각을 지우고 대기 중인 확인 메일을 취소한다. |

- 주소 설정 버전·인증 완료·수신 동의·동의 시각은 유지한다. 미인증 주소를 인증 완료로 바꾸거나 동의를 켜지 않는다.
- 정책 메일·서비스 내 알림·공급자 발송 ID와 결과는 유지한다. 발송 중·발송 완료·결과 미확인 확인 메일도 재발송하거나 취소 상태로 바꾸지 않는다.
- 확인용 HMAC은 원래 코드를 알 수 없으면 새 키로 변환할 수 없다. 미완료 인증은 코드를 다시 요청해야 한다. 확인 코드를 담은 암호문은 삭제한다.
- 수신 해제 토큰 해시는 이메일 암호화 키를 사용하지 않으므로 기존 링크를 유지한다.
- `apply`는 회원·이메일 설정·발송 테이블의 쓰기 잠금을 얻지 못하면 대기하지 않고 중단한다. 주소가 손상됐거나 현재 키가 맞지 않으면 앞서 바꾼 주소까지 롤백한다. 트랜잭션은 Spring의 [TransactionTemplate](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html)을 사용한다.
- 잠금은 교체 중의 충돌을 막을 뿐, 다른 API 프로세스의 종료를 확인하지는 못한다. **모든 API와 별도 DB 쓰기 작업을 중지한 상태에서 적용한다.**

## 환경변수와 실행

[전용 환경변수 예시](email-key-rotation.env.example)를 비밀 설정으로 주입한다. 파일을 자동으로 읽지 않으며 `.env`의 실제 값은 변경하지 않는다.

| 변수 | 의미 |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | 대상 PostgreSQL. 포트 기본값은 5432이며 기존 운영 연결 설정을 사용한다. |
| `EMAIL_ENCRYPTION_KEY` | 현재 DB에 사용한 키. `check`와 `apply` 모두 필요하다. |
| `EMAIL_ENCRYPTION_NEXT_KEY` | 현재 키와 다른 새 32바이트 난수의 Base64 문자열. `apply`에만 필요하다. 일반 API에는 주입하지 않는다. |

명령은 운영 DB 연결 프로필과 JDBC 구성만 시작한다. 컴포넌트 검색·웹 서버·Flyway·발송·정기 작업을 시작하지 않으며 공급자 키와 공개 도메인은 필요하지 않다. DB 스키마는 기존 앱의 Flyway 적용이 끝난 상태여야 한다. API나 수집 CLI가 함께 실행 중인지 자동 판별하지 않는다.

저장소 루트에서 환경변수를 주입한 뒤 실행한다. 키는 명령 인자로 전달하지 않는다.

```sh
npm run email:rotate-key -- --args=check
npm run email:rotate-key -- --args=apply
```

배포 이미지에서는 별도 유지보수 Job의 시작 명령을 다음과 같이 지정한다. 기존 API 컨테이너 안에서 서비스와 동시에 실행하지 않는다. 끝의 `check`를 `apply`로 바꾸면 교체한다.

```sh
java -Dloader.main=kr.youthpolicymate.member.EmailKeyRotationCommand \
  -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher check
```

Spring Boot의 [PropertiesLauncher와 loader.main](https://docs.spring.io/spring-boot/specification/executable-jar/property-launcher.html)을 사용한다. Dockerfile의 기본 시작 명령은 일반 API 실행으로 유지했다. 실제 이미지 빌드·Job 구성은 운영자가 진행한다.

## 운영자가 적용할 순서

1. 모든 API 인스턴스와 별도 쓰기 작업을 중지하고 [DB 백업](database-backup.md)을 확보한다. 기존 키도 복구할 수 있게 보관한다.
2. 현재 키로 `check`를 실행해 주소를 읽을 수 있는지 확인한다. 이 단계에서는 새 키가 필요하지 않다.
3. 현재 키와 새 키를 교체 명령에 주입하고 `apply`를 한 번 실행한다. 종료 코드 0과 교체 건수를 확인한다.
4. API의 `EMAIL_ENCRYPTION_KEY`를 새 키로 바꾼다. 새 키를 현재 키로 사용해 `check`를 실행한 뒤 API를 다시 시작한다. 교체용 `EMAIL_ENCRYPTION_NEXT_KEY` 주입은 제거한다.
5. 이메일 설정 조회와 기존 동의 상태를 확인한다. 미완료 인증 사용자는 새 확인 코드를 요청한다.

교체 명령은 서비스 비밀 설정을 자동으로 바꾸지 않는다. DB를 바꾼 뒤 이전 키로 API를 시작하면 주소를 읽지 못한다. 키가 분실되면 암호문만으로 복구할 수 없다. 이전 키로 암호화한 백업은 이 작업의 대상이 아니며, 해당 백업을 복원하려면 이전 키가 필요하다. 백업·키의 보관 및 폐기 시점은 운영자가 정한다.

오류는 비밀값·주소·SQL 원문 없이 안내하고 종료 코드 1을 반환한다. 연결 단절 등으로 커밋 결과를 확인하지 못했다면 곧바로 재적용하지 않는다. 주소가 있는 DB에서 현재 키와 새 키로 각각 `check`를 실행해 어떤 키로 읽히는지 확인한다. 양쪽 모두 실패하면 연결·잠금·암호문 상태를 점검하고, 필요하면 백업과 그 백업에 맞는 키로 복구한다. 주소가 0건이면 복호화 결과만으로 사용 키를 구분할 수 없다.

## 검증

- `npm run verify -- test:email-key-rotation`: 실제 Flyway 스키마의 PostgreSQL에서 읽기 전용 점검, 주소·동의·정책 발송 유지, 확인 코드 만료, 잘못된 키·손상 주소 롤백, 동시 쓰기 잠금 충돌을 확인했다.
- 독립 Java 프로세스로 `check → apply → 새 키 check`를 실행했다. 발송·AI·수집·알림 설정을 켠 테스트 환경에서도 해당 작업을 시작하지 않았고 키·주소·코드를 출력하지 않았다. 실제 공급자 호출은 없었다.
- 최초 검사는 잠금 충돌 예외의 예상 클래스만 달라 실패했다. PostgreSQL의 실제 잠금 오류 `55P03`을 검사하도록 고친 뒤 통과했다. 수정 후 로그: `.local/verification/1789222770860-fbb29e9d.log`.
- `npm run verify -- check:backend`: 전체 서버 테스트·배포 JAR 빌드 통과. 기존 이메일·운영 프로필·API 계약 검사를 포함한다. 로그: `.local/verification/1789222803927-c7591562.log`.
- 배포 JAR의 `PropertiesLauncher`에서 교체 명령에 진입하고 인자가 없으면 DB 연결 전 종료하는 것을 확인했다. 로그: `/tmp/youth-email-key-rotation-jar.log`. 정상 DB 교체는 독립 Java 프로세스와 임시 PostgreSQL로 검사했다.
- 웹·공개 API 계약·DB 스키마는 변경하지 않았다. 이전 웹 검사·빌드를 재사용했으며 브라우저를 실행하지 않았다. 실제 키 교체·운영 DB 복구·Docker 이미지·k3s 실행은 미검증이다.
