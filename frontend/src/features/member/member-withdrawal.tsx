"use client";

import { useState, type FormEvent } from "react";

export function MemberWithdrawal({ busy, disabled, onWithdraw }: { busy: boolean; disabled: boolean; onWithdraw: () => void }) {
  const [confirmed, setConfirmed] = useState(false);

  function withdraw(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (confirmed && !disabled) onWithdraw();
  }

  return <details className="member-email-disclosure member-withdrawal">
    <summary>회원 탈퇴</summary>
    <form className="member-panel" onSubmit={withdraw}>
      <h2>저장한 정보를 삭제할까요?</h2>
      <p>저장한 조건·관심 정책·일정·알림·이메일 정보를 삭제하고 모든 기기에서 로그아웃해요. 삭제한 정보는 복구할 수 없어요.</p>
      <p className="field-help">발송 중인 메일은 취소할 수 없어요. 카카오·네이버 계정과 서비스 연결은 유지돼요. 다시 로그인하면 새로 가입돼요.</p>
      <label className="withdrawal-confirmation"><input type="checkbox" checked={confirmed} disabled={disabled} required
        onChange={event => setConfirmed(event.target.checked)} />삭제 내용을 확인했어요.</label>
      <button type="submit" className="button-secondary" disabled={!confirmed || disabled}>{busy ? "탈퇴 처리 중…" : "회원 탈퇴하기"}</button>
    </form>
  </details>;
}
