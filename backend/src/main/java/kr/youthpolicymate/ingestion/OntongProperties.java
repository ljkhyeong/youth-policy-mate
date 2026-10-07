package kr.youthpolicymate.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** 온통청년 인증키·호출 한도·정기 범위 설정. 운영 API의 한도를 확인한 뒤 모든 수집 프로세스에 같은 값을 설정한다. */
@ConfigurationProperties("app.ontong")
record OntongProperties(String apiKey, int dailyLimit,
                        @DurationUnit(ChronoUnit.SECONDS) Duration interval, Schedule schedule) {
    OntongProperties {
        apiKey = Objects.requireNonNullElse(apiKey, "");
        interval = Objects.requireNonNullElse(interval, Duration.ZERO);
        schedule = Objects.requireNonNullElse(schedule, new Schedule(false, 0, 0, null));
        if (dailyLimit < 0 || interval.isNegative() || (dailyLimit == 0) != interval.isZero())
            throw new IllegalArgumentException("일일 수집 한도와 호출 간격을 함께 설정해주세요.");
    }

    record Schedule(boolean enabled, int firstPage, int lastPage, @DurationUnit(ChronoUnit.SECONDS) Duration cycle) {
        Schedule { cycle = Objects.requireNonNullElse(cycle, Duration.ZERO); }
    }

    boolean limited() { return dailyLimit > 0; }

    void requireLimits() { if (!limited()) throw new OntongApiClient.Failure("COLLECTION_LIMITS_REQUIRED"); }

    /** 기본 toString은 인증키를 포함하므로 로그에 남지 않게 가린다. */
    @Override
    public String toString() {
        return "OntongProperties[dailyLimit=" + dailyLimit + ", interval=" + interval + ", schedule=" + schedule + "]";
    }
}
