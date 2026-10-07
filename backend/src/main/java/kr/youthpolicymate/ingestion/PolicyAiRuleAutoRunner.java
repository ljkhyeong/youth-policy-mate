package kr.youthpolicymate.ingestion;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
@Profile("!preview")
public class PolicyAiRuleAutoRunner {
    private final PolicyAiRuleAutoStore store;
    private final PolicyAiRuleGenerationService generation;
    private final AiProperties properties;
    private final Clock clock;

    public PolicyAiRuleAutoRunner(PolicyAiRuleAutoStore store, PolicyAiRuleGenerationService generation,
                                 AiProperties properties, Clock clock) {
        this.store = store; this.generation = generation; this.properties = properties; this.clock = clock;
    }

    // 호출·응답 재처리는 기존 서비스를 사용하며 실행 전체를 트랜잭션으로 묶지 않는다.
    public Tick tick() {
        if (!properties.auto().configured()) return new Tick("CONFIGURATION_REQUIRED", null, null);
        AiProperties settings;
        try { settings = properties.requireUsable(clock.instant()); }
        catch (RuntimeException exception) { return new Tick("CONFIGURATION_REQUIRED", null, null); }
        var claim = store.claim(settings);
        if (claim.run() == null) return new Tick(claim.reason(), null, null);
        var run = claim.run();
        PolicyAiRuleGenerationService.Status result = null;
        boolean failed = false;
        try { result = generation.generate(run.requestId()); }
        catch (RuntimeException exception) {
            failed = true;
            // 본문·설정값이 포함될 수 있는 예외 메시지를 작업 로그에 남기지 않는다.
            try { result = generation.status(run.requestId()); }
            catch (RuntimeException unavailable) { /* DB 복구 후 동일 요청의 상태를 다시 확인한다. */ }
        }
        return new Tick(store.finish(run, result, failed, settings.auto().maxAttempts()), run.requestId(),
                result == null ? null : result.candidateStatus());
    }

    public record Tick(String state, UUID requestId, String candidateStatus) {}
}
