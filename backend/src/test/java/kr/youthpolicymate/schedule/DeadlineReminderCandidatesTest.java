package kr.youthpolicymate.schedule;

import kr.youthpolicymate.policy.ApplicationPeriod;
import kr.youthpolicymate.policy.RecruitmentSchedule;
import kr.youthpolicymate.policy.RecruitmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static kr.youthpolicymate.schedule.DeadlineReminderCandidates.DateStatus.FUTURE_DATE;
import static kr.youthpolicymate.schedule.DeadlineReminderCandidates.DateStatus.TODAY_REQUIRES_SEND_TIME_CHECK;
import static kr.youthpolicymate.schedule.DeadlineReminderCandidates.Outcome.CANDIDATES_AVAILABLE;
import static kr.youthpolicymate.schedule.DeadlineReminderCandidates.Outcome.NO_CONFIRMED_DEADLINE;
import static kr.youthpolicymate.schedule.DeadlineReminderCandidates.Outcome.NO_REMAINING_DATES;
import static kr.youthpolicymate.schedule.DeadlineReminderCandidates.Outcome.RECRUITMENT_CLOSED;
import static org.assertj.core.api.Assertions.assertThat;

class DeadlineReminderCandidatesTest {

    @ParameterizedTest(name = "마감 {0} → {1}, {2}, {3}")
    @CsvSource({
            "2026-09-07, 2026-08-31, 2026-09-04, 2026-09-06",
            "2027-01-03, 2026-12-27, 2026-12-31, 2027-01-02",
            "2028-03-01, 2028-02-23, 2028-02-27, 2028-02-29"
    })
    @DisplayName("월·연도·윤일을 넘어도 D-7·D-3·D-1 달력 날짜를 순서대로 계산한다")
    void calculatesCalendarDays(String end, String d7, String d3, String d1) {
        var deadline = LocalDate.parse(end);
        var clock = Clock.fixed(deadline.minusDays(10).atTime(12, 0).toInstant(ZoneOffset.ofHours(9)), ZoneOffset.UTC);
        var result = DeadlineReminderCandidates.calculate(schedule(datePeriod(end), "revision-1"), clock);

        assertThat(result.outcome()).isEqualTo(CANDIDATES_AVAILABLE);
        assertThat(result.dates()).extracting(DeadlineReminderCandidates.CandidateDate::daysBeforeDeadline)
                .containsExactly(7, 3, 1);
        assertThat(result.dates()).extracting(DeadlineReminderCandidates.CandidateDate::date)
                .containsExactly(LocalDate.parse(d7), LocalDate.parse(d3), LocalDate.parse(d1));
        assertThat(result.dates()).allMatch(candidate -> candidate.status() == FUTURE_DATE);
        assertThat(result.explanation()).contains("예약이나 발송에 사용할 수 없습니다");
    }

    @ParameterizedTest(name = "계산 시각 {0}, 첫 후보 D-{1} {2}, 후보 {3}개")
    @CsvSource({
            "2026-08-30T14:59:59Z, 7, FUTURE_DATE, 3",
            "2026-08-30T15:00:00Z, 7, TODAY_REQUIRES_SEND_TIME_CHECK, 3",
            "2026-08-31T14:59:59Z, 7, TODAY_REQUIRES_SEND_TIME_CHECK, 3",
            "2026-08-31T15:00:00Z, 3, FUTURE_DATE, 2"
    })
    @DisplayName("서울 자정 전후로 오늘 후보를 구분하고 지난 날짜는 오늘로 옮기지 않는다")
    void distinguishesTodayFromPast(String instant, int firstDays, DeadlineReminderCandidates.DateStatus status, int count) {
        var result = calculate(datePeriod("2026-09-07"), instant);

        assertThat(result.dates()).hasSize(count);
        assertThat(result.dates().getFirst().daysBeforeDeadline()).isEqualTo(firstDays);
        assertThat(result.dates().getFirst().status()).isEqualTo(status);
        assertThat(result.dates().getFirst().date()).isEqualTo(LocalDate.of(2026, 9, 7).minusDays(firstDays));
        assertThat(result.recruitment().evaluatedAt()).isEqualTo(Instant.parse(instant));
        assertThat(result.dates()).allMatch(candidate -> !candidate.date().isBefore(result.recruitment().evaluatedOnSeoul()));
    }

    static Stream<Arguments> withoutDeadline() {
        return Stream.of(
                Arguments.of(new ApplicationPeriod.Rolling(), NO_CONFIRMED_DEADLINE),
                Arguments.of(new ApplicationPeriod.UntilExhausted(), NO_CONFIRMED_DEADLINE),
                Arguments.of(new ApplicationPeriod.Closed(), RECRUITMENT_CLOSED),
                Arguments.of(new ApplicationPeriod.Unresolved("서로 다른 신청 마감 날짜를 확인해야 합니다."), NO_CONFIRMED_DEADLINE));
    }

    @ParameterizedTest
    @MethodSource("withoutDeadline")
    @DisplayName("마감 날짜가 없는 모집 유형은 후보 없이 원래 상태와 이유를 보존한다")
    void noCandidatesWithoutDeadline(ApplicationPeriod period, DeadlineReminderCandidates.Outcome expected) {
        var result = calculate(period, "2026-08-30T15:00:00Z");

        assertThat(result.outcome()).isEqualTo(expected);
        assertThat(result.dates()).isEmpty();
        assertThat(result.recruitment().schedule().applicationPeriod()).isEqualTo(period);
        if (period instanceof ApplicationPeriod.Unresolved unresolved) {
            assertThat(result.explanation()).contains(unresolved.reason());
        }
    }

