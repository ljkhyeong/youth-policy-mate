package kr.youthpolicymate.member;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.policy.catalog.BasicConditions;
import kr.youthpolicymate.policy.catalog.PolicyDeadline;
import kr.youthpolicymate.policy.catalog.PolicyRecruitment;
import java.time.Instant;
import java.util.List;

public final class MemberResponses {
    @Schema(name = "MemberSession", requiredProperties = {"authenticated", "displayName", "suggestedBirthDate", "csrfToken", "providers"})
    public record Session(boolean authenticated, String displayName, String suggestedBirthDate, String csrfToken, List<Provider> providers) {}
    @Schema(name = "LoginProvider", requiredProperties = {"id", "name", "url"})
    public record Provider(String id, String name, String url) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "MemberConditions", requiredProperties = {"conditions"})
    public record Conditions(@Schema(types = {"object", "null"}) BasicConditions conditions) {}
    @Schema(name = "SavedPolicy", requiredProperties = {"policyNumber", "title", "savedRevision", "currentRevision", "deadline", "savedAt", "applicationPeriod", "recruitment"})
    public record Saved(String policyNumber, String title, long savedRevision, long currentRevision,
                        PolicyDeadline deadline, Instant savedAt, String applicationPeriod, PolicyRecruitment recruitment) {}
    @Schema(name = "SavedPolicyList", requiredProperties = {"items"})
    public record SavedList(List<Saved> items) {}
    @Schema(name = "MemberNotification", requiredProperties = {"id", "policyNumber", "title", "message", "createdAt", "read"})
    public record Notification(String id, String policyNumber, String title, String message, Instant createdAt, boolean read) {}
    @Schema(name = "MemberNotificationList", requiredProperties = {"items"})
    public record Notifications(List<Notification> items) {}
}
