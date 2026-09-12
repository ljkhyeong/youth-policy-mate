"use client";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { memberApi, MemberApiError, type BasicConditions, type MemberSession, type SavedConditions } from "./member-api";
import type { ConditionDraft } from "@/features/conditions/condition-draft";

type SessionState = { phase: "loading" }
  | { phase: "ready"; session: MemberSession }
  | { phase: "error"; message: string; loginRequired: boolean };
type ConditionAction = "load" | "save" | "clear";

export function ConditionMemberControls({ input, prepareLoad, onSuggestBirthDate }: {
  input?: BasicConditions;
  prepareLoad: () => (value: ConditionDraft) => boolean;
  onSuggestBirthDate?: (value: string) => void;
}) {
  const [state, setState] = useState<SessionState>({ phase: "loading" });
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState<ConditionAction | null>(null);
  const [reload, setReload] = useState(0);
  const active = useRef<AbortController | null>(null);
  const restoreFocus = useRef(false);
  const retryButton = useRef<HTMLButtonElement>(null);
  const actionButton = useRef<HTMLButtonElement>(null);
  const loginLink = useRef<HTMLAnchorElement>(null);

  useEffect(() => {
    if (!restoreFocus.current || state.phase === "loading") return;
    (state.phase === "error" ? retryButton : state.session.authenticated ? actionButton : loginLink).current?.focus();
    restoreFocus.current = false;
  }, [state]);

  useEffect(() => {
    const controller = new AbortController(); active.current = controller;
    memberApi<MemberSession>("session", { signal: controller.signal }).then(session => {
      if (controller.signal.aborted) return;
      setState({ phase: "ready", session });
      if (session.authenticated && session.suggestedBirthDate) onSuggestBirthDate?.(session.suggestedBirthDate);
    }).catch(error => {
      if (!controller.signal.aborted) setState({ phase: "error", message: "로그인 상태를 확인하지 못했어요. 다시 시도해주세요.", loginRequired: error instanceof MemberApiError && error.status === 401 });
    }).finally(() => { if (active.current === controller) active.current = null; });
    return () => { controller.abort(); active.current?.abort(); };
  }, [onSuggestBirthDate, reload]);

  function retry() {
    restoreFocus.current = true; setState({ phase: "loading" }); setMessage(""); setReload(value => value + 1);
  }

  async function act(action: ConditionAction) {
    if (state.phase !== "ready" || !state.session.authenticated || active.current) return;
    const controller = new AbortController(); active.current = controller;
    setBusy(action); setMessage("");
    try {
      if (action === "load") {
        const applySaved = prepareLoad();
        const saved = await memberApi<SavedConditions>("conditions", { signal: controller.signal });
        if (controller.signal.aborted) return;
        if (saved.conditions) setMessage(applySaved(saved.conditions)
          ? "저장한 조건을 불러왔어요. 현재 상황과 맞는지 확인해주세요."
          : "입력 내용이 바뀌어 저장한 조건을 적용하지 않았어요.");
        else setMessage("저장한 조건이 없어요.");
      } else {
        await memberApi("conditions", { method: action === "save" ? "PUT" : "DELETE", body: action === "save" ? input : undefined, csrf: state.session.csrfToken, signal: controller.signal });
        if (controller.signal.aborted) return;
        setMessage(action === "save" ? "내 조건을 계정에 저장했어요." : "계정에 저장한 조건을 삭제했어요.");
      }
    } catch (error) {
      if (controller.signal.aborted) return;
      if (error instanceof MemberApiError && error.status === 401) {
        restoreFocus.current = true;
        setState({ phase: "error", message: error.message, loginRequired: true });
      } else setMessage(action === "load" ? "조건을 불러오지 못했어요. 다시 시도해주세요." : "처리 결과를 확인하지 못했어요. 다시 시도해주세요.");
    } finally {
      if (!controller.signal.aborted) setBusy(null);
      if (active.current === controller) active.current = null;
    }
  }
  if (state.phase === "loading") return <p role="status" className="data-retention-note">로그인 상태 확인 중…</p>;
  if (state.phase === "error") return <div className="condition-member-controls">
    <p role="alert">{state.message}</p>
    <div className="form-actions">
      <button ref={retryButton} type="button" className="button-secondary" onClick={retry}>로그인 상태 다시 확인</button>
      {state.loginRequired && <Link className="text-link" href="/login">로그인하기</Link>}
    </div>
    <p className="field-help">조건은 계속 직접 입력할 수 있어요.</p>
  </div>;
  const { session } = state;
  if (!session.authenticated) return <p className="data-retention-note"><Link ref={loginLink} href="/login">로그인</Link>하면 입력한 조건을 저장하고 다시 불러올 수 있어요.</p>;
  return <div className="condition-member-controls">
    <p><strong>{session.displayName}님의 조건</strong> · 저장은 아래 버튼을 눌렀을 때만 해요.</p>
    <div className="form-actions">
      {input && <button ref={actionButton} type="button" className="button-primary" disabled={busy !== null} onClick={() => act("save")}>{busy === "save" ? "저장 중…" : "내 조건 저장"}</button>}
      {!input && <button ref={actionButton} type="button" className="button-secondary" disabled={busy !== null} onClick={() => act("load")}>{busy === "load" ? "조건 불러오는 중…" : "저장한 조건 불러오기"}</button>}
      {!input && session.suggestedBirthDate && <p className="field-help">카카오에서 제공한 생년월일: {session.suggestedBirthDate} · 양력. 본인 정보가 맞는지 확인해주세요.</p>}
      <button type="button" className="text-button" disabled={busy !== null} onClick={() => act("clear")}>{busy === "clear" ? "삭제 중…" : "저장한 조건 삭제"}</button>
    </div><p role="status">{message}</p>
  </div>;
}
