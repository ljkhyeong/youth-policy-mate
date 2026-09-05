import type { components } from "@/generated/policy-api";

export type EmailSettings = components["schemas"]["MemberEmailSettings"];
export type EmailAddress = components["schemas"]["MemberEmailAddress"];
export type EmailCode = components["schemas"]["MemberEmailCode"];
export type EmailConsent = components["schemas"]["MemberEmailConsent"];

export type MemberSession = components["schemas"]["MemberSession"];
export type SavedPolicies = components["schemas"]["SavedPolicyList"];
export type Notifications = components["schemas"]["MemberNotificationList"];
export type SavedConditions = components["schemas"]["MemberConditions"];
export type BasicConditions = components["schemas"]["BasicConditions"];
export type PolicyChecks = components["schemas"]["PolicyCheckResponse"];

export class MemberApiError extends Error {
  constructor(public status: number, message: string, public code?: string) { super(message); }
}

export async function memberApi<T>(path: string, options: { method?: string; body?: unknown; csrf?: string; signal?: AbortSignal } = {}): Promise<T> {
  const response = await fetch(`/api/member/${path}`, {
    method: options.method || "GET", credentials: "same-origin", cache: "no-store", signal: options.signal,
    headers: { Accept: "application/json", ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(options.csrf ? { "X-CSRF-TOKEN": options.csrf } : {}) },
    ...(options.body ? { body: JSON.stringify(options.body) } : {}),
  });
  if (!response.ok) {
    if (response.status === 401) throw new MemberApiError(401, "로그인이 만료됐어요. 다시 로그인해주세요.");
    const error = await response.json().catch(() => null);
    throw new MemberApiError(response.status, "요청을 완료하지 못했어요. 입력 내용과 로그인 상태를 확인하고 다시 시도해주세요.",
      typeof error?.code === "string" ? error.code : undefined);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
