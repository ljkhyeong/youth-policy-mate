package kr.youthpolicymate.policy.catalog;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static kr.youthpolicymate.policy.RecruitmentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class PolicyRecruitmentTest {
    private final ObjectNode raw = JsonMapper.builder().build().createObjectNode();

    @Test @DisplayName("날짜형 모집은 서울 자정 경계를 사용하고 마감 당일을 접수 기간으로 유지한다")
    void keepsDateOnlyPeriod() {
        raw.put("aplyPrdSeCd", "0057001").put("aplyYmd", "20260906 ~ 20260907");
        assertThat(at("2026-09-05T14:59:59Z").status()).isEqualTo(BEFORE_OPENING);
        assertThat(at("2026-09-05T15:00:00Z").status()).isEqualTo(OPEN);
        assertThat(at("2026-09-07T14:59:59Z").status()).isEqualTo(OPEN);
        assertThat(at("2026-09-07T15:00:00Z").status()).isEqualTo(CLOSED);
        assertThat(at("2026-09-06T00:00:00Z").explanation()).contains("정확한 접수 시각은 확인되지 않았으므로");
        assertThat(PolicyDeadline.from(raw).date()).hasToString("2026-09-07");
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
        assertThat(PolicyDeadline.from(raw).date()).isNull();
    }

    @Test @DisplayName("잘못된 날짜·복수 회차·선착순·소진·본문의 다른 날짜는 접수 상태와 알림 모두 보류한다")
    void sharesUnresolvedPeriodsWithReminders() {
        raw.put("aplyPrdSeCd", "0057001");
        for (var period : new String[]{"20260201 ~ 20260229", "20260908 ~ 20260907", "20260901 ~ 20260902, 20260906 ~ 20260907", "미정"}) {
            raw.put("aplyYmd", period);
            assertThat(at("2026-09-06T00:00:00Z").status()).isEqualTo(UNKNOWN);
            assertThat(PolicyDeadline.from(raw).date()).isNull();
        }
        raw.put("aplyYmd", "20280228 ~ 20280229");
        assertThat(at("2028-02-29T00:00:00Z").status()).isEqualTo(OPEN);
        for (var description : new String[]{"선착순 신청", "예산 소진까지", "회차별 안내", "다른 마감: 2028.2.27"}) {
            raw.put("etcMttrCn", description);
            assertThat(at("2028-02-29T00:00:00Z").status()).isEqualTo(UNKNOWN);
            assertThat(PolicyDeadline.from(raw).date()).isNull();
        }
    }

    @Test @DisplayName("검토된 공고의 정확한 마감 시각만 적용하고 원문 변경 시 이전 시각을 중단한다")
    void guardsReviewedClosingTimes() {
        var beforeClose = Instant.parse("2026-04-14T08:59:59Z");
        var close = Instant.parse("2026-04-14T09:00:00Z");
        assertThat(PolicyRecruitment.from(MovingFeeRules.NUMBER, 1, MovingFeeRules.CONTENT_HASH, raw, beforeClose).status()).isEqualTo(OPEN);
        assertThat(PolicyRecruitment.from(MovingFeeRules.NUMBER, 1, MovingFeeRules.CONTENT_HASH, raw, close).status()).isEqualTo(CLOSED);
        assertThat(PolicyRecruitment.from(MovingFeeRules.NUMBER, 2, "changed", raw, close).status()).isEqualTo(UNKNOWN);
        assertThat(PolicyRecruitment.from(SeoulYouthNetworkRules.NUMBER, 1, SeoulYouthNetworkRules.CONTENT_HASH, raw, Instant.parse("2026-05-29T07:59:59Z")).status()).isEqualTo(OPEN);
        assertThat(PolicyRecruitment.from(SeoulYouthNetworkRules.NUMBER, 1, SeoulYouthNetworkRules.CONTENT_HASH, raw, Instant.parse("2026-05-29T08:00:00Z")).status()).isEqualTo(CLOSED);
    }
    private PolicyRecruitment at(String time) { return PolicyRecruitment.from("123", 1, "hash", raw, Instant.parse(time)); }
}
