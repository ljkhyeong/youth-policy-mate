"use client";

import { useEffect, useId, useRef, useState } from "react";
import { PolicyContentComparison } from "@/features/policies/policy-content-comparison";
import { memberApi, MemberApiError, type SavedPolicyChanges as Changes } from "./member-api";

const timestamp = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", dateStyle: "medium", timeStyle: "short" });

export function SavedPolicyChanges({ policyNumber }: { policyNumber: string }) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [data, setData] = useState<Changes | null>(null);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const restoreFocus = useRef(false);
  const retryButton = useRef<HTMLButtonElement>(null);
  const comparison = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!restoreFocus.current || (!data && !error)) return;
    (data ? comparison : retryButton).current?.focus();
    restoreFocus.current = false;
  }, [data, error]);
  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    memberApi<Changes>(`policies/${policyNumber}/changes`, { signal: controller.signal })
      .then(result => { if (!controller.signal.aborted) setData(result); })
      .catch(failure => {
        if (controller.signal.aborted) return;
        setError(failure instanceof MemberApiError && failure.status === 404
          ? "저장한 정책을 확인할 수 없어요. 내 정책 목록을 새로고침해주세요."
          : failure instanceof Error ? failure.message : "변경 내용을 불러오지 못했어요.");
      });
    return () => controller.abort();
  }, [open, policyNumber, reload]);

  return <div className="saved-policy-changes">
    <button type="button" className="text-button" aria-expanded={open} aria-controls={id}
      onClick={() => { restoreFocus.current = false; setData(null); setError(""); setOpen(value => !value); }}>{open ? "변경 내용 접기" : "변경 내용 보기"}</button>
    <div ref={comparison} id={id} hidden={!open} role="region" aria-label="정책 변경 내용" tabIndex={-1}>
      {open && !data && !error && <p role="status">변경 내용을 불러오고 있어요.</p>}
      {error && <div role="alert"><p>{error}</p><button ref={retryButton} type="button" className="text-button"
        onClick={() => { restoreFocus.current = true; setError(""); setReload(value => value + 1); }}>다시 불러오기</button></div>}
      {data && <>
        <p className="field-help">같은 정책번호의 저장 당시 내용과 현재 수집 내용을 비교합니다.</p>
        <p className="field-help">저장일: <time dateTime={data.savedAt}>{timestamp.format(new Date(data.savedAt))}</time><br />
          현재 원본 수집: <time dateTime={data.current.sourceCapturedAt}>{timestamp.format(new Date(data.current.sourceCapturedAt))}</time> (한국 시간)</p>
        <PolicyContentComparison previous={data.saved.content} current={data.current.content} previousLabel="저장 당시" currentLabel="현재"
          fields={["title", "description", "category", "organization", "applicationPeriod", "sections", "links"]} />
      </>}
    </div>
  </div>;
}
