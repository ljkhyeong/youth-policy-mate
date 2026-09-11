"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { memberApi, type MemberSession } from "@/features/member/member-api";
import { rememberLoginDestination } from "@/features/member/login-destination";

export function LoginPanel({ admin = false }: { admin?: boolean }) {
  const [session, setSession] = useState<MemberSession | null>(null);
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    memberApi<MemberSession>("session", { signal: controller.signal }).then(result => {
      if (controller.signal.aborted) return;
      setSession(result);
      if (new URLSearchParams(window.location.search).has("error")) setError("로그인을 완료하지 못했어요. 다시 시도하거나 정책을 둘러보세요.");
    }).catch(() => {
      if (!controller.signal.aborted) setError("로그인 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.");
    });
    return () => controller.abort();
  }, []);
  return <section className="member-panel">
    {error && <p role="alert" className="field-error">{error}</p>}
    {!session && !error && <p role="status">로그인 방법을 확인하고 있어요.</p>}
    {session?.authenticated ? <a className="button-primary" href={admin ? "/admin/collection-exceptions" : "/my"}>{admin ? "수집 예외 확인" : "내 정책으로 이동"}</a> : <>
      {session?.providers.map(provider => <a key={provider.id} className="button-primary button-block" href={provider.url}
        onClick={() => rememberLoginDestination(admin)}>{provider.name}로 로그인</a>)}
      {session?.providers.length === 0 && <div className="availability-note"><div><strong>로그인 기능을 준비 중이에요</strong><p>지금은 로그인 없이 정책 검색과 조건 확인을 이용할 수 있어요.</p></div></div>}
      <p className="data-retention-note">로그인 후 원하는 조건과 정책의 저장 버튼을 눌러주세요. 자동 저장되지는 않아요.</p>
    </>}
    <Link className="text-link" href="/policies">로그인 없이 정책 찾기</Link>
  </section>;
}
