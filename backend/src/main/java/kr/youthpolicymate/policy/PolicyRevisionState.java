package kr.youthpolicymate.policy;

import java.util.Objects;
import java.util.Optional;

import kr.youthpolicymate.policy.PolicyObservation.Failed;
import kr.youthpolicymate.policy.PolicyObservation.Readable;

// 한 정책의 적용 판단만 계산한다. 저장·순번 발급·DB 동시성 제어는 수행하지 않는다.
public final class PolicyRevisionState {
    private final String policyId;
    private final Optional<PolicyObservation> lastProcessedObservation;
    private final Optional<AppliedRevision> currentRevision;
    private final Optional<PolicyObservation> lastConfirmedObservation;

    private PolicyRevisionState(String policyId, Optional<PolicyObservation> lastProcessedObservation,
                                Optional<AppliedRevision> currentRevision, Optional<PolicyObservation> lastConfirmedObservation) {
        this.policyId = policyId;
        this.lastProcessedObservation = lastProcessedObservation;
        this.currentRevision = currentRevision;
        this.lastConfirmedObservation = lastConfirmedObservation;
    }

    public static PolicyRevisionState empty(String policyId) {
        if (policyId == null || policyId.isBlank()) throw new IllegalArgumentException("내부 정책 식별자가 필요합니다.");
        return new PolicyRevisionState(policyId, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public Transition consider(PolicyObservation observation) {
        Objects.requireNonNull(observation, "적용할 수집 결과가 필요합니다.");
        if (!policyId.equals(observation.policyId())) throw new IllegalArgumentException("다른 정책의 수집 결과는 적용할 수 없습니다.");

        if (lastProcessedObservation.isPresent()) {
            var previous = lastProcessedObservation.orElseThrow();
            if (observation.collectionSequence() < previous.collectionSequence()) return unchanged(Decision.STALE);
            if (observation.collectionSequence() == previous.collectionSequence()) {
                return unchanged(previous.equals(observation) ? Decision.REPLAYED : Decision.SEQUENCE_CONFLICT);
            }
        }

        if (observation.outcome() instanceof Failed) return retainCurrent(Decision.OBSERVATION_FAILED, observation);

        var readable = (Readable) observation.outcome();
        if (currentRevision.isPresent()) {
            var applied = (Readable) currentRevision.orElseThrow().observation().outcome();
            if (!applied.content().comparisonVersion().equals(readable.content().comparisonVersion())) {
                return retainCurrent(Decision.COMPARISON_REQUIRED, observation);
            }
            if (applied.content().equals(readable.content())) {
                // 개정이 그대로여도 수집 순번을 갱신해야 중간 순번의 늦은 변경을 막는다.
                return new Transition(Decision.UNCHANGED, new PolicyRevisionState(policyId,
                        Optional.of(observation), currentRevision, Optional.of(observation)));
            }
        }

        long nextNumber = Math.incrementExact(currentRevision.map(AppliedRevision::number).orElse(0L));
        var nextRevision = new AppliedRevision(nextNumber, observation);
        return new Transition(Decision.REVISION_CREATED, new PolicyRevisionState(policyId,
                Optional.of(observation), Optional.of(nextRevision), Optional.of(observation)));
    }

    private Transition retainCurrent(Decision decision, PolicyObservation observation) {
        return new Transition(decision, new PolicyRevisionState(policyId,
                Optional.of(observation), currentRevision, lastConfirmedObservation));
    }

    private Transition unchanged(Decision decision) { return new Transition(decision, this); }

    public String policyId() { return policyId; }
    public Optional<PolicyObservation> lastProcessedObservation() { return lastProcessedObservation; }
    public Optional<AppliedRevision> currentRevision() { return currentRevision; }
    public Optional<PolicyObservation> lastConfirmedObservation() { return lastConfirmedObservation; }

    public enum Decision {
        REVISION_CREATED, UNCHANGED, OBSERVATION_FAILED, COMPARISON_REQUIRED,
        STALE, REPLAYED, SEQUENCE_CONFLICT
    }

    public record Transition(Decision decision, PolicyRevisionState state) {}

    // observation에는 이 개정을 만든 원본 근거·비교 내용·수집 순번·확인 시각이 함께 남는다.
    public record AppliedRevision(long number, PolicyObservation observation) {
        public AppliedRevision {
            if (number < 1 || observation == null || !(observation.outcome() instanceof Readable)) {
                throw new IllegalArgumentException("적용 개정에는 양수 번호와 표시 가능한 수집 결과가 필요합니다.");
            }
        }
    }
}
