const destinationKey = "ypm-login-destination";

export function rememberLoginDestination(admin: boolean) {
  if (admin) sessionStorage.setItem(destinationKey, "admin");
  else sessionStorage.removeItem(destinationKey);
}

export function consumeLoginDestination() {
  const destination = sessionStorage.getItem(destinationKey);
  const policy = sessionStorage.getItem("ypm-pending-policy");
  sessionStorage.removeItem(destinationKey);
  sessionStorage.removeItem("ypm-pending-policy");
  if (destination === "admin") return "/admin/collection-exceptions";
  return policy && /^[0-9]{1,100}$/.test(policy) ? `/policies/${policy}` : "/my";
}
