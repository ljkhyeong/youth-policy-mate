package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"title", "description", "sourceLabel", "sourceUrl"})
public record PolicySourceNotice(String title, String description, String sourceLabel, String sourceUrl) {
    static List<PolicySourceNotice> forContent(String number, String contentHash) {
        if (!YouthTomorrowSavingsRules.NUMBER.equals(number) || !YouthTomorrowSavingsRules.CONTENT_HASH.equals(contentHash)) return List.of();
        return List.of(new PolicySourceNotice(
                "소득 기준 확인 필요",
                "수집된 안내의 가구 소득인정액 기준이 중위소득 50% 이하와 100% 이하로 서로 달라요. "
                        + "복지로의 2026년 모집 공고는 50% 이하로 안내해요. 자세한 신청 기준은 해당 공고를 확인해주세요.",
                "복지로 2026년 모집 공고",
                "https://www.bokjiro.go.kr/ssis-tbu/cms/pc/customer/notice/1309680_1141.html"),
                new PolicySourceNotice("출생일 기준 확인 필요",
                        "수집 안내와 2026년 사업 지침의 출생일 기준이 달라요. 지침에 따른 5월 모집 대상은 1986.5.1.~2011.5.31. 출생자예요.",
                        "2026년 사업 지침 (61쪽)", YouthTomorrowSavingsRules.SOURCE));
    }
}
