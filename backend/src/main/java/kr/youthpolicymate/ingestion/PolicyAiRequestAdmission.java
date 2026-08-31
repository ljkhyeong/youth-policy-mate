package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiRequestBudget.Balance;
import kr.youthpolicymate.ingestion.AiRequestBudget.CostCeiling;
import kr.youthpolicymate.ingestion.PolicyAiResult.Request;
import kr.youthpolicymate.ingestion.PolicyAiResult.Unavailable;
import kr.youthpolicymate.policy.PolicyRevisionState;

import java.time.Instant;
import java.util.Objects;

public final class PolicyAiRequestAdmission {
    private PolicyAiRequestAdmission() {}

    public static Decision assess(PolicyRevisionState.Transition change, PolicyAiCandidateState candidates,
                                  Request request, Mode mode, AiRequestBudget budget, Instant at) {
        Objects.requireNonNull(change, "정책 개정 적용 결과가 필요합니다.");
        Objects.requireNonNull(candidates, "AI 후보 상태가 필요합니다.");
        Objects.requireNonNull(request, "검사할 AI 예정 요청이 필요합니다.");
        Objects.requireNonNull(mode, "자동 처리·재시도 구분이 필요합니다.");
        Objects.requireNonNull(budget, "예산·최대 비용 확인 상태가 필요합니다.");
        Objects.requireNonNull(at, "검사 시각이 필요합니다.");
        var policy = Objects.requireNonNull(change.state(), "현재 정책 상태가 필요합니다.");
        Objects.requireNonNull(change.decision(), "개정 적용 결정이 필요합니다.");
        if (!policy.policyId().equals(request.policyId()) || !policy.policyId().equals(candidates.policyId())
                || candidates.kind() != request.kind()) {
            throw new IllegalArgumentException("정책·후보·요청의 정책 ID와 AI 작업 종류가 같아야 합니다.");
        }
        if (at.isBefore(request.preparedAt())) throw new IllegalArgumentException("요청 준비 전에 검사할 수 없습니다.");
        if (policy.currentRevision().isEmpty()) return defer(Reason.NO_CURRENT_REVISION);
        var current = policy.currentRevision().orElseThrow();
        if (!current.equals(request.sourceRevision())) return defer(Reason.STALE_POLICY);

        var reusable = candidates.candidateFor(policy, request.generationVersion());
        if (reusable.isPresent()) return new Reuse(reusable.orElseThrow());

        var previous = candidates.lastProcessedResult();
        if (previous.filter(result -> result.request().sequence() >= request.sequence()).isPresent()) {
            return defer(Reason.REQUEST_ALREADY_PROCESSED);
        }
        if (mode == Mode.AUTOMATIC) {
            if (change.decision() != PolicyRevisionState.Decision.REVISION_CREATED) return defer(Reason.NO_NEW_REVISION);
            if (previous.filter(result -> result.request().sourceRevision().equals(current)).isPresent()) {
                return defer(Reason.EXPLICIT_RETRY_REQUIRED);
            }
        } else if (previous.filter(result -> result.request().sourceRevision().equals(current)
                && result.request().generationVersion().equals(request.generationVersion())
                && result.outcome() instanceof Unavailable).isEmpty()) {
            return defer(Reason.RETRY_NOT_APPLICABLE);
        }

        if (budget.balance().isEmpty()) return defer(Reason.BUDGET_NOT_CONFIGURED);
        var balance = budget.balance().orElseThrow();
        if (!balance.contains(at)) return defer(Reason.BUDGET_PERIOD_INACTIVE);
        if (balance.remainingWon().signum() <= 0) return defer(Reason.BUDGET_LIMIT);
        if (budget.costCeiling().isEmpty()) return defer(Reason.COST_NOT_CONFIRMED);
        var cost = budget.costCeiling().orElseThrow();
        if (!cost.request().equals(request)) return defer(Reason.COST_REQUEST_MISMATCH);
        if (!at.isBefore(cost.validUntil())) return defer(Reason.COST_EXPIRED);
        if (cost.maximumWon().compareTo(balance.remainingWon()) > 0) return defer(Reason.BUDGET_LIMIT);
        return new ReservationRequired(balance, cost);
    }

    private static Deferred defer(Reason reason) { return new Deferred(reason); }

    public enum Mode { AUTOMATIC, RETRY }
    public sealed interface Decision permits Reuse, Deferred, ReservationRequired {}
    public record Reuse(PolicyAiResult candidate) implements Decision {}
    public record Deferred(Reason reason) implements Decision {}

    // 이 결과만으로 호출하지 않는다. 최신 상태 확인과 요청별 DB 예약 저장이 먼저다.
    public record ReservationRequired(Balance balance, CostCeiling cost) implements Decision {}

    public enum Reason {
        NO_CURRENT_REVISION, STALE_POLICY, REQUEST_ALREADY_PROCESSED, NO_NEW_REVISION, EXPLICIT_RETRY_REQUIRED,
        RETRY_NOT_APPLICABLE, BUDGET_NOT_CONFIGURED, BUDGET_PERIOD_INACTIVE, BUDGET_LIMIT,
        COST_NOT_CONFIRMED, COST_REQUEST_MISMATCH, COST_EXPIRED
    }
}
