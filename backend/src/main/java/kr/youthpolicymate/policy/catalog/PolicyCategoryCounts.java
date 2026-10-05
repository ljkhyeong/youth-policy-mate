package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"items", "total"})
public record PolicyCategoryCounts(@Schema(description = "다섯 분야를 항상 같은 순서로 제공. 정책이 없으면 0. 복수 분류 정책은 분야마다 세므로 합이 total보다 클 수 있음") List<Item> items,
                                   @Schema(description = "전체 공개 정책 수. 다섯 분야에 맞지 않는 정책도 포함") long total) {
    @Schema(name = "PolicyCategoryCount", requiredProperties = {"category", "count"})
    public record Item(PolicyCategory category, long count) {}
}
