import Link from "next/link";

export function SiteShell({ children, active }: { children: React.ReactNode; active: "home" | "conditions" }) {
  return (
    <div className="mx-auto flex min-h-svh max-w-6xl flex-col px-6 sm:px-10">
      <a className="skip-link" href="#main-content">본문으로 이동</a>
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-stone-300 py-6">
        <Link href="/" className="text-lg font-bold tracking-tight">청년정책메이트</Link>
        <nav aria-label="주 메뉴" className="flex items-center gap-6 text-sm">
          <Link href="/" aria-current={active === "home" ? "page" : undefined} className="nav-link">서비스 소개</Link>
          <Link href="/conditions" aria-current={active === "conditions" ? "page" : undefined} className="nav-link">내 조건</Link>
        </nav>
      </header>
      {children}
      <footer className="border-t border-stone-300 py-6 text-xs leading-6 text-stone-600">
        서비스 준비 중 · 실제 정책 신청과 최종 자격 확인은 공식 신청처에서 진행합니다.
      </footer>
    </div>
  );
}
