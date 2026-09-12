"use client";
import { announceAccountChange } from "@/features/member/account-transitions";

import Link from "next/link";
import { MemberEmailSettings } from "@/features/member/member-email-settings";
import { MemberNotifications } from "@/features/member/member-notifications";
import { MemberPolicyList } from "@/features/member/member-policy-list";
import { MemberWithdrawal } from "@/features/member/member-withdrawal";
import type { RecruitmentFilter } from "@/features/policies/policy-recruitment";
import { useEffect, useRef, useState } from "react";
import { memberApi, type MemberSession, type SavedPolicies } from "@/features/member/member-api";

export function MemberDashboard() {
  const [session, setSession] = useState<MemberSession | null>(null);
  const [policies, setPolicies] = useState<SavedPolicies | null>(null);
  const [unreadCount, setUnreadCount] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);
  const [withdrawn, setWithdrawn] = useState(false);
  const completion = useRef<HTMLHeadingElement>(null);
  useEffect(() => { if (withdrawn) completion.current?.focus(); }, [withdrawn]);
  const [tab, setTab] = useState<"saved" | "calendar" | "notifications">("saved");
  const [calendarFilter, setCalendarFilter] = useState<RecruitmentFilter>("");
  useEffect(() => {
    if (withdrawn) return;
    const controller = new AbortController();
    (async () => {
      try {
        const current = await memberApi<MemberSession>("session", { signal: controller.signal });
        if (controller.signal.aborted) return;
        setSession(current);
        if (!current.authenticated) return;
        const saved = await memberApi<SavedPolicies>("policies", { signal: controller.signal });
        if (!controller.signal.aborted) setPolicies(saved);
      } catch { if (!controller.signal.aborted) setError("내 정보를 불러오지 못했어요. 다시 시도해주세요."); }
    })();
    return () => controller.abort();
  }, [reload, withdrawn]);
  function reloadData() {
    setError(""); setPolicies(null); setUnreadCount(null); setSession(null);
    setReload(value => value + 1);
  }
  async function mutate(path: string, method: string) {
    if (!session) return;
    setBusy(true); setError("");
    try { await memberApi(path, { method, csrf: session.csrfToken }); reloadData(); }
    catch (failure) { setError(failure instanceof Error ? failure.message : "요청에 실패했어요."); }
    finally { setBusy(false); }
  }
  function completeWithdrawal() {
    setWithdrawn(true); setSession(null); setPolicies(null); setUnreadCount(null); setError("");
    sessionStorage.removeItem("ypm-pending-policy");
    announceAccountChange();
  }
  async function logout() {
    if (!session) return;
    setBusy(true);
    try {
      await memberApi("logout", { method: "POST", csrf: session.csrfToken });
      sessionStorage.removeItem("ypm-pending-policy");
      announceAccountChange();
      window.location.replace("/");
    } catch { setError("로그아웃을 완료하지 못했어요. 다시 시도해주세요."); setBusy(false); }
  }
  if (withdrawn) return <section className="member-panel" role="status"><h2 ref={completion} tabIndex={-1}>탈퇴가 완료됐어요</h2><p>저장한 정보를 삭제하고 모든 기기에서 로그아웃했어요.</p><Link href="/" className="button-primary">홈으로</Link></section>;
  if (session && !session.authenticated) return <section className="member-panel"><h2>로그인하고 관심 정책을 저장하세요</h2><p>로그인하면 저장한 정책의 마감일과 알림을 볼 수 있어요.</p><Link href="/login" className="button-primary">로그인하기</Link><Link href="/policies" className="text-link">정책 둘러보기</Link></section>;
  return <>
    {error && <div className="member-panel" role="alert"><p>{error}</p><button className="button-secondary" onClick={reloadData}>다시 불러오기</button></div>}
    {!policies && !error && <p role="status">내 정책을 불러오고 있어요.</p>}
    {session?.authenticated && <div className="member-toolbar member-account-toolbar"><strong>{session.displayName}님의 정책</strong><Link href="/conditions">내 조건 관리</Link><button type="button" className="text-button" disabled={busy} onClick={reloadData}>새로고침</button><button type="button" className="text-button" disabled={busy} onClick={logout}>로그아웃</button></div>}
    <nav className="member-tabs" aria-label="내 정책 보기">
      {(["saved", "calendar", "notifications"] as const).map(value => <button type="button" aria-current={tab === value ? "page" : undefined} key={value} onClick={() => setTab(value)}>{value === "saved" ? "관심 정책" : value === "calendar" ? "마감 일정" : `알림${unreadCount ? ` (${unreadCount})` : ""}`}</button>)}
    </nav>
    {policies && tab !== "notifications" && <MemberPolicyList policies={policies.items} calendar={tab === "calendar"}
      filter={calendarFilter} onFilterChange={setCalendarFilter} busy={busy} onRemove={number => mutate(`policies/${number}`, "DELETE")} />}
    {session?.authenticated && tab === "notifications" && <details className="member-email-disclosure">
      <summary>이메일 알림 설정</summary><MemberEmailSettings csrf={session.csrfToken} />
    </details>}
    {session?.authenticated && policies && <MemberNotifications csrf={session.csrfToken} active={tab === "notifications"} onUnreadCount={setUnreadCount} />}
    {session?.authenticated && <MemberWithdrawal csrf={session.csrfToken} onDeleted={completeWithdrawal} />}
  </>;
}
