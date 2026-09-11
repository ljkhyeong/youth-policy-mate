package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.policy.ApplicationPeriod;
import kr.youthpolicymate.policy.RecruitmentAssessment;
import kr.youthpolicymate.policy.RecruitmentSchedule;
import kr.youthpolicymate.policy.RecruitmentStatus;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

@Schema(requiredProperties = {"status", "explanation", "evaluatedAt"})
public record PolicyRecruitment(RecruitmentStatus status, String explanation, Instant evaluatedAt) {
    static PolicyRecruitment from(String number, long revision, String hash, JsonNode raw, Instant now) {
        var period = period(number, hash, raw);
        var source = PolicyCatalogStore.sourceUrl(number);
        var location = "온통청년 신청기간·추가 안내";
        if (SeoulYouthNetworkRules.NUMBER.equals(number) && SeoulYouthNetworkRules.CONTENT_HASH.equals(hash)) {
            source = SeoulYouthNetworkRules.SOURCE;
            location = SeoulYouthNetworkRules.SCOPE;
        } else if (MovingFeeRules.NUMBER.equals(number) && MovingFeeRules.CONTENT_HASH.equals(hash)) {
            source = MovingFeeRules.SOURCE;
            location = MovingFeeRules.SCOPE;
        } else if (FutureYouthJobsRules.NUMBER.equals(number) && FutureYouthJobsRules.CONTENT_HASH.equals(hash)) {
            source = FutureYouthJobsRules.SOURCE;
            location = FutureYouthJobsRules.SCOPE;
        }
        var assessment = new RecruitmentAssessment(new RecruitmentSchedule(number, Long.toString(revision), period,
                source, location, Optional.empty()), now);
        return new PolicyRecruitment(assessment.status(), assessment.explanation(), now);
    }
    static ApplicationPeriod period(String number, String hash, JsonNode raw) {
        if (SeoulYouthNetworkRules.NUMBER.equals(number) && SeoulYouthNetworkRules.CONTENT_HASH.equals(hash))
            return times(SeoulYouthNetworkRules.OPEN_AT, SeoulYouthNetworkRules.CLOSE_AT);
        if (MovingFeeRules.NUMBER.equals(number) && MovingFeeRules.CONTENT_HASH.equals(hash))
            return times(MovingFeeRules.OPEN_AT, MovingFeeRules.CLOSE_AT);
        if (FutureYouthJobsRules.NUMBER.equals(number) && FutureYouthJobsRules.CONTENT_HASH.equals(hash))
            return new ApplicationPeriod.Dates(java.time.LocalDate.of(2026, 5, 18), java.time.LocalDate.of(2026, 5, 31));
        return PolicyApplicationPeriod.parse(raw);
    }
    private static ApplicationPeriod.Times times(Instant open, Instant close) {
        var seoul = ZoneId.of("Asia/Seoul");
        return new ApplicationPeriod.Times(open.atZone(seoul), close.atZone(seoul));
    }
}
