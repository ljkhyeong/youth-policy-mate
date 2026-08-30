package kr.youthpolicymate.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RecruitmentScheduleTest {

    @Test
    @DisplayName("날짜형 마감을 시각으로 바꾸지 않고 원래 날짜와 기간을 유지한다")
    void projectsDateOnlyDeadline() {
        var end = LocalDate.of(2026, 9, 3);
        var period = new ApplicationPeriod.Dates(end.minusDays(10), end);
        var schedule = schedule(period);

        assertThat(schedule.confirmedDeadlineOnSeoul()).contains(end);
        assertThat(schedule.applicationPeriod()).isSameAs(period);
    }

    @ParameterizedTest(name = "원문 마감 {0} → 서울 날짜 {1}")
    @CsvSource({
            "2026-09-02T15:30:00Z, 2026-09-03",
            "2026-09-02T15:30:00-07:00[America/Los_Angeles], 2026-09-03",
            "2026-09-03T00:00:00+09:00[Asia/Seoul], 2026-09-03"
    })
    @DisplayName("시각형은 서울 마감 날짜를 제공하되 원문 시간대·종료 순간을 유지한다")
    void projectsExactDeadlineToSeoul(String end, String expectedDate) {
        var close = ZonedDateTime.parse(end);
        var period = new ApplicationPeriod.Times(close.minusDays(10), close);
        var schedule = schedule(period);

        assertThat(schedule.confirmedDeadlineOnSeoul()).contains(LocalDate.parse(expectedDate));
        assertThat(schedule.applicationPeriod()).isSameAs(period);
        assertThat(((ApplicationPeriod.Times) schedule.applicationPeriod()).closesAtExclusive()).isEqualTo(close);
    }

    static Stream<ApplicationPeriod> withoutConfirmedDeadline() {
        return Stream.of(new ApplicationPeriod.Rolling(), new ApplicationPeriod.UntilExhausted(),
                new ApplicationPeriod.Closed(), new ApplicationPeriod.Unresolved("신청기간이 서로 달라 확인해야 합니다."));
    }

    @ParameterizedTest
    @MethodSource("withoutConfirmedDeadline")
    @DisplayName("상시·소진 시 종료·날짜 없는 마감·미확인에는 마감 날짜를 만들지 않는다")
    void doesNotInventDeadline(ApplicationPeriod period) {
        assertThat(schedule(period).confirmedDeadlineOnSeoul()).isEmpty();
    }

    private static RecruitmentSchedule schedule(ApplicationPeriod period) {
        return new RecruitmentSchedule("sample-policy", "revision-1", period,
                "sample-source", "인공 자료 · 신청기간", Optional.empty());
    }
}
