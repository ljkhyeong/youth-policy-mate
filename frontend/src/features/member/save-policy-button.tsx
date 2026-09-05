"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { memberApi, type MemberSession, type SavedPolicies } from "./member-api";

export function SavePolicyButton({ policyNumber }: { policyNumber: string }) {
  const router = useRouter();
  const [session, setSession] = useState<MemberSession | null>(null);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(true);
  const [message, setMessage] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    (async () => {
      try {
        const current = await memberApi<MemberSession>("session", { signal: controller.signal });
        if (controller.signal.aborted) return;
        setSession(current);
        if (current.authenticated) {
          const policies = await memberApi<SavedPolicies>("policies", { signal: controller.signal });
          if (!controller.signal.aborted) setSaved(policies.items.some(item => item.policyNumber === policyNumber));
        }
      } catch { if (!controller.signal.aborted) setMessage("저장 상태를 불러오지 못했어요. 새로고침 후 다시 확인해주세요."); }
      finally { if (!controller.signal.aborted) setBusy(false); }
    })();
    return () => controller.abort();
  }, [policyNumber]);
  async function toggle() {
    if (!session?.authenticated) {
      sessionStorage.setItem("ypm-pending-policy", policyNumber);
      router.push(`/login?policy=${policyNumber}`);
      return;
    }
    setBusy(true); setMessage("");
    try {
      await memberApi(`policies/${policyNumber}`, { method: saved ? "DELETE" : "PUT", csrf: session.csrfToken });
      setSaved(!saved); setMessage(saved ? "저장을 취소했어요. 발송 대기 중인 알림도 취소했어요." : "저장했어요. 마감일이 있으면 ‘마감 일정’에서 확인할 수 있어요.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "저장에 실패했어요."); }
    finally { setBusy(false); }
  }
  return <div className="member-save-panel">
    <button className={saved ? "button-secondary" : "button-primary"} type="button" disabled={busy || session === null} aria-pressed={saved} onClick={toggle}>
      {busy ? "저장 상태 확인 중…" : saved ? "저장 취소" : "관심 정책 저장"}
    </button>
    <a className="text-link" href="/my">저장한 정책 보기</a>
    <p role="status">{message}</p>
  </div>;
}
