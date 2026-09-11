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
            case BEFORE_OPENING -> "아직 접수 시작 전이에요.";
            case OPEN -> schedule.applicationPeriod() instanceof ApplicationPeriod.Dates
                    ? "오늘(서울 기준)은 접수 기간에 해당해요. 정확한 접수 시각은 공식 신청처에서 확인해주세요."
                    : "공고의 접수 기간에 해당해요. 실제 접수 여부는 공식 신청처에서 확인해주세요.";
            case CLOSED -> switch (schedule.applicationPeriod()) {
                case ApplicationPeriod.Dates ignored -> "공고의 마감일이 지났어요(서울 기준).";
                case ApplicationPeriod.Times ignored -> "공고의 마감 시각이 됐거나 지났어요.";
                default -> "공고상 접수가 마감됐어요. 정확한 마감 날짜·시각은 확인되지 않았어요.";
            };
            case ROLLING -> "상시 모집이에요. 현재 접수 여부는 공식 신청처에서 확인해주세요.";
            case UNTIL_EXHAUSTED -> "예산·인원 소진 시 마감돼요. 현재 접수 여부는 공식 신청처에서 확인해주세요.";
            case UNKNOWN -> ((ApplicationPeriod.Unresolved) schedule.applicationPeriod()).reason();
        };
    }
}
