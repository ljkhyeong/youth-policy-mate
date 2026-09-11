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
  return <main className="member-main"><p role="status">로그인을 마쳤어요. 잠시만 기다려주세요.</p></main>;
}
