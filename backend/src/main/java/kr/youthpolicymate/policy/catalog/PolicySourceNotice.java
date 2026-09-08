package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"title", "description", "sourceLabel", "sourceUrl"})
public record PolicySourceNotice(String title, String description, String sourceLabel, String sourceUrl) {
    static final String SAVINGS_NUMBER = "20260430005400113009";
    static final String SAVINGS_CONTENT_HASH = "f3709a60376cdaf861ee232c1fc411292f2d3bdcb28c807ca87190a19698733c";

    static List<PolicySourceNotice> forContent(String number, String contentHash) {
        if (!SAVINGS_NUMBER.equals(number) || !SAVINGS_CONTENT_HASH.equals(contentHash)) return List.of();
        return List.of(new PolicySourceNotice(
                "소득 기준 확인 필요",
                "수집된 안내의 가구 소득인정액 기준이 중위소득 50% 이하와 100% 이하로 서로 달라요. "
                        + "복지로의 2026년 모집 공고는 50% 이하로 안내해요. 자세한 신청 기준은 해당 공고를 확인해주세요.",
                "복지로 2026년 모집 공고",
                "https://www.bokjiro.go.kr/ssis-tbu/cms/pc/customer/notice/1309680_1141.html"));
    }
}