    static Stream<Arguments> passedReminderDates() {
        var close = ZonedDateTime.parse("2026-08-31T18:00:00+09:00[Asia/Seoul]");
        var times = new ApplicationPeriod.Times(close.minusDays(10), close);
        return Stream.of(
                Arguments.of(times, "2026-08-31T00:00:00Z", NO_REMAINING_DATES),
                Arguments.of(times, "2026-08-31T09:00:00Z", RECRUITMENT_CLOSED),
                Arguments.of(datePeriod("2026-08-31"), "2026-08-31T15:00:00Z", RECRUITMENT_CLOSED));
    }

    @ParameterizedTest
    @MethodSource("passedReminderDates")
    @DisplayName("후보 날짜가 모두 지난 경우와 실제 모집 마감을 구분하며 보충 알림을 만들지 않는다")
    void separatesNoRemainingDatesFromClosed(ApplicationPeriod period, String instant, DeadlineReminderCandidates.Outcome expected) {
        var result = calculate(period, instant);

        assertThat(result.outcome()).isEqualTo(expected);
        assertThat(result.dates()).isEmpty();
        if (expected == NO_REMAINING_DATES) {
            assertThat(result.recruitment().status()).isEqualTo(RecruitmentStatus.OPEN);
            assertThat(result.explanation()).contains("지난 날짜의 알림을 오늘로 옮기지 않습니다");
        }
    }

    @Test
    @DisplayName("UTC 원문의 날짜가 아닌 서울 마감 날짜를 사용하고 원래 시각을 남긴다")
    void usesPolicyOwnedSeoulDeadline() {
        var close = ZonedDateTime.parse("2026-09-02T15:30:00Z");
        var period = new ApplicationPeriod.Times(close.minusDays(10), close);
        var result = calculate(period, "2026-08-25T15:00:00Z");

        assertThat(result.dates()).extracting(DeadlineReminderCandidates.CandidateDate::date)
                .containsExactly(LocalDate.of(2026, 8, 27), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 2));
        assertThat(result.recruitment().schedule().applicationPeriod()).isSameAs(period);
    }

    @Test
    @DisplayName("새 개정의 마감 날짜와 미확인 전환은 이전 후보를 재사용하지 않는다")
    void recalculatesFromRevisedSchedule() {
        var clock = Clock.fixed(Instant.parse("2026-08-30T15:00:00Z"), ZoneOffset.UTC);
        var original = DeadlineReminderCandidates.calculate(schedule(datePeriod("2026-09-07"), "revision-1"), clock);
        var changed = DeadlineReminderCandidates.calculate(schedule(datePeriod("2026-09-10"), "revision-2"), clock);
        var unresolved = DeadlineReminderCandidates.calculate(
                schedule(new ApplicationPeriod.Unresolved("변경된 마감 날짜 확인 필요"), "revision-3"), clock);

        assertThat(original.dates().getFirst().date()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(changed.dates()).extracting(DeadlineReminderCandidates.CandidateDate::date)
                .containsExactly(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 9));
        assertThat(changed.recruitment().schedule().policyId()).isEqualTo("sample-policy");
        assertThat(changed.recruitment().schedule().policyRevision()).isEqualTo("revision-2");
        assertThat(changed.recruitment().schedule().sourceReference()).isEqualTo("sample-source-revision-2");
        assertThat(changed.recruitment().schedule().sourceExcerpt()).contains("실제 정책이 아닌 인공 신청기간");
        assertThat(unresolved.outcome()).isEqualTo(NO_CONFIRMED_DEADLINE);
        assertThat(unresolved.dates()).isEmpty();
        assertThat(unresolved.recruitment().schedule().policyRevision()).isEqualTo("revision-3");
    }

    @Test
    @DisplayName("모집 전의 날짜도 마감 기준 후보로 계산하되 발송 확정으로 처리하지 않는다")
    void keepsCandidatesBeforeOpening() {
        var period = new ApplicationPeriod.Dates(LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 7));
        var result = calculate(period, "2026-08-30T15:00:00Z");

        assertThat(result.recruitment().status()).isEqualTo(RecruitmentStatus.BEFORE_OPENING);
        assertThat(result.outcome()).isEqualTo(CANDIDATES_AVAILABLE);
        assertThat(result.dates()).hasSize(3);
        assertThat(result.dates().getFirst().status()).isEqualTo(TODAY_REQUIRES_SEND_TIME_CHECK);
        assertThat(result.explanation()).contains("발송 시각·저장 상태·수신 조건 확인 전");
    }

    private static ApplicationPeriod.Dates datePeriod(String deadline) {
        var end = LocalDate.parse(deadline);
        return new ApplicationPeriod.Dates(end.minusDays(20), end);
    }

    private static RecruitmentSchedule schedule(ApplicationPeriod period, String revision) {
        return new RecruitmentSchedule("sample-policy", revision, period, "sample-source-" + revision,
                "인공 자료 · 신청기간", Optional.of("실제 정책이 아닌 인공 신청기간"));
    }

    private static DeadlineReminderCandidates calculate(ApplicationPeriod period, String instant) {
        return DeadlineReminderCandidates.calculate(schedule(period, "revision-1"),
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
