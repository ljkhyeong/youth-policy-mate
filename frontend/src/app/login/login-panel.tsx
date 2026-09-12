"use client";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { memberApi, type MemberSession } from "@/features/member/member-api";
import { getLoginDestination, readLoginDestination, rememberLoginDestination } from "@/features/member/login-destination";

export function LoginPanel({ admin = false, policy, member, loginFailed = false }: { admin?: boolean; policy?: string; member?: string; loginFailed?: boolean }) {
  const [session, setSession] = useState<MemberSession | null>(null);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [previousDestination, setPreviousDestination] = useState("/my");
  const restoreFocus = useRef(false);
  const panel = useRef<HTMLElement>(null);
  const resumeLogin = loginFailed && !admin && !policy && !member;
  const destination = resumeLogin ? previousDestination : getLoginDestination(admin, policy, member);
  useEffect(() => {
    if (!restoreFocus.current || (!session && !error)) return;
    panel.current?.querySelector<HTMLElement>("button, a")?.focus();
    restoreFocus.current = false;
  }, [session, error]);
  useEffect(() => {
    const controller = new AbortController();
    memberApi<MemberSession>("session", { signal: controller.signal }).then(result => {
      if (controller.signal.aborted) return;
      if (resumeLogin) setPreviousDestination(readLoginDestination());
      setSession(result);
    }).catch(() => {
      if (!controller.signal.aborted) setError("로그인 방법을 불러오지 못했어요. 다시 시도해주세요.");
    });
    return () => controller.abort();
  }, [reload, resumeLogin]);
  return <section ref={panel} className="member-panel">
    {error && <div role="alert"><p className="field-error">{error}</p>
      <button type="button" className="button-secondary" onClick={() => {
        restoreFocus.current = true; setError(""); setReload(value => value + 1);
      }}>다시 불러오기</button>
    </div>}
    {session && !session.authenticated && loginFailed && <p role="alert" className="field-error">로그인을 완료하지 못했어요. 로그인 방법을 선택해 다시 시도해주세요.</p>}
    {!session && !error && <p role="status">로그인 방법을 확인하고 있어요.</p>}
    {session?.authenticated ? <a className="button-primary" href={destination}>{destination === "/admin/collection-exceptions" ? "수집 오류·보정 관리" : destination.startsWith("/my") ? "내 정책으로 이동" : "정책으로 돌아가기"}</a> : <>
      {session?.providers.map(provider => <a key={provider.id} className="button-primary button-block" href={provider.url}
        onClick={() => { if (!resumeLogin) rememberLoginDestination(admin, policy, member); }}>{provider.name}로 로그인</a>)}
      {session?.providers.length === 0 && <div className="availability-note"><div><strong>지금은 로그인할 수 없어요</strong><p>지금은 로그인 없이 정책 검색과 조건 확인을 이용할 수 있어요.</p></div></div>}
      <p className="data-retention-note">로그인 후 원하는 조건과 정책의 저장 버튼을 눌러주세요. 자동 저장되지는 않아요.</p>
    </>}
    <Link className="text-link" href="/policies">로그인 없이 정책 찾기</Link>
  </section>;
}
