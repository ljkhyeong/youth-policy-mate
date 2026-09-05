package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"items", "page", "pageSize", "total", "hasNext"})
public record PolicyListResponse(List<PolicySummary> items,
                                 @Schema(description = "1부터 시작하는 페이지") int page,
                                 int pageSize, long total, boolean hasNext) {}
