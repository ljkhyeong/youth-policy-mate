package kr.youthpolicymate.policy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

import static kr.youthpolicymate.policy.RecruitmentStatus.BEFORE_OPENING;
import static kr.youthpolicymate.policy.RecruitmentStatus.CLOSED;
import static kr.youthpolicymate.policy.RecruitmentStatus.OPEN;
import static kr.youthpolicymate.policy.RecruitmentStatus.ROLLING;
import static kr.youthpolicymate.policy.RecruitmentStatus.UNKNOWN;
import static kr.youthpolicymate.policy.RecruitmentStatus.UNTIL_EXHAUSTED;

public record RecruitmentAssessment(RecruitmentSchedule schedule, Instant evaluatedAt) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public RecruitmentAssessment {
        schedule = Objects.requireNonNull(schedule, "모집 안내 자료가 필요합니다.");
        evaluatedAt = Objects.requireNonNull(evaluatedAt, "모집 상태를 계산할 시각이 필요합니다.");
    }

    public static RecruitmentAssessment evaluate(RecruitmentSchedule schedule, Clock clock) {
        Objects.requireNonNull(clock, "모집 상태 계산에 사용할 시계가 필요합니다.");
        // 시계를 한 번만 읽어 시각형·날짜형 결과가 같은 기준 시점을 사용하게 한다.
        return new RecruitmentAssessment(schedule, clock.instant());
    }

    public LocalDate evaluatedOnSeoul() {
        return evaluatedAt.atZone(SEOUL).toLocalDate();
    }

    public RecruitmentStatus status() {
        return switch (schedule.applicationPeriod()) {
            case ApplicationPeriod.Dates dates -> {
                LocalDate today = evaluatedOnSeoul();
                yield today.isBefore(dates.startsOnInclusive()) ? BEFORE_OPENING
                        : today.isAfter(dates.endsOnInclusive()) ? CLOSED : OPEN;
            }
            case ApplicationPeriod.Times times -> evaluatedAt.isBefore(times.opensAtInclusive().toInstant())
                    ? BEFORE_OPENING : evaluatedAt.isBefore(times.closesAtExclusive().toInstant()) ? OPEN : CLOSED;
            case ApplicationPeriod.Rolling ignored -> ROLLING;
            case ApplicationPeriod.UntilExhausted ignored -> UNTIL_EXHAUSTED;
            case ApplicationPeriod.Closed ignored -> CLOSED;
            case ApplicationPeriod.Unresolved ignored -> UNKNOWN;
        };
    }

    public String explanation() {
        return switch (status()) {
            case BEFORE_OPENING -> "확인한 신청기간이 아직 시작되지 않았습니다.";
            case OPEN -> schedule.applicationPeriod() instanceof ApplicationPeriod.Dates
                    ? "서울 날짜 기준으로 신청기간에 포함됩니다. 정확한 접수 시각은 확인되지 않았으므로 공식 신청처의 운영 시간을 확인해야 합니다."
                    : "확인한 접수 시작 시각 이후이고 마감 시각 전입니다. 실제 접수 상태는 공식 신청처에서 확인해야 합니다.";
            case CLOSED -> switch (schedule.applicationPeriod()) {
                case ApplicationPeriod.Dates ignored -> "서울 날짜 기준으로 확인한 신청 종료일이 지났습니다.";
                case ApplicationPeriod.Times ignored -> "확인한 접수 마감 시각에 도달했거나 지났습니다.";
                default -> "원문에서 모집 마감을 확인했습니다. 이 안내만으로 정확한 마감 날짜나 시각을 알 수는 없습니다.";
            };
            case ROLLING -> "원문에서 상시 모집으로 확인했습니다. 현재 접수 여부는 공식 신청처에서 확인해야 합니다.";
            case UNTIL_EXHAUSTED -> "예산·인원 소진 시 끝나는 모집입니다. 소진 여부와 현재 접수 여부를 공식 신청처에서 확인해야 합니다.";
            case UNKNOWN -> ((ApplicationPeriod.Unresolved) schedule.applicationPeriod()).reason();
        };
    }
}
