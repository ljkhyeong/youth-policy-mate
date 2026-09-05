package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"policyNumber", "title", "description", "category", "organization", "applicationPeriod", "collectedAt"})
public record PolicySummary(String policyNumber, String title, String description, String category,
                            String organization, String applicationPeriod, Instant collectedAt) {}
