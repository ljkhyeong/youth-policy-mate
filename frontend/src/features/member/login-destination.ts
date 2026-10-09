import { parseMemberDestination } from "./member-location";

const destinationKey = "ypm-login-destination";
const pendingPolicyKey = "ypm-pending-policy";

function validPolicy(policy?: string | null) {
  return policy && /^[0-9]{1,100}$/.test(policy) ? policy : null;
}

export function getLoginDestination(admin: boolean, policy?: string | null, member?: string | null) {
  if (admin) return "/admin/collection-exceptions";
  const number = validPolicy(policy);
  return number ? `/policies/${number}` : parseMemberDestination(member) ?? "/my";
}

export function rememberLoginDestination(admin: boolean, policy?: string, member?: string) {
  const memberDestination = parseMemberDestination(member);
  if (admin) sessionStorage.setItem(destinationKey, "admin");
  else if (!validPolicy(policy) && memberDestination) sessionStorage.setItem(destinationKey, memberDestination);
  else sessionStorage.removeItem(destinationKey);
  const number = admin ? null : validPolicy(policy);
  if (number) sessionStorage.setItem(pendingPolicyKey, number);
  else forgetPendingPolicy();
}

// 로그인 전에 고른 정책을 지운다. 로그아웃·탈퇴 뒤 다음 로그인이 그 정책으로 돌아가지 않게 한다.
export function forgetPendingPolicy() {
  sessionStorage.removeItem(pendingPolicyKey);
}

export function readLoginDestination() {
  const destination = sessionStorage.getItem(destinationKey);
  const policy = sessionStorage.getItem(pendingPolicyKey);
  return getLoginDestination(destination === "admin", policy, destination);
}

export function consumeLoginDestination() {
  const destination = readLoginDestination();
  sessionStorage.removeItem(destinationKey);
  forgetPendingPolicy();
  return destination;
}
