const destinationKey = "ypm-login-destination";

function validPolicy(policy?: string | null) {
  return policy && /^[0-9]{1,100}$/.test(policy) ? policy : null;
}

export function getLoginDestination(admin: boolean, policy?: string | null) {
  if (admin) return "/admin/collection-exceptions";
  const number = validPolicy(policy);
  return number ? `/policies/${number}` : "/my";
}

export function rememberLoginDestination(admin: boolean, policy?: string) {
  if (admin) sessionStorage.setItem(destinationKey, "admin");
  else sessionStorage.removeItem(destinationKey);
  const number = admin ? null : validPolicy(policy);
  if (number) sessionStorage.setItem("ypm-pending-policy", number);
  else sessionStorage.removeItem("ypm-pending-policy");
}

export function readLoginDestination() {
  const destination = sessionStorage.getItem(destinationKey);
  const policy = sessionStorage.getItem("ypm-pending-policy");
  return getLoginDestination(destination === "admin", policy);
}

export function consumeLoginDestination() {
  const destination = readLoginDestination();
  sessionStorage.removeItem(destinationKey);
  sessionStorage.removeItem("ypm-pending-policy");
  return destination;
}
