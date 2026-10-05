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

// 접수 기간인 정책에만 서버가 계산한 남은 일수를 표시한다. 이전 서버 응답에는 값이 없을 수 있다.
export function deadlineLabel(recruitment: Pick<Recruitment, "status"> & { daysUntilDeadline?: number | null }) {
  const days = recruitment.daysUntilDeadline;
  if (recruitment.status !== "OPEN" || typeof days !== "number" || days < 0) return null;
  return days === 0 ? "오늘 마감" : `D-${days}`;
}

export function RecruitmentBadge({ recruitment }: { recruitment: Recruitment }) {
  const deadline = deadlineLabel(recruitment);
  return <span className="recruitment-status">
    <span className="recruitment-badge" data-status={recruitment.status}>{recruitmentLabels[recruitment.status]}</span>
    {deadline && <strong className="recruitment-dday"><span aria-hidden="true">{deadline}</span>
      <span className="sr-only">{recruitment.daysUntilDeadline === 0 ? "오늘 마감" : `마감까지 ${recruitment.daysUntilDeadline}일`}
        {recruitment.deadlineOnSeoul && ` · ${recruitment.deadlineOnSeoul.replaceAll("-", ".")} 마감`}</span></strong>}
  </span>;
}

export function RecruitmentExplanation({ recruitment }: { recruitment: Recruitment }) {
  return <details className="recruitment-explanation"><summary>접수 상태 안내</summary><p>{recruitment.explanation}</p></details>;
}

export function PolicyRecruitment({ recruitment, compact = false }: { recruitment: Recruitment; compact?: boolean }) {
  return <div className="policy-recruitment" role="group" aria-label="접수 상태">
    <RecruitmentBadge recruitment={recruitment} />
    {compact ? <RecruitmentExplanation recruitment={recruitment} /> : <p>{recruitment.explanation}</p>}
  </div>;
}
