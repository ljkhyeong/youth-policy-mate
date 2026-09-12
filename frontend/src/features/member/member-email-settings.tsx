"use client";

import { useEffect, useRef, useState } from "react";
import { memberApi, MemberApiError, type EmailSettings, type EmailAddress, type EmailCode, type EmailConsent } from "./member-api";

function errorText(failure: unknown) {
  if (failure instanceof MemberApiError) {
    switch (failure.code) {
      case "EMAIL_RATE_LIMITED": return "인증 메일은 1분 간격, 시간당 최대 3회 요청할 수 있어요. 잠시 후 다시 요청해주세요.";
      case "EMAIL_CODE_INVALID": return "인증 코드가 틀렸거나 만료됐어요. 5회 실패했다면 새 코드를 요청해주세요.";
      case "EMAIL_NOT_VERIFIED": return "이메일 인증을 먼저 완료해주세요.";
      case "EMAIL_UNAVAILABLE": return "현재 이메일 알림을 이용할 수 없어요. ‘알림’ 탭은 이용할 수 있어요.";
    }
    return failure.message;
  }
  return "이메일 설정 요청을 완료하지 못했어요. 다시 시도해주세요.";
}

export function MemberEmailSettings({ csrf }: { csrf: string }) {
  const [settings, setSettings] = useState<EmailSettings | null>(null);
  const [address, setAddress] = useState("");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [reload, setReload] = useState(0);
  const active = useRef<AbortController | null>(null);
  const submitting = useRef(false);

  useEffect(() => {
    const controller = new AbortController(); active.current = controller;
    memberApi<EmailSettings>("email-settings", { signal: controller.signal })
      .then(value => { if (!controller.signal.aborted) setSettings(value); })
      .catch(failure => { if (!controller.signal.aborted) setError(errorText(failure)); });
    return () => { controller.abort(); active.current?.abort(); };
  }, [reload]);

  async function change(path: string, method: string, body: EmailAddress | EmailCode | EmailConsent | undefined, message: string) {
    if (submitting.current) return;
    submitting.current = true; active.current?.abort();
    const controller = new AbortController(); active.current = controller;
    setBusy(true); setError(""); setNotice("");
    try {
      await memberApi(path, { method, body, csrf, signal: controller.signal });
      if (controller.signal.aborted) return;
      setAddress(""); setCode(""); setNotice(message); setSettings(null);
      const value = await memberApi<EmailSettings>("email-settings", { signal: controller.signal });
      if (!controller.signal.aborted) setSettings(value);
    } catch (failure) {
      if (!controller.signal.aborted) {
        setError(errorText(failure));
        if (failure instanceof MemberApiError && failure.status === 401) setSettings(null);
        if (failure instanceof MemberApiError && failure.code === "EMAIL_CODE_INVALID") {
          const value = await memberApi<EmailSettings>("email-settings", { signal: controller.signal }).catch(() => null);
          if (value && !controller.signal.aborted) setSettings(value);
        }
      }
    } finally {
      if (!controller.signal.aborted) { setBusy(false); submitting.current = false; }
    }
  }

  return <section className="member-panel email-settings" aria-labelledby="email-settings-title">
    <h2 id="email-settings-title">이메일 알림</h2>
    <p>이메일 인증 후 수신에 동의하면 저장한 정책의 마감과 변경 알림을 받을 수 있어요.</p>
    {error && <div role="alert"><p>{error}</p><button type="button" className="text-button" disabled={busy} onClick={() => { setError(""); setReload(value => value + 1); }}>설정 다시 불러오기</button></div>}
    {notice && <p role="status">{notice}</p>}
    {!settings && !error && <p role="status">이메일 설정을 불러오고 있어요.</p>}
    {settings && <>
      {!settings.available && <p className="field-help">현재 이메일 알림을 이용할 수 없어요. ‘마감 일정’과 ‘알림’ 탭은 이용할 수 있어요.</p>}
      {settings.addressRegistered && <div className="email-current">
        <p><strong>{settings.address || "등록한 이메일 주소"}</strong></p>
        <p>{settings.verified ? "이메일 인증 완료" : "이메일 인증 필요"} · {settings.enabled ? "이메일 알림 켜짐" : "이메일 알림 꺼짐"}</p>
        {settings.deliveryIssue && <p role="status" className="field-help">{settings.deliveryIssue === "COMPLAINED"
          ? "스팸 신고로 이메일 알림을 껐어요. 다시 받으려면 이메일을 인증해주세요."
          : "반송 또는 수신 차단으로 이메일 알림을 껐어요. 주소를 확인하고 다시 인증해주세요."}</p>}
        {settings.verified && <button type="button" className="button-secondary" disabled={busy || (!settings.available && !settings.enabled)}
          onClick={() => change("email-settings", "PUT", { enabled: !settings.enabled }, settings.enabled ? "이메일 알림을 끄고 발송 대기 중인 이메일을 취소했어요." : "이메일 알림을 켰어요. 새 알림부터 이메일로 보내요.")}>
          {settings.enabled ? "이메일 알림 끄기" : "수신 동의하고 알림 켜기"}
        </button>}
        <button type="button" className="text-button" disabled={busy} onClick={() => change("email-settings", "DELETE", undefined, "이메일 주소를 삭제하고 발송 대기 중인 이메일을 취소했어요.")}>이메일 주소 삭제</button>
      </div>}
      {settings.available && <>
        <form onSubmit={event => { event.preventDefault(); void change("email-verification", "POST", { address: address.trim() }, "인증 메일을 요청했어요. 받은 메일의 8자리 코드를 입력해주세요."); }}>
          <fieldset disabled={busy}>
            <label htmlFor="notification-email">{settings.addressRegistered ? "인증 메일을 받을 이메일 주소" : "알림을 받을 이메일 주소"}</label>
            <input id="notification-email" type="email" autoComplete="email" required maxLength={254} value={address} onChange={event => setAddress(event.target.value)} aria-describedby="email-address-help" />
            <p id="email-address-help" className="field-help">재요청하면 이전 코드는 사용할 수 없고, 알림 수신 동의도 해제돼요. 1분 간격, 시간당 최대 3회 요청할 수 있어요.</p>
            <button type="submit" className="button-secondary">인증 메일 요청</button>
          </fieldset>
        </form>
        {!settings.verified && settings.addressRegistered && <>
          <p className="field-help" role="status">{settings.verificationDelivery === "DELIVERED" ? "수신 메일 서버에 전달됐어요. 받은편지함과 스팸함을 확인해주세요."
            : settings.verificationDelivery === "DELAYED" ? "수신 메일 서버의 처리가 지연되고 있어요. 잠시 후 메일함을 확인해주세요."
            : settings.verificationDelivery === "SENT" ? "메일 발송 요청이 접수됐어요. 받은편지함과 스팸함을 확인해주세요."
            : settings.verificationDelivery === "UNKNOWN" ? "메일 발송 여부를 확인할 수 없어요. 메일함을 확인하고, 메일이 없으면 새 코드를 요청해주세요."
            : settings.verificationDelivery === "FAILED" ? "인증 메일을 보내지 못했어요. 잠시 후 새 코드를 요청해주세요."
            : settings.verificationDelivery === "PENDING" || settings.verificationDelivery === "SENDING" ? "인증 메일을 보내는 중이에요. 잠시 후 메일함을 확인해주세요." : "새 인증 메일을 요청해주세요."}</p>
          <button type="button" className="text-button" disabled={busy} onClick={() => setReload(value => value + 1)}>발송 상태 새로고침</button>
          <form onSubmit={event => { event.preventDefault(); void change("email-verification/confirm", "POST", { code }, "이메일 인증을 마쳤어요. 수신에 동의하면 이메일 알림을 받을 수 있어요."); }}>
            <fieldset disabled={busy || !settings.verificationExpiresAt}>
              <label htmlFor="email-verification-code">8자리 인증 코드</label>
              <input id="email-verification-code" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{8}" maxLength={8} required value={code} onChange={event => setCode(event.target.value)} aria-describedby="email-code-help" />
              <p id="email-code-help" className="field-help">요청 후 10분간 유효하고 최대 5회 입력할 수 있어요.</p>
              <button type="submit" className="button-secondary">이메일 인증</button>
            </fieldset>
          </form>
        </>}
      </>}
      <p className="field-help">이메일 알림을 끄거나 주소를 삭제해도 ‘알림’ 탭의 안내는 유지돼요. 발송 중인 이메일은 취소할 수 없어요.</p>
    </>}
  </section>;
}
