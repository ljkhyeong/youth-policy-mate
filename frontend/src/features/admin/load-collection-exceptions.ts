import { cookies } from "next/headers";
import type { components } from "@/generated/policy-api";

export type ExceptionPage = components["schemas"]["CollectionExceptionPage"];
export type ExceptionDetail = components["schemas"]["CollectionExceptionDetail"];
export type PageFailureList = components["schemas"]["CollectionPageFailureList"];
export type ReplayPage = components["schemas"]["CollectionReplayPage"];
export type LoadFailure = "unauthenticated" | "forbidden" | "missing" | "invalid" | "unavailable";
type Loaded<T> = { status: "available"; data: T } | { status: LoadFailure };

async function load<T>(path: string): Promise<Loaded<T>> {
  const session = (await cookies()).get("YPM_SESSION");
  if (!session) return { status: "unauthenticated" };
  try {
    const base = process.env.POLICY_API_BASE_URL || "http://127.0.0.1:8080";
    const response = await fetch(new URL(`/api/v1/admin/${path}`, base), {
      cache: "no-store", redirect: "error", signal: AbortSignal.timeout(8000),
      headers: { Accept: "application/json", Cookie: `YPM_SESSION=${session.value}` },
    });
    if (response.status === 401) return { status: "unauthenticated" };
    if (response.status === 403) return { status: "forbidden" };
    if (response.status === 404) return { status: "missing" };
    if (response.status === 400) return { status: "invalid" };
    if (!response.ok || !response.headers.get("content-type")?.includes("application/json")) return { status: "unavailable" };
    return { status: "available", data: await response.json() as T };
  } catch { return { status: "unavailable" }; }
}

export function collectionPage(value: string | string[] | undefined): number {
  const page = typeof value === "string" ? Number(value) : 1;
  return Number.isInteger(page) && page >= 1 && page <= 1000 ? page : 1;
}

export function loadCollectionExceptions(page: number) {
  return load<ExceptionPage>(`collection-exceptions?page=${page}&pageSize=20`);
}

export function loadCollectionPageFailures(page: number) {
  return load<PageFailureList>(`collection-exceptions/pages?page=${page}&pageSize=20`);
}

export function loadCollectionReplays(page: number) {
  return load<ReplayPage>(`collection-exceptions/replays?page=${page}&pageSize=20`);
}

export function loadCollectionException(runId: string, itemIndex: string): Promise<Loaded<ExceptionDetail>> {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(runId) || !/^[0-9]$/.test(itemIndex)) {
    return Promise.resolve({ status: "missing" });
  }
  return load<ExceptionDetail>(`collection-exceptions/${runId}/${itemIndex}`);
}

export type CorrectionItem = components["schemas"]["PolicyCorrectionItem"];
export type CorrectionPage = components["schemas"]["PolicyCorrectionPage"];
export type CorrectionPolicy = components["schemas"]["CollectionExceptionCurrentPolicy"];

export function loadPolicyCorrections(page: number) {
  return load<CorrectionPage>(`policy-corrections?page=${page}&pageSize=20`);
}

export function loadCorrectionPolicy(number: string): Promise<Loaded<CorrectionPolicy>> {
  if (!/^[0-9]{1,100}$/.test(number)) return Promise.resolve({ status: "invalid" });
  return load<CorrectionPolicy>(`policy-corrections/policies/${number}`);
}

export type RuleReviewPage = components["schemas"]["PolicyRuleReviewPage"];
export type RuleReviewDetail = components["schemas"]["PolicyRuleReviewDetail"];

export function loadRuleReviews(page: number, filter: string, query: string) {
  return load<RuleReviewPage>(`policy-rule-reviews?${new URLSearchParams({ page: String(page), pageSize: "20", filter, query })}`);
}

export function loadRuleReview(number: string): Promise<Loaded<RuleReviewDetail>> {
  if (!/^[0-9]{1,100}$/.test(number)) return Promise.resolve({ status: "missing" });
  return load<RuleReviewDetail>(`policy-rule-reviews/${number}`);
}

export type AiRunPage = components["schemas"]["PolicyAiRunPage"];

export function loadAiRuns(page: number, filter: string, query: string) {
  return load<AiRunPage>(`policy-ai-runs?${new URLSearchParams({ page: String(page), pageSize: "20", filter, query })}`);
}

export type EmailDeliveryPage = components["schemas"]["AdminEmailDeliveryPage"];

export function loadEmailDeliveries(page: number, days: number, state: string, kind: string) {
  const params = new URLSearchParams({ page: String(page), pageSize: "20", days: String(days) });
  if (state !== "ALL") params.set("state", state);
  if (kind !== "ALL") params.set("kind", kind);
  return load<EmailDeliveryPage>(`email-deliveries?${params}`);
}
