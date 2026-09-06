package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.policy.ApplicationPeriod;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 전체 목록 필터에 쓰는 기간이다. 날짜형 종료일도 다음 날 서울 자정의 미포함 경계로 저장한다. */
public record PolicyRecruitmentWindow(String kind, OffsetDateTime opensAt, OffsetDateTime closesAt) {
    public static PolicyRecruitmentWindow from(String number, String hash, JsonNode raw) {
        return switch (PolicyRecruitment.period(number, hash, raw)) {
            case ApplicationPeriod.Dates dates -> new PolicyRecruitmentWindow("PERIOD",
                    dates.startsOnInclusive().atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
                    dates.endsOnInclusive().plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
            case ApplicationPeriod.Times times -> new PolicyRecruitmentWindow("PERIOD",
                    times.opensAtInclusive().toOffsetDateTime(), times.closesAtExclusive().toOffsetDateTime());
            case ApplicationPeriod.Rolling ignored -> new PolicyRecruitmentWindow("ROLLING", null, null);
            case ApplicationPeriod.UntilExhausted ignored -> new PolicyRecruitmentWindow("UNTIL_EXHAUSTED", null, null);
            case ApplicationPeriod.Closed ignored -> new PolicyRecruitmentWindow("CLOSED", null, null);
            case ApplicationPeriod.Unresolved ignored -> new PolicyRecruitmentWindow("UNKNOWN", null, null);
        };
    }
}
