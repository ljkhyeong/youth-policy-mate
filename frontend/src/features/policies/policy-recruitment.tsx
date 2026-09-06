import type { components } from "@/generated/policy-api";

type Recruitment = components["schemas"]["PolicyRecruitment"];
const labels: Record<Recruitment["status"], string> = {
  BEFORE_OPENING: "접수 전", OPEN: "접수 기간", CLOSED: "마감",
  ROLLING: "상시", UNTIL_EXHAUSTED: "소진 시 마감", UNKNOWN: "기간 미확인",
};

export function PolicyRecruitment({ recruitment }: { recruitment: Recruitment }) {
  return <div className="policy-recruitment" role="group" aria-label="접수 상태">
    <span className="recruitment-badge" data-status={recruitment.status}>{labels[recruitment.status]}</span>
    <p>{recruitment.explanation}</p>
  </div>;
}
