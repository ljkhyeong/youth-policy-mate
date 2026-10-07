package kr.youthpolicymate.policy.catalog;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static kr.youthpolicymate.eligibility.ConditionOutcome.*;
import static org.assertj.core.api.Assertions.assertThat;
import static kr.youthpolicymate.policy.catalog.PolicyRuleFixtures.*;

class BasicConditionRulesTest {
    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");

    @Test @DisplayName("출생연도 기준·공고일 기준·현재 만 나이를 구분하고 양 끝 날짜를 포함한다")
    void usesEachPolicyReferenceDate() {
        assertThat(exam().compareBirth(LocalDate.parse("1991-01-01"), NOW).age().outcome()).isEqualTo(MET);
        assertThat(exam().compareBirth(LocalDate.parse("1990-12-31"), NOW).age().outcome()).isEqualTo(NOT_MET);
        assertThat(age(K_PASS, LocalDate.parse("2007-09-06"), NOW).outcome()).isEqualTo(MET);
        assertThat(age(K_PASS, LocalDate.parse("2007-09-07"), NOW).outcome()).isEqualTo(NOT_MET);
        assertThat(age(K_PASS, LocalDate.parse("1980-01-01"), NOW).outcome()).isEqualTo(MET);
        for (var date : new String[]{"1986-01-02", "2007-01-01"}) {
            assertThat(age(SEOUL_YOUTH_NETWORK, LocalDate.parse(date)).outcome()).isEqualTo(MET);
        }
        assertThat(age(SEOUL_YOUTH_NETWORK, LocalDate.parse("2007-01-02")).outcome()).isEqualTo(NOT_MET);
        for (var date : new String[]{"1986-01-01", "2007-12-31"}) {
            assertThat(age(MOVING_FEE, LocalDate.parse(date)).outcome()).isEqualTo(MET);
        }
        for (var date : new String[]{"1985-12-31", "2008-01-01"}) {
            assertThat(age(MOVING_FEE, LocalDate.parse(date)).outcome()).isEqualTo(NOT_MET);
        }
    }

    @Test @DisplayName("군복무 정보가 없으면 연령 연장을 미확인으로 남기고 거주·취업 상태를 대신 판정하지 않는다")
    void keepsUnconfirmedExtension() {
        var input = new BasicConditions(LocalDate.parse("1986-01-01"), "강남구", BasicConditions.EmploymentStatus.OTHER);
        var result = comparisons(input, NOW).get(SEOUL_YOUTH_NETWORK);
        assertThat(result.age().outcome()).isEqualTo(UNKNOWN);
        assertThat(result.age().explanation()).contains("연장");
        assertThat(result.priority()).isEqualTo(1);
        assertThat(result.periodNotice()).contains("마감");
    }

    @Test @DisplayName("서울 자정에 검토 연도가 끝나면 연령 비교를 재사용하지 않는다")
    void expiresReviewedComparisons() {
        var input = new BasicConditions(LocalDate.parse("2000-01-01"), "강남구", BasicConditions.EmploymentStatus.NOT_EMPLOYED);
        assertThat(comparisons(input, Instant.parse("2026-12-31T14:59:59Z"))).isNotEmpty();
        assertThat(comparisons(input, Instant.parse("2026-12-31T15:00:00Z"))).isEmpty();
        assertThat(age(K_PASS, LocalDate.parse("2007-09-06"), Instant.parse("2026-09-05T14:59:59Z")).outcome()).isEqualTo(NOT_MET);
        assertThat(age(K_PASS, LocalDate.parse("2007-09-06"), Instant.parse("2026-09-05T15:00:00Z")).outcome()).isEqualTo(MET);
    }
}
