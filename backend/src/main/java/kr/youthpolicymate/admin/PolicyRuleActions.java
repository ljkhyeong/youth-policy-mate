package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import kr.youthpolicymate.policy.catalog.PolicyRuleDefinition;

public final class PolicyRuleActions {
    private PolicyRuleActions() {}
    public enum Action { DRAFT, PUBLISH }

    @Schema(name = "PolicyRuleDraftRequest", requiredProperties = {"requestId", "expectedRevision", "definitionJson", "reason"})
    public record Draft(@NotNull UUID requestId, @NotNull @Positive Long expectedRevision,
                        @NotBlank @Size(max = 131072) String definitionJson, @NotBlank @Size(max = 500) String reason) {}

    @Schema(name = "PolicyRulePublishRequest", requiredProperties = {"requestId", "expectedRevision", "expectedRuleVersion", "reason"})
    public record Publish(@NotNull UUID requestId, @NotNull @Positive Long expectedRevision,
                          @NotBlank @Size(max = 80) String expectedRuleVersion, @NotBlank @Size(max = 500) String reason) {}

    @Schema(name = "PolicyRuleActionResult", requiredProperties = {"requestId", "versionId", "policyNumber", "ruleVersion", "action", "actorId", "revision", "reason", "performedAt"})
    public record Result(UUID requestId, UUID versionId, String policyNumber, String ruleVersion, Action action,
                         UUID actorId, long revision, String reason, Instant performedAt) {}

    @Schema(name = "PolicyRuleFile", requiredProperties = {"definition"})
    public record File(PolicyRuleDefinition definition) {}

    static class Invalid extends RuntimeException {}
    static class Changed extends RuntimeException {}
    static class Missing extends RuntimeException {}
}
