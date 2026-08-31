package kr.youthpolicymate.devpreview;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.policy.RecruitmentStatus;
import kr.youthpolicymate.schedule.DeadlineReminderCandidates;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(requiredProperties = {"outcome", "explanation", "applicationPeriod", "deadlineOnSeoul", "recruitment", "dates", "basis"})
public record ReminderResultResponse(
        DeadlineReminderCandidates.Outcome outcome,
        String explanation,
        ApplicationPeriodResponse applicationPeriod,
        @Schema(types = {"string", "null"}, format = "date", description = "확인된 마감의 서울 날짜. 없으면 null이며 임의의 마감일을 만들지 않는다.") LocalDate deadlineOnSeoul,
        RecruitmentResponse recruitment,
        List<CandidateDateResponse> dates,
        BasisResponse basis
) {
    static ReminderResultResponse from(DeadlineReminderCandidates result) {
        var recruitment = result.recruitment();
        var schedule = recruitment.schedule();
        return new ReminderResultResponse(result.outcome(), result.explanation(),
                ApplicationPeriodResponse.from(schedule.applicationPeriod()),
                schedule.confirmedDeadlineOnSeoul().orElse(null),
                new RecruitmentResponse(recruitment.status(), recruitment.explanation()),
                result.dates().stream().map(date -> new CandidateDateResponse(
                        date.daysBeforeDeadline(), date.date(), date.status())).toList(),
                new BasisResponse(schedule.policyId(), schedule.policyRevision(), recruitment.evaluatedAt(),
                        recruitment.evaluatedOnSeoul(), schedule.sourceReference(), schedule.sourceLocation(),
                        schedule.sourceExcerpt().orElse(null)));
    }

    @Schema(name = "ReminderRecruitment", requiredProperties = {"status", "explanation"})
    public record RecruitmentResponse(RecruitmentStatus status, String explanation) {}

    @Schema(name = "ReminderCandidateDate", requiredProperties = {"daysBeforeDeadline", "date", "status"})
    public record CandidateDateResponse(
            @Schema(description = "마감 며칠 전인지 나타내는 정수. 현재 후보 계산은 7·3·1일 전을 제공한다.") int daysBeforeDeadline,
            @Schema(description = "서울의 달력 날짜. 발송 시각이 아님") LocalDate date,
            DeadlineReminderCandidates.DateStatus status
    ) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "ReminderBasis", requiredProperties = {"policyId", "policyRevision", "evaluatedAt", "evaluatedOnSeoul",
            "sourceReference", "sourceLocation", "sourceExcerpt"})
    public record BasisResponse(
            String policyId, String policyRevision,
            @Schema(description = "계산 기준 순간. 원문 수집 시각이 아님") Instant evaluatedAt,
            @Schema(description = "계산 기준 순간의 서울 날짜. 오늘 후보의 기준") LocalDate evaluatedOnSeoul,
            String sourceReference, String sourceLocation,
            @Schema(types = {"string", "null"}, description = "기록된 발췌문. 없으면 null") String sourceExcerpt
    ) {}
}
