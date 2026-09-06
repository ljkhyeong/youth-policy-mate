package kr.youthpolicymate.policy.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(requiredProperties = {"date", "note"})
public record PolicyDeadline(@Schema(types = {"string", "null"}, format = "date") LocalDate date, String note) {
    public static PolicyDeadline from(JsonNode raw) {
        return switch (PolicyApplicationPeriod.parse(raw)) {
            case kr.youthpolicymate.policy.ApplicationPeriod.Dates dates -> new PolicyDeadline(dates.endsOnInclusive(),
                    "온통청년에 안내된 마감일이에요. 마감 시간은 공식 안내를 확인해주세요.");
            case kr.youthpolicymate.policy.ApplicationPeriod.Rolling ignored -> new PolicyDeadline(null, "상시 접수라 마감 알림을 제공하지 않아요.");
            case kr.youthpolicymate.policy.ApplicationPeriod.Closed ignored -> new PolicyDeadline(null, "온통청년 안내 기준으로 접수가 끝났어요.");
            case kr.youthpolicymate.policy.ApplicationPeriod.Unresolved unresolved -> new PolicyDeadline(null,
                    "마감일을 확인할 수 없어 알림을 예약하지 못했어요. " + unresolved.reason());
            default -> throw new IllegalStateException("마감 날짜 추출에 지원하지 않는 신청기간입니다.");
        };
    }
}
