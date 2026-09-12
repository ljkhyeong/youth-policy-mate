package kr.youthpolicymate.ingestion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

// AI 예약의 저장·재전달 비교는 PostgreSQL에 맞춰 마이크로초 단위로 처리한다.
final class AiDatabaseTime {
    private AiDatabaseTime() {}

    static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    static Optional<Instant> nullableInstant(ResultSet resultSet, String column) throws SQLException {
        return Optional.ofNullable(resultSet.getObject(column, OffsetDateTime.class)).map(OffsetDateTime::toInstant);
    }

    static OffsetDateTime dbTime(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    static boolean sameDatabaseInstant(Instant left, Instant right) {
        return left.truncatedTo(ChronoUnit.MICROS).equals(right.truncatedTo(ChronoUnit.MICROS));
    }
}
