package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationLifecycleStore.Snapshot;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Cancellation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.ChargeConfirmation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.NoChargeConfirmation;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Attempt;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Status;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.Billing;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.ConfirmedCharge;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.ConfirmedNoCharge;

import java.util.Objects;

// 공급자 조회 DTO가 아니다. 실제 어댑터가 기존 호출과 청구 상태를 내부 사실로 분류해 반환한다.
@FunctionalInterface
public interface PolicyAiRecoveryPort {
    Outcome inspect(Inspection inspection);

    record Inspection(Snapshot reservation, Attempt attempt) {
        public Inspection {
            Objects.requireNonNull(reservation, "확인할 AI 요청 예약 상태가 필요합니다.");
            Objects.requireNonNull(attempt, "AI 예약 복구 시도가 필요합니다.");
            if (!reservation.reservationId().equals(attempt.reservationId())) {
                throw new IllegalArgumentException("복구 시도와 확인할 AI 요청 예약이 다릅니다.");
            }
            if (attempt.status() != Status.ACTIVE) {
                throw new IllegalArgumentException("활성 상태인 AI 예약 복구 시도만 확인할 수 있습니다.");
            }
        }
    }

    sealed interface Outcome permits ResponseFound, ChargeFound, NoChargeFound, NotDispatched,
            CheckFailed, ReviewRequired {}

    record ResponseFound(PolicyAiResult result, Billing billing) implements Outcome {
        public ResponseFound {
            Objects.requireNonNull(result, "복구한 AI 응답 결과가 필요합니다.");
            Objects.requireNonNull(billing, "복구한 AI 응답의 청구 확인 상태가 필요합니다.");
            if (billing instanceof ConfirmedCharge confirmed
                    && confirmed.confirmation().confirmedAt().isBefore(result.recordedAt())) {
                throw new IllegalArgumentException("복구한 청구 확인은 AI 응답 확인보다 빠를 수 없습니다.");
            }
            if (billing instanceof ConfirmedNoCharge noCharge
                    && noCharge.confirmation().confirmedAt().isBefore(result.recordedAt())) {
                throw new IllegalArgumentException("복구한 무과금 확인은 AI 응답 확인보다 빠를 수 없습니다.");
            }
        }
    }

    record ChargeFound(ChargeConfirmation confirmation) implements Outcome {
        public ChargeFound {
            Objects.requireNonNull(confirmation, "확인된 AI 청구 정보가 필요합니다.");
        }
    }

    record NoChargeFound(NoChargeConfirmation confirmation) implements Outcome {
        public NoChargeFound {
            Objects.requireNonNull(confirmation, "확인된 AI 무과금 정보가 필요합니다.");
        }
    }

    record NotDispatched(Cancellation cancellation) implements Outcome {
        public NotDispatched {
            Objects.requireNonNull(cancellation, "외부 호출 전 취소 정보가 필요합니다.");
        }
    }

    enum CheckFailed implements Outcome { INSTANCE }
    enum ReviewRequired implements Outcome { INSTANCE }
}
