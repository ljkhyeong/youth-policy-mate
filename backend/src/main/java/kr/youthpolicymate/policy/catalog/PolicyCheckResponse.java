package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.eligibility.EligibilityStatus;
import java.time.Instant;
import java.util.List;

@Schema(requiredProperties = {"items", "page", "total", "hasNext", "evaluatedAt"})
public record PolicyCheckResponse(List<Item> items, int page, long total, boolean hasNext, Instant evaluatedAt) {
    @Schema(name = "PolicyCheckItem", requiredProperties = {"policyNumber", "revision", "title", "status", "explanation", "applicationPeriod", "sourceUrl", "collectedAt", "checks", "questionnaireAvailable", "ruleVersion"})
    public record Item(String policyNumber, long revision, String title, EligibilityStatus status, String explanation,
                       String applicationPeriod, String sourceUrl, Instant collectedAt, List<Check> checks,
                       @Schema(description = "현재 원문·적용 시점에 맞는 공통요건 질문 제공 여부. 자격·모집 상태와 무관")
                       boolean questionnaireAvailable,
                       @Schema(description = "적용한 연령 규칙 버전. 검토된 비교 기준이 없으면 빈 문자열") String ruleVersion) {}
    @Schema(name = "PolicyConditionCheck", requiredProperties = {"label", "providedValue", "explanation", "evidence", "outcome"})
    public record Check(String label, String providedValue, String explanation, String evidence, kr.youthpolicymate.eligibility.ConditionAssessment.Outcome outcome) {}
    public enum Sort { AGE_MATCH, RECENT }
}
