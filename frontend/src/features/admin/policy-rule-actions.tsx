"use client";
/* eslint-disable @next/next/no-html-link-for-pages -- 처리 후 최신 권한·원문·규칙을 서버에서 다시 조회한다. */

import { useId, useRef, useState, type FormEvent } from "react";
import type { components } from "@/generated/policy-api";
import { MemberApiError, memberApi, type MemberSession } from "@/features/member/member-api";
import type { RuleReviewDetail } from "./load-collection-exceptions";

type Draft = components["schemas"]["PolicyRuleDraftRequest"];
type Publish = components["schemas"]["PolicyRulePublishRequest"];
type Result = components["schemas"]["PolicyRuleActionResult"];
type RuleFile = components["schemas"]["PolicyRuleFile"];
type Props = { policyNumber: string; revision: number; versionId?: string; expectedRuleVersion?: string };

export function RuleDraftForm({ policyNumber, revision }: Props) {
  return <section className="member-panel" aria-labelledby="rule-draft-heading">
    <h2 id="rule-draft-heading">새 초안 등록</h2>
    <p className="field-help">검토한 규칙 파일을 등록합니다. 초안을 저장해도 현재 질문은 바뀌지 않습니다. 기존 파일을 수정할 때는 새 버전명과 원문·연도·기간을 함께 확인하세요.</p>
    <RuleActionForm policyNumber={policyNumber} revision={revision} />
  </section>;
}

function RuleActionForm({ policyNumber, revision, versionId, expectedRuleVersion }: Props) {
  const id = useId();
  const [definitionJson, setDefinitionJson] = useState("");
  const [fileBusy, setFileBusy] = useState(false);
  const [reason, setReason] = useState("");
  const [verified, setVerified] = useState(false);
  const [busy, setBusy] = useState(false);
  const [blocked, setBlocked] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [message, setMessage] = useState("");
  const [result, setResult] = useState<Result | null>(null);
  const pending = useRef<Draft | Publish | null>(null);
  const locked = busy || fileBusy || blocked || uncertain || Boolean(result);
  const publishing = Boolean(versionId);

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
    if (!pending.current && (!reason.trim() || (publishing ? !verified : !definitionJson))) return;
    pending.current ??= publishing
      ? { requestId: crypto.randomUUID(), expectedRevision: revision, expectedRuleVersion: expectedRuleVersion!, reason: reason.trim() }
      : { requestId: crypto.randomUUID(), expectedRevision: revision, definitionJson, reason: reason.trim() };
    setBusy(true); setMessage("");
    try {
      const session = await memberApi<MemberSession>("session");
      if (!session.authenticated) throw new MemberApiError(401, "로그인 필요");
      setResult(await memberApi<Result>(`policy-rule-reviews/${policyNumber}/${publishing ? `versions/${versionId}/publish` : "drafts"}`,
        { method: "POST", csrf: session.csrfToken, body: pending.current }));
      setUncertain(false);
    } catch (error) {
      if (error instanceof MemberApiError && [400, 401, 403, 404, 409, 413].includes(error.status)) {
        pending.current = null; setUncertain(false); setBlocked([401, 403, 404, 409].includes(error.status));
        setMessage(error.status === 401 || error.status === 403 ? "로그인과 관리자 권한을 확인한 뒤 다시 열어주세요."
          : error.status === 404 || error.status === 409 ? "원문·기간·버전 또는 처리 상태가 바뀌었습니다. 최신 내용을 다시 확인해주세요."
            : "규칙 파일의 형식·질문·기간·용량과 사유를 확인해주세요. 파일은 128KB까지 등록할 수 있습니다.");
      } else { setUncertain(true); setMessage("처리 결과를 확인하지 못했습니다. 입력을 유지한 채 ‘처리 결과 다시 확인’을 눌러주세요."); }
    } finally { setBusy(false); }
  }

  return <form onSubmit={submit} aria-label={publishing ? "초안 적용" : "규칙 초안 등록"}>
    {!publishing && <div className="form-field"><label htmlFor={`${id}-file`}>규칙 파일</label>
      <input id={`${id}-file`} type="file" accept=".json,application/json" disabled={locked} onChange={event => { void readFile(event.target.files?.[0]); }} />
      <p className="field-help">JSON 형식 · 최대 128KB · 개인 조건이나 인증키를 넣지 마세요.</p></div>}
    {publishing && <label className="rule-review-confirm"><input type="checkbox" checked={verified} disabled={locked}
      onChange={event => setVerified(event.target.checked)} />공식 원문·질문·판정표·적용 기간을 확인했습니다.</label>}
    <div className="form-field"><label htmlFor={`${id}-reason`}>{publishing ? "적용 사유" : "등록 사유"}</label>
      <textarea id={`${id}-reason`} required rows={2} maxLength={500} value={reason} readOnly={locked}
        onChange={event => setReason(event.target.value)} /></div>
    <button className="button-primary disabled:cursor-default disabled:opacity-50" type="submit"
      disabled={busy || fileBusy || blocked || Boolean(result) || !reason.trim() || (publishing ? !verified : !definitionJson)}>
      {busy ? "처리 확인 중…" : result ? "처리 완료" : blocked ? "최신 상태 확인 필요" : uncertain ? "처리 결과 다시 확인" : publishing ? "이 초안 적용" : "초안 저장"}
    </button>
    {message && <p role="alert">{message}</p>}
    {result && <p role="status">{result.action === "DRAFT" ? "초안을 저장했습니다. 최신 내용에서 질문과 판정표를 검토한 뒤 적용하세요." : "규칙을 적용했습니다. 최신 내용과 공개 질문을 확인하세요."}</p>}
    {(result || blocked) && <div className="form-actions"><a className="text-link" href={`/admin/collection-exceptions/rules/${policyNumber}`}>최신 내용 확인</a></div>}
  </form>;
}

