package kr.youthpolicymate.ingestion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OntongSweepSchedulerTest {
    private ApplicationContextRunner context(OntongRequestLimits limits) {
        return new ApplicationContextRunner().withUserConfiguration(OntongSweepScheduler.class)
                .withBean(OntongSweepStore.class, () -> mock(OntongSweepStore.class))
                .withBean(OntongSweepRunner.class, () -> mock(OntongSweepRunner.class))
                .withBean(OntongRequestLimits.class, () -> limits);
    }
    @Test @DisplayName("명시적으로 켜고 호출 한도·간격·범위·주기·키를 설정한 경우에만 정기 수집기를 만든다")
    void requiresCompleteConfiguration() {
        context(new OntongRequestLimits(0, 0)).run(value -> assertThat(value).doesNotHaveBean(OntongSweepScheduler.class));
        context(new OntongRequestLimits(0, 0)).withPropertyValues("app.ontong.schedule.enabled=true")
                .run(value -> assertThat(value).hasFailed());
        context(new OntongRequestLimits(3, 30)).withPropertyValues("app.ontong.schedule.enabled=true", "ONTONG_COLLECTION_FIRST_PAGE=1",
                "ONTONG_COLLECTION_LAST_PAGE=2", "ONTONG_COLLECTION_CYCLE_SECONDS=86400", "ONTONG_API_KEY=test-key")
                .run(value -> assertThat(value).hasSingleBean(OntongSweepScheduler.class));
    }
}
