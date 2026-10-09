import type { components } from "@/generated/policy-api";
import { formatPolicyPeriod } from "./policy-period";

type Recruitment = Pick<components["schemas"]["PolicyRecruitment"], "status" | "deadlineOnSeoul">;

// 접수 기간인 정책은 마감일에 볼펜 동그라미를 친다. 기간 문자열의 끝이 서버가 계산한 마감일과 같을 때만 표시한다.
export function PolicyPeriodText({ period, recruitment }: { period: string; recruitment: Recruitment }) {
  const text = formatPolicyPeriod(period);
  const deadline = recruitment.status === "OPEN" && recruitment.deadlineOnSeoul ? recruitment.deadlineOnSeoul.replaceAll("-", ".") : null;
  if (!deadline || !text.endsWith(deadline)) return <span>{text}</span>;
  return <span>{text.slice(0, -deadline.length)}<span className="pen-circle">{deadline}</span></span>;
}
