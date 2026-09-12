"use client";
/* eslint-disable @next/next/no-html-link-for-pages -- 처리 후 최신 권한·원문·규칙을 서버에서 다시 조회한다. */

import { useState } from "react";
import type { components } from "@/generated/policy-api";
import { memberApi } from "@/features/member/member-api";
import type { RuleReviewDetail } from "./load-collection-exceptions";

import { RuleActionForm } from "./policy-rule-action-form";
import { RuleEditor } from "./policy-rule-editor";

type RuleFile = components["schemas"]["PolicyRuleFile"];
type Props = { policyNumber: string; revision: number; versionId?: string; expectedRuleVersion?: string };

export function RuleDraftForm({ policyNumber, revision }: Props) {
  return <section className="member-panel" aria-labelledby="rule-draft-heading">
    <h2 id="rule-draft-heading">새 초안 등록</h2>
    <p className="field-help">검토한 규칙 파일을 등록합니다. 초안을 저장해도 현재 질문은 바뀌지 않습니다. 기존 파일을 수정할 때는 새 버전명과 원문·연도·기간을 함께 확인하세요.</p>
    <RuleActionForm policyNumber={policyNumber} revision={revision} />
  </section>;
}

export function RuleVersionControls({ policyNumber, revision, contentHash, expectedRuleVersion, version }: {
  policyNumber: string; revision: number; contentHash: string; expectedRuleVersion: string; version: RuleReviewDetail["versions"][number];
}) {
  const [definition, setDefinition] = useState<RuleFile["definition"] | null>(null);
  const [editing, setEditing] = useState(false);
  const file = definition ? JSON.stringify(definition, null, 2) : null;
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  async function load() {
    if (busy) return;
    setBusy(true); setMessage("");
    try { setDefinition((await memberApi<RuleFile>(`policy-rule-reviews/${policyNumber}/versions/${version.id}`)).definition); }
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
      {!editing && <button className="button-secondary" type="button" onClick={() => setEditing(true)}>수정해서 새 초안 만들기</button>}
    </>}
    {message && <p role="alert">{message}</p>}
    {editing && definition && <RuleEditor definition={definition} revision={revision} contentHash={contentHash} />}
    {!editing && version.state === "DRAFT" && (version.canPublish ? file !== null ? <RuleActionForm policyNumber={policyNumber} revision={revision}
      versionId={version.id} expectedRuleVersion={expectedRuleVersion} /> : <p className="field-help">전체 규칙을 확인하면 적용할 수 있습니다.</p>
      : <p className="field-help">원문이 다르거나 적용 기간이 아닙니다. 최신 원문과 기간을 확인해주세요.</p>)}
  </div>;
}
