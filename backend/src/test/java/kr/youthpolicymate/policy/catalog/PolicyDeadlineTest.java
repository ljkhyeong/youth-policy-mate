package kr.youthpolicymate.policy.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class PolicyDeadlineTest {
    @Test
    @DisplayName("확인된 단일 구간의 종료는 시각을 만들지 않은 날짜로 제공한다")
    void preservesDate() {
        var raw = JsonMapper.builder().build().createObjectNode().put("aplyPrdSeCd", "0057001").put("aplyYmd", "20260901 ~ 20260912");
        assertThat(PolicyDeadline.from(raw).date()).hasToString("2026-09-12");
    }

    @Test
    @DisplayName("상시·회차·소진·충돌 날짜에는 마감 예약 날짜를 만들지 않는다")
    void holdsUnclearDeadline() {
        var raw = JsonMapper.builder().build().createObjectNode().put("aplyPrdSeCd", "0057001").put("aplyYmd", "20260901 ~ 20260912");
        for (var text : new String[]{"선착순 접수", "예산 소진까지", "회차별 별도 신청", "신청 마감은 2026.09.10입니다"}) {
            raw.put("plcyAplyMthdCn", text);
            assertThat(PolicyDeadline.from(raw).date()).isNull();
        }
        raw.put("aplyPrdSeCd", "0057002").put("plcyAplyMthdCn", "");
        assertThat(PolicyDeadline.from(raw).date()).isNull();
        raw.put("aplyPrdSeCd", "0057001").put("aplyYmd", "20260901 ~ 20260230");
        assertThat(PolicyDeadline.from(raw).date()).isNull();
    }
}
