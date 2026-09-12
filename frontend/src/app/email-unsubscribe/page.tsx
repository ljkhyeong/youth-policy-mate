import { SiteShell } from "@/components/site-shell";
import { EmailUnsubscribe } from "@/features/member/email-unsubscribe";

export const metadata = {
  title: "이메일 수신 해제 · 청년정책메이트",
  robots: { index: false, follow: false },
  referrer: "no-referrer" as const,
};

export default function EmailUnsubscribePage() {
  return <SiteShell><main id="main-content" className="member-main">
    <header><p className="page-label">이메일 알림</p><h1>이메일 수신 해제</h1></header>
    <EmailUnsubscribe />
  </main></SiteShell>;
}
