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
    public static PolicyRecruitment from(String number, long revision, String hash, JsonNode raw, Instant now) {
        var period = period(number, hash, raw);
        var source = PolicyCatalogStore.sourceUrl(number);
        var location = "온통청년 신청기간·추가 안내";
        if ("20260520005400213208".equals(number) && "f5ae512cf9721607bb849c8d466db4b21e17eb2c8b84eb8dda013fa158d98ca7".equals(hash)) {
            source = "https://youth.seoul.go.kr/bbs/view.do?key=2303300002&pstSn=2605150002";
            location = "2026년 하반기 서울청년정책네트워크 참여 조건";
        } else if ("20260614005400213232".equals(number) && "e3f828c1c37c1ecddde5a2dc59065e1179642d0fd7be3e919ec8cb9b07441c42".equals(hash)) {
            source = "https://youth.seoul.go.kr/bbs/view.do?key=2303300002&pstSn=2604010002";
            location = "2026년 상반기 서울 청년 중개보수·이사비 지원 조건";
        } else if ("20260722005400213264".equals(number) && "0d98b50fc87fc4e319676be23e6a900304a4434215e997004ed3e1f47bb8dfea".equals(hash)) {
            source = "https://youth.seoul.go.kr/bbs/view.do?key=2303300002&pstSn=2605040004";
            location = "2026년 5월 미래 청년 일자리 참여 조건";
        }
        var assessment = new RecruitmentAssessment(new RecruitmentSchedule(number, Long.toString(revision), period,
                source, location, Optional.empty()), now);
        return new PolicyRecruitment(assessment.status(), assessment.explanation(), now);
    }
    static ApplicationPeriod period(String number, String hash, JsonNode raw) {
        if ("20260520005400213208".equals(number) && "f5ae512cf9721607bb849c8d466db4b21e17eb2c8b84eb8dda013fa158d98ca7".equals(hash))
            return times(Instant.parse("2026-05-20T00:00:00Z"), Instant.parse("2026-05-29T08:00:00Z"));
        if ("20260614005400213232".equals(number) && "e3f828c1c37c1ecddde5a2dc59065e1179642d0fd7be3e919ec8cb9b07441c42".equals(hash))
            return times(Instant.parse("2026-04-01T01:00:00Z"), Instant.parse("2026-04-14T09:00:00Z"));
        if ("20260722005400213264".equals(number) && "0d98b50fc87fc4e319676be23e6a900304a4434215e997004ed3e1f47bb8dfea".equals(hash))
            return new ApplicationPeriod.Dates(java.time.LocalDate.of(2026, 5, 18), java.time.LocalDate.of(2026, 5, 31));
        return PolicyApplicationPeriod.parse(raw);
    }
    private static ApplicationPeriod.Times times(Instant open, Instant close) {
        var seoul = ZoneId.of("Asia/Seoul");
        return new ApplicationPeriod.Times(open.atZone(seoul), close.atZone(seoul));
    }
}
