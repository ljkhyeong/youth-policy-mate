import { recruitmentLabels, type RecruitmentFilter } from "@/features/policies/policy-recruitment";

export function getMemberLocation(view?: string | null, status?: string | null) {
  return {
    view: view === "calendar" || view === "notifications" || view === "email" ? view : "saved",
    status: status && Object.hasOwn(recruitmentLabels, status) ? status as RecruitmentFilter : "" as const,
  };
}

function memberParams(view?: string | null, status?: string | null) {
  const location = getMemberLocation(view, status);
  const params = new URLSearchParams();
  if (location.view !== "saved") params.set("view", location.view);
  if (location.status) params.set("status", location.status);
  return params;
}

export function getMemberHref(view?: string | null, status?: string | null) {
  const query = memberParams(view, status).toString();
  return query ? `/my?${query}` : "/my";
}

export function getMemberLoginHref(view?: string | null, status?: string | null) {
  const params = memberParams(view, status);
  params.set("next", "my");
  return `/login?${params}`;
}

export function parseMemberDestination(destination?: string | null) {
  if (destination !== "/my" && !destination?.startsWith("/my?")) return null;
  const params = new URLSearchParams(destination.slice(4));
  return getMemberHref(params.get("view"), params.get("status"));
}
