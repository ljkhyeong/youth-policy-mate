package kr.youthpolicymate.ingestion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PolicyAiRuleSchedulerTest {
    @Test @DisplayName("자동 실행은 기본 비활성화하고 웹 서버에서만 등록한다")
    void requiresEnabledWebServer() {
        new WebApplicationContextRunner().withUserConfiguration(PolicyAiRuleScheduler.class)
                .run(context -> assertThat(context).doesNotHaveBean(PolicyAiRuleScheduler.class));
        new ApplicationContextRunner().withUserConfiguration(PolicyAiRuleScheduler.class)
                .withPropertyValues("app.ai.auto.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(PolicyAiRuleScheduler.class));
    }

    @Test @DisplayName("AI 응답 대기가 기존 수집·알림 스케줄러를 막지 않는다")
    void separatesSchedulingThreads() {
        new WebApplicationContextRunner().withUserConfiguration(PolicyAiRuleScheduler.class)
                .withBean(PolicyAiRuleAutoRunner.class, () -> mock(PolicyAiRuleAutoRunner.class))
                .withPropertyValues("app.ai.auto.enabled=true", "AI_AUTO_INTERVAL_SECONDS=0")
                .run(context -> {
                    var ai = context.getBean("policyAiRuleTasks", ThreadPoolTaskScheduler.class);
                    var normal = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
                    assertThat(ai).isNotSameAs(normal);
                    var entered = new CountDownLatch(1);
                    var release = new CountDownLatch(1);
                    var other = new CountDownLatch(1);
                    ai.execute(() -> {
                        entered.countDown();
                        try { release.await(5, TimeUnit.SECONDS); }
                        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                    });
                    try {
                        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                        normal.execute(other::countDown);
                        assertThat(other.await(5, TimeUnit.SECONDS)).isTrue();
                    } finally { release.countDown(); }
                });
    }
}
