import { recruitmentLabels, type RecruitmentFilter } from "@/features/policies/policy-recruitment";
import type { NotificationFilter } from "./member-api";

type QueryValue = string | string[] | null;
type MemberLocationInput = { view?: QueryValue; status?: QueryValue; page?: QueryValue | number; filter?: QueryValue };
export type MemberLocation = {
  view: "saved" | "calendar" | "notifications" | "email";
  status: RecruitmentFilter;
  page: number;
  filter: NotificationFilter;
};

export function getMemberLocation({ view, status, page, filter }: MemberLocationInput = {}): MemberLocation {
  const number = typeof page === "string" || typeof page === "number" ? Number(page) : NaN;
  return {
    view: view === "calendar" || view === "notifications" || view === "email" ? view : "saved",
    status: typeof status === "string" && Object.hasOwn(recruitmentLabels, status) ? status as RecruitmentFilter : "",
    page: Number.isInteger(number) && number >= 1 && number <= 2_147_483_647 ? number : 1,
    filter: filter === "UNREAD" ? "UNREAD" : "ALL",
  };
}

export function readMemberLocation(params: Pick<URLSearchParams, "get">) {
  return getMemberLocation({ view: params.get("view"), status: params.get("status"), page: params.get("page"), filter: params.get("filter") });
}

function memberParams(input: MemberLocationInput) {
  const location = getMemberLocation(input);
  const params = new URLSearchParams();
  if (location.view !== "saved") params.set("view", location.view);
  if (location.status) params.set("status", location.status);
  if (location.page > 1) params.set("page", String(location.page));
  if (location.filter !== "ALL") params.set("filter", location.filter);
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
