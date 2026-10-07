package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import kr.youthpolicymate.policy.catalog.PolicyContent;
import kr.youthpolicymate.policy.catalog.PolicyCorrectionStore.Field;

import java.time.Instant;
import java.util.UUID;

public final class PolicyCorrections {
    private PolicyCorrections() {}
    public enum Status { ACTIVE, CONFLICT, RELEASED }
    public enum Action { KEEP, USE_SOURCE }

    @Schema(name = "PolicyCorrectionRequest", requiredProperties = {"requestId", "policyNumber", "expectedRevision", "field", "value", "reason"})
    public record Request(@NotNull UUID requestId, @NotNull @Pattern(regexp = "[0-9]{1,100}") String policyNumber,
                          @NotNull @Positive Long expectedRevision, @NotNull Field field,
                          @NotBlank @Size(max = 500) String value, @NotBlank @Size(max = 500) String reason) {}

    @Schema(name = "PolicyCorrectionResolution", requiredProperties = {"requestId", "expectedRevision", "reviewSnapshotId", "action", "reason"})
    public record Resolution(@NotNull UUID requestId, @NotNull @Positive Long expectedRevision,
                             @NotNull @Positive Long reviewSnapshotId, @NotNull Action action,
                             @NotBlank @Size(max = 500) String reason) {}

    @Schema(name = "PolicyCorrectionItem", requiredProperties = {"id", "policyNumber", "field", "value", "reason", "actorId",
            "requestedRevision", "appliedRevision", "createdAt", "status", "sourceValue", "reviewSnapshotId", "reviewValue",
            "currentRevision", "reviewContent", "resolution", "resolvedBy", "resolvedReason", "resolvedRevision", "resolvedAt"})
    public record Item(UUID id, String policyNumber, Field field, String value, String reason, UUID actorId,
                       long requestedRevision, long appliedRevision, Instant createdAt, Status status, String sourceValue,
                       long reviewSnapshotId, String reviewValue, long currentRevision, PolicyContent reviewContent,
                       @Schema(types = {"string", "null"}) String resolution,
                       @Schema(types = {"string", "null"}, format = "uuid") UUID resolvedBy,
                       @Schema(types = {"string", "null"}) String resolvedReason,
                       @Schema(types = {"integer", "null"}, format = "int64") Long resolvedRevision,
                       @Schema(types = {"string", "null"}, format = "date-time") Instant resolvedAt) {}
}
