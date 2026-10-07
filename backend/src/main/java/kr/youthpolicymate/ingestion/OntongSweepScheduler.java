package kr.youthpolicymate.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!preview")
@EnableScheduling
@ConditionalOnBooleanProperty("app.ontong.schedule.enabled")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OntongSweepScheduler {
    private static final Logger log = LoggerFactory.getLogger(OntongSweepScheduler.class);
    private final OntongSweepStore sweeps;
    private final OntongSweepRunner runner;
    private final OntongProperties.Schedule schedule;
    private String previous = "";
    // 정기 실행에 필요한 한도·간격·범위·주기·키는 OntongProperties 바인딩에서 검증한다.
    OntongSweepScheduler(OntongSweepStore sweeps, OntongSweepRunner runner, OntongProperties properties) {
        this.sweeps = sweeps; this.runner = runner; this.schedule = properties.schedule();
    }
    @Scheduled(fixedDelayString = "${ONTONG_COLLECTION_POLL_MS:5000}", initialDelayString = "${ONTONG_COLLECTION_POLL_MS:5000}")
    public void poll() {
        try {
            sweeps.scheduled(schedule.firstPage(), schedule.lastPage(), schedule.cycle()).ifPresent(id -> {
                var result = id + " | " + runner.tick(id);
                if (!result.equals(previous)) { log.info("정책 범위 수집 상태: {}", result); previous = result; }
            });
        } catch (RuntimeException exception) {
            if (!previous.equals("STORE_FAILED")) log.warn("정책 범위 수집을 진행하지 못했습니다. DB와 수집 이력을 확인해주세요.");
            previous = "STORE_FAILED";
        }
    }
}
