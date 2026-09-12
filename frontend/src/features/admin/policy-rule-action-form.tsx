"use client";
import { useId, useRef, useState, type FormEvent, type ReactNode } from "react";
import type { components } from "@/generated/policy-api";
import { MemberApiError } from "@/features/member/member-api";
import { useAdminMutation } from "./use-admin-mutation";

type Draft = components["schemas"]["PolicyRuleDraftRequest"];
type Publish = components["schemas"]["PolicyRulePublishRequest"];
type Result = components["schemas"]["PolicyRuleActionResult"];
type Props = { policyNumber: string; revision: number; versionId?: string; expectedRuleVersion?: string;
  draftJson?: string; children?: ReactNode };

export function RuleActionForm({ policyNumber, revision, versionId, expectedRuleVersion, draftJson, children }: Props) {
  const id = useId();
  const [definitionJson, setDefinitionJson] = useState("");
  const [fileBusy, setFileBusy] = useState(false);
  const [reason, setReason] = useState("");
  const [verified, setVerified] = useState(false);
  const { busy, mutate } = useAdminMutation();
  const [blocked, setBlocked] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [message, setMessage] = useState("");
  const [result, setResult] = useState<Result | null>(null);
  const pending = useRef<Draft | Publish | null>(null);
  const locked = busy || fileBusy || blocked || uncertain || Boolean(result);
  const publishing = Boolean(versionId);
  const candidate = draftJson ?? definitionJson;

  async function readFile(file?: File) {
    setDefinitionJson(""); setMessage("");
    if (!file) return;
    if (file.size > 131072 || !file.name.toLowerCase().endsWith(".json")) {
      setMessage("128KB 이하의 규칙 파일(.json)을 선택해주세요."); return;
    }
    setFileBusy(true);
    try { setDefinitionJson(await file.text()); }
    catch { setMessage("파일을 읽지 못했습니다. 다시 선택해주세요."); }
    finally { setFileBusy(false); }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || fileBusy || blocked || result) return;
    if (!pending.current && (!reason.trim() || (publishing ? !verified : !candidate))) return;
    pending.current ??= publishing
      ? { requestId: crypto.randomUUID(), expectedRevision: revision, expectedRuleVersion: expectedRuleVersion!, reason: reason.trim() }
      : { requestId: crypto.randomUUID(), expectedRevision: revision, definitionJson: candidate, reason: reason.trim() };
    setMessage("");
    try {
      const response = await mutate<Result>(`policy-rule-reviews/${policyNumber}/${publishing ? `versions/${versionId}/publish` : "drafts"}`, pending.current);
      if (!response) return;
      setResult(response);
      setUncertain(false);
    } catch (error) {
      if (error instanceof MemberApiError && [400, 401, 403, 404, 409, 413].includes(error.status)) {
        pending.current = null; setUncertain(false); setBlocked([401, 403, 404, 409].includes(error.status));
        setMessage(error.status === 401 || error.status === 403 ? "로그인과 관리자 권한을 확인한 뒤 다시 열어주세요."
          : error.status === 404 || error.status === 409 ? "원문·기간·버전 또는 처리 상태가 바뀌었습니다. 최신 내용을 다시 확인해주세요."
            : "규칙 파일의 형식·질문·기간·용량과 사유를 확인해주세요. 파일은 128KB까지 등록할 수 있습니다.");
      } else { setUncertain(true); setMessage("처리 결과를 확인하지 못했습니다. 입력을 유지한 채 ‘처리 결과 다시 확인’을 눌러주세요."); }
    }
  }

  return <form onSubmit={submit} onInvalid={event => {
    let parent = (event.target as HTMLElement).parentElement;
    while (parent) { if (parent instanceof HTMLDetailsElement) parent.open = true; parent = parent.parentElement; }
  }} aria-label={publishing ? "초안 적용" : "규칙 초안 등록"}>
    {children && <fieldset disabled={locked} className="rule-editor-fields">{children}</fieldset>}
    {!publishing && draftJson === undefined && <div className="form-field"><label htmlFor={`${id}-file`}>규칙 파일</label>
      <input id={`${id}-file`} type="file" accept=".json,application/json" disabled={locked} onChange={event => { void readFile(event.target.files?.[0]); }} />
      <p className="field-help">JSON 형식 · 최대 128KB · 개인 조건이나 인증키를 넣지 마세요.</p></div>}
    {publishing && <label className="rule-review-confirm"><input type="checkbox" checked={verified} disabled={locked}
      onChange={event => setVerified(event.target.checked)} />공식 원문·질문·판정표·적용 기간을 확인했습니다.</label>}
    <div className="form-field"><label htmlFor={`${id}-reason`}>{publishing ? "적용 사유" : "등록 사유"}</label>
      <textarea id={`${id}-reason`} required rows={2} maxLength={500} value={reason} readOnly={locked}
        onChange={event => setReason(event.target.value)} /></div>
    <button className="button-primary disabled:cursor-default disabled:opacity-50" type="submit"
      disabled={busy || fileBusy || blocked || Boolean(result) || !reason.trim() || (publishing ? !verified : !candidate)}>
      {busy ? "처리 확인 중…" : result ? "처리 완료" : blocked ? "최신 상태 확인 필요" : uncertain ? "처리 결과 다시 확인" : publishing ? "이 초안 적용" : "초안 저장"}
    </button>
    {message && <p role="alert">{message}</p>}
    {result && <p role="status">{result.action === "DRAFT" ? "초안을 저장했습니다. 최신 내용에서 질문과 판정표를 검토한 뒤 적용하세요." : "규칙을 적용했습니다. 최신 내용과 공개 질문을 확인하세요."}</p>}
    {(result || blocked) && <div className="form-actions"><a className="text-link" href={`/admin/collection-exceptions/rules/${policyNumber}`}>최신 내용 확인</a></div>}
  </form>;
}
