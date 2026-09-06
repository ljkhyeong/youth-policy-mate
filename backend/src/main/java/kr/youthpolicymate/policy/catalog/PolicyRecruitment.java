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
        ApplicationPeriod period;
        var source = PolicyCatalogStore.sourceUrl(number);
        var location = "온통청년 신청기간·추가 안내";
        if (SeoulYouthNetworkRules.NUMBER.equals(number) && SeoulYouthNetworkRules.CONTENT_HASH.equals(hash)) {
            period = times(SeoulYouthNetworkRules.OPEN_AT, SeoulYouthNetworkRules.CLOSE_AT);
            source = SeoulYouthNetworkRules.SOURCE;
            location = SeoulYouthNetworkRules.SCOPE;
        } else if (MovingFeeRules.NUMBER.equals(number) && MovingFeeRules.CONTENT_HASH.equals(hash)) {
            period = times(MovingFeeRules.OPEN_AT, MovingFeeRules.CLOSE_AT);
            source = MovingFeeRules.SOURCE;
            location = MovingFeeRules.SCOPE;
        } else period = PolicyApplicationPeriod.parse(raw);
        var assessment = new RecruitmentAssessment(new RecruitmentSchedule(number, Long.toString(revision), period,
                source, location, Optional.empty()), now);
        return new PolicyRecruitment(assessment.status(), assessment.explanation(), now);
    }
    private static ApplicationPeriod.Times times(Instant open, Instant close) {
        var seoul = ZoneId.of("Asia/Seoul");
        return new ApplicationPeriod.Times(open.atZone(seoul), close.atZone(seoul));
    }
}
