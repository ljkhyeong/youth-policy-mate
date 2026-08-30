package kr.youthpolicymate.eligibility;

import java.util.List;
import java.util.Objects;

import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.NOT_MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN;
import static kr.youthpolicymate.eligibility.EligibilityStatus.ELIGIBLE;
import static kr.youthpolicymate.eligibility.EligibilityStatus.INELIGIBLE;
import static kr.youthpolicymate.eligibility.EligibilityStatus.NEEDS_REVIEW;

public record EligibilityDecision(
        EvaluationBasis basis,
        PolicyReview policyReview,
        List<ConditionAssessment> conditions
) {

    public EligibilityDecision {
        basis = Objects.requireNonNull(basis, "판정에 적용한 정책과 규칙 정보가 필요합니다.");
        policyReview = Objects.requireNonNull(policyReview, "필수 조건과 예외의 검토 상태가 필요합니다.");
        conditions = List.copyOf(conditions);
        if (conditions.stream().map(ConditionAssessment::conditionId).distinct().count() != conditions.size()) {
            throw new IllegalArgumentException("같은 조건을 중복해서 판정 결과에 넣을 수 없습니다.");
        }
    }

    public EligibilityStatus status() {
        // 검토하지 못한 예외가 불충족 판단을 뒤집을 수 있으므로 먼저 확인한다.
        if (policyReview.completion() == PolicyReview.Completion.INCOMPLETE || conditions.isEmpty()
                || conditions.stream().anyMatch(condition -> condition.uncertainty()
                        .filter(cause -> cause == ConditionAssessment.Uncertainty.UNRESOLVED_POLICY).isPresent())) {
            return NEEDS_REVIEW;
        }
        if (conditions.stream().anyMatch(condition -> condition.outcome() == NOT_MET)) {
            return INELIGIBLE;
        }
        if (conditions.stream().anyMatch(condition -> condition.outcome() == UNKNOWN)) {
            return NEEDS_REVIEW;
        }
        return ELIGIBLE;
    }

    public String explanation() {
        if (conditions.isEmpty()) {
            return "판정할 조건이 없어 신청 자격을 확인할 수 없습니다.";
        }
        if (policyReview.completion() == PolicyReview.Completion.INCOMPLETE) {
            return "결과에 영향을 줄 수 있는 정책 조건이나 예외를 추가로 확인해야 합니다.";
        }
        return switch (status()) {
            case ELIGIBLE -> "입력 조건이 확인한 필수 조건과 예외를 모두 충족합니다. 공식 자격 인증이나 선정 보장은 아닙니다.";
            case INELIGIBLE -> "명확히 적용되는 필수 조건 중 충족하지 못한 항목이 있습니다.";
            case NEEDS_REVIEW -> "판정하지 못한 항목이 있습니다. 항목별 이유와 원문 근거를 확인해주세요.";
        };
    }
}
