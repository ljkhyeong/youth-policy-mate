import { SiteShell } from "@/components/site-shell";
import { MemberDashboard } from "./member-dashboard";
export const metadata = { title: "내 정책·일정 · 청년정책메이트", robots: { index: false, follow: false } };
export default function MyPage() { return <SiteShell active="my"><main id="main-content" className="member-main"><header><p className="page-label">내 정책</p><h1>관심 정책과 마감 일정</h1><p>저장한 정책의 변경과 신청 마감일을 함께 확인하세요.</p></header><MemberDashboard /></main></SiteShell>; }
