"use client";

import Link from "next/link";
import { getMemberHref } from "./member-location";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";

function subscribe(change: () => void) {
  window.addEventListener("hashchange", change);
  return () => window.removeEventListener("hashchange", change);
}
const fragment = () => window.location.hash.slice(1);

export function EmailUnsubscribe() {
  const token = useSyncExternalStore(subscribe, fragment, () => null);
  if (token === null) return <p role="status">메일 링크를 확인하고 있어요.</p>;
  return <UnsubscribeForm key={token} token={token} />;
}

function UnsubscribeForm({ token }: { token: string }) {
  const [state, setState] = useState<"ready" | "sending" | "done" | "invalid">("ready");
  const [error, setError] = useState("");
  const pending = useRef<AbortController | null>(null);
  const completion = useRef<HTMLHeadingElement>(null);
  const actionButton = useRef<HTMLButtonElement>(null);
  useEffect(() => () => pending.current?.abort(), []);
  useEffect(() => {
    if (state === "done" || state === "invalid") completion.current?.focus();
    else if (error) actionButton.current?.focus();
  }, [state, error]);

  async function unsubscribe() {
    if (pending.current) return;
    const controller = new AbortController(); pending.current = controller;
    setState("sending"); setError("");
    try {
      const response = await fetch(`/api/member/email-unsubscribe/${token}`, {
        method: "POST", credentials: "omit", cache: "no-store", signal: controller.signal,
      });
      if (controller.signal.aborted) return;
      if (response.status === 404) { setState("invalid"); return; }
      if (!response.ok) throw new Error();
      setState("done");
    } catch {
      if (!controller.signal.aborted) { setState("ready"); setError("수신 해제 결과를 확인하지 못했어요. 다시 시도해주세요."); }
    } finally { pending.current = null; }
  }

  if (!/^[A-Za-z0-9_-]{43}$/.test(token) || state === "invalid") return <section className="member-panel">
    <h2 ref={completion} tabIndex={-1}>사용할 수 없는 링크예요</h2><p>최근 받은 메일의 링크를 열거나 로그인 후 이메일 알림을 꺼주세요.</p>
    <Link href={getMemberHref("email")} className="button-secondary">내 알림 설정으로</Link>
  </section>;
  if (state === "done") return <section className="member-panel" role="status">
    <h2 ref={completion} tabIndex={-1}>이메일 알림을 껐어요</h2>
    <p>관심 정책과 서비스 내 알림은 유지돼요. 이후 주소를 바꿨다면 새 주소의 설정은 바뀌지 않아요.</p>
    <Link href="/" className="button-primary">홈으로</Link>
  </section>;
  return <section className="member-panel">
    <h2>이메일 알림을 끌까요?</h2>
    <p>이 메일을 받은 주소의 정책 알림을 중단해요. 관심 정책과 서비스 내 알림은 유지돼요.</p>
    <p className="field-help">발송 중인 메일은 취소할 수 없어요. 주소를 바꿨다면 새 주소의 설정은 유지돼요.</p>
    {error && <p role="alert">{error}</p>}
    <button ref={actionButton} type="button" className="button-primary" disabled={state === "sending"} onClick={unsubscribe}>
      {state === "sending" ? "수신 해제 중…" : "이메일 알림 끄기"}
    </button>
  </section>;
}
