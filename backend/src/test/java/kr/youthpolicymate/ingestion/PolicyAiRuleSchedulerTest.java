package kr.youthpolicymate.ingestion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PolicyAiRuleSchedulerTest {
    @Test @DisplayName("자동 실행은 기본 비활성화하고 웹 서버에서 켠 경우에만 등록한다")
    void requiresEnabledWebServer() {
        new WebApplicationContextRunner().withUserConfiguration(PolicyAiRuleScheduler.class)
                .run(context -> assertThat(context).doesNotHaveBean(PolicyAiRuleScheduler.class));
        new WebApplicationContextRunner().withUserConfiguration(PolicyAiRuleScheduler.class)
                .withBean(PolicyAiRuleAutoRunner.class, () -> mock(PolicyAiRuleAutoRunner.class))
                .withPropertyValues("app.ai.auto.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(PolicyAiRuleScheduler.class));
        new ApplicationContextRunner().withUserConfiguration(PolicyAiRuleScheduler.class)
                .withPropertyValues("app.ai.auto.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(PolicyAiRuleScheduler.class));
    }
}
