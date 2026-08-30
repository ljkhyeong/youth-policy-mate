package kr.youthpolicymate.eligibility;

import java.time.LocalDate;
import java.util.Objects;

// 주된 취업상태가 아니라 원문에서 정의와 기준일을 확인한 단일 사실이다.
public record EmploymentFact(
        String policyId,
        String policyRevision,
        String conditionId,
        String definition,
        LocalDate referenceDate
) {

    public EmploymentFact {
        if (policyId == null || policyId.isBlank()
                || policyRevision == null || policyRevision.isBlank()
                || conditionId == null || conditionId.isBlank()
                || definition == null || definition.isBlank()) {
            throw new IllegalArgumentException("정책 식별자·개정, 취업 조건 식별자와 확인한 사실의 정의가 필요합니다.");
        }
        referenceDate = Objects.requireNonNull(referenceDate, "확인한 취업 기준일이 필요합니다.");
    }
}
