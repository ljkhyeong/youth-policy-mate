"use client";
import { announceAccountChange } from "@/features/member/account-transitions";
import { useEffect, useRef } from "react";
import { consumeLoginDestination } from "@/features/member/login-destination";

export default function LoginComplete() {
  const redirected = useRef(false);
  useEffect(() => {
    if (redirected.current) return;
    redirected.current = true;
    const destination = consumeLoginDestination();
    announceAccountChange();
    window.location.replace(destination);
  }, []);
  return <main className="member-main"><p role="status">로그인한 화면으로 이동하고 있어요.</p></main>;
}
