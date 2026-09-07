import { SiteShell } from "@/components/site-shell";

export const dynamic = "force-dynamic";
export const metadata = { title: "수집 예외 · 청년정책메이트", robots: { index: false, follow: false } };

export default function CollectionLayout({ children }: { children: React.ReactNode }) {
  return <SiteShell><main id="main-content" className="member-main exception-main">{children}</main></SiteShell>;
}
