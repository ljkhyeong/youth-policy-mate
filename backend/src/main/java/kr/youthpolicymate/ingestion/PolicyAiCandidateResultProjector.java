package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.CompletionDecision;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.RecoveryResult;
import kr.youthpolicymate.ingestion.PolicyAiExecutionCoordinator.Responded;
import kr.youthpolicymate.ingestion.PolicyAiExecutionCoordinator.Run;
import kr.youthpolicymate.ingestion.PolicyAiExecutionCoordinator.UncertainRun;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryCoordinator.Recovered;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.ResponseFound;
import kr.youthpolicymate.ingestion.PolicyAiResult.Request;
import kr.youthpolicymate.policy.PolicyRevisionState;

import java.util.Objects;

// AI 결과를 저장하지 않는다. 현재 정책·예정 요청과 비교한 다음 후보 상태만 반환한다.
public final class PolicyAiCandidateResultProjector {
    public Projection projectExecution(PolicyRevisionState policy, Request expected,
                                       PolicyAiCandidateState candidates, Run run) {
        validateScope(policy, expected, candidates);
        Objects.requireNonNull(run, "AI 실행 결과가 필요합니다.");
        if (run instanceof Responded responded) {
            return evaluate(Source.EXECUTION, policy, expected, candidates, responded.result());
        }
        return skipped(Source.EXECUTION,
                run instanceof UncertainRun ? SkipReason.EXECUTION_RESULT_UNCERTAIN
                        : SkipReason.EXECUTION_NOT_STARTED,
                candidates);
    }

    public Projection projectRecovery(PolicyRevisionState policy, Request expected,
                                      PolicyAiCandidateState candidates,
                                      PolicyAiRecoveryCoordinator.Run run) {
        validateScope(policy, expected, candidates);
        Objects.requireNonNull(run, "AI 예약 복구 결과가 필요합니다.");
        if (!(run instanceof Recovered recovered)) {
            return skipped(Source.RECOVERY, SkipReason.RECOVERY_NOT_STARTED, candidates);
        }
        if (!(recovered.outcome() instanceof ResponseFound response)) {
            return skipped(Source.RECOVERY, SkipReason.RECOVERY_RESPONSE_NOT_FOUND, candidates);
        }
        if (!wasApplied(recovered)) {
            return skipped(Source.RECOVERY, SkipReason.RECOVERY_RESULT_NOT_APPLIED, candidates);
        }
        return evaluate(Source.RECOVERY, policy, expected, candidates, response.result());
    }

    private static boolean wasApplied(Recovered recovered) {
        var completion = recovered.application().completion();
        boolean completed = completion.decision() == CompletionDecision.COMPLETED
                || completion.decision() == CompletionDecision.REPLAYED;
        return completed && completion.attempt()
                .flatMap(AiReservationRecoveryStore.Attempt::result)
                .filter(result -> result == RecoveryResult.CHECK_COMPLETED)
                .isPresent();
    }

    private static Projection evaluate(Source source, PolicyRevisionState policy, Request expected,
                                       PolicyAiCandidateState candidates, PolicyAiResult result) {
        return new Evaluated(source, result, candidates.consider(policy, expected, result));
    }

    private static Projection skipped(Source source, SkipReason reason, PolicyAiCandidateState candidates) {
        return new Skipped(source, reason, candidates);
    }

    private static void validateScope(PolicyRevisionState policy, Request expected,
                                      PolicyAiCandidateState candidates) {
        Objects.requireNonNull(policy, "현재 정책 상태가 필요합니다.");
        Objects.requireNonNull(expected, "최신 AI 예정 요청이 필요합니다.");
        Objects.requireNonNull(candidates, "현재 AI 후보 상태가 필요합니다.");
        if (!candidates.policyId().equals(policy.policyId())
                || !candidates.policyId().equals(expected.policyId())
                || candidates.kind() != expected.kind()) {
            throw new IllegalArgumentException("정책·예정 요청·AI 후보 상태의 범위가 다릅니다.");
        }
    }

    public sealed interface Projection permits Evaluated, Skipped {}

    public enum Source { EXECUTION, RECOVERY }

    public enum SkipReason {
        EXECUTION_NOT_STARTED,
        EXECUTION_RESULT_UNCERTAIN,
        RECOVERY_NOT_STARTED,
        RECOVERY_RESPONSE_NOT_FOUND,
        RECOVERY_RESULT_NOT_APPLIED
    }

    public record Evaluated(
            Source source, PolicyAiResult result, PolicyAiCandidateState.Transition transition
    ) implements Projection {
        public Evaluated {
            Objects.requireNonNull(source, "AI 결과 출처가 필요합니다.");
            Objects.requireNonNull(result, "검사한 AI 결과가 필요합니다.");
            Objects.requireNonNull(transition, "AI 후보 상태 검사 결과가 필요합니다.");
        }
    }

    public record Skipped(Source source, SkipReason reason, PolicyAiCandidateState state) implements Projection {
        public Skipped {
            Objects.requireNonNull(source, "AI 결과 출처가 필요합니다.");
            Objects.requireNonNull(reason, "AI 후보 반영 생략 사유가 필요합니다.");
            Objects.requireNonNull(state, "유지할 AI 후보 상태가 필요합니다.");
        }
    }
}
