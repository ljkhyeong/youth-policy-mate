package kr.youthpolicymate.ingestion;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.util.UUID;

@Service
@Profile("!preview")
public class PolicyAiRuleGenerationService {
    private final PolicyAiRuleDraftStore drafts;
    private final PolicyAiRuleCallStore calls;
    private final AiBudgetReservationStore reservations;
    private final AiBudgetReservationLifecycleStore lifecycle;
    private final OpenAiRuleClient client;
    private final Clock clock;

    public PolicyAiRuleGenerationService(PolicyAiRuleDraftStore drafts, PolicyAiRuleCallStore calls,
                                         AiBudgetReservationStore reservations, AiBudgetReservationLifecycleStore lifecycle,
                                         OpenAiRuleClient client, Clock clock) {
        this.drafts = drafts; this.calls = calls; this.reservations = reservations; this.lifecycle = lifecycle;
        this.client = client; this.clock = clock;
    }

    public Status generate(UUID id) {
        var source = drafts.prepared(id);
        if (drafts.result(id).isPresent()) return status(id);
        var saved = calls.find(id);
        if (saved.isPresent() && saved.get().response() != null) {
            drafts.complete(id, client.candidate(saved.get().response()));
            return status(id);
        }
        if (saved.isPresent() && lifecycle.find(id.toString()).orElseThrow().phase() != AiBudgetReservationState.Phase.HELD)
            return status(id);
        var settings = client.settings(clock.instant());
        if (saved.isEmpty()) {
            if (!drafts.lockCurrent(source)) throw new IllegalStateException("공고·요청이 변경됐거나 이미 결과가 있습니다.");
            var body = client.request(source, settings);
            long tokens = client.countTokens(body);
            saved = java.util.Optional.of(calls.reserve(source, body, tokens, settings, clock.instant()));
        }
        var call = saved.orElseThrow();
        var dispatch = new AiBudgetReservationState.Dispatch("rule-" + id, clock.instant());
        var coordinator = new PolicyAiExecutionCoordinator(reservations, lifecycle, invocation -> {
            OpenAiRuleClient.Response response;
            try { response = client.generate(call.body()); }
            catch (RestClientException | IllegalStateException exception) {
                return new PolicyAiExecutionPort.Uncertain(new AiBudgetReservationState.UncertainOutcome("rule-unknown-" + id,
                        clock.instant(), AiBudgetReservationState.UncertainReason.PROVIDER_STATUS_UNAVAILABLE));
            }
            // 응답부터 별도 커밋한다. 초안 저장이 실패해도 재실행 시 외부 호출 없이 다시 처리한다.
            calls.response(id, response, clock.instant());
            var candidate = drafts.complete(id, client.candidate(response));
            PolicyAiResult.Outcome outcome = candidate.status() == PolicyAiRuleDraftStore.Status.DRAFT_CREATED
                    ? new PolicyAiResult.Generated(id.toString(), candidate.bodySha256())
                    : new PolicyAiResult.Unavailable(response.status() == 200 ? PolicyAiResult.UnavailableReason.INVALID_OUTPUT
                    : PolicyAiResult.UnavailableReason.REQUEST_FAILED);
            return new PolicyAiExecutionPort.ResponseReceived(new PolicyAiResult(invocation.request(), clock.instant(), outcome),
                    PolicyAiExecutionPort.PendingCharge.INSTANCE);
        });
        coordinator.execute(id.toString(), new PolicyAiRequestAdmission.ReservationRequired(calls.balance(call.budgetId()), call.cost(source)),
                call.reservedAt(), dispatch, () -> calls.dispatch(source, call, dispatch));
        return status(id);
    }

    public Status status(UUID id) {
        var result = drafts.result(id).orElse(null);
        var reservation = lifecycle.find(id.toString()).orElse(null);
        var call = calls.find(id).orElse(null);
        return new Status(id, result == null ? null : result.status().name(), result == null ? null : result.versionId(),
                reservation == null ? null : reservation.phase().name(), call == null ? null : call.maximumWon(),
                call != null && call.response() != null);
    }

    public AiBudgetReservationLifecycleStore.Transition settle(UUID id, String confirmationId, java.time.Instant confirmedAt,
                                                               java.math.BigDecimal actualWon) {
        requireCall(id);
        return lifecycle.settle(id.toString(), new AiBudgetReservationState.ChargeConfirmation(confirmationId, confirmedAt, actualWon));
    }

    public AiBudgetReservationLifecycleStore.Transition noCharge(UUID id, String confirmationId, java.time.Instant confirmedAt) {
        requireCall(id);
        return lifecycle.releaseAfterNoCharge(id.toString(), new AiBudgetReservationState.NoChargeConfirmation(confirmationId, confirmedAt));
    }

    private void requireCall(UUID id) {
        if (calls.find(id).isEmpty()) throw new IllegalArgumentException("AI 호출 기록을 찾을 수 없습니다.");
    }

    public record Status(UUID requestId, String candidateStatus, UUID versionId, String reservationPhase,
                         java.math.BigDecimal reservedMaximumWon, boolean responseStored) {}
}
