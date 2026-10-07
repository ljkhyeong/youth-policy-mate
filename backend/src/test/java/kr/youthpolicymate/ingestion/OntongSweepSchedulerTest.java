package kr.youthpolicymate.ingestion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.AbstractApplicationContextRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OntongSweepSchedulerTest {
    private static final String[] COMPLETE = {"app.ontong.daily-limit=3", "app.ontong.interval=30", "app.ontong.api-key=test-key",
            "app.ontong.schedule.enabled=true", "app.ontong.schedule.first-page=1", "app.ontong.schedule.last-page=2",
            "app.ontong.schedule.cycle=86400"};
    @EnableConfigurationProperties(OntongProperties.class) static class Bind {}
    private static <R extends AbstractApplicationContextRunner<R, ?, ?>> R context(R runner) {
        return runner.withUserConfiguration(Bind.class, OntongSweepScheduler.class)
                .withBean(OntongSweepStore.class, () -> mock(OntongSweepStore.class))
                .withBean(OntongSweepRunner.class, () -> mock(OntongSweepRunner.class));
    }
    @Test @DisplayName("웹 서버에서 명시적으로 켜고 호출 한도·간격·범위·주기·키를 설정한 경우에만 정기 수집기를 만들고, 운영 명령은 정기 설정 누락과 관계없이 시작한다")
    void requiresCompleteConfiguration() {
        context(new WebApplicationContextRunner()).run(value -> assertThat(value).doesNotHaveBean(OntongSweepScheduler.class));
        context(new WebApplicationContextRunner()).withPropertyValues("app.ontong.schedule.enabled=true")
                .run(value -> assertThat(value).hasFailed());
        context(new WebApplicationContextRunner()).withPropertyValues(COMPLETE).run(value -> {
            assertThat(value).hasSingleBean(OntongSweepScheduler.class);
            var properties = value.getBean(OntongProperties.class);
            assertThat(properties.interval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.schedule().cycle()).isEqualTo(Duration.ofDays(1));
            assertThat(properties.toString()).doesNotContain("test-key");
        });
        context(new ApplicationContextRunner()).withPropertyValues(COMPLETE)
                .run(value -> assertThat(value).doesNotHaveBean(OntongSweepScheduler.class));
        context(new ApplicationContextRunner()).withPropertyValues("app.ontong.schedule.enabled=true")
                .run(value -> assertThat(value).hasNotFailed().doesNotHaveBean(OntongSweepScheduler.class));
    }
}
