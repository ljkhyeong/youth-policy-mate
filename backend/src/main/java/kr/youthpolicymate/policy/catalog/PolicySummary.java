package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"policyNumber", "title", "description", "category", "organization", "applicationPeriod", "collectedAt", "questionnaireAvailable", "recruitment"})
public record PolicySummary(String policyNumber, String title, String description, String category,
                            String organization, String applicationPeriod, Instant collectedAt,
                            @Schema(description = "현재 원문·적용 시점에 맞는 공통요건 질문 제공 여부. 자격·모집 상태와 무관")
                            boolean questionnaireAvailable, PolicyRecruitment recruitment) {}
