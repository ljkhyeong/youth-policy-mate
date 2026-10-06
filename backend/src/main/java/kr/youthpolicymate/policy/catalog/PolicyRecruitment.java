package kr.youthpolicymate.policy.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.policy.ApplicationPeriod;
import kr.youthpolicymate.policy.RecruitmentAssessment;
import kr.youthpolicymate.policy.RecruitmentSchedule;
import kr.youthpolicymate.policy.RecruitmentStatus;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static kr.youthpolicymate.policy.SeoulTime.SEOUL;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(requiredProperties = {"status", "explanation", "evaluatedAt", "deadlineOnSeoul", "daysUntilDeadline"})
public record PolicyRecruitment(RecruitmentStatus status, String explanation, Instant evaluatedAt,
                                @Schema(types = {"string", "null"}, format = "date", description = "확인한 마감일(서울). 상시·소진형·기간 미확인과 날짜 없는 마감은 null")
                                LocalDate deadlineOnSeoul,
                                @Schema(types = {"integer", "null"}, description = "평가 시점의 서울 날짜부터 마감일까지 남은 일수. 마감일이 없으면 null")
                                Integer daysUntilDeadline) {
    public static PolicyRecruitment from(String number, long revision, String hash, JsonNode raw, Instant now) {
        // 상태와 설명은 신청기간만으로 계산한다. 원문 참조는 일정 모델의 필수 값이라 기본 출처를 넘긴다.
        var schedule = new RecruitmentSchedule(number, Long.toString(revision), period(number, hash, raw),
                PolicyCatalogStore.sourceUrl(number), "온통청년 신청기간·추가 안내", Optional.empty());
        var assessment = new RecruitmentAssessment(schedule, now);
        // 마감일은 마감 알림과 같은 계산을 사용한다.
        var deadline = schedule.confirmedDeadlineOnSeoul().orElse(null);
        return new PolicyRecruitment(assessment.status(), assessment.explanation(), now, deadline,
                deadline == null ? null : (int) ChronoUnit.DAYS.between(assessment.evaluatedOnSeoul(), deadline));
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
        return new ApplicationPeriod.Times(open.atZone(SEOUL), close.atZone(SEOUL));
    }
}
