"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { memberApi, type BasicConditions, type MemberSession, type SavedConditions } from "./member-api";
import type { ConditionDraft } from "@/features/conditions/condition-draft";

export function ConditionMemberControls({ input, onLoad, onSuggestBirthDate }: { input?: BasicConditions; onLoad: (value: ConditionDraft) => void; onSuggestBirthDate?: (value: string) => void }) {
  const [session, setSession] = useState<MemberSession | null>(null);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    memberApi<MemberSession>("session", { signal: controller.signal }).then(value => { if (!controller.signal.aborted) { setSession(value); if (value.authenticated && value.suggestedBirthDate) onSuggestBirthDate?.(value.suggestedBirthDate); } }).catch(() => {});
    return () => controller.abort();
  }, [onSuggestBirthDate]);
  async function act(action: "load" | "save" | "clear") {
    if (!session) return;
    setBusy(true); setMessage("");
    try {
      if (action === "load") {
        const saved = await memberApi<SavedConditions>("conditions");
        if (saved.conditions) { onLoad(saved.conditions); setMessage("저장한 조건을 불러왔어요. 현재 상황과 맞는지 확인해주세요."); }
        else setMessage("저장한 조건이 없어요.");
      } else {
        await memberApi("conditions", { method: action === "save" ? "PUT" : "DELETE", body: action === "save" ? input : undefined, csrf: session.csrfToken });
        setMessage(action === "save" ? "확인한 조건을 내 계정에 저장했어요." : "계정에 저장한 조건을 삭제했어요.");
      }
    } catch (error) { setMessage(error instanceof Error ? error.message : "요청에 실패했어요."); }
    finally { setBusy(false); }
  }
  if (!session?.authenticated) return <p className="data-retention-note">입력은 자동 저장하지 않아요. <Link href="/login">로그인</Link>하면 확인한 조건을 계정에 저장할 수 있어요.</p>;
  return <div className="condition-member-controls">
    <p><strong>{session.displayName}님의 조건</strong> · 저장은 아래 버튼을 눌렀을 때만 해요.</p>
    <div className="form-actions">
      {input && <button type="button" className="button-primary" disabled={busy} onClick={() => act("save")}>확인한 조건을 내 계정에 저장</button>}
      {!input && <button type="button" className="button-secondary" disabled={busy} onClick={() => act("load")}>저장한 내 조건 불러오기</button>}
      {!input && session.suggestedBirthDate && <p className="field-help">카카오에서 제공한 양력 생년월일은 {session.suggestedBirthDate}예요. 빈 생년월일 칸에 입력했어요. 본인 정보가 맞는지 확인하거나 수정해주세요.</p>}
      <button type="button" className="text-button" disabled={busy} onClick={() => act("clear")}>계정에 저장한 조건 삭제</button>
    </div><p role="status">{message}</p>
  </div>;
}
