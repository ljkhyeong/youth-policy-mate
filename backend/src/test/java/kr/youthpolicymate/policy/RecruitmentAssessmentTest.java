package kr.youthpolicymate.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static kr.youthpolicymate.policy.RecruitmentStatus.BEFORE_OPENING;
import static kr.youthpolicymate.policy.RecruitmentStatus.CLOSED;
import static kr.youthpolicymate.policy.RecruitmentStatus.OPEN;
import static kr.youthpolicymate.policy.RecruitmentStatus.ROLLING;
import static kr.youthpolicymate.policy.RecruitmentStatus.UNKNOWN;
import static kr.youthpolicymate.policy.RecruitmentStatus.UNTIL_EXHAUSTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecruitmentAssessmentTest {

    private static final ApplicationPeriod.Dates DATES = new ApplicationPeriod.Dates(
            LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 2));
    private static final ApplicationPeriod.Times TIMES = new ApplicationPeriod.Times(
            ZonedDateTime.parse("2026-08-31T09:00:00+09:00[Asia/Seoul]"),
            ZonedDateTime.parse("2026-08-31T18:00:00+09:00[Asia/Seoul]"));

    @ParameterizedTest(name = "시각 {0} → {1}")
    @CsvSource({
            "2026-08-30T14:59:59.999999999Z, BEFORE_OPENING",
            "2026-08-30T15:00:00Z, OPEN",
            "2026-09-02T14:59:59.999999999Z, OPEN",
            "2026-09-02T15:00:00Z, CLOSED"
    })
    @DisplayName("날짜형 신청기간은 서울 자정 경계와 양 끝 날짜 포함을 적용한다")
    void evaluatesSeoulDateBoundaries(String instant, RecruitmentStatus expected) {
        var result = evaluate(DATES, instant);

        assertThat(result.status()).isEqualTo(expected);
        assertThat(result.schedule().applicationPeriod()).isEqualTo(DATES);
        if (expected == OPEN) {
            assertThat(result.explanation()).contains("서울 날짜 기준", "정확한 접수 시각은 확인되지 않았으므로");
        }
    }

    @ParameterizedTest(name = "하루 모집 {0} → {1}")
    @CsvSource({
            "2026-08-30T14:59:59Z, BEFORE_OPENING",
            "2026-08-30T15:00:00Z, OPEN",
            "2026-08-31T15:00:00Z, CLOSED"
    })
    @DisplayName("시작일과 종료일이 같은 날짜형 모집도 하루 기간으로 보존한다")
    void permitsSingleDayDatePeriod(String instant, RecruitmentStatus expected) {
        var date = LocalDate.of(2026, 8, 31);

        assertThat(evaluate(new ApplicationPeriod.Dates(date, date), instant).status()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "시각 {0} → {1}")
    @CsvSource({
            "2026-08-30T23:59:59.999999999Z, BEFORE_OPENING",
            "2026-08-31T00:00:00Z, OPEN",
            "2026-08-31T08:59:59.999999999Z, OPEN",
            "2026-08-31T09:00:00Z, CLOSED",
            "2026-08-31T09:00:01Z, CLOSED"
    })
    @DisplayName("시각형 모집은 시작 시각을 포함하고 명시적 접수 종료 순간부터 마감이다")
    void evaluatesExactTimeBoundaries(String instant, RecruitmentStatus expected) {
        var result = evaluate(TIMES, instant);

        assertThat(result.status()).isEqualTo(expected);
        assertThat(result.schedule().applicationPeriod()).isEqualTo(TIMES);
        if (expected == CLOSED) assertThat(result.explanation()).contains("마감 시각에 도달했거나 지났습니다");
    }

    static Stream<ApplicationPeriod> noDeadlinePeriods() {
        return Stream.of(new ApplicationPeriod.Rolling(), new ApplicationPeriod.UntilExhausted(), new ApplicationPeriod.Closed());
    }

    @ParameterizedTest
    @MethodSource("noDeadlinePeriods")
    @DisplayName("상시·소진 시 종료·명시적 마감을 날짜 경과로 바꾸지 않는다")
    void keepsNonCalendarRecruitment(ApplicationPeriod period) {
        RecruitmentStatus expected = period instanceof ApplicationPeriod.Rolling ? ROLLING
                : period instanceof ApplicationPeriod.UntilExhausted ? UNTIL_EXHAUSTED : CLOSED;
        var before = evaluate(period, "2026-08-30T15:00:00Z");
        var after = evaluate(period, "2027-08-30T15:00:00Z");

        assertThat(before.status()).isEqualTo(expected);
        assertThat(after.status()).isEqualTo(expected);
        assertThat(after.schedule().applicationPeriod()).isEqualTo(period);
        assertThat(after.explanation()).isEqualTo(before.explanation());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "신청기간이 없고 사업 종료일만 있습니다.",
            "신청기간과 마감 상태 코드가 서로 다릅니다.",
            "복수 모집 차수 사이의 기간을 확인해야 합니다.",
            "마감 시각의 시간대를 확인하지 못했습니다."
    })
    @DisplayName("누락·충돌·미지원 기간은 이유와 빈 발췌문을 보존하고 날짜를 보충하지 않는다")
    void keepsUnresolvedReason(String reason) {
        var schedule = new RecruitmentSchedule("sample-policy", "revision-2", new ApplicationPeriod.Unresolved(reason),
                "sample-source", "신청기간 필드·관련 본문", Optional.empty());
        var result = RecruitmentAssessment.evaluate(schedule,
                Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC));

        assertThat(result.status()).isEqualTo(UNKNOWN);
        assertThat(result.explanation()).isEqualTo(reason);
        assertThat(result.schedule().sourceExcerpt()).isEmpty();
        assertThat(result.schedule().applicationPeriod()).isEqualTo(new ApplicationPeriod.Unresolved(reason));
    }

    @Test
    @DisplayName("정책 개정·근거·원문의 시간대와 계산 시점을 결과에 보존한다")
    void preservesSourceAndRevision() {
        var result = evaluate(TIMES, "2026-08-31T01:00:00Z");

        assertThat(result.schedule().policyId()).isEqualTo("sample-policy");
        assertThat(result.schedule().policyRevision()).isEqualTo("revision-1");
        assertThat(result.schedule().sourceReference()).isEqualTo("sample-source-revision-1");
        assertThat(result.schedule().sourceLocation()).isEqualTo("인공 자료 · 신청기간");
        assertThat(result.schedule().sourceExcerpt()).contains("화면·서버 점검용 인공 신청기간이며 실제 정책 원문이 아님");
        assertThat(result.schedule().applicationPeriod()).isEqualTo(TIMES);
        assertThat(TIMES.closesAtExclusive().getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
        assertThat(result.evaluatedAt()).isEqualTo(Instant.parse("2026-08-31T01:00:00Z"));
        assertThat(result.evaluatedOnSeoul()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("주입한 시계의 시간대와 무관하게 같은 순간은 같은 서울 날짜와 결과다")
    void ignoresClockZoneForSeoulCalendar() {
        var schedule = schedule(DATES);
        var instant = Instant.parse("2026-08-30T15:00:00Z");
        var utc = RecruitmentAssessment.evaluate(schedule, Clock.fixed(instant, ZoneOffset.UTC));
        var losAngeles = RecruitmentAssessment.evaluate(schedule, Clock.fixed(instant, ZoneId.of("America/Los_Angeles")));

        assertThat(utc).isEqualTo(losAngeles);
        assertThat(utc.evaluatedOnSeoul()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(utc.status()).isEqualTo(OPEN);
    }

    @Test
    @DisplayName("계산 중 서울 날짜가 바뀌어도 시계를 한 번 읽은 시점으로 결과를 유지한다")
    void readsClockOnce() {
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-08-30T14:59:59Z"), Instant.parse("2026-08-30T15:00:00Z"));

        var result = RecruitmentAssessment.evaluate(schedule(DATES), clock);

        assertThat(result.status()).isEqualTo(BEFORE_OPENING);
        assertThat(result.explanation()).contains("아직 시작되지 않았습니다");
        assertThat(result.evaluatedOnSeoul()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(result.status()).isEqualTo(BEFORE_OPENING);
        verify(clock, times(1)).instant();
    }

    @Test
    @DisplayName("역전된 날짜와 실제 순간이 같거나 역전된 시각 범위를 거절한다")
    void rejectsInvalidRanges() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ApplicationPeriod.Dates(
                DATES.endsOnInclusive(), DATES.startsOnInclusive()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ApplicationPeriod.Times(
                TIMES.closesAtExclusive(), TIMES.opensAtInclusive()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ApplicationPeriod.Times(
                TIMES.opensAtInclusive(), TIMES.opensAtInclusive().withZoneSameInstant(ZoneOffset.UTC)));
    }

    private static RecruitmentSchedule schedule(ApplicationPeriod period) {
        return new RecruitmentSchedule("sample-policy", "revision-1", period, "sample-source-revision-1",
                "인공 자료 · 신청기간", Optional.of("화면·서버 점검용 인공 신청기간이며 실제 정책 원문이 아님"));
    }

    private static RecruitmentAssessment evaluate(ApplicationPeriod period, String instant) {
        return RecruitmentAssessment.evaluate(schedule(period), Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
