package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.AiRequestBudget.Balance;
import kr.youthpolicymate.ingestion.AiRequestBudget.CostCeiling;
import kr.youthpolicymate.ingestion.PolicyAiRequestAdmission.Deferred;
import kr.youthpolicymate.ingestion.PolicyAiRequestAdmission.Mode;
import kr.youthpolicymate.ingestion.PolicyAiRequestAdmission.ReservationRequired;
import kr.youthpolicymate.ingestion.PolicyAiRequestAdmission.Reuse;
import kr.youthpolicymate.ingestion.PolicyAiResult.Generated;
import kr.youthpolicymate.ingestion.PolicyAiResult.Kind;
import kr.youthpolicymate.ingestion.PolicyAiResult.Request;
import kr.youthpolicymate.ingestion.PolicyAiResult.Unavailable;
import kr.youthpolicymate.ingestion.PolicyAiResult.UnavailableReason;
import kr.youthpolicymate.policy.PolicyObservation;
import kr.youthpolicymate.policy.PolicyObservation.ContentFingerprint;
import kr.youthpolicymate.policy.PolicyObservation.Failed;
import kr.youthpolicymate.policy.PolicyObservation.FailureReason;
import kr.youthpolicymate.policy.PolicyObservation.Readable;
import kr.youthpolicymate.policy.PolicyObservation.SnapshotReference;
import kr.youthpolicymate.policy.PolicyRevisionState;
import kr.youthpolicymate.policy.PolicyRevisionState.Transition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static kr.youthpolicymate.ingestion.PolicyAiRequestAdmission.Reason.*;
import static kr.youthpolicymate.ingestion.PolicyAiRequestAdmission.assess;
import static org.assertj.core.api.Assertions.*;

class PolicyAiRequestAdmissionTest {
    private static final String POLICY = "synthetic-policy";
    private static final String VERSION = "synthetic-generation";
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");

    @Test
    @DisplayName("같은 내용의 후보는 예산 미설정·소진·원천 수집 실패에도 추가 비용 없이 재사용한다")
    void reusesBeforeCheckingBudget() {
        var first = firstRevision();
        var request = request(first, VERSION, 10);
        var candidate = new PolicyAiResult(request, NOW, new Generated("candidate-a", "a".repeat(64)));
        var candidates = empty().consider(first.state(), request, candidate).state();
        var unchanged = first.state().consider(observation(2, "a"));
        var failed = unchanged.state().consider(new PolicyObservation(POLICY, 3, NOW,
                new Failed(FailureReason.FETCH_FAILED, Optional.empty())));
        var noBudget = new AiRequestBudget(Optional.empty(), Optional.empty());

        assertThat(assess(unchanged, candidates, request, Mode.AUTOMATIC, noBudget, NOW)).isEqualTo(new Reuse(candidate));
        assertThat(assess(failed, candidates, request, Mode.AUTOMATIC, budget(request, "0", "0", "0", "1"), NOW))
                .isEqualTo(new Reuse(candidate));
        assertThat(candidates.lastGeneratedResult()).contains(candidate);
    }

    @Test
    @DisplayName("새 개정의 최대 비용이 확정액·예약액을 뺀 잔액과 같으면 예약 필요를 반환한다")
    void requiresReservationAtExactRemainingAmount() {
        var change = firstRevision();
        var request = request(change, VERSION, 10);
        var budget = budget(request, "100.00", "40.25", "49.50", "10.25");

        assertThat(assess(change, empty(), request, Mode.AUTOMATIC, budget, NOW))
                .isEqualTo(new ReservationRequired(budget.balance().orElseThrow(), budget.costCeiling().orElseThrow()));
        assertThat(budget.balance().orElseThrow().remainingWon()).isEqualByComparingTo("10.25");
        assertThat(budget.balance().orElseThrow().reservedWon()).isEqualByComparingTo("49.5");
    }

