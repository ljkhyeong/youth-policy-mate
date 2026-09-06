import type { RecruitmentFilter } from "@/features/policies/policy-recruitment";
import type { components } from "@/generated/policy-api";

export type PolicyList = components["schemas"]["PolicyListResponse"];
export type PolicyDetail = components["schemas"]["PolicyDetailResponse"];
type Loaded<T> = { status: "available"; data: T } | { status: "missing" | "unavailable" };

// 공개 정책의 서버 렌더링에서만 사용한다. 브라우저에서 온통청년을 직접 호출하지 않는다.
async function load<T>(path: string): Promise<Loaded<T>> {
  try {
    const base = process.env.POLICY_API_BASE_URL || "http://127.0.0.1:8080";
    const response = await fetch(new URL(path, base), {
      cache: "no-store", redirect: "error", signal: AbortSignal.timeout(5000),
      headers: { Accept: "application/json" },
    });
    if (response.status === 404) return { status: "missing" };
    if (!response.ok || !response.headers.get("content-type")?.includes("application/json")) return { status: "unavailable" };
    return { status: "available", data: await response.json() as T };
  } catch {
    return { status: "unavailable" };
  }
}

export function loadPolicies(query: string, page: number, questionsOnly = false, recruitmentStatus: RecruitmentFilter = "") {
  const search = new URLSearchParams({ q: query, page: String(page), pageSize: "20" });
  if (questionsOnly) search.set("questionsOnly", "true");
  if (recruitmentStatus) search.set("recruitmentStatus", recruitmentStatus);
  return load<PolicyList>(`/api/v1/policies?${search}`);
}

export function loadPolicy(number: string): Promise<Loaded<PolicyDetail>> {
  if (!/^\d{1,100}$/.test(number)) return Promise.resolve({ status: "missing" });
  return load<PolicyDetail>(`/api/v1/policies/${number}`);
}
