package kr.youthpolicymate.ingestion;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

// AI 예약의 저장·재전달 비교는 PostgreSQL에 맞춰 마이크로초 단위로 처리한다.
final class AiDatabaseTime {
    private AiDatabaseTime() {}

    static OffsetDateTime dbTime(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    static boolean sameDatabaseInstant(Instant left, Instant right) {
        return left.truncatedTo(ChronoUnit.MICROS).equals(right.truncatedTo(ChronoUnit.MICROS));
    }
}
