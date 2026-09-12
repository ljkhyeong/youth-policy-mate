"use client";

import { useEffect, useRef, useState } from "react";
import { memberApi, MemberApiError, type MemberSession } from "@/features/member/member-api";

export function useAdminMutation() {
  const [busy, setBusy] = useState(false);
  const active = useRef<AbortController | null>(null);
  useEffect(() => () => active.current?.abort(), []);

  async function mutate<T>(path: string, body: unknown): Promise<T | undefined> {
    if (active.current) return;
    const controller = new AbortController(); active.current = controller;
    const location = window.location.pathname + window.location.search;
    // 주소가 바뀐 뒤 컴포넌트가 정리되기 전에도 변경 요청을 중단한다.
    const isCurrent = () => !controller.signal.aborted && location === window.location.pathname + window.location.search;
    setBusy(true);
    try {
      const session = await memberApi<MemberSession>("session", { signal: controller.signal });
      if (!isCurrent()) return;
      if (!session.authenticated) throw new MemberApiError(401, "로그인이 필요합니다.");
      const result = await memberApi<T>(path, { method: "POST", csrf: session.csrfToken, body, signal: controller.signal });
      if (isCurrent()) return result;
    } catch (failure) {
      if (isCurrent()) throw failure;
    } finally {
      if (active.current === controller) active.current = null;
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  return { busy, mutate };
}
