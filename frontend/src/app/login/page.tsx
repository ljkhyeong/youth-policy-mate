import { SiteShell } from "@/components/site-shell";
import { LoginPanel } from "./login-panel";

export const metadata = { title: "로그인 · 청년정책메이트", robots: { index: false, follow: false } };
export default function LoginPage() {
  return <SiteShell active="my"><main id="main-content" className="member-main"><header><p className="page-label">내 정책</p><h1>관심 정책을 이어서 관리하세요</h1><p>로그인하면 내 조건과 관심 정책을 저장하고, 마감 일정과 알림을 확인할 수 있어요.</p></header><LoginPanel /></main></SiteShell>;
}
