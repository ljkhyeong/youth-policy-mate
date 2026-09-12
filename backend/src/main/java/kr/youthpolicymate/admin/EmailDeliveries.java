package kr.youthpolicymate.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.member.ResendEmailLookup;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EmailDeliveries {
    private EmailDeliveries() {}
    public enum State { PENDING, SENDING, SENT, DELIVERED, DELAYED, FAILED, UNKNOWN, BOUNCED, COMPLAINED, SUPPRESSED, CANCELED }
    public enum Kind { VERIFICATION, POLICY }

    @Schema(name = "AdminEmailProviderStatus", requiredProperties = {"event", "checkedAt"})
    public record ProviderStatus(ResendEmailLookup.Event event, Instant checkedAt) {}

    @Schema(name = "AdminEmailDeliverySummary", requiredProperties = {"total", "failed", "unknown"})
    public record Summary(long total, long failed, long unknown) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "AdminEmailDelivery", requiredProperties = {"id", "kind", "state", "provider", "providerMessageId", "createdAt", "startedAt", "finishedAt", "providerEventAt"})
    public record Item(UUID id, Kind kind, State state,
                       @Schema(types = {"string", "null"}, allowableValues = {"smtp", "resend"}) String provider,
                       @Schema(types = {"string", "null"}, format = "uuid") UUID providerMessageId,
                       Instant createdAt,
                       @Schema(types = {"string", "null"}, format = "date-time") Instant startedAt,
                       @Schema(types = {"string", "null"}, format = "date-time") Instant finishedAt,
                       @Schema(types = {"string", "null"}, format = "date-time") Instant providerEventAt) {}

    @Schema(name = "AdminEmailDeliveryPage", requiredProperties = {"items", "page", "pageSize", "total", "hasNext", "since", "checkedAt", "sendingEnabled", "provider", "summary"})
    public record Page(List<Item> items, int page, int pageSize, long total, boolean hasNext, Instant since, Instant checkedAt,
                       boolean sendingEnabled, @Schema(allowableValues = {"smtp", "resend"}) String provider, Summary summary) {}
}
