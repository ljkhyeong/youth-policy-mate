package kr.youthpolicymate.ingestion;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

// 외부 API 호출을 DB 트랜잭션 밖에 두려고 이 서비스에는 @Transactional을 붙이지 않는다.
@Service
public class PolicyAiRuleGenerationService {
    private final PolicyAiRuleDraftStore drafts;
    private final PolicyAiRuleCallStore calls;
    private final OpenAiRuleClient client;
    private final AiProperties properties;
    private final Clock clock;

    public PolicyAiRuleGenerationService(PolicyAiRuleDraftStore drafts, PolicyAiRuleCallStore calls, OpenAiRuleClient client,
                                         AiProperties properties, Clock clock) {
        this.drafts = drafts; this.calls = calls; this.client = client; this.properties = properties; this.clock = clock;
    }

    public Status generate(UUID id) {
        var source = drafts.prepared(id);
        if (drafts.result(id).isPresent()) return status(id);
        var saved = calls.find(id);
        if (saved.isPresent() && saved.get().response() != null) {
            drafts.complete(id, client.candidate(saved.get().response()));
            return status(id);
        }
        // 발송·취소·정산한 예약은 다시 호출하지 않는다.
        if (saved.isPresent() && !saved.get().phase().equals("HELD")) return status(id);
        var settings = properties.requireUsable(clock.instant());
        if (saved.isEmpty()) {
            if (!drafts.lockCurrent(source)) throw new IllegalStateException("공고·요청이 변경됐거나 이미 결과가 있습니다.");
            var body = client.request(source, settings);
            long tokens = client.countTokens(body);
            saved = Optional.of(calls.reserve(source, body, tokens, settings, clock.instant()));
        }
        var call = saved.orElseThrow();
        // 공고 변경·요금 만료로 취소했거나 다른 실행이 먼저 발송했으면 외부 호출 없이 현재 상태를 돌려준다.
        if (!calls.dispatch(source, call, clock.instant())) return status(id);
        OpenAiRuleClient.Response response;
        try { response = client.generate(call.body()); }
        catch (RestClientException | IllegalStateException exception) {
            calls.markOutcomeUnknown(id, clock.instant());
            return status(id);
        }
        // 응답부터 별도로 커밋한다. 초안 저장이 실패해도 다시 실행하면 외부 호출 없이 처리한다.
        calls.response(id, response, clock.instant());
        drafts.complete(id, client.candidate(response));
        return status(id);
    }

    public Status status(UUID id) {
        var result = drafts.result(id).orElse(null);
        var call = calls.find(id).orElse(null);
        return new Status(id, result == null ? null : result.status().name(), result == null ? null : result.versionId(),
                call == null ? null : call.phase(), call == null ? null : call.maximumWon(), call != null && call.response() != null);
    }

    public record Status(UUID requestId, String candidateStatus, UUID versionId, String reservationPhase,
                         BigDecimal reservedMaximumWon, boolean responseStored) {}
}
