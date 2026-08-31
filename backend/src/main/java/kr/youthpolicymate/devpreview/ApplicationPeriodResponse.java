package kr.youthpolicymate.devpreview;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.policy.ApplicationPeriod;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(requiredProperties = {"kind", "startsOnInclusive", "endsOnInclusive", "opensAtInclusive",
        "openingTimeZone", "closesAtExclusive", "closingTimeZone", "reason"},
        description = "기간 종류에 해당하지 않는 필드도 생략하지 않고 null로 보낸다. 날짜형과 시각형을 혼합하지 않는다.")
public record ApplicationPeriodResponse(
        Kind kind,
        @Schema(types = {"string", "null"}, format = "date", description = "DATES의 시작일, 포함 경계") LocalDate startsOnInclusive,
        @Schema(types = {"string", "null"}, format = "date", description = "DATES의 종료일, 포함 경계. 정확한 시각이 아님") LocalDate endsOnInclusive,
        @Schema(types = {"string", "null"}, format = "date-time", description = "TIMES의 시작 순간, 포함 경계") OffsetDateTime opensAtInclusive,
        @Schema(types = {"string", "null"}, description = "원래 시작 시간대 ID") String openingTimeZone,
        @Schema(types = {"string", "null"}, format = "date-time", description = "TIMES의 마감 순간, 해당 순간부터 종료") OffsetDateTime closesAtExclusive,
        @Schema(types = {"string", "null"}, description = "원래 마감 시간대 ID") String closingTimeZone,
        @Schema(types = {"string", "null"}, description = "UNRESOLVED의 미확인 이유") String reason
) {
    public enum Kind { DATES, TIMES, ROLLING, UNTIL_EXHAUSTED, CLOSED, UNRESOLVED }

    static ApplicationPeriodResponse from(ApplicationPeriod period) {
        return switch (period) {
            case ApplicationPeriod.Dates dates -> new ApplicationPeriodResponse(Kind.DATES,
                    dates.startsOnInclusive(), dates.endsOnInclusive(), null, null, null, null, null);
            case ApplicationPeriod.Times times -> new ApplicationPeriodResponse(Kind.TIMES, null, null,
                    times.opensAtInclusive().toOffsetDateTime(), times.opensAtInclusive().getZone().getId(),
                    times.closesAtExclusive().toOffsetDateTime(), times.closesAtExclusive().getZone().getId(), null);
            case ApplicationPeriod.Rolling ignored -> undated(Kind.ROLLING);
            case ApplicationPeriod.UntilExhausted ignored -> undated(Kind.UNTIL_EXHAUSTED);
            case ApplicationPeriod.Closed ignored -> undated(Kind.CLOSED);
            case ApplicationPeriod.Unresolved unresolved -> new ApplicationPeriodResponse(Kind.UNRESOLVED,
                    null, null, null, null, null, null, unresolved.reason());
        };
    }

    private static ApplicationPeriodResponse undated(Kind kind) {
        return new ApplicationPeriodResponse(kind, null, null, null, null, null, null, null);
    }
}
