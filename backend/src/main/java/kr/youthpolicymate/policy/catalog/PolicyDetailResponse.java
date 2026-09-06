package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"policyNumber", "revision", "content", "sourceUrl", "collectedAt", "recruitment"})
public record PolicyDetailResponse(String policyNumber,
                                   @Schema(description = "서비스 내부 적용 개정 번호") long revision,
                                   PolicyContent content, String sourceUrl, Instant collectedAt, PolicyRecruitment recruitment) {}
