package kr.youthpolicymate.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@Profile("!preview")
@EnableScheduling
@ConditionalOnProperty(name = "app.ontong.schedule.enabled", havingValue = "true")
public class OntongSweepScheduler {
    private static final Logger log = LoggerFactory.getLogger(OntongSweepScheduler.class);
    private final OntongSweepStore sweeps;
    private final OntongSweepRunner runner;
    private final int first;
    private final int last;
    private final Duration interval;
    private String previous = "";
    public OntongSweepScheduler(OntongSweepStore sweeps, OntongSweepRunner runner, OntongRequestLimits limits, Environment environment) {
        this.sweeps = sweeps; this.runner = runner; limits.requireConfigured();
        first = environment.getProperty("ONTONG_COLLECTION_FIRST_PAGE", Integer.class, 0);
        last = environment.getProperty("ONTONG_COLLECTION_LAST_PAGE", Integer.class, 0);
        OntongSweepStore.validateRange(first, last);
        interval = Duration.ofSeconds(environment.getProperty("ONTONG_COLLECTION_CYCLE_SECONDS", Long.class, 0L));
        if (interval.isZero() || interval.isNegative()) throw new IllegalArgumentException("정기 수집 주기를 설정해주세요.");
        if (environment.getProperty("ONTONG_API_KEY", "").isBlank()) throw new IllegalArgumentException("온통청년 인증키를 설정해주세요.");
    }
    @Scheduled(fixedDelayString = "${ONTONG_COLLECTION_POLL_MS:5000}", initialDelayString = "${ONTONG_COLLECTION_POLL_MS:5000}")
    public void poll() {
        try {
            sweeps.scheduled(first, last, interval).ifPresent(id -> {
                var result = id + " | " + runner.tick(id);
                if (!result.equals(previous)) { log.info("정책 범위 수집 상태: {}", result); previous = result; }
            });
        } catch (RuntimeException exception) {
            if (!previous.equals("STORE_FAILED")) log.warn("정책 범위 수집을 진행하지 못했습니다. DB와 수집 이력을 확인해주세요.");
            previous = "STORE_FAILED";
        }
    }
}
