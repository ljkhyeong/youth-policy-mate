import { toEligibilityExamplesView, type EligibilityExamplesResponse } from "@/features/eligibility/eligibility-api-view";
import type { EligibilityExampleView } from "@/features/eligibility/eligibility-result-view";

type PreviewLoadResult = { status: "available"; examples: readonly EligibilityExampleView[] } | { status: "unavailable" };

// 개발 전용 서버 컴포넌트에서만 호출한다. 기존 조건 입력이나 회원 정보를 보내지 않는다.
export async function loadEligibilityExamples(): Promise<PreviewLoadResult> {
  if (process.env.NODE_ENV !== "development") return { status: "unavailable" };
  try {
    const response = await fetch("http://127.0.0.1:8081/api/dev/eligibility-examples", {
      cache: "no-store", redirect: "error", signal: AbortSignal.timeout(5000),
      headers: { Accept: "application/json" },
    });
    if (!response.ok || !response.headers.get("content-type")?.includes("application/json")) return { status: "unavailable" };
    const body = await response.json() as EligibilityExamplesResponse;
    return { status: "available", examples: toEligibilityExamplesView(body) };
  } catch {
    return { status: "unavailable" };
  }
}
