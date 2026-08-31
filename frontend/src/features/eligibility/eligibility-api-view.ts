import type { components, operations } from "@/generated/preview-api";
import type { ConditionResultView, EligibilityExampleView } from "./eligibility-result-view";

export type EligibilityExamplesResponse = operations["listDevelopmentEligibilityExamples"]["responses"][200]["content"]["application/json"];
type ConditionResponse = components["schemas"]["EligibilityCondition"];
type RecruitmentStatus = components["schemas"]["EligibilityRecruitment"]["status"];

const RECRUITMENT_LABELS: Record<RecruitmentStatus, string> = {
  BEFORE_OPENING: "모집 전", OPEN: "접수 기간", CLOSED: "모집 마감",
  ROLLING: "상시 모집", UNTIL_EXHAUSTED: "예산·인원 소진 시 종료", UNKNOWN: "신청기간 확인 필요",
};

// 개발 예시의 항목 제목만 정한다. 조건 ID로 자격 상태를 계산하지 않는다.
const CONDITION_LABELS: Record<string, string> = {
  "sample-age": "연령", "sample-residence": "거주", "sample-employment": "취업", "sample-income": "소득",
};

function toConditionView(condition: ConditionResponse): ConditionResultView {
  const common = { ...condition, label: CONDITION_LABELS[condition.conditionId] ?? condition.conditionId };
  if (condition.outcome === "UNKNOWN"
    && (condition.uncertainty === "MISSING_USER_INPUT" || condition.uncertainty === "UNRESOLVED_POLICY")) {
    return { ...common, outcome: condition.outcome, uncertainty: condition.uncertainty };
  }
  if ((condition.outcome === "MET" || condition.outcome === "NOT_MET") && condition.uncertainty === null) {
    return { ...common, outcome: condition.outcome, uncertainty: null };
  }
  throw new Error("항목 결과와 미확인 원인을 함께 확인할 수 없습니다.");
}

export function toEligibilityExamplesView(response: EligibilityExamplesResponse): readonly EligibilityExampleView[] {
  if (response.dataKind !== "SYNTHETIC" || !Array.isArray(response.examples) || response.examples.length === 0) {
    throw new Error("개발용 인공 자격 자료를 확인할 수 없습니다.");
  }
  return response.examples.map((example: components["schemas"]["EligibilityExample"]) => ({
    id: example.id, label: example.label, description: example.description,
    result: { ...example.result, conditions: example.result.conditions.map(toConditionView) },
    recruitment: { label: RECRUITMENT_LABELS[example.recruitment.status], explanation: example.recruitment.explanation },
  }));
}
