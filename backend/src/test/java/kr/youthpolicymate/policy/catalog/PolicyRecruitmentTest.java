package kr.youthpolicymate.policy.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.stream.Stream;
import kr.youthpolicymate.policy.ApplicationPeriod;
import kr.youthpolicymate.policy.RecruitmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static kr.youthpolicymate.policy.RecruitmentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static kr.youthpolicymate.policy.catalog.PolicyRuleFixtures.*;

class PolicyRecruitmentTest {
    private static final ApplicationPeriod.Dates DATES = new ApplicationPeriod.Dates(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 2));
    private static final ApplicationPeriod.Times TIMES = new ApplicationPeriod.Times(
            ZonedDateTime.parse("2026-08-31T09:00:00+09:00[Asia/Seoul]"), ZonedDateTime.parse("2026-08-31T18:00:00+09:00[Asia/Seoul]"));
    private final ObjectNode raw = JsonMapper.builder().build().createObjectNode();

    @ParameterizedTest(name = "시각 {0} → {1}")
    @CsvSource({
            "2026-08-30T14:59:59.999999999Z, BEFORE_OPENING",
            "2026-08-30T15:00:00Z, OPEN",
            "2026-09-02T14:59:59.999999999Z, OPEN",
            "2026-09-02T15:00:00Z, CLOSED"
    })
    @DisplayName("날짜형 신청기간은 서울 자정 경계와 양 끝 날짜 포함을 적용한다")
    void evaluatesSeoulDateBoundaries(String instant, RecruitmentStatus expected) {
        var result = PolicyRecruitment.of(DATES, Instant.parse(instant));
        assertThat(result.status()).isEqualTo(expected);
        assertThat(result.deadlineOnSeoul()).isEqualTo(DATES.endsOnInclusive());
        if (expected == OPEN) assertThat(result.explanation()).contains("서울 기준", "정확한 접수 시각은 공식 신청처에서 확인");
    }

    @ParameterizedTest(name = "하루 모집 {0} → {1}")
    @CsvSource({"2026-08-30T14:59:59Z, BEFORE_OPENING", "2026-08-30T15:00:00Z, OPEN", "2026-08-31T15:00:00Z, CLOSED"})
    @DisplayName("시작일과 종료일이 같은 날짜형 모집도 하루 기간으로 보존한다")
    void permitsSingleDayDatePeriod(String instant, RecruitmentStatus expected) {
        var date = LocalDate.of(2026, 8, 31);
        assertThat(PolicyRecruitment.of(new ApplicationPeriod.Dates(date, date), Instant.parse(instant)).status()).isEqualTo(expected);
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
        var result = PolicyRecruitment.of(TIMES, Instant.parse(instant));
        assertThat(result.status()).isEqualTo(expected);
        if (expected == CLOSED) assertThat(result.explanation()).contains("마감 시각이 됐거나 지났어요");
    }

    @ParameterizedTest(name = "원문 마감 {0} → 서울 날짜 {1}")
    @CsvSource({
            "2026-09-02T15:30:00Z, 2026-09-03",
            "2026-09-02T15:30:00-07:00[America/Los_Angeles], 2026-09-03",
            "2026-09-03T00:00:00+09:00[Asia/Seoul], 2026-09-03"
    })
    @DisplayName("시각형은 원문 시간대와 관계없이 마감 순간의 서울 날짜를 마감일로 제공한다")
    void projectsExactDeadlineToSeoul(String end, String expectedDate) {
        var close = ZonedDateTime.parse(end);
        var result = PolicyRecruitment.of(new ApplicationPeriod.Times(close.minusDays(10), close), Instant.parse("2026-08-25T00:00:00Z"));
        assertThat(result.deadlineOnSeoul()).isEqualTo(LocalDate.parse(expectedDate));
    }

    static Stream<ApplicationPeriod> noDeadlinePeriods() {
        return Stream.of(new ApplicationPeriod.Rolling(), new ApplicationPeriod.Closed());
    }

    @ParameterizedTest
    @MethodSource("noDeadlinePeriods")
    @DisplayName("상시·명시적 마감은 날짜 경과로 바꾸지 않고 마감일을 만들지 않는다")
    void keepsNonCalendarRecruitment(ApplicationPeriod period) {
        RecruitmentStatus expected = period instanceof ApplicationPeriod.Rolling ? ROLLING : CLOSED;
        var before = PolicyRecruitment.of(period, Instant.parse("2026-08-30T15:00:00Z"));
        var after = PolicyRecruitment.of(period, Instant.parse("2027-08-30T15:00:00Z"));
        assertThat(before.status()).isEqualTo(expected);
        assertThat(after.status()).isEqualTo(expected);
        assertThat(after.explanation()).isEqualTo(before.explanation());
        assertThat(after.deadlineOnSeoul()).isNull();
        assertThat(after.daysUntilDeadline()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"신청기간이 없고 사업 종료일만 있습니다.", "신청기간과 마감 상태 코드가 서로 다릅니다.", "마감 시각의 시간대를 확인하지 못했습니다."})
    @DisplayName("누락·충돌·미지원 기간은 이유를 설명으로 보존하고 날짜를 보충하지 않는다")
    void keepsUnresolvedReason(String reason) {
        var result = PolicyRecruitment.of(new ApplicationPeriod.Unresolved(reason), Instant.parse("2026-08-31T00:00:00Z"));
        assertThat(result.status()).isEqualTo(UNKNOWN);
        assertThat(result.explanation()).isEqualTo(reason);
        assertThat(result.deadlineOnSeoul()).isNull();
        assertThat(PolicyDeadline.from(result).note()).endsWith(reason);
    }

    @Test @DisplayName("역전된 날짜와 실제 순간이 같거나 역전된 시각 범위를 거절한다")
    void rejectsInvalidRanges() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ApplicationPeriod.Dates(DATES.endsOnInclusive(), DATES.startsOnInclusive()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ApplicationPeriod.Times(TIMES.closesAtExclusive(), TIMES.opensAtInclusive()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ApplicationPeriod.Times(
                TIMES.opensAtInclusive(), TIMES.opensAtInclusive().withZoneSameInstant(ZoneOffset.UTC)));
    }

    @Test @DisplayName("날짜형 모집은 서울 자정 경계를 사용하고 마감 당일을 접수 기간으로 유지한다")
    void keepsDateOnlyPeriod() {
        raw.put("aplyPrdSeCd", "0057001").put("aplyYmd", "20260906 ~ 20260907");
        assertThat(at("2026-09-05T14:59:59Z").status()).isEqualTo(BEFORE_OPENING);
        assertThat(at("2026-09-05T15:00:00Z").status()).isEqualTo(OPEN);
        assertThat(at("2026-09-07T14:59:59Z").status()).isEqualTo(OPEN);
        assertThat(at("2026-09-07T15:00:00Z").status()).isEqualTo(CLOSED);
        assertThat(at("2026-09-06T00:00:00Z").explanation()).contains("정확한 접수 시각은 공식 신청처에서 확인");
        assertThat(PolicyDeadline.from(at("2026-09-07T15:00:00Z")).date()).hasToString("2026-09-07");
    }

    @Test @DisplayName("상시·명시적 마감·미확인을 구분하고 코드·날짜 충돌에는 접수 상태를 확정하지 않는다")
    void distinguishesNonDatePeriods() {
        raw.put("aplyPrdSeCd", "0057002");
        assertThat(at("2026-09-06T00:00:00Z").status()).isEqualTo(ROLLING);
        raw.put("aplyPrdSeCd", "0057003");
        assertThat(at("2026-09-06T00:00:00Z").status()).isEqualTo(CLOSED);
        raw.put("aplyPrdSeCd", "other");
        assertThat(at("2026-09-06T00:00:00Z").status()).isEqualTo(UNKNOWN);
        raw.put("aplyPrdSeCd", "0057002").put("aplyYmd", "20260906 ~ 20260907");
        assertThat(at("2026-09-06T00:00:00Z").status()).isEqualTo(UNKNOWN);
        assertThat(PolicyDeadline.from(at("2026-09-06T00:00:00Z")).date()).isNull();
    }

    @Test @DisplayName("마감 날짜가 없으면 상시·접수 종료·미확인 사유를 구분해 안내한다")
    void explainsMissingDeadline() {
        for (var code : new String[]{"0057002", "0057003", "unknown", ""}) {
            raw.put("aplyPrdSeCd", code);
            var deadline = PolicyDeadline.from(at("2026-09-06T00:00:00Z"));
            assertThat(deadline.date()).isNull();
            assertThat(deadline.note()).contains(switch (code) {
                case "0057002" -> "상시 접수";
                case "0057003" -> "접수가 끝났어요";
                default -> "마감일을 확인할 수 없어";
            });
        }
    }

    @Test @DisplayName("잘못된 날짜·복수 회차·선착순·소진·본문의 다른 날짜는 접수 상태와 알림 모두 보류한다")
    void sharesUnresolvedPeriodsWithReminders() {
        raw.put("aplyPrdSeCd", "0057001");
        for (var period : new String[]{"20260201 ~ 20260229", "20260908 ~ 20260907", "20260901 ~ 20260902, 20260906 ~ 20260907", "미정"}) {
            raw.put("aplyYmd", period);
            assertThat(at("2026-09-06T00:00:00Z").status()).isEqualTo(UNKNOWN);
            assertThat(PolicyDeadline.from(at("2026-09-06T00:00:00Z")).date()).isNull();
        }
        raw.put("aplyYmd", "20280228 ~ 20280229");
        assertThat(at("2028-02-29T00:00:00Z").status()).isEqualTo(OPEN);
        for (var description : new String[]{"선착순 신청", "예산 소진까지", "회차별 안내", "다른 마감: 2028.2.27"}) {
            raw.put("etcMttrCn", description);
            assertThat(at("2028-02-29T00:00:00Z").status()).isEqualTo(UNKNOWN);
            assertThat(PolicyDeadline.from(at("2028-02-29T00:00:00Z")).date()).isNull();
        }
    }

    @Test @DisplayName("검토된 공고의 정확한 마감 시각만 적용하고 원문 변경 시 이전 시각을 중단한다")
    void guardsReviewedClosingTimes() {
        var beforeClose = Instant.parse("2026-04-14T08:59:59Z");
        var close = Instant.parse("2026-04-14T09:00:00Z");
        assertThat(PolicyRecruitment.from(MOVING_FEE, hash(MOVING_FEE), raw, beforeClose).status()).isEqualTo(OPEN);
        assertThat(PolicyRecruitment.from(MOVING_FEE, hash(MOVING_FEE), raw, close).status()).isEqualTo(CLOSED);
        assertThat(PolicyRecruitment.from(MOVING_FEE, "changed", raw, close).status()).isEqualTo(UNKNOWN);
        assertThat(PolicyRecruitment.from(SEOUL_YOUTH_NETWORK, hash(SEOUL_YOUTH_NETWORK), raw, Instant.parse("2026-05-29T07:59:59Z")).status()).isEqualTo(OPEN);
        assertThat(PolicyRecruitment.from(SEOUL_YOUTH_NETWORK, hash(SEOUL_YOUTH_NETWORK), raw, Instant.parse("2026-05-29T08:00:00Z")).status()).isEqualTo(CLOSED);
        // 저장 정책 마감일도 화면과 같은 보정 마감일을 쓴다.
        assertThat(PolicyDeadline.from(PolicyRecruitment.from(MOVING_FEE, hash(MOVING_FEE), raw, close)).date()).hasToString("2026-04-14");
    }

    @Test @DisplayName("저장 정책 마감과 같은 마감일과 서울 날짜 기준 남은 일수를 제공하고 마감일이 없으면 비운다")
    void providesDeadlineAndDaysLeft() {
        raw.put("aplyPrdSeCd", "0057001").put("aplyYmd", "20260906 ~ 20260907");
        assertThat(at("2026-09-05T15:00:00Z").deadlineOnSeoul()).hasToString("2026-09-07");
        assertThat(at("2026-09-05T15:00:00Z").daysUntilDeadline()).isOne();
        assertThat(at("2026-09-07T14:59:59Z").daysUntilDeadline()).isZero();
        assertThat(at("2026-09-07T15:00:00Z").daysUntilDeadline()).isEqualTo(-1);
        assertThat(PolicyRecruitment.from(MOVING_FEE, hash(MOVING_FEE), raw, Instant.parse("2026-04-13T15:00:00Z")))
                .satisfies(moving -> {
                    assertThat(moving.deadlineOnSeoul()).hasToString("2026-04-14");
                    assertThat(moving.daysUntilDeadline()).isZero();
                });
        raw.put("aplyPrdSeCd", "0057002").remove("aplyYmd");
        assertThat(at("2026-09-06T00:00:00Z").deadlineOnSeoul()).isNull();
        assertThat(at("2026-09-06T00:00:00Z").daysUntilDeadline()).isNull();
    }

    private PolicyRecruitment at(String time) { return PolicyRecruitment.from("123", "hash", raw, Instant.parse(time)); }
}
