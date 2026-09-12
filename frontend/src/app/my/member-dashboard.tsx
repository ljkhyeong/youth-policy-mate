"use client";
import { announceAccountChange } from "@/features/member/account-transitions";

import Link from "next/link";
import { MemberEmailSettings } from "@/features/member/member-email-settings";
import { MemberNotifications } from "@/features/member/member-notifications";
import { MemberPolicyList } from "@/features/member/member-policy-list";
import { MemberWithdrawal } from "@/features/member/member-withdrawal";
import type { RecruitmentFilter } from "@/features/policies/policy-recruitment";
import { useCallback, useEffect, useRef, useState } from "react";
import { memberApi, MemberApiError, type MemberSession, type SavedPolicies } from "@/features/member/member-api";

type MemberAction = "remove" | "logout" | "withdraw";

function announceSessionEnd() {
  sessionStorage.removeItem("ypm-pending-policy");
  announceAccountChange();
}

function isCurrentRequest(controller: AbortController) {
  // 화면 전환 중에는 주소가 바뀐 뒤 컴포넌트가 정리될 수 있다.
  return !controller.signal.aborted && window.location.pathname === "/my";
}

export function MemberDashboard() {
  const [session, setSession] = useState<MemberSession | null>(null);
  const [policies, setPolicies] = useState<SavedPolicies | null>(null);
  const [unreadCount, setUnreadCount] = useState<number | null>(null);
  const [error, setError] = useState("");
  const [pending, setPending] = useState<MemberAction | "load" | null>("load");
  const [notice, setNotice] = useState("");
  const [loginRequired, setLoginRequired] = useState(false);
  const [withdrawn, setWithdrawn] = useState(false);
  const active = useRef<AbortController | null>(null);
  const sessionEndPending = useRef(false);
  const restoreFocus = useRef(false);
  const completion = useRef<HTMLHeadingElement>(null);
  const retryButton = useRef<HTMLButtonElement>(null);
  const refreshButton = useRef<HTMLButtonElement>(null);
  const loginLink = useRef<HTMLAnchorElement>(null);
  const busy = pending !== null;
  const endingSession = pending === "logout" || pending === "withdraw";
  useEffect(() => {
    if (!restoreFocus.current || pending) return;
    (withdrawn ? completion : error ? retryButton : session?.authenticated ? refreshButton : loginLink).current?.focus();
    restoreFocus.current = false;
  }, [pending, withdrawn, error, session]);
  const [tab, setTab] = useState<"saved" | "calendar" | "notifications">("saved");
  const [calendarFilter, setCalendarFilter] = useState<RecruitmentFilter>("");
  const clearData = useCallback(() => { setSession(null); setPolicies(null); setUnreadCount(null); }, []);
  const readData = useCallback((controller: AbortController) =>
    memberApi<MemberSession>("session", { signal: controller.signal }).then(async current => {
      if (!isCurrentRequest(controller)) return;
      setSession(current);
      if (!current.authenticated && sessionEndPending.current) announceSessionEnd();
      sessionEndPending.current = false;
      if (!current.authenticated) return;
      const saved = await memberApi<SavedPolicies>("policies", { signal: controller.signal });
      if (isCurrentRequest(controller)) setPolicies(saved);
    }).catch(failure => {
      if (!isCurrentRequest(controller)) return;
      const expired = failure instanceof MemberApiError && failure.status === 401;
      if (expired) clearData();
      setLoginRequired(expired);
      setError(expired ? failure.message : "내 정보를 불러오지 못했어요. 다시 시도해주세요.");
    }), [clearData]);

  useEffect(() => {
    const controller = new AbortController(); active.current = controller;
    void readData(controller).finally(() => {
      if (isCurrentRequest(controller)) setPending(null);
      if (active.current === controller) active.current = null;
    });
    return () => { controller.abort(); active.current?.abort(); };
  }, [readData]);

  async function reloadData() {
    if (active.current || busy || withdrawn) return;
    const controller = new AbortController(); active.current = controller;
    restoreFocus.current = true;
    clearData(); setError(""); setNotice(""); setLoginRequired(false); setPending("load");
    try { await readData(controller); }
    finally {
      if (isCurrentRequest(controller)) setPending(null);
      if (active.current === controller) active.current = null;
    }
  }
  async function runAction(action: MemberAction, policyNumber?: string) {
    if (!session?.authenticated || active.current || busy) return;
    const controller = new AbortController(); active.current = controller;
    restoreFocus.current = true;
    setPending(action); setError(""); setNotice(""); setLoginRequired(false);
    if (action !== "remove") sessionEndPending.current = true;
    try {
      const path = action === "remove" ? `policies/${policyNumber}` : action === "logout" ? "logout" : "account";
      await memberApi(path, { method: action === "logout" ? "POST" : "DELETE", csrf: session.csrfToken, signal: controller.signal });
      if (!isCurrentRequest(controller)) return;
      clearData();
      if (action === "remove") {
        setNotice("저장을 해제하고 대기 중인 알림을 취소했어요."); setPending("load");
        await readData(controller);
      } else {
        sessionEndPending.current = false; announceSessionEnd();
        if (action === "withdraw") setWithdrawn(true);
        else window.location.replace("/");
      }
    } catch (failure) {
      if (!isCurrentRequest(controller)) return;
      clearData();
      const expired = failure instanceof MemberApiError && failure.status === 401;
      setLoginRequired(expired);
      setError(expired ? failure.message : action === "remove" ? "저장 해제 결과를 확인하지 못했어요. 다시 불러와주세요."
        : action === "logout" ? "로그아웃 여부를 확인하지 못했어요. 로그인 상태를 다시 확인해주세요."
        : "탈퇴 완료 여부를 확인하지 못했어요. 로그인 상태를 다시 확인해주세요.");
    } finally {
      if (isCurrentRequest(controller)) setPending(null);
      if (active.current === controller) active.current = null;
    }
  }
  if (withdrawn) return <section className="member-panel" role="status"><h2 ref={completion} tabIndex={-1}>탈퇴가 완료됐어요</h2><p>저장한 정보를 삭제하고 모든 기기에서 로그아웃했어요.</p><Link href="/" className="button-primary">홈으로</Link></section>;
  if (session && !session.authenticated) return <section className="member-panel"><h2>로그인하고 관심 정책을 저장하세요</h2><p>로그인하면 저장한 정책의 마감일과 알림을 볼 수 있어요.</p><Link ref={loginLink} href="/login" className="button-primary">로그인하기</Link><Link href="/policies" className="text-link">정책 둘러보기</Link></section>;
  return <>
    {error && <div className="member-panel" role="alert"><p>{error}</p><div className="member-toolbar">
      <button ref={retryButton} type="button" className="button-secondary" disabled={busy} onClick={reloadData}>다시 불러오기</button>
      {loginRequired && <Link href="/login" className="text-link">로그인하기</Link>}
    </div></div>}
    {notice && <p role="status">{notice}</p>}
    {busy && <p role="status">{pending === "load" ? "내 정책을 불러오고 있어요." : pending === "remove" ? "저장을 해제하고 있어요." : pending === "logout" ? "로그아웃 중이에요." : "탈퇴 처리 중이에요."}</p>}
    {session?.authenticated && <div className="member-toolbar member-account-toolbar"><strong>{session.displayName}님의 정책</strong><Link href="/conditions">내 조건 관리</Link><button ref={refreshButton} type="button" className="text-button" disabled={busy} onClick={reloadData}>새로고침</button><button type="button" className="text-button" disabled={busy} onClick={() => runAction("logout")}>로그아웃</button></div>}
    <nav className="member-tabs" aria-label="내 정책 보기">
      {(["saved", "calendar", "notifications"] as const).map(value => <button type="button" aria-current={tab === value ? "page" : undefined} key={value} onClick={() => setTab(value)}>{value === "saved" ? "관심 정책" : value === "calendar" ? "마감 일정" : `알림${unreadCount ? ` (${unreadCount})` : ""}`}</button>)}
    </nav>
    {policies && !endingSession && tab !== "notifications" && <MemberPolicyList policies={policies.items} calendar={tab === "calendar"}
      filter={calendarFilter} onFilterChange={setCalendarFilter} busy={busy} onRemove={number => runAction("remove", number)} />}
    {session?.authenticated && !endingSession && tab === "notifications" && <details className="member-email-disclosure">
      <summary>이메일 알림 설정</summary><MemberEmailSettings csrf={session.csrfToken} />
    </details>}
    {session?.authenticated && !endingSession && policies && <MemberNotifications csrf={session.csrfToken} active={tab === "notifications"} onUnreadCount={setUnreadCount} />}
    {session?.authenticated && pending !== "logout" && <MemberWithdrawal busy={pending === "withdraw"} disabled={busy} onWithdraw={() => runAction("withdraw")} />}
  </>;
}
