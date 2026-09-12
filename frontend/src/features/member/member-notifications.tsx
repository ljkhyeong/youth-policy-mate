"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { memberApi, type NotificationFilter, type Notifications } from "./member-api";

const receivedAt = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul", dateStyle: "medium", timeStyle: "short",
});

export function MemberNotifications({ csrf, active, onUnreadCount }: {
  csrf: string; active: boolean; onUnreadCount: (count: number | null) => void;
}) {
  const [data, setData] = useState<Notifications | null>(null);
  const [page, setPage] = useState(1);
  const [filter, setFilter] = useState<NotificationFilter>("ALL");
  const [reload, setReload] = useState(0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const readRequest = useRef<AbortController | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    memberApi<Notifications>(`notifications?page=${page}&pageSize=20&filter=${filter}`, { signal: controller.signal })
      .then(result => {
        if (controller.signal.aborted) return;
        setData(result);
        onUnreadCount(result.unreadCount);
      })
      .catch(failure => {
        if (controller.signal.aborted) return;
        onUnreadCount(null);
        setError(failure instanceof Error ? failure.message : "알림을 불러오지 못했어요.");
      });
    return () => controller.abort();
  }, [page, filter, reload, onUnreadCount]);

  useEffect(() => () => readRequest.current?.abort(), []);

  function changePage(next: number) {
    setData(null); setError(""); setPage(next);
  }

  async function markRead(id: string) {
    const controller = new AbortController();
    readRequest.current = controller;
    setBusy(true); setError("");
    try {
      await memberApi(`notifications/${id}/read`, { method: "POST", csrf, signal: controller.signal });
      if (controller.signal.aborted) return;
      setData(null);
      if (filter === "UNREAD") setPage(1);
      setReload(value => value + 1);
    } catch (failure) {
      if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "읽음 처리를 완료하지 못했어요.");
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  if (!active) return null;
  return <section aria-label="서비스 알림" className="member-list">
    <div className="member-panel">
      <div className="rule-review-field"><label htmlFor="notification-filter">알림 보기</label>
        <select id="notification-filter" className="member-calendar-filter" value={filter} disabled={busy}
          onChange={event => { changePage(1); setFilter(event.target.value as NotificationFilter); }}>
          <option value="ALL">전체 알림</option><option value="UNREAD">안 읽은 알림</option>
        </select></div>
      <p className="field-help">최신순 · 받은 시각은 한국 시간입니다.</p>
      {data && <p role="status">{data.total}건 · 안 읽은 알림 {data.unreadCount}건</p>}
    </div>
    {error && <div className="member-panel" role="alert"><p>{error}</p>
      {!data && <button type="button" className="button-secondary" onClick={() => { setError(""); setReload(value => value + 1); }}>다시 불러오기</button>}
    </div>}
    {!data && !error && <p role="status">알림을 불러오고 있어요.</p>}
    {data?.items.length === 0 && <div className="member-panel">
      <h2>{page > 1 ? "이 페이지에 알림이 없어요" : filter === "UNREAD" ? "안 읽은 알림이 없어요" : "도착한 알림이 없어요"}</h2>
      {page > 1 ? <button type="button" className="button-secondary" onClick={() => changePage(1)}>첫 페이지 보기</button>
        : <p>저장한 정책의 내용 변경과 마감 안내가 이곳에 표시돼요.</p>}
    </div>}
    {data?.items.map(notification => <article className="member-panel" key={notification.id} data-read={notification.read}>
      <p className="page-label">{notification.read ? "읽은 알림" : "새 알림"}</p>
      <h2><Link href={`/policies/${notification.policyNumber}`}>{notification.title}</Link></h2>
      <p>{notification.message}</p>
      <p className="field-help">받은 시각: <time dateTime={notification.createdAt}>{receivedAt.format(new Date(notification.createdAt))}</time></p>
      {!notification.read && <button type="button" className="text-button" disabled={busy} onClick={() => markRead(notification.id)}>읽음으로 표시</button>}
    </article>)}
    {data && (page > 1 || data.hasNext) && <nav className="policy-pagination" aria-label="알림 페이지">
      <button type="button" className="button-secondary" disabled={busy || page === 1} onClick={() => changePage(page - 1)}>이전</button>
      <span>{page}페이지</span>
      <button type="button" className="button-secondary" disabled={busy || !data.hasNext} onClick={() => changePage(page + 1)}>다음</button>
    </nav>}
  </section>;
}
