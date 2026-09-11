"use client";
import { clearConfirmedBirth } from "@/features/conditions/confirmed-birth";
import { useEffect } from "react";

let windowId: string | undefined;
const sender = () => windowId ??= crypto.randomUUID();

export function announceAccountChange() {
  clearConfirmedBirth();
  if (typeof BroadcastChannel === "undefined") return;
  const channel = new BroadcastChannel("ypm-account");
  channel.postMessage({ type: "changed", sender: sender() });
  channel.close();
}

export function AccountTransitions() {
  useEffect(() => {
    if (typeof BroadcastChannel === "undefined") return;
    const channel = new BroadcastChannel("ypm-account");
    channel.onmessage = event => {
      if (event.data?.type === "changed" && event.data.sender !== sender()) { clearConfirmedBirth(); window.location.reload(); }
    };
    // 뒤로 가기로 이전 계정의 화면 스냅샷을 다시 표시하지 않는다.
    const restore = (event: PageTransitionEvent) => { if (event.persisted) window.location.reload(); };
    window.addEventListener("pageshow", restore);
    return () => { channel.close(); window.removeEventListener("pageshow", restore); };
  }, []);
  return null;
}
