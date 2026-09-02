# AI 예약 복구 재확인·자동 중단 정책

2026-09-01 구현 기준. [복구 소유권 설계](../design/ai-reservation-recovery.md)에 따라 미완료 AI 예약의 복구 이력으로 다음 확인 가능 시각과 자동 중단 사유를 계산한다. 후속 [내부 운영 조회](ai-reservation-recovery-operations-query.md)가 PostgreSQL 대상과 이력을 함께 읽고, [작업 배정](ai-reservation-recovery-work-assignment.md)이 잠근 현재 상태에 이 정책을 다시 적용한다. 실제 복구 실행·스케줄러에는 연결하지 않았다.

## 입력과 결과

`AiReservationRecoveryRetryPolicy`는 다음 입력을 받는다.

- 호출 측이 정한 최대 시도 횟수와 시도별 재확인 간격
- 현재 `AiBudgetReservationLifecycleStore.Snapshot`
- 예약별 순서가 보존된 `AiReservationRecoveryStore.Attempt` 이력
- 판단 시각

고정 기본값은 없다. 최대 횟수는 1 이상이어야 하고 간격은 모두 0보다 길어야 한다. 최대 3회라면 첫 시도 뒤와 두 번째 시도 뒤의 간격 두 개를 전달한다.

| 결과 | 의미 |
|---|---|
| `Ready` | 첫 시도 또는 설정한 간격을 지난 다음 시도를 시작할 수 있음 |
| `Deferred.ACTIVE_LEASE` | 현재 작업자의 임대가 아직 유효함 |
| `Deferred.RETRY_INTERVAL` | 직전 완료·만료 뒤 재확인 간격이 지나지 않음 |
| `Stopped.RESERVATION_TERMINAL` | 정산·취소·무과금 해제로 예약이 종료됨 |
| `Stopped.MANUAL_REVIEW_REQUIRED` | 기술 재시도가 아니라 사람 확인이 필요함 |
| `Stopped.MAXIMUM_ATTEMPTS_REACHED` | 완료·만료를 포함한 시도 수가 한도에 도달함 |

`CHECK_FAILED`는 설정한 간격 뒤 재확인한다. `CHECK_COMPLETED`도 예약이 미완료이면 청구 대기 가능성이 있으므로 같은 규칙을 적용한다. 작업자 장애로 끝난 `EXPIRED`와 임대 시간이 지난 `ACTIVE`도 시도 횟수에 포함한다.

## 저장·작업자 연결 전제

순수 정책은 `claimNext`를 호출하거나 복구 이력을 수정하지 않는다. 내부 운영 조회도 후보 예약과 이력을 읽어 결과를 보여줄 뿐 소유권을 획득하지 않는다. 작업 배정은 조회 당시와 예약 잠금 뒤 판단이 모두 `Ready`인 경우에만 소유권을 만든다. 판단 뒤 소유권 획득 사이의 경쟁은 기존 예약 잠금·활성 임대 제약으로 다시 확인한다.

현재 간격과 최대 횟수는 운영값이 아니다. 공급자 응답 특성·요금·조회 제한을 확인한 뒤 값을 정해야 한다. 수동 재실행, 한도 도달 해제, 운영자 승인 계약도 아직 없다.

## 검사

저장소 루트에서 실행한다.

```sh
npm run test:ai-recovery-policy
npm run test:ingestion
npm run check:backend
```

전용 10건은 첫 시도, 활성 임대, 임대 만료 뒤 간격, 정확한 가능 시각 경계, 확인 실패·확인 완료의 재확인, 수동 검토, 완료·만료를 포함한 최대 횟수, 종료 예약, 일정·이력 입력 오류를 확인한다.

`test:ingestion` 69건과 내부 운영 조회·작업 배정을 포함한 전체 서버 306건이 실패·오류·건너뛰기 없이 통과했다. 전체 구성은 DB 없는 도메인 245건, 개발 API·계약 13건, PostgreSQL 예약·실행·복구·연결·운영 조회·작업 배정/기본 차단 48건이다. 화면·API·Flyway 계약은 변경하지 않았다.

## 남은 작업

- 공급자 선정 뒤 실제 간격·최대 시도 횟수 확정
- 배정된 시도를 복구 조정자의 트랜잭션 밖 확인 입력으로 연결하는 작업자
- 최대 횟수 도달·수동 검토 예약의 명시적 재개와 감사 기록
- 필요 시 임대 갱신과 끝난 임대의 소급 연장 차단
