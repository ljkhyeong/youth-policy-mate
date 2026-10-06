import Link from "next/link";
import { ThemeSwitch } from "@/components/theme-switch";

type Section = "home" | "policies" | "conditions" | "my";
const sections: readonly { key: Section; href: string; label: string }[] = [
  { key: "home", href: "/", label: "홈" }, { key: "policies", href: "/policies", label: "정책 찾기" },
  { key: "conditions", href: "/conditions", label: "내 조건" }, { key: "my", href: "/my", label: "내 정책" },
];

// 모든 폭에서 같은 상단 글자 메뉴를 쓴다. 현재 메뉴는 굵은 글자와 먹색 밑줄로 표시한다.
export function SiteShell({ children, active }: { children: React.ReactNode; active?: Section }) {
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">본문으로 이동</a>
      <header className="app-header">
        <div className="app-header-inner">
          <Link href="/" className="brand-link" aria-label="청년정책메이트 홈">
            청년정책메이트<span className="brand-sub" aria-hidden="true"> — 공고 정리함</span>
          </Link>
          <nav aria-label="주 메뉴" className="main-nav">
            {sections.map(section => <Link key={section.key} href={section.href} className="nav-link"
              aria-current={active === section.key ? "page" : undefined}>{section.label}</Link>)}
          </nav>
        </div>
      </header>
      {children}
      <footer className="app-footer">
        <p>신청 자격과 접수 여부는 공식 신청처에서 확인해주세요.</p>
        <ThemeSwitch />
      </footer>
    </div>
  );
}
