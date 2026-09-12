package kr.youthpolicymate.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Profile("!preview")
@EnableScheduling
@ConditionalOnProperty(name = "app.ai.auto.enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PolicyAiRuleScheduler {
    private static final Logger log = LoggerFactory.getLogger(PolicyAiRuleScheduler.class);
    private final PolicyAiRuleAutoRunner runner;
    private String previous = "";

    public PolicyAiRuleScheduler(PolicyAiRuleAutoRunner runner) { this.runner = runner; }

    @Bean
    ThreadPoolTaskScheduler policyAiRuleTasks() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("policy-ai-");
        return scheduler;
    }

    // 다른 정기 수집·알림 작업은 기본 스케줄러를 사용한다.
    @Bean
    ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("scheduling-");
        return scheduler;
    }

    @Scheduled(scheduler = "policyAiRuleTasks", fixedDelay = 60, initialDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void poll() {
        String result;
        try { result = runner.tick().toString(); }
        catch (RuntimeException exception) { result = "STORE_UNAVAILABLE"; }
        if (!result.equals(previous)) { log.info("AI 규칙 자동 처리: {}", result); previous = result; }
    }
}
