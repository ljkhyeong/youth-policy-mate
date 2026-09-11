package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.policy.catalog.PolicyQuestions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PolicyRuleReviews {
    private PolicyRuleReviews() {}
    public enum Status { SOURCE_CHANGED, EXPIRED, MISSING, SCHEDULED, ACTIVE }
    public enum Filter { REVIEW, ALL, SOURCE_CHANGED, EXPIRED, MISSING, SCHEDULED, ACTIVE }
    public enum VersionState { CURRENT, DRAFT, PREVIOUS }

    @Schema(name = "PolicyRuleReviewPage", requiredProperties = {"items", "page", "pageSize", "total", "hasNext", "checkedAt"})
    public record Page(List<Item> items, int page, int pageSize, long total, boolean hasNext, Instant checkedAt) {}

    @Schema(name = "PolicyRuleReviewItem", requiredProperties = {"policyNumber", "title", "revision", "status", "collectedAt", "draftCount"})
    public record Item(String policyNumber, String title, long revision, Status status, Instant collectedAt, long draftCount) {}

    @Schema(name = "PolicyRuleReviewDetail", requiredProperties = {"item", "currentPolicy", "rawPolicyJson", "contentHash", "versions", "checkedAt"})
    public record Detail(Item item, CollectionExceptions.CurrentPolicy currentPolicy, String rawPolicyJson,
                         String contentHash, @Schema(description = "현재 지정 버전 우선, 그 외 최근 20개 버전") List<Version> versions, Instant checkedAt) {}

    @Schema(name = "PolicyRuleReviewVersion", requiredProperties = {"id", "ruleVersion", "state", "sourceMatches", "validFrom", "validUntil",
            "scope", "reason", "sourceUrl", "questions", "remainingChecks", "createdAt", "changeReason", "canPublish", "createdBy", "publishedAt", "publishedBy", "publishReason"})
    public record Version(UUID id, String ruleVersion, VersionState state, boolean sourceMatches,
                          Instant validFrom, Instant validUntil, String scope, String reason, String sourceUrl,
                          List<PolicyQuestions.Question> questions, List<String> remainingChecks, Instant createdAt, String changeReason,
                          boolean canPublish, String createdBy,
                          @Schema(types = {"string", "null"}, format = "date-time") Instant publishedAt,
                          @Schema(types = {"string", "null"}) String publishedBy,
                          @Schema(types = {"string", "null"}) String publishReason) {}
}
