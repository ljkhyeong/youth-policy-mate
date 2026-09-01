# AI 실행·복구 결과의 현재 후보 검사 연결

2026-09-01 구현 기준. [AI 후보 설계](../design/policy-ai-candidates.md)에 따라 인공 실행·복구에서 확인한 `PolicyAiResult`를 현재 정책 개정과 최신 예정 요청에 다시 대조한다. 후보 상태의 다음 값을 계산할 뿐 DB에는 저장하지 않는다.

## 연결 범위

`PolicyAiCandidateResultProjector`는 결과가 생긴 경로에 따라 다음처럼 처리한다.

| 입력 | 처리 |
|---|---|
| 실행 `Responded` | 청구 대기·확정과 관계없이 `PolicyAiCandidateState.consider`로 검사 |
| 실행 `UncertainRun`·`NotStarted` | 결과 미확인 또는 미실행 사유로 생략 |
| 복구 `ResponseFound` + `CHECK_COMPLETED` | 복구 적용이 완료된 응답만 `consider`로 검사 |
| 복구의 청구·무과금·호출 전 중단만 확인 | AI 응답이 없으므로 생략 |
| 복구 시도 미완료·수동 검토 | 오래되거나 적용하지 못한 결과일 수 있으므로 생략 |

실행 응답과 복구 응답은 같은 `consider` 규칙을 거친다. 현재 적용 개정·원본 근거·생성 방식·요청 순번이 다르면 `REVISION_MISMATCH` 같은 기존 결정을 반환하며 후보를 교체하지 않는다. 실행·복구가 성공했다는 이유로 오래된 결과를 현재 후보로 승격하지 않는다.

복구 포트의 `ResponseFound`는 AI 결과와 청구 상태를 함께 보존한다. 청구가 아직 대기 중이면 예약은 `DISPATCHED`와 예약액을 유지하면서 응답 확인만 `CHECK_COMPLETED`로 끝낸다. 확정 청구·무과금이면 기존 복구 펜싱 안에서 정산·해제를 적용한다. 청구 사실만 있는 `ChargeFound`·`NoChargeFound`는 후보 검사 근거가 아니다.

## 반환값과 저장 책임

- `Evaluated`: 결과 출처, 검사한 `PolicyAiResult`, 기존 후보 모델의 상태 전이를 반환한다.
- `Skipped`: 결과 출처, 생략 사유, 변경하지 않은 후보 상태를 반환한다.
- 정책·예정 요청·후보 상태의 정책 ID와 작업 종류가 다르면 입력 오류로 거절한다.

반환한 다음 상태를 저장하는 책임은 호출 측에 있다. 실제 저장을 연결할 때에는 AI 응답을 기다리는 동안 DB 잠금을 잡지 않고, 저장 직전 현재 정책 개정·최신 예정 요청·마지막 처리 순번을 짧은 트랜잭션에서 다시 확인해야 한다.

## 검사

저장소 루트에서 실행한다.

```sh
npm run test:ai-candidate-projection
npm run test:ai-recovery-execution
npm run test:ingestion
npm run check:backend
```

순수 결과 연결 8건은 정상·미생성 실행 결과, 결과 미확인·미실행 생략, 적용 완료한 복구 응답, 수동 검토·청구 전용 복구 생략, 복구 중 정책 개정 변경, 입력 범위 불일치를 확인한다. 복구 조정자 10건은 청구 대기 응답의 예약액 유지와 응답 확인 완료, 응답과 확정 청구의 동시 복구를 포함한다.

`test:ingestion` 59건과 전체 서버 286건이 실패·오류·건너뛰기 없이 통과했다. 전체 구성은 DB 없는 도메인 235건, 개발 API·계약 13건, PostgreSQL 예약·실행·복구·연결/기본 차단 38건이다. 화면·API·Flyway 계약은 변경하지 않았다.

## 남은 작업

- 후보 본문·근거 구간과 `PolicyAiCandidateState`의 PostgreSQL 저장
- 저장 직전 현재 정책 개정·최신 예정 요청의 원자적 재검사
- 실제 공급자 응답·청구 조회를 내부 결과로 변환하는 어댑터
- 후보 품질 검토, 자동 공개 기준과 자격 규칙 승격의 별도 승인 경계
