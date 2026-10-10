package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.policy.ApplicationPeriod;
import kr.youthpolicymate.policy.RecruitmentStatus;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static kr.youthpolicymate.policy.RecruitmentStatus.*;
import static kr.youthpolicymate.policy.SeoulTime.SEOUL;

@Schema(requiredProperties = {"status", "explanation", "evaluatedAt", "deadlineOnSeoul", "daysUntilDeadline"})
public record PolicyRecruitment(RecruitmentStatus status, String explanation, Instant evaluatedAt,
                                @Schema(types = {"string", "null"}, format = "date", description = "확인한 마감일(서울). 상시·기간 미확인과 날짜 없는 마감은 null")
                                LocalDate deadlineOnSeoul,
                                @Schema(types = {"integer", "null"}, description = "평가 시점의 서울 날짜부터 마감일까지 남은 일수. 마감일이 없으면 null")
                                Integer daysUntilDeadline) {
    public static PolicyRecruitment from(String number, String hash, JsonNode raw, Instant now) {
        return of(period(number, hash, raw), now);
    }

    // 날짜형은 서울 날짜로 양 끝을 포함하고, 시각형은 시작 시각 포함·마감 시각 미포함으로 비교한다.
    // 테스트에서 임의 신청기간의 경계를 확인할 수 있게 package-private로 둔다.
    static PolicyRecruitment of(ApplicationPeriod period, Instant now) {
        var today = LocalDate.ofInstant(now, SEOUL);
        var status = switch (period) {
            case ApplicationPeriod.Dates dates -> today.isBefore(dates.startsOnInclusive()) ? BEFORE_OPENING
                    : today.isAfter(dates.endsOnInclusive()) ? CLOSED : OPEN;
            case ApplicationPeriod.Times times -> now.isBefore(times.opensAtInclusive().toInstant()) ? BEFORE_OPENING
                    : now.isBefore(times.closesAtExclusive().toInstant()) ? OPEN : CLOSED;
            case ApplicationPeriod.Rolling ignored -> ROLLING;
            case ApplicationPeriod.Closed ignored -> CLOSED;
            case ApplicationPeriod.Unresolved ignored -> UNKNOWN;
        };
        LocalDate deadline = switch (period) {
            case ApplicationPeriod.Dates dates -> dates.endsOnInclusive();
            case ApplicationPeriod.Times times -> LocalDate.ofInstant(times.closesAtExclusive().toInstant(), SEOUL);
            default -> null;
        };
        return new PolicyRecruitment(status, explanation(period, status), now, deadline,
                deadline == null ? null : (int) ChronoUnit.DAYS.between(today, deadline));
    }

    private static String explanation(ApplicationPeriod period, RecruitmentStatus status) {
        return switch (status) {
            case BEFORE_OPENING -> "아직 접수 시작 전이에요.";
            case OPEN -> period instanceof ApplicationPeriod.Dates
                    ? "오늘(서울 기준)은 접수 기간에 해당해요. 정확한 접수 시각은 공식 신청처에서 확인해주세요."
                    : "공고의 접수 기간에 해당해요. 실제 접수 여부는 공식 신청처에서 확인해주세요.";
            case CLOSED -> switch (period) {
                case ApplicationPeriod.Dates ignored -> "공고의 마감일이 지났어요(서울 기준).";
                case ApplicationPeriod.Times ignored -> "공고의 마감 시각이 됐거나 지났어요.";
                default -> "공고상 접수가 마감됐어요. 정확한 마감 날짜·시각은 확인되지 않았어요.";
            };
            case ROLLING -> "상시 모집이에요. 현재 접수 여부는 공식 신청처에서 확인해주세요.";
            case UNKNOWN -> ((ApplicationPeriod.Unresolved) period).reason();
        };
    }

    static ApplicationPeriod period(String number, String hash, JsonNode raw) {
        if ("20260520005400213208".equals(number) && "f5ae512cf9721607bb849c8d466db4b21e17eb2c8b84eb8dda013fa158d98ca7".equals(hash))
            return times(Instant.parse("2026-05-20T00:00:00Z"), Instant.parse("2026-05-29T08:00:00Z"));
        if ("20260614005400213232".equals(number) && "e3f828c1c37c1ecddde5a2dc59065e1179642d0fd7be3e919ec8cb9b07441c42".equals(hash))
            return times(Instant.parse("2026-04-01T01:00:00Z"), Instant.parse("2026-04-14T09:00:00Z"));
        if ("20260722005400213264".equals(number) && "0d98b50fc87fc4e319676be23e6a900304a4434215e997004ed3e1f47bb8dfea".equals(hash))
            return new ApplicationPeriod.Dates(LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 31));
        return PolicyApplicationPeriod.parse(raw);
    }
    private static ApplicationPeriod.Times times(Instant open, Instant close) {
        return new ApplicationPeriod.Times(open.atZone(SEOUL), close.atZone(SEOUL));
    }
}
