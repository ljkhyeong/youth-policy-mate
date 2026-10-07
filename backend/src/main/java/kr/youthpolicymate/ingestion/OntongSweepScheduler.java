package kr.youthpolicymate.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnBooleanProperty("app.ontong.schedule.enabled")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OntongSweepScheduler {
    private static final Logger log = LoggerFactory.getLogger(OntongSweepScheduler.class);
    private final OntongSweepStore sweeps;
    private final OntongSweepRunner runner;
    private final OntongProperties.Schedule schedule;
    private String previous = "";
    // 정기 실행에만 필요한 값은 웹 서버에서 이 빈을 만들 때 검증해 운영 명령 시작에는 영향을 주지 않는다.
    OntongSweepScheduler(OntongSweepStore sweeps, OntongSweepRunner runner, OntongProperties properties) {
        properties.requireLimits();
        schedule = properties.schedule();
        OntongSweepStore.validateRange(schedule.firstPage(), schedule.lastPage());
        if (!schedule.cycle().isPositive()) throw new IllegalArgumentException("정기 수집 주기를 설정해주세요.");
        if (properties.apiKey().isBlank()) throw new IllegalArgumentException("온통청년 인증키를 설정해주세요.");
        this.sweeps = sweeps; this.runner = runner;
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
