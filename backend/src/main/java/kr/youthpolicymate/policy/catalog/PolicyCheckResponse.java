package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.eligibility.EligibilityStatus;
import java.time.Instant;
import java.util.List;

@Schema(requiredProperties = {"items", "page", "total", "hasNext", "evaluatedAt"})
public record PolicyCheckResponse(List<Item> items, int page, long total, boolean hasNext, Instant evaluatedAt) {
    @Schema(name = "PolicyCheckItem", requiredProperties = {"policyNumber", "revision", "title", "status", "explanation", "applicationPeriod", "sourceUrl", "collectedAt", "checks"})
    public record Item(String policyNumber, long revision, String title, EligibilityStatus status, String explanation,
                       String applicationPeriod, String sourceUrl, Instant collectedAt, List<Check> checks) {}
    @Schema(name = "PolicyConditionCheck", requiredProperties = {"label", "providedValue", "explanation", "evidence"})
    public record Check(String label, String providedValue, String explanation, String evidence) {}
}
