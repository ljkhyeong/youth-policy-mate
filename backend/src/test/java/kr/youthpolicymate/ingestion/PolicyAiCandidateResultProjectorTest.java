package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiBudgetReservationLifecycleStore.Snapshot;
import kr.youthpolicymate.ingestion.AiBudgetReservationLifecycleStore.Transition;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.ChargeConfirmation;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Dispatch;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.Phase;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.UncertainOutcome;
import kr.youthpolicymate.ingestion.AiBudgetReservationState.UncertainReason;
import kr.youthpolicymate.ingestion.AiRequestBudget.Balance;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Attempt;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.ClaimOutcome;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.CompletionOutcome;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.RecoveryResult;
import kr.youthpolicymate.ingestion.AiReservationRecoveryStore.Status;
import kr.youthpolicymate.ingestion.PolicyAiCandidateResultProjector.Evaluated;
import kr.youthpolicymate.ingestion.PolicyAiCandidateResultProjector.SkipReason;
import kr.youthpolicymate.ingestion.PolicyAiCandidateResultProjector.Skipped;
import kr.youthpolicymate.ingestion.PolicyAiExecutionPort.PendingCharge;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryApplier.Application;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.ChargeFound;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Inspection;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.Outcome;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryPort.ResponseFound;
import kr.youthpolicymate.ingestion.PolicyAiResult.Generated;
import kr.youthpolicymate.ingestion.PolicyAiResult.Kind;
import kr.youthpolicymate.ingestion.PolicyAiResult.Request;
import kr.youthpolicymate.ingestion.PolicyAiResult.Unavailable;
import kr.youthpolicymate.ingestion.PolicyAiResult.UnavailableReason;
import kr.youthpolicymate.policy.PolicyObservation;
import kr.youthpolicymate.policy.PolicyObservation.ContentFingerprint;
import kr.youthpolicymate.policy.PolicyObservation.Readable;
import kr.youthpolicymate.policy.PolicyObservation.SnapshotReference;
import kr.youthpolicymate.policy.PolicyRevisionState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class PolicyAiCandidateResultProjectorTest {
    private static final String POLICY = "synthetic-policy";
    private static final String VERSION = "generation-a";
    private static final Instant BASE = Instant.parse("2026-09-01T01:00:00Z");

    private final PolicyAiCandidateResultProjector projector = new PolicyAiCandidateResultProjector();

    @Test
    @DisplayName("인공 실행의 정상 응답을 현재 정책의 후보로 검사해 기록한다")
    void projectsGeneratedExecutionResult() {
        var policy = policy();
        var request = request(policy, Kind.SUMMARY, 10);
        var result = generated(request, "candidate-a");

        var projection = projector.projectExecution(policy, request, empty(), responded(result));

        assertThat(projection).isInstanceOfSatisfying(Evaluated.class, evaluated -> {
            assertThat(evaluated.source()).isEqualTo(PolicyAiCandidateResultProjector.Source.EXECUTION);
            assertThat(evaluated.transition().decision())
                    .isEqualTo(PolicyAiCandidateState.Decision.CANDIDATE_RECORDED);
            assertThat(evaluated.transition().state().candidateFor(policy, VERSION)).contains(result);
        });
    }

    @Test
    @DisplayName("인공 실행의 미생성 결과를 기록하되 기존 정상 후보는 유지한다")
    void recordsUnavailableExecutionWithoutDeletingCandidate() {
        var policy = policy();
        var firstRequest = request(policy, Kind.SUMMARY, 10);
        var firstResult = generated(firstRequest, "candidate-a");
        var candidates = empty().consider(policy, firstRequest, firstResult).state();
        var failedRequest = request(policy, Kind.SUMMARY, 20);
        var failed = new PolicyAiResult(failedRequest, at(30),
                new Unavailable(UnavailableReason.INVALID_OUTPUT));

        var projection = projector.projectExecution(policy, failedRequest, candidates, responded(failed));

        assertThat(projection).isInstanceOfSatisfying(Evaluated.class, evaluated -> {
            assertThat(evaluated.transition().decision())
                    .isEqualTo(PolicyAiCandidateState.Decision.UNAVAILABLE_RECORDED);
            assertThat(evaluated.transition().state().candidateFor(policy, VERSION)).contains(firstResult);
            assertThat(evaluated.transition().state().lastProcessedResult()).contains(failed);
        });
    }

    @Test
    @DisplayName("실행을 시작하지 못했거나 결과가 미확인이면 후보 상태를 그대로 유지한다")
    void skipsExecutionWithoutConfirmedResponse() {
        var policy = policy();
        var request = request(policy, Kind.SUMMARY, 10);
        var candidates = empty();

        var notStarted = projector.projectExecution(policy, request, candidates,
                new PolicyAiExecutionCoordinator.NotStarted(
                        PolicyAiExecutionCoordinator.StopReason.RESERVATION_REJECTED,
                        reservationAttempt(), Optional.empty()));
        var uncertain = projector.projectExecution(policy, request, candidates,
                new PolicyAiExecutionCoordinator.UncertainRun(
                        reservationAttempt(), transition(AiBudgetReservationLifecycleStore.Decision.OUTCOME_UNKNOWN),
                        new UncertainOutcome("unknown-a", at(20), UncertainReason.TIMEOUT)));

        assertSkipped(notStarted, SkipReason.EXECUTION_NOT_STARTED, candidates);
        assertSkipped(uncertain, SkipReason.EXECUTION_RESULT_UNCERTAIN, candidates);
    }

    @Test
    @DisplayName("복구 시도에서 확인하고 적용한 AI 응답을 현재 정책 후보로 기록한다")
    void projectsAppliedRecoveryResponse() {
        var policy = policy();
        var request = request(policy, Kind.SUMMARY, 10);
        var result = generated(request, "candidate-a");

        var projection = projector.projectRecovery(policy, request, empty(),
                recovered(new ResponseFound(result, PendingCharge.INSTANCE), RecoveryResult.CHECK_COMPLETED));

        assertThat(projection).isInstanceOfSatisfying(Evaluated.class, evaluated -> {
            assertThat(evaluated.source()).isEqualTo(PolicyAiCandidateResultProjector.Source.RECOVERY);
            assertThat(evaluated.transition().decision())
                    .isEqualTo(PolicyAiCandidateState.Decision.CANDIDATE_RECORDED);
            assertThat(evaluated.transition().state().candidateFor(policy, VERSION)).contains(result);
        });
    }

    @Test
    @DisplayName("복구 응답이 수동 검토로 끝나면 내용이 있어도 후보로 검사하지 않는다")
    void skipsRecoveryResponseThatWasNotApplied() {
        var policy = policy();
        var request = request(policy, Kind.SUMMARY, 10);
        var candidates = empty();

        var projection = projector.projectRecovery(policy, request, candidates,
                recovered(new ResponseFound(generated(request, "candidate-a"), PendingCharge.INSTANCE),
                        RecoveryResult.MANUAL_REVIEW_REQUIRED));

        assertSkipped(projection, SkipReason.RECOVERY_RESULT_NOT_APPLIED, candidates);
    }

    @Test
    @DisplayName("복구에서 청구만 확인했으면 AI 후보 결과가 있다고 추정하지 않는다")
    void skipsRecoveryWithoutAiResponse() {
        var policy = policy();
        var request = request(policy, Kind.SUMMARY, 10);
        var candidates = empty();

        var projection = projector.projectRecovery(policy, request, candidates,
                recovered(new ChargeFound(new ChargeConfirmation("charge-a", at(30), money("7"))),
                        RecoveryResult.CHECK_COMPLETED));

        assertSkipped(projection, SkipReason.RECOVERY_RESPONSE_NOT_FOUND, candidates);
    }

    @Test
    @DisplayName("응답 뒤 정책 개정이 바뀌면 실행 결과를 이전 후보로만 남기고 수용하지 않는다")
    void rejectsResultFromPreviousPolicyRevision() {
        var previous = policy();
        var oldRequest = request(previous, Kind.SUMMARY, 10);
        var oldResult = generated(oldRequest, "candidate-a");
        var current = previous.consider(observation(2, "c")).state();
        var expected = request(current, Kind.SUMMARY, 20);

        var projection = projector.projectExecution(current, expected, empty(), responded(oldResult));

        assertThat(projection).isInstanceOfSatisfying(Evaluated.class, evaluated -> {
            assertThat(evaluated.transition().decision())
                    .isEqualTo(PolicyAiCandidateState.Decision.REVISION_MISMATCH);
            assertThat(evaluated.transition().state().lastGeneratedResult()).isEmpty();
        });
    }

    @Test
    @DisplayName("정책·예정 요청·후보 상태의 정책 또는 작업 종류가 다르면 입력 오류로 거절한다")
    void rejectsMismatchedProjectionScope() {
        var policy = policy();
        var summary = request(policy, Kind.SUMMARY, 10);
        var response = responded(generated(summary, "candidate-a"));

        assertThatIllegalArgumentException().isThrownBy(() -> projector.projectExecution(
                policy, summary, PolicyAiCandidateState.empty(POLICY, Kind.CONDITION_EXTRACTION), response));
        assertThatIllegalArgumentException().isThrownBy(() -> projector.projectExecution(
                PolicyRevisionState.empty("other-policy"), summary, empty(), response));
    }

    private static PolicyAiExecutionCoordinator.Responded responded(PolicyAiResult result) {
        return new PolicyAiExecutionCoordinator.Responded(
                reservationAttempt(), transition(AiBudgetReservationLifecycleStore.Decision.DISPATCHED),
                result, PendingCharge.INSTANCE);
    }

    private static PolicyAiRecoveryCoordinator.Recovered recovered(Outcome outcome, RecoveryResult result) {
        var active = recoveryAttempt(Status.ACTIVE, Optional.empty(), Optional.empty(), Optional.empty());
        var inspection = new Inspection(snapshot(), active);
        var completed = recoveryAttempt(Status.COMPLETED, Optional.of(Phase.DISPATCHED),
                Optional.of(at(40)), Optional.of(result));
        return new PolicyAiRecoveryCoordinator.Recovered(
                new ClaimOutcome(AiReservationRecoveryStore.ClaimDecision.CLAIMED, Optional.of(active)),
                inspection, outcome,
                new Application(Optional.empty(), new CompletionOutcome(
                        AiReservationRecoveryStore.CompletionDecision.COMPLETED, Optional.of(completed))));
    }

    private static Attempt recoveryAttempt(Status status, Optional<Phase> completedPhase,
                                           Optional<Instant> completedAt, Optional<RecoveryResult> result) {
        return new Attempt("attempt-a", "reservation-a", 1, "worker-a", Phase.DISPATCHED,
                at(15), at(60), status, completedPhase, completedAt, result);
    }

    private static Snapshot snapshot() {
        return new Snapshot("reservation-a", "budget-a", Phase.DISPATCHED, money("10"), at(10),
                Optional.of(new Dispatch("dispatch-a", at(12))), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), at(12));
    }

    private static AiBudgetReservationStore.Attempt reservationAttempt() {
        return new AiBudgetReservationStore.Attempt(AiBudgetReservationStore.Decision.RESERVED,
                Optional.of(new Balance("budget-a", at(1), at(100), money("100"), money("0"), money("10"))));
    }

    private static Transition transition(AiBudgetReservationLifecycleStore.Decision decision) {
        return new Transition(decision, Optional.empty());
    }

    private static void assertSkipped(PolicyAiCandidateResultProjector.Projection projection,
                                      SkipReason reason, PolicyAiCandidateState state) {
        assertThat(projection).isInstanceOfSatisfying(Skipped.class, skipped -> {
            assertThat(skipped.reason()).isEqualTo(reason);
            assertThat(skipped.state()).isSameAs(state);
        });
    }

    private static PolicyAiCandidateState empty() {
        return PolicyAiCandidateState.empty(POLICY, Kind.SUMMARY);
    }

    private static PolicyRevisionState policy() {
        return PolicyRevisionState.empty(POLICY).consider(observation(1, "b")).state();
    }

    private static PolicyObservation observation(long sequence, String content) {
        return new PolicyObservation(POLICY, sequence, at(sequence), new Readable(
                new SnapshotReference("synthetic-source", "raw-" + sequence, "a".repeat(64)),
                new ContentFingerprint("comparison-a", content.repeat(64))));
    }

    private static Request request(PolicyRevisionState policy, Kind kind, long sequence) {
        return new Request(policy.currentRevision().orElseThrow(), kind, VERSION, sequence, at(5));
    }

    private static PolicyAiResult generated(Request request, String candidateId) {
        return new PolicyAiResult(request, at(20), new Generated(candidateId, "f".repeat(64)));
    }

    private static Instant at(long seconds) { return BASE.plusSeconds(seconds); }
    private static BigDecimal money(String amount) { return new BigDecimal(amount); }
}