    @Test
    @DisplayName("신규 정책뿐 아니라 실제 내용이 바뀐 개정도 별도 비용 예약 대상으로 판단한다")
    void admitsChangedRevisionWithoutReusingOldCandidate() {
        var first = firstRevision();
        var oldRequest = request(first, VERSION, 10);
        var candidate = new PolicyAiResult(oldRequest, NOW, new Generated("old-candidate", "a".repeat(64)));
        var candidates = empty().consider(first.state(), oldRequest, candidate).state();
        var changed = first.state().consider(observation(2, "b"));
        var nextRequest = request(changed, VERSION, 20);

        assertThat(assess(changed, candidates, nextRequest, Mode.AUTOMATIC, budget(nextRequest, "100", "20", "0", "5"), NOW))
                .isInstanceOf(ReservationRequired.class);
        assertThat(candidates.lastGeneratedResult()).contains(candidate);
        assertThat(candidates.candidateFor(changed.state(), VERSION)).isEmpty();
    }

    @Test
    @DisplayName("진행 중 예약액을 무시하거나 소수 최대 비용을 버려 한도를 통과시키지 않는다")
    void includesOutstandingReservationsAndFractionalCost() {
        var change = firstRevision();
        var request = request(change, VERSION, 10);
        assertThat(assess(change, empty(), request, Mode.AUTOMATIC, budget(request, "100", "40", "50", "10.001"), NOW))
                .isEqualTo(new Deferred(BUDGET_LIMIT));
        assertThat(assess(change, empty(), request, Mode.AUTOMATIC, budget(request, "100", "0", "100", "1"), NOW))
                .isEqualTo(new Deferred(BUDGET_LIMIT));
    }

    @Test
    @DisplayName("0원 한도와 이미 한도를 초과한 사용액은 새 호출을 보류한다")
    void blocksDisabledAndOverspentBudget() {
        var change = firstRevision();
        var request = request(change, VERSION, 10);
        assertThat(assess(change, empty(), request, Mode.AUTOMATIC, budget(request, "0", "0", "0", "0"), NOW))
                .isEqualTo(new Deferred(BUDGET_LIMIT));
        var overspent = budget(request, "100", "101", "0", "0");
        assertThat(overspent.balance().orElseThrow().remainingWon()).isEqualByComparingTo("-1");
        assertThat(assess(change, empty(), request, Mode.AUTOMATIC, overspent, NOW)).isEqualTo(new Deferred(BUDGET_LIMIT));
    }

    @Test
    @DisplayName("예산 미설정과 요청 최대 비용 미확인을 0원으로 대신하지 않는다")
    void defersMissingBudgetAndCost() {
        var change = firstRevision();
        var request = request(change, VERSION, 10);
        var known = budget(request, "100", "0", "0", "1");
        assertThat(assess(change, empty(), request, Mode.AUTOMATIC, new AiRequestBudget(Optional.empty(), known.costCeiling()), NOW))
                .isEqualTo(new Deferred(BUDGET_NOT_CONFIGURED));
        assertThat(assess(change, empty(), request, Mode.AUTOMATIC, new AiRequestBudget(known.balance(), Optional.empty()), NOW))
                .isEqualTo(new Deferred(COST_NOT_CONFIRMED));
    }

    @Test
    @DisplayName("예산 기간은 시작 포함·종료 제외이며 기간 밖에서 예약하지 않는다")
    void checksBudgetPeriodBoundaries() {
        var change = firstRevision();
        var request = request(change, VERSION, 10);
        var balance = new Balance("synthetic-budget", NOW, NOW.plusSeconds(30), money("100"), money("0"), money("0"));
        var budget = new AiRequestBudget(Optional.of(balance), Optional.of(cost(request, "1")));

        assertThat(assess(change, empty(), request, Mode.AUTOMATIC, budget, NOW.minusNanos(1)))
                .isEqualTo(new Deferred(BUDGET_PERIOD_INACTIVE));
        assertThat(assess(change, empty(), request, Mode.AUTOMATIC, budget, NOW)).isInstanceOf(ReservationRequired.class);
        assertThat(assess(change, empty(), request, Mode.AUTOMATIC, budget, NOW.plusSeconds(30)))
                .isEqualTo(new Deferred(BUDGET_PERIOD_INACTIVE));
    }

    @Test
    @DisplayName("최대 비용 정보는 유효 종료 시각과 같으면 만료로 처리한다")
    void checksCostExpiryWithoutExtendingValidity() {
        var change = firstRevision();
        var request = request(change, VERSION, 10);
        var known = budget(request, "100", "0", "0", "1");
        var expired = new AiRequestBudget(known.balance(), Optional.of(new CostCeiling(request, "price-a", NOW, money("1"))));
        assertThat(assess(change, empty(), request, Mode.AUTOMATIC, expired, NOW.minusNanos(1)))
                .isInstanceOf(ReservationRequired.class);
        assertThat(assess(change, empty(), request, Mode.AUTOMATIC, expired, NOW)).isEqualTo(new Deferred(COST_EXPIRED));
    }

