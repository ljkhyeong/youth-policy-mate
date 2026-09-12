package kr.youthpolicymate.ingestion;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
@Profile("!preview")
public class PolicyAiRuleAutoRunner {
    private final PolicyAiRuleAutoStore store;
    private final PolicyAiRuleGenerationService generation;
    private final OpenAiRuleClient client;
    private final Environment environment;
    private final Clock clock;

    public PolicyAiRuleAutoRunner(PolicyAiRuleAutoStore store, PolicyAiRuleGenerationService generation,
                                 OpenAiRuleClient client, Environment environment, Clock clock) {
        this.store = store; this.generation = generation; this.client = client; this.environment = environment; this.clock = clock;
    }

    // 호출·응답 재처리는 기존 서비스를 사용하며 실행 전체를 트랜잭션으로 묶지 않는다.
    public Tick tick() {
        PolicyAiRuleAutoStore.Limits limits;
        OpenAiRuleClient.Settings settings;
        try {
            limits = new PolicyAiRuleAutoStore.Limits(environment.getProperty("AI_AUTO_DAILY_LIMIT", Integer.class, 0),
                    environment.getProperty("AI_AUTO_INTERVAL_SECONDS", Long.class, 300L),
                    environment.getProperty("AI_AUTO_MAX_ATTEMPTS", Integer.class, 3));
            settings = client.settings(clock.instant());
        } catch (RuntimeException exception) { return new Tick("CONFIGURATION_REQUIRED", null, null); }
        var claim = store.claim(limits, settings);
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
        return new Tick(store.finish(run, result, failed, limits.maximumAttempts()), run.requestId(),
                result == null ? null : result.candidateStatus());
    }

    public record Tick(String state, UUID requestId, String candidateStatus) {}
}
