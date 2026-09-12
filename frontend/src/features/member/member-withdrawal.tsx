"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { memberApi } from "./member-api";

export function MemberWithdrawal({ csrf, onDeleted }: { csrf: string; onDeleted: () => void }) {
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef<AbortController | null>(null);
  useEffect(() => () => pending.current?.abort(), []);

  async function withdraw(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!confirmed || pending.current) return;
    const controller = new AbortController();
    pending.current = controller;
    setBusy(true); setError("");
    try {
      await memberApi("account", { method: "DELETE", csrf, signal: controller.signal });
      if (!controller.signal.aborted) onDeleted();
    } catch {
      if (!controller.signal.aborted) {
        setError("탈퇴 완료 여부를 확인하지 못했어요. 로그인 상태를 다시 확인한 뒤 시도해주세요.");
        setBusy(false);
      }
    } finally {
      pending.current = null;
    }
  }

  return <details className="member-email-disclosure member-withdrawal">
    <summary>회원 탈퇴</summary>
    <form className="member-panel" onSubmit={withdraw}>
      <h2>저장한 정보를 삭제할까요?</h2>
      <p>저장한 조건·관심 정책·일정·알림·이메일 정보를 삭제하고 모든 기기에서 로그아웃해요. 삭제한 정보는 복구할 수 없어요.</p>
      <p className="field-help">발송 중인 메일은 취소할 수 없어요. 카카오·네이버 계정과 서비스 연결은 유지돼요. 다시 로그인하면 새로 가입돼요.</p>
      <label className="withdrawal-confirmation"><input type="checkbox" checked={confirmed} disabled={busy} required
        onChange={event => setConfirmed(event.target.checked)} />삭제 내용을 확인했어요.</label>
      {error && <p role="alert">{error}</p>}
      <button type="submit" className="button-secondary" disabled={!confirmed || busy}>{busy ? "탈퇴 처리 중…" : "회원 탈퇴하기"}</button>
    </form>
  </details>;
}
