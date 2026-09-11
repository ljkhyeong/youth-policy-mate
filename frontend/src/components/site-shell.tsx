import Link from "next/link";

export function SiteShell({ children, active }: { children: React.ReactNode; active?: "home" | "policies" | "conditions" | "my" }) {
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">본문으로 이동</a>
      <header className="app-header">
        <div className="app-header-inner">
          <Link href="/" className="brand-link" aria-label="청년정책메이트 홈">
            <span className="brand-mark" aria-hidden="true">청</span>
            <span>청년정책메이트</span>
          </Link>
          <nav aria-label="주 메뉴" className="desktop-nav">
            <NavLink href="/" active={active === "home"} icon="home">홈</NavLink>
            <NavLink href="/policies" active={active === "policies"} icon="search">정책 찾기</NavLink>
            <NavLink href="/conditions" active={active === "conditions"} icon="person">내 조건</NavLink>
            <NavLink href="/my" active={active === "my"} icon="saved">내 정책</NavLink>
          </nav>
        </div>
      </header>
      {children}
      <footer className="app-footer">
        신청 자격과 접수 여부는 공식 신청처에서 확인해주세요.
      </footer>
      <nav aria-label="모바일 주 메뉴" className="mobile-tabbar">
        <NavLink href="/" active={active === "home"} icon="home">홈</NavLink>
        <NavLink href="/policies" active={active === "policies"} icon="search">정책 찾기</NavLink>
        <NavLink href="/conditions" active={active === "conditions"} icon="person">내 조건</NavLink>
        <NavLink href="/my" active={active === "my"} icon="saved">내 정책</NavLink>
      </nav>
    </div>
  );
}

function NavLink({ href, active, icon, children }: {
  href: string;
  active: boolean;
  icon: "home" | "person" | "search" | "saved";
  children: React.ReactNode;
}) {
  return (
    <Link href={href} aria-current={active ? "page" : undefined} className="nav-link">
      <NavIcon name={icon} />
      <span>{children}</span>
    </Link>
  );
}

function NavIcon({ name }: { name: "home" | "person" | "search" | "saved" }) {
  if (name === "saved") return <svg className="nav-icon" aria-hidden="true" viewBox="0 0 24 24" fill="none"><path d="M6 4h12v17l-6-4-6 4V4Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" /></svg>;
  if (name === "search") return <svg className="nav-icon" aria-hidden="true" viewBox="0 0 24 24" fill="none"><circle cx="10.5" cy="10.5" r="6.5" stroke="currentColor" strokeWidth="1.8" /><path d="m15.5 15.5 4.5 4.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>;
  if (name === "home") {
    return (
      <svg className="nav-icon" aria-hidden="true" viewBox="0 0 24 24" fill="none">
        <path d="M4 10.8 12 4l8 6.8v8.1a1.1 1.1 0 0 1-1.1 1.1H5.1A1.1 1.1 0 0 1 4 18.9v-8.1Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
        <path d="M9.2 20v-5.7h5.6V20" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
      </svg>
    );
  }
  return (
    <svg className="nav-icon" aria-hidden="true" viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="8" r="3.5" stroke="currentColor" strokeWidth="1.8" />
      <path d="M5.5 20c.5-4 2.8-6 6.5-6s6 2 6.5 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}
