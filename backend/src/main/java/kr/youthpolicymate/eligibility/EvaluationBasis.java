package kr.youthpolicymate.eligibility;

import java.time.Instant;
import java.util.Objects;

public record EvaluationBasis(String policyId, String policyRevision, String ruleVersion, Instant evaluatedAt) {

    public EvaluationBasis {
        if (policyId == null || policyId.isBlank()
                || policyRevision == null || policyRevision.isBlank()
                || ruleVersion == null || ruleVersion.isBlank()) {
            throw new IllegalArgumentException("정책 식별자, 적용 개정과 규칙 버전이 필요합니다.");
        }
        evaluatedAt = Objects.requireNonNull(evaluatedAt, "판정 시점이 필요합니다.");
    }
}
