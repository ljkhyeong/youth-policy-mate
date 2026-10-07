package kr.youthpolicymate.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@EnableScheduling
@ConditionalOnBooleanProperty("app.ai.auto.enabled")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PolicyAiRuleScheduler {
    private static final Logger log = LoggerFactory.getLogger(PolicyAiRuleScheduler.class);
    private final PolicyAiRuleAutoRunner runner;
    private String previous = "";

    public PolicyAiRuleScheduler(PolicyAiRuleAutoRunner runner) { this.runner = runner; }

    @Scheduled(fixedDelay = 60, initialDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void poll() {
        String result;
        try { result = runner.tick().toString(); }
        catch (RuntimeException exception) { result = "STORE_UNAVAILABLE"; }
        if (!result.equals(previous)) { log.info("AI 규칙 자동 처리: {}", result); previous = result; }
    }
}
