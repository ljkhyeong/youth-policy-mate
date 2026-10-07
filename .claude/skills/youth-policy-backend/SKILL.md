---
name: youth-policy-backend
description: 청년정책메이트의 Spring Boot 공통 코드·회원 인증·세션·JDBC/Flyway 변경 기준. 저장소 공통 스킬에 Claude Code 작업 보충을 더한다.
when_to_use: backend/ 아래 Java·설정·마이그레이션을 수정하거나 회원 인증·세션·권한·DB 스키마를 바꿀 때. 수집·자격·알림 규칙이 함께 바뀌면 해당 youth-policy 스킬도 적용한다.
---

# 백엔드 개발

공통 기준은 저장소 `skills/youth-policy-backend/SKILL.md`가 원본이다. 아래에 내용이 보이지 않으면 그 파일을 직접 읽는다. 공통 기준의 상대 링크는 원본 위치 기준이며 `../../docs/`는 저장소 루트의 `docs/`다.

## 공통 기준

!`cat "${CLAUDE_SKILL_DIR}/../../../skills/youth-policy-backend/SKILL.md"`

## Claude Code 보충

### 코드 위치

- `backend/src/main/java/kr/youthpolicymate/` 아래 기능 패키지: `member`(회원·OAuth·세션·이메일 Outbox), `policy`(모집·개정)와 `policy/catalog`(조회·질문·규칙 데이터와 판정표·연령 비교), `eligibility`(조건 결과·상태 값과 자치구 enum), `ingestion`(수집·AI·예산 예약), `admin`(관리자 API), `config`(보안·시간).
- DB 접근은 `JdbcClient`·`JdbcTemplate`과 `@Transactional`이다. 저장소 클래스 이름은 `*Store`가 많다. 새 계층을 만들기 전에 같은 패키지의 `*Store`·`*Service` 구성을 따른다.
- 현재 시각은 `config/TimeConfiguration`의 `Clock` 빈(UTC)을 주입받는다. 서울 날짜가 필요하면 `policy/SeoulTime.SEOUL`로 명시 변환한다(Clock 빈의 시간대에 의존하지 않는다). `LocalDate.now()`·`Instant.now()`를 직접 호출하지 않는다.

### 마이그레이션 번호

SQL은 `backend/src/main/resources/db/migration`, Java 마이그레이션은 `backend/src/main/java/db/migration`에 있다. 다음 번호는 두 위치를 함께 확인한다.

```bash
ls backend/src/main/resources/db/migration backend/src/main/java/db/migration | sed -nE 's/^V([0-9]+)__.*/\1/p' | sort -n | tail -1
```

출력한 최댓값에 1을 더한다. 브랜치를 병합할 때 같은 번호가 생기지 않았는지 다시 확인한다.

### 실행 전 확인

- PostgreSQL Testcontainers 테스트 전에 `docker info --format '{{.ServerVersion}}'`로 Docker 실행 여부를 확인한다. 실패하면 Docker 미실행을 코드 실패로 보고하지 않는다.
- Gradle이 Java 25 도구체인을 찾지 못하면 [HANDOFF](../../../HANDOFF.md)의 최근 `JAVA_HOME`을 확인한다. 시스템 기본 JDK는 바꾸지 않는다.
- 변경 파일에 맞는 `test:*` 스크립트 선택과 기록은 `youth-policy-verify` 스킬을 따른다.
- `.env`의 값을 출력하거나 로그·커밋에 남기지 않는다. 설정 존재 여부만 확인한다.
