# AI 후보의 개정·버전 검사 모델

2026-08-31 구현 기준. [설계](../design/policy-ai-candidates.md)에 따라 현재 정책에 맞는 AI 요약·조건 추출 후보 참조만 수용·재사용하는 순수 Java 모델을 추가했다. 실제 AI 호출·후보 본문·화면·DB는 연결하지 않았다.

## 코드와 사용

코드는 `backend/src/main/java/kr/youthpolicymate/ingestion/`에 있다.

| 코드 | 역할 |
|---|---|
| `PolicyAiResult.Request` | 근거인 정책 적용 개정, 요약/조건 추출 구분, 생성 방식 버전, AI 요청 순번·준비 시각 |
| `PolicyAiResult` | 예정 요청과 결과 확인 시각, 후보 참조 또는 미생성 사유 |
| `Generated` | 후보 내부 ID·본문 SHA-256. 실제 본문 저장·정확성 검사 기능은 없음 |
| `Unavailable` | 요청 실패·잘못된 출력·한도 보류를 호출 측이 분류한 값 |
| `PolicyAiCandidateState` | 한 정책·작업 종류의 마지막 처리 결과와 마지막 정상 후보, 수용·현재 재사용 판단 |

`PolicyAiCandidateState.empty(policyId, kind)`로 시작한다. 요약과 조건 추출은 별도 상태이며 후보를 서로 교체하지 않는다.

`consider(currentPolicy, expectedRequest, result)`에 현재 정책과 최신 예정 요청을 함께 전달한다. 반환한 결정과 다음 불변 상태를 사용한다. 이 모델은 요청 순번을 발급하거나 예정 요청을 저장하지 않는다. 호출 측은 원천 수집·AI 응답의 도착 순서가 아닌 별도 AI 요청 순번을 사용해야 한다.

정책 적용 개정은 기존 `PolicyRevisionState.AppliedRevision`을 그대로 받는다. 개정 번호뿐 아니라 개정을 만든 원본·비교 내용·수집 순번·시각까지 같아야 한다. 같은 내용의 후속 수집은 적용 개정의 원본을 바꾸지 않으므로 기존 후보를 재사용할 수 있다.

주요 결정은 다음과 같다.

- `CANDIDATE_RECORDED`·`UNAVAILABLE_RECORDED`: 정상 후보 또는 미생성 사유를 기록한다. 미생성은 마지막 정상 후보를 지우지 않는다.
- `NO_CURRENT_REVISION`·`REVISION_MISMATCH`·`SOURCE_MISMATCH`: 현재 적용 개정 없음, 개정 번호 차이, 동일 번호의 원본 근거 차이를 구분한다.
- `GENERATION_VERSION_MISMATCH`: 현재 예정 요청과 다른 생성 방식의 결과다.
- `STALE_REQUEST`·`UNEXPECTED_REQUEST`·`REQUEST_CONFLICT`: 낮은 순번, 예정 요청보다 높은 미발급 순번, 같은 순번에 다른 요청 정보가 붙은 경우다.
- `REPLAYED`·`RESULT_CONFLICT`: 같은 결과 재전달 또는 같은 요청에 다른 결과가 붙은 경우다. 기존 상태를 유지한다.

`candidateFor(currentPolicy, generationVersion)`는 현재 개정과 사용할 생성 방식에 맞는 후보만 반환한다. `lastGeneratedResult()`는 마지막 후보 기록을 확인하는 용도이며, 현재 후보 조회를 대신하지 않는다. `lastProcessedResult()`에는 마지막으로 수용한 정상·미생성 결과가 남는다. 전체 요청·거절·실패·이전 후보 이력은 저장하지 않는다.

## 검증

저장소 루트에서 실행한다.

```sh
npm run test:ai-candidates
npm run test:ingestion
npm run check:backend
```

`test:ai-candidates`는 전용 12건, `test:ingestion`은 수집 진행 12·AI 후보 12·후속 [사전 판단](ai-request-admission.md) 14·[예약 상태](ai-budget-reservation-lifecycle.md) 13건, 총 51건을 선택한다. 두 단위 검사에는 인증키·DB·Docker가 필요하지 않다. 전체 빌드는 실제 PostgreSQL 통합 테스트 때문에 Docker가 필요하다.

전용 명령의 12건과 전체 빌드의 서버 215건(도메인 200·개발 API/계약 13·실제 DB/기본 차단 2)이 실패·건너뛰기 없이 통과했다. 이번 DB 검사는 첫 실행에 통과했다. Gradle 캐시·Docker 접근에는 권한 확장을 사용했고 기존 JVM 클래스 공유 경고는 유지했다.

대표 검증은 요약/조건 추출의 분리, 현재 개정 없음, 같은 내용 재수집·원천 실패의 기존 후보 유지, 개정 변경·A→B→A, 동일 개정 번호의 원본 차이, 생성 방식 변경, 재시도 전후의 늦은 결과, 재전달·충돌, 실패·출력 오류·한도 보류 뒤 재사용·복구다. 실패 결과를 같은 순번의 성공으로 교체하지 않는 것도 확인한다.

화면·API 계약·DB 스키마는 바꾸지 않았다. 웹 53건은 앞선 검증 기록이며 이번에는 웹·브라우저·생성 타입 검사를 다시 실행하지 않았다. 실제 원천·AI 호출, GitHub 푸시와 배포는 하지 않았다.

## 남은 연결과 한계

- 후보 ID·해시의 형식과 버전 관계만 확인한다. 후보 본문·근거 구간·정책 해석의 정확성이나 자격 조건을 검증하지 않는다.
- `BUDGET_LIMIT`은 호출 측이 전달한 사유다. 후속 [사전 판단·예약 모델](ai-request-admission.md)에서 주어진 한도·사용액·예약액·최대 비용을 비교하고 최초 DB 예약을 저장하지만, 이 사유 기록과 직접 연결하지 않았다. 실제 가격 계산·외부 청구 차단은 없으며 후보 재사용 불가가 유료 호출 허용을 뜻하지 않는다.
- 정책·예정 요청은 저장소의 최신 값이어야 한다. 응답이 스스로 제시한 요청을 최신 예정 요청으로 사용하면 안 된다. 기록 시점의 원자적 DB 비교·갱신은 아직 없다.
- AI 결과 대기 중에는 DB 잠금을 잡지 않는다. 실제 저장에서는 정책 개정·최신 예정 요청·마지막 처리 순번을 함께 다시 확인해야 한다.
- 현재 모델은 원본·정책 상태를 수정하지 않는다. 실제 화면의 원문 유지·안내와 규칙 판정 연결은 별도 구현이므로 PRD AC-06·AC-11 전체 완료가 아니다.
- 수집 실행 모델과 AI 결과의 저장·재시작 복구, 실제 자동 공개·자격 규칙 승격은 연결하지 않았다. AI 후보를 확정 규칙으로 자동 바꾸는 메서드는 제공하지 않는다.
