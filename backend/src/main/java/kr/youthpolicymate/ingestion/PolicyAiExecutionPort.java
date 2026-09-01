package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationState.ChargeConfirmation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Dispatch;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.NoChargeConfirmation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.UncertainOutcome;
import kr.youthpolicymate.ingestion.PolicyAiResult.Request;

import java.util.Objects;

// 공급자 요청·응답 DTO가 아니다. 실제 어댑터가 외부 결과와 청구 상태를 내부 사실로 분류해 반환한다.
@FunctionalInterface
public interface PolicyAiExecutionPort {
    Outcome execute(Invocation invocation);

    record Invocation(String reservationId, Request request, Dispatch dispatch) {
        public Invocation {
            requireText(reservationId, "AI 요청 예약 식별자가 필요합니다.");
            Objects.requireNonNull(request, "실행할 AI 예정 요청이 필요합니다.");
            Objects.requireNonNull(dispatch, "외부 호출 식별 정보가 필요합니다.");
            if (dispatch.dispatchedAt().isBefore(request.preparedAt())) {
                throw new IllegalArgumentException("외부 호출은 AI 요청 준비보다 빠를 수 없습니다.");
            }
        }
    }

    sealed interface Outcome permits ResponseReceived, Uncertain {}

    record ResponseReceived(PolicyAiResult result, Billing billing) implements Outcome {
        public ResponseReceived {
            Objects.requireNonNull(result, "분류한 AI 응답 결과가 필요합니다.");
            Objects.requireNonNull(billing, "AI 응답의 청구 확인 상태가 필요합니다.");
        }
    }

    record Uncertain(UncertainOutcome uncertain) implements Outcome {
        public Uncertain {
            Objects.requireNonNull(uncertain, "결과 미확인 정보가 필요합니다.");
        }
    }

    sealed interface Billing permits PendingCharge, ConfirmedCharge, ConfirmedNoCharge {}

    enum PendingCharge implements Billing { INSTANCE }

    record ConfirmedCharge(ChargeConfirmation confirmation) implements Billing {
        public ConfirmedCharge {
            Objects.requireNonNull(confirmation, "청구 확인 정보가 필요합니다.");
        }
    }

    record ConfirmedNoCharge(NoChargeConfirmation confirmation) implements Billing {
        public ConfirmedNoCharge {
            Objects.requireNonNull(confirmation, "무과금 확인 정보가 필요합니다.");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }
}