    @Test
    @DisplayName("다른 시도나 생성 방식에 산정한 비용을 새 요청에 사용하지 않는다")
    void bindsCostToExactRequest() {
        var change = firstRevision();
        var request = request(change, VERSION, 20);
        for (var other : new Request[]{request(change, VERSION, 10), request(change, "other-version", 20)}) {
            assertThat(assess(change, empty(), request, Mode.AUTOMATIC, budget(other, "100", "0", "0", "1"), NOW))
                    .isEqualTo(new Deferred(COST_REQUEST_MISMATCH));
        }
    }

    @Test
    @DisplayName("변경 없는 수집이나 생성 방식 변경만으로 새 자동 호출을 만들지 않는다")
    void requiresNewRevisionForAutomaticWork() {
        var first = firstRevision();
        var unchanged = first.state().consider(observation(2, "a"));
        var request = request(unchanged, "new-generation", 20);
        assertThat(assess(unchanged, empty(), request, Mode.AUTOMATIC, budget(request, "100", "0", "0", "1"), NOW))
                .isEqualTo(new Deferred(NO_NEW_REVISION));

        var oldRequest = request(first, VERSION, 10);
        var oldCandidate = new PolicyAiResult(oldRequest, NOW, new Generated("old-generation", "a".repeat(64)));
        var candidates = empty().consider(first.state(), oldRequest, oldCandidate).state();
        assertThat(assess(unchanged, candidates, request, Mode.AUTOMATIC, budget(request, "100", "0", "0", "1"), NOW))
                .isEqualTo(new Deferred(NO_NEW_REVISION));
    }

    @Test
    @DisplayName("실패·잘못된 출력·한도 보류는 명시적 새 재시도에서만 비용을 다시 확인한다")
    void retriesUnavailableResultWithoutBypassingBudget() {
        var change = firstRevision();
        var previous = request(change, VERSION, 10);
        var retry = request(change, VERSION, 20);
        for (var reason : UnavailableReason.values()) {
            var result = new PolicyAiResult(previous, NOW, new Unavailable(reason));
            var candidates = empty().consider(change.state(), previous, result).state();
            var available = budget(retry, "100", "10", "5", "1");
            assertThat(assess(change, candidates, retry, Mode.AUTOMATIC, available, NOW))
                    .isEqualTo(new Deferred(EXPLICIT_RETRY_REQUIRED));
            assertThat(assess(change, candidates, retry, Mode.RETRY, available, NOW)).isInstanceOf(ReservationRequired.class);
            assertThat(assess(change, candidates, retry, Mode.RETRY, budget(retry, "100", "90", "10", "1"), NOW))
                    .isEqualTo(new Deferred(BUDGET_LIMIT));
        }
    }

    @Test
    @DisplayName("이미 처리한 요청과 현재 개정·생성 방식의 실패가 없는 재시도를 보류한다")
    void rejectsProcessedOrUnrelatedRetry() {
        var first = firstRevision();
        var previous = request(first, VERSION, 10);
        var failure = new PolicyAiResult(previous, NOW, new Unavailable(UnavailableReason.REQUEST_FAILED));
        var candidates = empty().consider(first.state(), previous, failure).state();
        var retry = request(first, VERSION, 20);
        assertThat(assess(first, candidates, previous, Mode.RETRY, budget(previous, "100", "0", "0", "1"), NOW))
                .isEqualTo(new Deferred(REQUEST_ALREADY_PROCESSED));
        assertThat(assess(first, empty(), retry, Mode.RETRY, budget(retry, "100", "0", "0", "1"), NOW))
                .isEqualTo(new Deferred(RETRY_NOT_APPLICABLE));
        var changed = first.state().consider(observation(2, "b"));
        var next = request(changed, VERSION, 20);
        assertThat(assess(changed, candidates, next, Mode.RETRY, budget(next, "100", "0", "0", "1"), NOW))
                .isEqualTo(new Deferred(RETRY_NOT_APPLICABLE));
        var otherVersion = request(first, "other-generation", 20);
        assertThat(assess(first, candidates, otherVersion, Mode.RETRY, budget(otherVersion, "100", "0", "0", "1"), NOW))
                .isEqualTo(new Deferred(RETRY_NOT_APPLICABLE));
    }

