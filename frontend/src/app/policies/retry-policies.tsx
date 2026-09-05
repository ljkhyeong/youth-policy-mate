"use client";

import { useRouter } from "next/navigation";
import { useTransition } from "react";

export function RetryPolicies() {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  return <button type="button" className="button-primary" disabled={pending}
    onClick={() => startTransition(() => router.refresh())}>
    {pending ? "불러오는 중…" : "다시 불러오기"}
  </button>;
}