export function RuleVersionControls({ policyNumber, revision, expectedRuleVersion, version }: {
  policyNumber: string; revision: number; expectedRuleVersion: string; version: RuleReviewDetail["versions"][number];
}) {
  const [file, setFile] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  async function load() {
    if (busy) return;
    setBusy(true); setMessage("");
    try { setFile((await memberApi<RuleFile>(`policy-rule-reviews/${policyNumber}/versions/${version.id}`)).definitionJson); }
    catch { setMessage("파일을 불러오지 못했습니다. 로그인과 관리자 권한을 확인한 뒤 다시 시도해주세요."); }
    finally { setBusy(false); }
  }
  function download() {
    if (!file) return;
    const url = URL.createObjectURL(new Blob([file], { type: "application/json" }));
    const link = document.createElement("a"); link.href = url; link.download = `policy-rule-${version.id}.json`; link.click(); URL.revokeObjectURL(url);
  }
  return <div className="rule-version-controls">
    {file === null ? <button className="button-secondary" type="button" onClick={() => { void load(); }} disabled={busy}>
      {busy ? "규칙 확인 중…" : "전체 규칙 확인"}</button> : <>
      <details><summary>전체 판정표·연령 기준·예외 보기</summary><pre className="exception-raw" tabIndex={0} aria-label="전체 규칙 파일">{file}</pre></details>
      <button className="button-secondary" type="button" onClick={download}>규칙 파일 받기</button>
    </>}
    {message && <p role="alert">{message}</p>}
    {version.state === "DRAFT" && (version.canPublish ? file !== null ? <RuleActionForm policyNumber={policyNumber} revision={revision}
      versionId={version.id} expectedRuleVersion={expectedRuleVersion} /> : <p className="field-help">전체 규칙을 확인하면 적용할 수 있습니다.</p>
      : <p className="field-help">원문이 다르거나 적용 기간이 아닙니다. 최신 원문과 기간을 확인해주세요.</p>)}
  </div>;
}
