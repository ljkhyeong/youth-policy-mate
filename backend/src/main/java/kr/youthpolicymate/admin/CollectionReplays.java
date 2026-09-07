package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CollectionReplays {
    private CollectionReplays() {}

    public enum Outcome { APPLIED, UNCHANGED, REPLAYED, STALE, INVALID_ITEM }

    @Schema(name = "CollectionReplayRequest", requiredProperties = {"requestId", "expectedAttempts", "reason"})
    public record Request(@NotNull UUID requestId, @NotNull @Min(0) Integer expectedAttempts,
                          @NotBlank @Size(max = 500) String reason) {}

    @Schema(name = "CollectionReplayResult", requiredProperties = {"requestId", "runId", "itemIndex", "actorId", "reason",
            "expectedAttempts", "attempt", "outcome", "policyNumber", "policyRevision", "processedAt"})
    public record Result(UUID requestId, UUID runId, int itemIndex, UUID actorId, String reason, int expectedAttempts,
                         int attempt, Outcome outcome,
                         @Schema(types = {"string", "null"}) String policyNumber,
                         @Schema(types = {"integer", "null"}, format = "int64", description = "처리 후 개정. 반영하지 않은 결과는 null") Long policyRevision,
                         Instant processedAt) {}

    @Schema(name = "CollectionReplayPage", requiredProperties = {"items", "page", "pageSize", "hasNext"})
    public record Page(List<Result> items, int page, int pageSize, boolean hasNext) {}

    public static class Changed extends RuntimeException {}
}
