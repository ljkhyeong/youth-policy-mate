"use client";
import { announceAccountChange } from "@/features/member/account-transitions";

import Link from "next/link";
import { useEffect, useState } from "react";
import { memberApi, type MemberSession, type SavedPolicies, type Notifications } from "@/features/member/member-api";

export function MemberDashboard() {
  const [session, setSession] = useState<MemberSession | null>(null);
  const [policies, setPolicies] = useState<SavedPolicies | null>(null);
  const [notifications, setNotifications] = useState<Notifications | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [reload, setReload] = useState(0);
  const [tab, setTab] = useState<"saved" | "calendar" | "notifications">("saved");
  useEffect(() => {
    const controller = new AbortController();
    (async () => {
      try {
        const current = await memberApi<MemberSession>("session", { signal: controller.signal });
        if (controller.signal.aborted) return;
        setSession(current);
        if (!current.authenticated) return;
        const saved = await memberApi<SavedPolicies>("policies", { signal: controller.signal });
        const messages = await memberApi<Notifications>("notifications", { signal: controller.signal });
        if (!controller.signal.aborted) { setPolicies(saved); setNotifications(messages); }
      } catch { if (!controller.signal.aborted) setError("내 정보를 불러오지 못했어요. 다시 시도해주세요."); }
    })();
    return () => controller.abort();
  }, [reload]);
  function reloadData() {
    setError(""); setPolicies(null); setNotifications(null); setSession(null);
    setReload(value => value + 1);
  }
  async function mutate(path: string, method: string) {
    if (!session) return;
    setBusy(true); setError("");
    try { await memberApi(path, { method, csrf: session.csrfToken }); reloadData(); }
    catch (failure) { setError(failure instanceof Error ? failure.message : "요청에 실패했어요."); }
    finally { setBusy(false); }
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
  if (session && !session.authenticated) return <section className="member-panel"><h2>로그인하고 관심 정책을 저장하세요</h2><p>내 일정과 알림은 로그인한 계정에서만 확인할 수 있어요.</p><Link href="/login" className="button-primary">로그인하기</Link><Link href="/policies" className="text-link">공개 정책 둘러보기</Link></section>;
  return <>
    {error && <div className="member-panel" role="alert"><p>{error}</p><button className="button-secondary" onClick={reloadData}>다시 불러오기</button></div>}
    {!policies && !error && <p role="status">내 정책을 불러오고 있어요.</p>}
    {session?.authenticated && <div className="member-toolbar"><strong>{session.displayName}님의 정책</strong><Link href="/conditions">내 조건 관리</Link><button type="button" className="text-button" disabled={busy} onClick={logout}>로그아웃</button></div>}
    <nav className="member-tabs" aria-label="내 정책 보기">
      {(["saved", "calendar", "notifications"] as const).map(value => <button type="button" aria-current={tab === value ? "page" : undefined} key={value} onClick={() => setTab(value)}>{value === "saved" ? "관심 정책" : value === "calendar" ? "마감 일정" : "알림"}</button>)}
    </nav>
    {policies && tab !== "notifications" && <div className="member-list">
      {policies.items.length === 0 && <section className="member-panel"><h2>저장한 정책이 아직 없어요</h2><p>정책 상세에서 관심 정책을 저장하면 이곳에서 이어서 볼 수 있어요.</p><Link href="/policies" className="button-primary">정책 찾기</Link></section>}
      {policies.items.map(policy => <article className="member-panel" key={policy.policyNumber}>
        {tab === "calendar" && <p className="member-deadline">{policy.deadline.date ? `${policy.deadline.date} 마감` : "마감일 확인 필요"}</p>}
        <h2><Link href={`/policies/${policy.policyNumber}`}>{policy.title}</Link></h2>
        <p className="policy-period">신청기간: {policy.applicationPeriod}</p>
        <p>{policy.deadline.note}</p>
        {policy.savedRevision !== policy.currentRevision && <p className="member-change">저장한 뒤 정책 내용이 바뀌었어요. 최신 안내를 확인해주세요.</p>}
        {tab === "calendar" && policy.deadline.date && <p className="field-help">다가오는 마감 7·3·1일 전 서비스 내 알림을 예약해요. 지난 날짜의 알림은 보내지 않아요.</p>}
        <button className="text-button" type="button" disabled={busy} onClick={() => mutate(`policies/${policy.policyNumber}`, "DELETE")}>관심 정책 해제</button>
      </article>)}
    </div>}
    {notifications && tab === "notifications" && <div className="member-list">
      {notifications.items.length === 0 && <section className="member-panel"><h2>도착한 알림이 없어요</h2><p>저장한 정책의 내용 변경과 마감 안내가 이곳에 표시돼요.</p></section>}
      {notifications.items.map(notification => <article className="member-panel" key={notification.id} data-read={notification.read}>
        <p className="page-label">{notification.read ? "읽은 알림" : "새 알림"}</p><h2><Link href={`/policies/${notification.policyNumber}`}>{notification.title}</Link></h2><p>{notification.message}</p>
        {!notification.read && <button type="button" className="text-button" disabled={busy} onClick={() => mutate(`notifications/${notification.id}/read`, "POST")}>읽음 처리</button>}
      </article>)}
    </div>}
  </>;
}
