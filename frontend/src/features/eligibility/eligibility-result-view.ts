// 화면 표시용 모델이다. 확정 API DTO나 자격 판정 규칙으로 사용하지 않는다.
export type EligibilityStatusView = "ELIGIBLE" | "INELIGIBLE" | "NEEDS_REVIEW";

export type EvidenceView = {
  sourceReference: string;
  location: string;
  excerpt: string | null;
};

export type ConditionResultView = {
  conditionId: string;
  label: string;
  appliedCondition: string;
  comparedValue: string | null;
  referenceDate: string | null;
  explanation: string;
  evidence: EvidenceView;
} & (
  | { outcome: "MET" | "NOT_MET"; uncertainty: null }
  | { outcome: "UNKNOWN"; uncertainty: "MISSING_USER_INPUT" | "UNRESOLVED_POLICY" }
);

export type EligibilityResultView = {
  status: EligibilityStatusView;
  explanation: string;
  basis: {
    policyId: string;
    policyRevision: string;
    ruleVersion: string;
    evaluatedAt: string;
  };
  policyReview: {
    completion: "COMPLETE" | "INCOMPLETE";
    pendingIssues: readonly { explanation: string; evidence: EvidenceView }[];
  };
  conditions: readonly ConditionResultView[];
};

export type RecruitmentView = {
  label: string;
  explanation: string;
};
