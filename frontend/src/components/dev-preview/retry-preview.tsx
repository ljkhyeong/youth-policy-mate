"use client";

import { useTransition } from "react";
import { useRouter } from "next/navigation";

export function RetryPreview() {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  return <button type="button" className="button-primary disabled:cursor-wait disabled:opacity-60" disabled={pending}
    onClick={() => startTransition(() => router.refresh())}>
    {pending ? "서버 결과 확인 중…" : "서버 결과 다시 불러오기"}
  </button>;
}
