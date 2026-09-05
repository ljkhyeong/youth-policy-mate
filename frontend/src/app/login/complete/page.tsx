"use client";
import { announceAccountChange } from "@/features/member/account-transitions";
import { useEffect } from "react";

export default function LoginComplete() {
  useEffect(() => {
    const policy = sessionStorage.getItem("ypm-pending-policy");
    sessionStorage.removeItem("ypm-pending-policy");
    announceAccountChange();
    window.location.replace(policy && /^[0-9]{1,100}$/.test(policy) ? `/policies/${policy}` : "/my");
  }, []);
  return <main className="member-main"><p role="status">로그인한 화면으로 이동하고 있어요.</p></main>;
}
