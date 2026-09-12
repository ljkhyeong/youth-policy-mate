"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import type { components } from "@/generated/policy-api";
import { memberApi, MemberApiError } from "@/features/member/member-api";

type ProviderStatus = components["schemas"]["AdminEmailProviderStatus"];
const events = {
  SENT: "발송 요청 접수", DELIVERED: "수신 서버 전달", DELIVERY_DELAYED: "전달 지연", BOUNCED: "반송",
  COMPLAINED: "스팸 신고", SUPPRESSED: "수신 차단", FAILED: "발송 실패", OPENED: "열람 이벤트",
  CLICKED: "링크 클릭 이벤트", SCHEDULED: "발송 예약", CANCELED: "발송 취소", QUEUED: "발송 대기", UNKNOWN: "상태 미확인",
} satisfies Record<ProviderStatus["event"], string>;
const checkedTime = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", dateStyle: "medium", timeStyle: "medium" });
const errors: Record<string, string> = {
  EMAIL_PROVIDER_ID_MISSING: "조회할 Resend 발송 ID가 없습니다. 목록을 새로고침해주세요.",
  EMAIL_PROVIDER_NOT_CONFIGURED: "Resend 조회용 API 키가 설정되지 않았습니다.",
  EMAIL_PROVIDER_ACCESS_DENIED: "Resend 조회용 API 키의 권한을 확인해주세요.",
  EMAIL_PROVIDER_NOT_FOUND: "Resend에서 발송 기록을 찾지 못했습니다. 전달 실패를 뜻하지 않습니다.",
  EMAIL_PROVIDER_RATE_LIMITED: "Resend 조회 한도에 도달했습니다. 잠시 후 다시 조회해주세요.",
};

export function EmailProviderStatus({ id }: { id: string }) {
  const [data, setData] = useState<ProviderStatus | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [loginRequired, setLoginRequired] = useState(false);
  const activeRequest = useRef<AbortController | null>(null);
  const button = useRef<HTMLButtonElement>(null);
  const restoreFocus = useRef(false);
  useEffect(() => () => activeRequest.current?.abort(), []);
  useEffect(() => {
    if (busy || !restoreFocus.current) return;
    restoreFocus.current = false;
    if (button.current?.closest("details")?.open
      && (document.activeElement === document.body || document.activeElement === button.current)) button.current.focus();
  }, [busy]);

  async function load() {
    if (activeRequest.current) return;
    const controller = new AbortController(); activeRequest.current = controller;
    const location = window.location.pathname + window.location.search;
    const current = () => !controller.signal.aborted && location === window.location.pathname + window.location.search;
    restoreFocus.current = true;
    setBusy(true); setData(null); setError(""); setLoginRequired(false);
    try {
      const result = await memberApi<ProviderStatus>(`email-deliveries/${id}/provider-status`, { signal: controller.signal });
      if (current()) setData(result);
    } catch (failure) {
      if (!current()) return;
      const expired = failure instanceof MemberApiError && failure.status === 401;
      setLoginRequired(expired);
      setError(expired ? "관리자 계정으로 다시 로그인해주세요."
        : failure instanceof MemberApiError && failure.status === 403 ? "관리자 권한이 없습니다."
          : failure instanceof MemberApiError && failure.code && errors[failure.code]
            ? errors[failure.code] : "Resend 상태를 불러오지 못했습니다. 다시 조회해주세요.");
    } finally {
      if (activeRequest.current === controller) activeRequest.current = null;
      if (current()) setBusy(false);
    }
  }

  return <div>
    <button ref={button} type="button" className="button-secondary" disabled={busy} onClick={load}>
      {busy ? "Resend 조회 중…" : "Resend 상태 조회"}
    </button>
    {busy && <p role="status">공급자의 최신 상태를 확인하고 있습니다.</p>}
    {error && <p role="alert">{error} {loginRequired && <Link href="/login?next=admin" className="text-link">로그인하기</Link>}</p>}
    {data && <div role="status">
      <p><strong>Resend: {events[data.event]}</strong></p>
      <p className="field-help">조회 시각: <time dateTime={data.checkedAt}>{checkedTime.format(new Date(data.checkedAt))}</time> (서울)</p>
      <p className="field-help">조회 결과입니다. 발송 기록과 수신 설정은 변경하지 않습니다.</p>
      {["OPENED", "CLICKED"].includes(data.event) && <p className="field-help">자동 감지 이벤트일 수 있으며 실제 사용자 열람을 보장하지 않습니다.</p>}
      {data.event === "UNKNOWN" && <p className="field-help">이 상태로 접수·전달 여부를 판단할 수 없습니다.</p>}
    </div>}
  </div>;
}
