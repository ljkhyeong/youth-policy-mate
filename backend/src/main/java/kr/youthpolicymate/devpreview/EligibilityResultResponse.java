package kr.youthpolicymate.devpreview;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.eligibility.ConditionAssessment;
import kr.youthpolicymate.eligibility.EligibilityDecision;
import kr.youthpolicymate.eligibility.EligibilityStatus;
import kr.youthpolicymate.eligibility.PolicyReview;
import kr.youthpolicymate.eligibility.SourceEvidence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(requiredProperties = {"status", "explanation", "basis", "policyReview", "conditions"})
public record EligibilityResultResponse(
        EligibilityStatus status, String explanation, BasisResponse basis,
        PolicyReviewResponse policyReview, List<ConditionResponse> conditions
) {
    static EligibilityResultResponse from(EligibilityDecision decision) {
        var basis = decision.basis();
        var review = decision.policyReview();
        return new EligibilityResultResponse(decision.status(), decision.explanation(),
                new BasisResponse(basis.policyId(), basis.policyRevision(), basis.ruleVersion(), basis.evaluatedAt()),
                new PolicyReviewResponse(review.completion(), review.pendingIssues().stream()
                        .map(issue -> new PendingIssueResponse(issue.explanation(), EvidenceResponse.from(issue.evidence()))).toList()),
                decision.conditions().stream().map(ConditionResponse::from).toList());
    }

    @Schema(name = "EligibilityBasis", requiredProperties = {"policyId", "policyRevision", "ruleVersion", "evaluatedAt"})
    public record BasisResponse(String policyId, String policyRevision, String ruleVersion,
            @Schema(description = "판정 계산 시점. 조건별 기준일이나 원문 수집 시각이 아님") Instant evaluatedAt) {}

    @Schema(name = "EligibilityPolicyReview", requiredProperties = {"completion", "pendingIssues"})
    public record PolicyReviewResponse(PolicyReview.Completion completion, List<PendingIssueResponse> pendingIssues) {}

    @Schema(name = "EligibilityPendingIssue", requiredProperties = {"explanation", "evidence"})
    public record PendingIssueResponse(String explanation, EvidenceResponse evidence) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "EligibilityEvidence", requiredProperties = {"sourceReference", "location", "excerpt"})
    public record EvidenceResponse(String sourceReference, String location,
            @Schema(types = {"string", "null"}, description = "기록된 원문 발췌. 없으면 null") String excerpt) {
        static EvidenceResponse from(SourceEvidence evidence) {
            return new EvidenceResponse(evidence.sourceReference(), evidence.location(), evidence.excerpt().orElse(null));
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(name = "EligibilityCondition", requiredProperties = {"conditionId", "appliedCondition", "comparedValue",
            "referenceDate", "outcome", "uncertainty", "explanation", "evidence"})
    public record ConditionResponse(
            String conditionId, String appliedCondition,
            @Schema(types = {"string", "null"}, description = "비교에 사용한 값. 없다고 0이나 미입력으로 단정하지 않는다") String comparedValue,
            @Schema(types = {"string", "null"}, format = "date", description = "조건별 정책 기준일. 없으면 null") LocalDate referenceDate,
            ConditionAssessment.Outcome outcome,
            @Schema(types = {"string", "null"}, description = "UNKNOWN 항목의 미확인 원인. MET·NOT_MET이면 null") ConditionAssessment.Uncertainty uncertainty,
            String explanation, EvidenceResponse evidence
    ) {
        static ConditionResponse from(ConditionAssessment assessment) {
            return new ConditionResponse(assessment.conditionId(), assessment.appliedCondition(),
                    assessment.comparedValue().orElse(null), assessment.referenceDate().orElse(null),
                    assessment.outcome(), assessment.uncertainty().orElse(null), assessment.explanation(),
                    EvidenceResponse.from(assessment.evidence()));
        }
    }
}
