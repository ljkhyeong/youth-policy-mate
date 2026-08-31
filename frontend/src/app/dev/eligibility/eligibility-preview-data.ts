import type { ConditionResultView, EligibilityExampleView, EvidenceView, RecruitmentView } from "@/features/eligibility/eligibility-result-view";

function evidence(location: string, excerpt: string | null): EvidenceView {
  return { sourceReference: "화면 점검용 인공 자료 · 실제 정책 원문 아님", location, excerpt };
}

const age: ConditionResultView = {
  conditionId: "sample-age", label: "연령", appliedCondition: "기준일에 만 19세 이상 ~ 34세 이하",
  comparedValue: "만 27세", referenceDate: "2026-08-01", outcome: "MET", uncertainty: null,
  explanation: "기준일의 만 나이가 예시 조건에 포함됩니다.",
  evidence: evidence("인공 자료 1항 · 연령", "2026년 8월 1일 기준 만 19세 이상, 만 34세 이하"),
};
const residence: ConditionResultView = {
  conditionId: "sample-residence", label: "거주", appliedCondition: "기준일에 서울특별시 거주",
  comparedValue: "서울특별시 마포구 · 2026-08-01 기준", referenceDate: "2026-08-01", outcome: "MET", uncertainty: null,
  explanation: "거주 입력의 기준일과 예시 조건의 기준일이 같고 허용 지역에 포함됩니다.",
  evidence: evidence("인공 자료 2항 · 거주", "2026년 8월 1일 기준 서울특별시 거주"),
};
const employment: ConditionResultView = {
  conditionId: "sample-employment", label: "취업", appliedCondition: "취업 상태 제한 없음",
  comparedValue: null, referenceDate: null, outcome: "MET", uncertainty: null,
  explanation: "예시 자료에서 취업 제한이 없음을 확인했습니다. 사용자 답변을 비교하지 않았으며 이 항목만 충족합니다.",
  evidence: evidence("인공 자료 3항 · 취업", "취업 상태에 따른 제한 없음"),
};
const income: ConditionResultView = {
  conditionId: "sample-income", label: "소득", appliedCondition: "본인의 2025년 세전 연간 근로소득 합계 25,000,000원 이하",
  comparedValue: "20,000,000원 초과 ~ 25,000,000원 이하", referenceDate: "2025-12-31", outcome: "MET", uncertainty: null,
  explanation: "동일한 정의·대상·기간의 답변 구간 전체가 예시 허용 범위 안에 있습니다.",
  evidence: evidence("인공 자료 4항 · 소득", "2025년 1월 1일~12월 31일 본인의 세전 근로소득 합계 25,000,000원 이하. 적용 기준일은 2025년 12월 31일."),
};
const missingIncome: ConditionResultView = {
  ...income, comparedValue: null, outcome: "UNKNOWN", uncertainty: "MISSING_USER_INPUT",
  explanation: "해당 정의·기간에 대한 소득 답변이 없어 추가 확인이 필요합니다. 0원이나 조건 불충족으로 처리하지 않습니다.",
};
const ageNotMet: ConditionResultView = {
  ...age, comparedValue: "만 36세", outcome: "NOT_MET", uncertainty: null,
  explanation: "기준일의 만 나이가 예시 상한인 만 34세를 초과합니다.",
};
const basis = {
  policyId: "sample-policy-display-only", policyRevision: "sample-revision-1",
  ruleVersion: "sample-rule-1", evaluatedAt: "2026-08-30T00:00:00Z",
};
const reviewComplete = { completion: "COMPLETE", pendingIssues: [] } as const;
const openRecruitment: RecruitmentView = {
  label: "모집 중 · 예시", explanation: "자료에 미리 정한 모집 상태입니다. 모집 중이어도 자격 충족을 뜻하지 않습니다.",
};

// 서버를 호출하거나 답변으로 계산하지 않는 고정 표시 자료다.
export const ELIGIBILITY_EXAMPLES: readonly EligibilityExampleView[] = [
  {
    id: "eligible-closed", label: "전체 충족 · 모집 종료",
    description: "모든 조건을 충족해도 모집이 끝났다면 지금 신청할 수 없다는 점을 확인합니다.",
    recruitment: { label: "모집 종료 · 예시", explanation: "이 예시는 모집이 끝난 상태입니다. 자격 안내가 신청 가능이어도 현재 접수를 뜻하지 않습니다." },
    result: {
      status: "ELIGIBLE", explanation: "입력 조건이 확인한 필수 조건과 예외를 모두 충족합니다.",
      basis, policyReview: reviewComplete, conditions: [age, residence, employment, income],
    },
  },
  {
    id: "missing-input", label: "사용자 정보 부족",
    description: "소득 구간이 정책 경계에 걸치면 추가 확인이 필요한 이유와 기존 구간을 함께 보여줍니다.",
    recruitment: openRecruitment,
    result: {
      status: "NEEDS_REVIEW", explanation: "소득 구간을 더 좁혀 확인해야 합니다. 현재 답변만으로 충족 여부를 정할 수 없습니다.",
      basis, policyReview: reviewComplete,
      conditions: [age, residence, employment, {
        ...income, comparedValue: "20,000,000원 초과 ~ 30,000,000원 이하", outcome: "UNKNOWN", uncertainty: "MISSING_USER_INPUT",
        explanation: "답변 구간이 25,000,000원 경계에 걸쳐 있습니다. 같은 정의·기간에서 더 좁은 구간을 확인해야 합니다.",
      }],
    },
  },
  {
    id: "unresolved-policy", label: "정책 조건 미해석",
    description: "불충족 항목이 있어도 결과에 영향을 줄 예외가 미확인이라면 전체 결과를 보류한 그대로 보여줍니다.",
    recruitment: openRecruitment,
    result: {
      status: "NEEDS_REVIEW", explanation: "결과에 영향을 줄 수 있는 정책 조건이나 예외를 추가로 확인해야 합니다.",
      basis: { ...basis, policyRevision: "sample-revision-2" },
      policyReview: {
        completion: "INCOMPLETE",
        pendingIssues: [{ explanation: "연령 상한의 예외 대상과 연장 범위를 아직 확인하지 못했습니다.", evidence: evidence("인공 자료 부록 · 예외 안내", "연령 상한의 예외는 별도 안내를 따릅니다.") }],
      },
      conditions: [ageNotMet, residence, {
        ...employment, appliedCondition: "취업 제외 대상의 세부 정의 미확인", outcome: "UNKNOWN", uncertainty: "UNRESOLVED_POLICY",
        explanation: "정책의 제외 대상 정의를 해석하지 못했습니다. 사용자가 답변해도 이 항목은 확정할 수 없습니다.",
        evidence: evidence("인공 자료 3항의 별도 안내 · 내용 미확인", null),
      }, income],
    },
  },
  {
    id: "ineligible", label: "명확한 조건 불충족",
    description: "정책 검토가 완료된 상황에서 명확한 불충족과 다른 항목의 답변 누락을 구분합니다.",
    recruitment: openRecruitment,
    result: {
      status: "INELIGIBLE", explanation: "명확히 적용되는 필수 조건 중 충족하지 못한 항목이 있습니다.",
      basis, policyReview: reviewComplete, conditions: [ageNotMet, residence, employment, missingIncome],
    },
  },
];
