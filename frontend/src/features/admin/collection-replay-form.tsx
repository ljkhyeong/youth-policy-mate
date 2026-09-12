"use client";
/* eslint-disable @next/next/no-html-link-for-pages -- 재처리 후 서버에서 최신 권한과 이력을 다시 조회한다. */

import { useRef, useState, type FormEvent } from "react";
import type { components } from "@/generated/policy-api";
import { MemberApiError } from "@/features/member/member-api";
import { replayLabels } from "./collection-replay-labels";
import { useAdminMutation } from "./use-admin-mutation";

type Request = components["schemas"]["CollectionReplayRequest"];
type Result = components["schemas"]["CollectionReplayResult"];

export function CollectionReplayForm({ runId, itemIndex, attempts }: { runId: string; itemIndex: number; attempts: number }) {
  const [reason, setReason] = useState("");
  const { busy, mutate } = useAdminMutation();
  const [blocked, setBlocked] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [message, setMessage] = useState("");
  const [result, setResult] = useState<Result | null>(null);
  const pending = useRef<Request | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || blocked || result) return;
    pending.current ??= { requestId: crypto.randomUUID(), expectedAttempts: attempts, reason: reason.trim() };
    setMessage("");
    try {
      const response = await mutate<Result>(`collection-replays/${runId}/${itemIndex}`, pending.current);
      if (!response) return;
      setResult(response);
      setUncertain(false);
    } catch (error) {
      if (error instanceof MemberApiError && [400, 401, 403, 409, 413].includes(error.status)) {
        pending.current = null;
        setUncertain(false);
        setBlocked([401, 403, 409].includes(error.status));
        setMessage(error.status === 401 || error.status === 403 ? "로그인 또는 관리자 권한을 확인한 뒤 최신 항목을 다시 열어주세요."
          : error.status === 409 ? "항목 상태가 바뀌었거나 저장 원본을 처리할 수 없습니다. 최신 항목과 재처리 이력을 확인해주세요."
            : "재처리 사유와 입력 길이를 확인해주세요.");
      } else {
        setUncertain(true);
        setMessage("처리 결과를 확인하지 못했습니다. 아래 ‘재처리 결과 다시 확인’을 눌러주세요.");
      }
    }
  }

  return <section className="member-panel" aria-labelledby="replay-heading">
    <h2 id="replay-heading">실패 항목 재처리</h2>
    <p className="field-help">저장된 원본으로 이 항목을 다시 처리합니다. 사유와 처리 결과는 관리자 이력에 남습니다.</p>
    <form onSubmit={submit}>
      <div className="form-field">
        <label htmlFor="replay-reason">재처리 사유</label>
        <textarea id="replay-reason" required maxLength={500} rows={3} value={reason}
          readOnly={busy || uncertain || Boolean(result) || blocked}
          onChange={event => setReason(event.target.value)} aria-describedby="replay-help" />
        <p id="replay-help" className="field-help">조치하거나 확인한 내용을 적어주세요. 개인정보·인증키는 입력하지 마세요.</p>
      </div>
      <button className="button-primary disabled:cursor-default disabled:opacity-50" type="submit" disabled={busy || blocked || Boolean(result) || !reason.trim()}>
        {busy ? "처리 확인 중…" : result ? "결과 확인 완료" : blocked ? "최신 상태 확인 필요" : uncertain ? "재처리 결과 다시 확인" : "재처리"}
      </button>
    </form>
    {result && <div role="status">
      <p><strong>{replayLabels[result.outcome]}</strong>{result.policyRevision !== null && ` · 처리 후 버전 ${result.policyRevision}`}</p>
      {result.outcome === "INVALID_ITEM" && <p>원본이 검증을 통과하지 못했습니다. 원본과 처리 규칙을 확인해주세요.</p>}
      {result.outcome === "CORRECTION_CONFLICT" && <p>보정 관리에서 새 원본과 보정 값을 확인한 뒤 다시 처리해주세요.</p>}
      {result.outcome === "STALE" && <p>이후에 수집한 내용이 있어 현재 공개 내용을 유지했습니다.</p>}
    </div>}
    {message && <p role="alert">{message}</p>}
    <div className="form-actions">
      <a className="text-link" href="/admin/collection-exceptions/replays">재처리 이력 보기</a>
      {(blocked || result) && <a className="text-link" href="/admin/collection-exceptions">최신 실패 목록 보기</a>}
    </div>
  </section>;
}
