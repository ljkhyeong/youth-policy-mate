"use client";
/* eslint-disable @next/next/no-html-link-for-pages -- 보정 후 서버에서 최신 권한과 개정을 다시 조회한다. */

import { useId, useRef, useState, type FormEvent } from "react";
import type { components } from "@/generated/policy-api";
import { MemberApiError } from "@/features/member/member-api";
import type { CorrectionItem, CorrectionPolicy } from "./load-collection-exceptions";
import { useAdminMutation } from "./use-admin-mutation";

type Create = components["schemas"]["PolicyCorrectionRequest"];
type Resolve = components["schemas"]["PolicyCorrectionResolution"];
type Props = { policy: CorrectionPolicy; correction?: never } | { correction: CorrectionItem; policy?: never };

export function PolicyCorrectionForm({ policy, correction }: Props) {
  const id = useId();
  const [field, setField] = useState<Create["field"]>("TITLE");
  const [value, setValue] = useState("");
  const [action, setAction] = useState<Resolve["action"]>(correction?.status === "CONFLICT" ? "KEEP" : "USE_SOURCE");
  const [reason, setReason] = useState("");
  const { busy, mutate } = useAdminMutation();
  const [blocked, setBlocked] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [message, setMessage] = useState("");
  const [result, setResult] = useState<CorrectionItem | null>(null);
  const pending = useRef<Create | Resolve | null>(null);
  const locked = busy || blocked || uncertain || Boolean(result);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || blocked || result) return;
    pending.current ??= policy
      ? { requestId: crypto.randomUUID(), policyNumber: policy.policyNumber, expectedRevision: policy.revision, field, value: value.trim(), reason: reason.trim() }
      : { requestId: crypto.randomUUID(), expectedRevision: correction.currentRevision, reviewSnapshotId: correction.reviewSnapshotId, action, reason: reason.trim() };
    setMessage("");
    try {
      const response = await mutate<CorrectionItem>(policy ? "policy-corrections" : `policy-corrections/${correction.id}/resolutions`, pending.current);
      if (!response) return;
      setResult(response);
      setUncertain(false);
    } catch (error) {
      if (error instanceof MemberApiError && [400, 401, 403, 409, 413].includes(error.status)) {
        pending.current = null;
        setUncertain(false);
        setBlocked([401, 403, 409].includes(error.status));
        setMessage(error.status === 401 || error.status === 403 ? "로그인과 관리자 권한을 확인한 뒤 다시 열어주세요."
          : error.status === 409 ? "정책이나 보정 상태가 바뀌었습니다. 최신 내용을 다시 확인해주세요."
            : "보정 값과 사유를 확인해주세요. 현재 값과 같은 값은 적용할 수 없습니다.");
      } else {
        setUncertain(true);
        setMessage("처리 결과를 확인하지 못했습니다. 아래 ‘처리 결과 다시 확인’을 눌러주세요.");
      }
    }
  }

  return <form onSubmit={submit} aria-label={policy ? "정책 보정" : `정책 ${correction.policyNumber} 보정 처리`}>
    {policy ? <>
      <div className="form-field"><label htmlFor={`${id}-field`}>보정 항목</label>
        <select id={`${id}-field`} value={field} disabled={locked} onChange={event => { setField(event.target.value as Create["field"]); setValue(""); }}>
          <option value="TITLE">정책명</option><option value="ORGANIZATION">운영 기관</option>
        </select>
        <p className="field-help">현재 값: {field === "TITLE" ? policy.content.title : policy.content.organization}</p>
      </div>
      <div className="form-field"><label htmlFor={`${id}-value`}>보정 값</label>
        <input id={`${id}-value`} required maxLength={500} value={value} readOnly={locked} onChange={event => setValue(event.target.value)} />
      </div>
    </> : <div className="form-field"><label htmlFor={`${id}-action`}>처리 방법</label>
      <select id={`${id}-action`} value={action} disabled={locked} onChange={event => setAction(event.target.value as Resolve["action"])}>
        {correction.status === "CONFLICT" && <option value="KEEP">보정 유지</option>}
        <option value="USE_SOURCE">보정 해제 · 원본 적용</option>
      </select>
      <p className="field-help">{correction.status === "CONFLICT" ? "새 수집 원본을 기준으로 처리합니다. 다른 항목의 변경도 함께 반영됩니다." : "보정을 해제하고 현재 버전의 원본 값을 적용합니다."}</p>
    </div>}
    <div className="form-field"><label htmlFor={`${id}-reason`}>{policy ? "보정 사유" : "처리 사유"}</label>
      <textarea id={`${id}-reason`} required maxLength={500} rows={2} value={reason} readOnly={locked} onChange={event => setReason(event.target.value)} />
      <p className="field-help">확인 근거를 간단히 적어주세요. 개인정보·인증키는 입력하지 마세요.</p>
    </div>
    <button className="button-primary disabled:cursor-default disabled:opacity-50" type="submit"
      disabled={busy || blocked || Boolean(result) || !reason.trim() || Boolean(policy && !value.trim())}>
      {busy ? "처리 확인 중…" : result ? "결과 확인 완료" : blocked ? "최신 상태 확인 필요" : uncertain ? "처리 결과 다시 확인" : policy ? "보정 적용" : "적용"}
    </button>
    {message && <p role="alert">{message}</p>}
    {result && <p role="status">{policy ? "보정을 적용했습니다." : "보정 처리를 완료했습니다. 남은 수집 실패 항목을 재처리해주세요."}</p>}
    {(blocked || result) && <div className="form-actions"><a className="text-link" href="/admin/collection-exceptions/corrections">최신 보정 이력 보기</a>
      <a className="text-link" href="/admin/collection-exceptions">수집 실패 목록 보기</a></div>}
  </form>;
}