    @Test
    @DisplayName("현재 개정 없음·오래된 원본·다른 정책이나 작업 종류를 호출 검사에서 구분한다")
    void checksPolicyAndCandidateScope() {
        var first = firstRevision();
        var request = request(first, VERSION, 10);
        var budget = budget(request, "100", "0", "0", "1");
        var missing = PolicyRevisionState.empty(POLICY).consider(new PolicyObservation(POLICY, 1, NOW,
                new Failed(FailureReason.FETCH_FAILED, Optional.empty())));
        assertThat(assess(missing, empty(), request, Mode.AUTOMATIC, budget, NOW)).isEqualTo(new Deferred(NO_CURRENT_REVISION));
        var changed = first.state().consider(observation(2, "b"));
        assertThat(assess(changed, empty(), request, Mode.AUTOMATIC, budget, NOW)).isEqualTo(new Deferred(STALE_POLICY));
        var rewritten = new Request(new PolicyRevisionState.AppliedRevision(1, observation(2, "a")), Kind.SUMMARY, VERSION, 10, request.preparedAt());
        assertThat(assess(first, empty(), rewritten, Mode.AUTOMATIC, budget, NOW)).isEqualTo(new Deferred(STALE_POLICY));
        assertThatIllegalArgumentException().isThrownBy(() -> assess(first, PolicyAiCandidateState.empty("other-policy", Kind.SUMMARY), request, Mode.AUTOMATIC, budget, NOW));
        assertThatIllegalArgumentException().isThrownBy(() -> assess(first, PolicyAiCandidateState.empty(POLICY, Kind.CONDITION_EXTRACTION), request, Mode.AUTOMATIC, budget, NOW));
    }

    @Test
    @DisplayName("음수 비용·잘못된 기간·요청 준비 전 검사는 거절한다")
    void rejectsInvalidMonetaryAndTimeInputs() {
        var change = firstRevision();
        var request = request(change, VERSION, 10);
        assertThatIllegalArgumentException().isThrownBy(() -> budget(request, "100", "-1", "0", "1"));
        assertThatIllegalArgumentException().isThrownBy(() -> cost(request, "-0.01"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Balance("budget", NOW, NOW, money("100"), money("0"), money("0")));
        assertThatIllegalArgumentException().isThrownBy(() -> new CostCeiling(request, "price", request.preparedAt(), money("1")));
        assertThatIllegalArgumentException().isThrownBy(() -> assess(change, empty(), request, Mode.AUTOMATIC,
                budget(request, "100", "0", "0", "1"), request.preparedAt().minusNanos(1)));
    }

    private static PolicyAiCandidateState empty() { return PolicyAiCandidateState.empty(POLICY, Kind.SUMMARY); }
    private static BigDecimal money(String amount) { return new BigDecimal(amount); }
    private static Transition firstRevision() { return PolicyRevisionState.empty(POLICY).consider(observation(1, "a")); }

    private static PolicyObservation observation(long sequence, String content) {
        return new PolicyObservation(POLICY, sequence, NOW.minusSeconds(100 - sequence), new Readable(
                new SnapshotReference("synthetic-source", "raw-" + sequence, "a".repeat(64)),
                new ContentFingerprint("comparison-a", content.repeat(64))));
    }

    private static Request request(Transition change, String version, long sequence) {
        return new Request(change.state().currentRevision().orElseThrow(), Kind.SUMMARY, version, sequence, NOW.minusSeconds(30));
    }

    private static CostCeiling cost(Request request, String maximum) {
        return new CostCeiling(request, "synthetic-price", NOW.plusSeconds(60), money(maximum));
    }

    private static AiRequestBudget budget(Request request, String limit, String confirmed, String reserved, String maximum) {
        var balance = new Balance("synthetic-budget", NOW.minusSeconds(3600), NOW.plusSeconds(3600),
                money(limit), money(confirmed), money(reserved));
        return new AiRequestBudget(Optional.of(balance), Optional.of(cost(request, maximum)));
    }
}
