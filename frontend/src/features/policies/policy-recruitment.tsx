import type { components } from "@/generated/policy-api";

type Recruitment = components["schemas"]["PolicyRecruitment"];
export const recruitmentLabels: Record<Recruitment["status"], string> = {
  BEFORE_OPENING: "접수 전", OPEN: "접수 기간", CLOSED: "마감",
  ROLLING: "상시", UNTIL_EXHAUSTED: "소진 시 마감", UNKNOWN: "기간 미확인",
};

export type RecruitmentFilter = Recruitment["status"] | "";

export function RecruitmentOptions() {
  return <><option value="">전체 접수 상태</option>{Object.entries(recruitmentLabels).map(([value, label]) =>
    <option key={value} value={value}>{label}</option>)}</>;
}

export function PolicyRecruitment({ recruitment, compact = false }: { recruitment: Recruitment; compact?: boolean }) {
  return <div className="policy-recruitment" role="group" aria-label="접수 상태">
    <span className="recruitment-badge" data-status={recruitment.status}>{recruitmentLabels[recruitment.status]}</span>
    {compact ? <details className="recruitment-explanation"><summary>상태 안내</summary><p>{recruitment.explanation}</p></details>
      : <p>{recruitment.explanation}</p>}
  </div>;
}
