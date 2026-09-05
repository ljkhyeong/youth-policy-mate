package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"title", "description", "category", "organization", "applicationPeriod",
        "sections", "links", "regionCodes", "sourceModifiedAtText"})
public record PolicyContent(String title, String description, String category, String organization,
                            String applicationPeriod, List<TextSection> sections, List<OfficialLink> links,
                            List<String> regionCodes,
                            @Schema(description = "시간대가 확정되지 않은 원천 수정 일시 원문") String sourceModifiedAtText) {
    public PolicyContent {
        sections = List.copyOf(sections);
        links = List.copyOf(links);
        regionCodes = List.copyOf(regionCodes);
    }

    @Schema(name = "PolicyTextSection", requiredProperties = {"title", "text"})
    public record TextSection(String title, String text) {}

    @Schema(name = "PolicyOfficialLink", requiredProperties = {"label", "url"})
    public record OfficialLink(String label, String url) {}
}
