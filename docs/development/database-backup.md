# DB 백업과 복구 검증

`a3e7f6e` 기준. 로컬 Compose PostgreSQL을 수동으로 백업하고, 네트워크가 분리된 임시 DB에 실제로 복원하는 명령을 제공한다. PostgreSQL 버전은 `compose.yaml`의 설정을 사용한다.

## 실행

저장소 루트에서 Docker와 원본 PostgreSQL을 실행한 뒤 백업한다.

```sh
npm run db:up
npm run db:backup
```

파일명은 생략하면 `.local/backups/`에 생성 시각을 붙여 정한다. 직접 지정할 수도 있다. 복구 검증에는 백업 명령이 출력한 파일 경로를 전달한다.

```sh
npm run db:backup -- .local/backups/manual.dump
npm run db:verify-backup -- .local/backups/manual.dump
```

백업은 `pg_dump --format=custom`으로 생성한다. 쓰기가 끝난 파일만 최종 이름으로 저장하며 기존 파일은 덮어쓰지 않는다. 새 백업 폴더는 0700, 파일은 0600 권한이다. `.local/backups/`와 `*.dump`는 Git에서 제외한다. 다른 위치에 저장할 때도 해당 폴더의 접근 권한을 확인한다.

검증은 별도 PostgreSQL 컨테이너에서 `pg_restore --single-transaction --exit-on-error`를 실행하고 복원한 사용자 테이블 수를 표시한다. 호스트 포트와 디렉터리를 연결하지 않으며 앱·수집·AI·이메일 작업을 실행하지 않는다. 정상 완료·복구 실패·처리된 종료 신호에는 임시 컨테이너와 볼륨을 제거한다. 운영 DB를 대상으로 하는 복원 명령은 제공하지 않는다.

## 보관과 운영 적용

- 파일에는 회원 정보·세션·수집 원본을 포함한 DB 데이터가 들어간다. `dump` 형식은 암호화가 아니므로 공유 자료나 로그에 넣지 않는다.
- 앱 설정·이메일 암호화 키·외부 인증키는 DB 백업에 포함되지 않는다. PostgreSQL 전역 역할·테이블스페이스도 별도 대상이다. 검증용 복구는 원래 소유권과 권한 부여를 적용하지 않으며, 운영 복구에는 실제 역할·권한·설정을 준비해야 한다.
- 이번 명령은 같은 장비에 만드는 수동 백업이다. 정기 실행 주기·보관 기간·암호화한 외부 보관 위치·운영 DB 전환 절차는 배포 환경에 맞춰 정한다. 백업 파일을 자동 삭제하지 않는다.
- 강제 종료나 Docker 장애로 정리가 실행되지 않았다면 `ypm-restore-check-`로 시작하는 임시 컨테이너를 확인해 제거한다. 백업 중 강제 종료로 남은 `.backup-` 임시 폴더는 완료된 백업이 아니다.
- PostgreSQL 오류에 행이나 SQL 본문이 포함될 수 있어 도구는 작업 단계와 종료 코드만 출력한다. 실패 시 파일 경로·Docker 상태·이미지 버전부터 확인한다.

## 검증

| 명령 | 확인 범위·로그 |
|---|---|
| `npm run verify -- test:db-backup` | 독립된 PostgreSQL에서 텍스트·바이너리 데이터·외래키·시퀀스 복원, 원본 유지, 파일 권한, 덮어쓰기 차단, 실패·중단 정리. `.local/verification/1789184668862-f121b962.log` |
| `npm run verify -- check:tools` | 기존 개발 도구 검사. `.local/verification/1789184669936-1f1ec199.log` |

2026-09-12에 실제 로컬 DB를 `.local/backups/youth-policy-2026-09-12-restore-verified.dump`로 백업했다. 파일 0600·폴더 0700·Git 제외를 확인했고 임시 DB에 테이블 40개를 복구했다. 결과는 `/tmp/youth-db-backup-live-verification.log`에 있다. 완료 후 검증용 컨테이너가 없고 기존 서버 상태가 200임을 확인했다.

앱 코드·스키마·의존성을 변경하지 않아 웹·서버 전체 검사와 재시작은 반복하지 않았다. 앱 검증 기준은 `238a28a`를 유지한다. 운영 장비 장애·외부 보관·실제 서비스 전환은 이번 검증에 포함하지 않는다.
