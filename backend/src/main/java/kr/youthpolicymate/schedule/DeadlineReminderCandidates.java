package kr.youthpolicymate.schedule;

import kr.youthpolicymate.policy.RecruitmentAssessment;
import kr.youthpolicymate.policy.RecruitmentSchedule;
import kr.youthpolicymate.policy.RecruitmentStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record DeadlineReminderCandidates(RecruitmentAssessment recruitment) {

    private static final List<Integer> DAYS_BEFORE_DEADLINE = List.of(7, 3, 1);

    public DeadlineReminderCandidates {
        recruitment = Objects.requireNonNull(recruitment, "기준 시점이 고정된 모집 상태가 필요합니다.");
    }

    public static DeadlineReminderCandidates calculate(RecruitmentSchedule schedule, Clock clock) {
        return new DeadlineReminderCandidates(RecruitmentAssessment.evaluate(schedule, clock));
    }

    public List<CandidateDate> dates() {
        var deadline = recruitment.schedule().confirmedDeadlineOnSeoul();
        if (recruitment.status() == RecruitmentStatus.CLOSED || deadline.isEmpty()) {
            return List.of();
        }

        LocalDate today = recruitment.evaluatedOnSeoul();
        LocalDate deadlineDate = deadline.orElseThrow();
        var candidates = new ArrayList<CandidateDate>();
        for (int days : DAYS_BEFORE_DEADLINE) {
            LocalDate date = deadlineDate.minusDays(days);
            if (date.isBefore(today)) continue;
            candidates.add(new CandidateDate(days, date, date.equals(today)
                    ? DateStatus.TODAY_REQUIRES_SEND_TIME_CHECK : DateStatus.FUTURE_DATE));
        }
        return List.copyOf(candidates);
    }

    public Outcome outcome() {
        if (recruitment.status() == RecruitmentStatus.CLOSED) return Outcome.RECRUITMENT_CLOSED;
        if (recruitment.schedule().confirmedDeadlineOnSeoul().isEmpty()) return Outcome.NO_CONFIRMED_DEADLINE;
        return dates().isEmpty() ? Outcome.NO_REMAINING_DATES : Outcome.CANDIDATES_AVAILABLE;
    }

    public String explanation() {
        return switch (outcome()) {
            case CANDIDATES_AVAILABLE -> "알림 후보 날짜입니다. 발송 시각·저장 상태·수신 조건 확인 전에는 예약이나 발송에 사용할 수 없습니다. 오늘 후보는 발송 시각을 추가 확인해야 합니다.";
            case RECRUITMENT_CLOSED -> "모집이 마감되어 알림 후보를 만들지 않았습니다.";
            case NO_CONFIRMED_DEADLINE -> "확인된 신청 마감 날짜가 없어 후보를 만들지 않았습니다. " + recruitment.explanation();
            case NO_REMAINING_DATES -> "D-7·D-3·D-1 날짜가 모두 지났습니다. 지난 날짜의 알림을 오늘로 옮기지 않습니다.";
        };
    }

    public enum Outcome {
        CANDIDATES_AVAILABLE,
        NO_CONFIRMED_DEADLINE,
        RECRUITMENT_CLOSED,
        NO_REMAINING_DATES
    }

    public enum DateStatus {
        FUTURE_DATE,
        TODAY_REQUIRES_SEND_TIME_CHECK
    }

    public record CandidateDate(int daysBeforeDeadline, LocalDate date, DateStatus status) {
    }
}
