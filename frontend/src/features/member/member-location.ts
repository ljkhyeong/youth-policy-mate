import { recruitmentLabels, type RecruitmentFilter } from "@/features/policies/policy-recruitment";
import type { NotificationFilter } from "./member-api";

type QueryValue = string | string[] | null;
type MemberLocationInput = { view?: QueryValue; status?: QueryValue; page?: QueryValue | number; filter?: QueryValue; q?: QueryValue; changed?: QueryValue | boolean };
export type MemberLocation = {
  view: "saved" | "calendar" | "notifications" | "email";
  status: RecruitmentFilter;
  page: number;
  filter: NotificationFilter;
  q: string;
  changed: boolean;
};

export function getMemberLocation({ view, status, page, filter, q, changed }: MemberLocationInput = {}): MemberLocation {
  const number = typeof page === "string" || typeof page === "number" ? Number(page) : NaN;
  return {
    view: view === "calendar" || view === "notifications" || view === "email" ? view : "saved",
    status: typeof status === "string" && Object.hasOwn(recruitmentLabels, status) ? status as RecruitmentFilter : "",
    page: Number.isInteger(number) && number >= 1 && number <= 2_147_483_647 ? number : 1,
    filter: filter === "UNREAD" ? "UNREAD" : "ALL",
    q: typeof q === "string" ? q.trim().slice(0, 80) : "",
    changed: changed === true || changed === "1",
  };
}

export function readMemberLocation(params: Pick<URLSearchParams, "get">) {
  return getMemberLocation({ view: params.get("view"), status: params.get("status"), page: params.get("page"), filter: params.get("filter"), q: params.get("q"), changed: params.get("changed") });
}

function memberParams(input: MemberLocationInput) {
  const location = getMemberLocation(input);
  const params = new URLSearchParams();
  if (location.view !== "saved") params.set("view", location.view);
  if (location.status) params.set("status", location.status);
  if (location.page > 1) params.set("page", String(location.page));
  if (location.filter !== "ALL") params.set("filter", location.filter);
  if (location.q) params.set("q", location.q);
  if (location.changed) params.set("changed", "1");
  return params;
}

export function getMemberHref(input: MemberLocationInput = {}) {
  const query = memberParams(input).toString();
  return query ? `/my?${query}` : "/my";
}

export function getMemberLoginHref(input: MemberLocationInput = {}) {
  const params = memberParams(input);
  params.set("next", "my");
  return `/login?${params}`;
}

export function parseMemberDestination(destination?: string | null) {
  if (destination !== "/my" && !destination?.startsWith("/my?")) return null;
  const params = new URLSearchParams(destination.slice(4));
  return getMemberHref(readMemberLocation(params));
}
