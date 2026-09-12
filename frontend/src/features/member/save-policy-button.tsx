"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { memberApi, MemberApiError, type MemberSession, type SavedPolicies } from "./member-api";

type SaveState = { phase: "loading" }
  | { phase: "unavailable"; message: string; loginRequired: boolean }
  | { phase: "ready" | "saving"; session: MemberSession; saved: boolean };

export function SavePolicyButton({ policyNumber }: { policyNumber: string }) {
  const router = useRouter();
  const [state, setState] = useState<SaveState>({ phase: "loading" });
  const [message, setMessage] = useState("");
  const [reload, setReload] = useState(0);
  const active = useRef<AbortController | null>(null);
  const submitting = useRef(false);
  const restoreFocus = useRef(false);
  const button = useRef<HTMLButtonElement>(null);
  const retryButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!restoreFocus.current || state.phase === "loading" || state.phase === "saving") return;
    (state.phase === "ready" ? button : retryButton).current?.focus();
    restoreFocus.current = false;
  }, [state.phase]);

  useEffect(() => {
    const controller = new AbortController(); active.current = controller;
    (async () => {
      try {
        const current = await memberApi<MemberSession>("session", { signal: controller.signal });
        if (controller.signal.aborted) return;
        const policies = current.authenticated ? await memberApi<SavedPolicies>("policies", { signal: controller.signal }) : null;
        if (!controller.signal.aborted) setState({ phase: "ready", session: current, saved: policies?.items.some(item => item.policyNumber === policyNumber) ?? false });
      } catch (failure) {
        if (!controller.signal.aborted) setState({ phase: "unavailable", loginRequired: failure instanceof MemberApiError && failure.status === 401,
          message: failure instanceof MemberApiError && failure.status === 401 ? failure.message : "저장 상태를 불러오지 못했어요. 다시 불러와주세요." });
      }
    })();
    return () => { controller.abort(); active.current?.abort(); };
  }, [policyNumber, reload]);

  function retry() {
    restoreFocus.current = true; setState({ phase: "loading" }); setMessage(""); setReload(value => value + 1);
  }

  function login() {
    sessionStorage.setItem("ypm-pending-policy", policyNumber);
    router.push(`/login?policy=${policyNumber}`);
  }

  async function toggle() {
    if (state.phase !== "ready" || submitting.current) return;
    if (!state.session.authenticated) { login(); return; }
    submitting.current = true; active.current?.abort();
    const controller = new AbortController(); active.current = controller;
    restoreFocus.current = true; setState({ ...state, phase: "saving" }); setMessage("");
    try {
      await memberApi(`policies/${policyNumber}`, { method: state.saved ? "DELETE" : "PUT", csrf: state.session.csrfToken, signal: controller.signal });
      if (controller.signal.aborted) return;
      setState({ ...state, saved: !state.saved });
      setMessage(state.saved ? "저장을 해제하고 대기 중인 알림을 취소했어요." : "정책을 저장했어요. 확인된 마감일은 ‘마감 일정’에 표시돼요.");
    } catch (failure) {
      if (!controller.signal.aborted) setState({ phase: "unavailable", loginRequired: failure instanceof MemberApiError && failure.status === 401,
        message: failure instanceof MemberApiError && failure.status === 401 ? failure.message : "처리 결과를 확인하지 못했어요. 저장 상태를 다시 불러와주세요." });
    } finally { submitting.current = false; }
  }

  const saved = (state.phase === "ready" || state.phase === "saving") && state.saved;
  return <div className="member-save-panel">
    <button ref={button} className={saved ? "button-secondary" : "button-primary"} type="button" disabled={state.phase !== "ready"}
      aria-pressed={(state.phase === "ready" || state.phase === "saving") && state.session.authenticated ? state.saved : undefined} onClick={toggle}>
      {state.phase === "loading" ? "저장 상태 확인 중…" : state.phase === "saving" ? saved ? "저장 해제 중…" : "저장 중…"
        : state.phase === "unavailable" ? "저장 상태 확인 필요" : saved ? "저장 해제" : "관심 정책 저장"}
    </button>
    <a className="text-link" href="/my">저장한 정책 보기</a>
    {state.phase === "unavailable" && <div role="alert"><p>{state.message}</p>
      <button ref={retryButton} type="button" className="text-button" onClick={retry}>저장 상태 다시 불러오기</button>
      {state.loginRequired && <button type="button" className="button-secondary" onClick={login}>로그인하기</button>}
    </div>}
    <p role="status">{message}</p>
  </div>;
}
