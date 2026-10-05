import type { Metadata } from "next";
import { SiteShell } from "@/components/site-shell";
import { ConditionForm } from "@/features/conditions/condition-form";
import { getSeoulDate } from "@/lib/seoul-date";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "내 조건 입력 · 청년정책메이트",
  description: "입력 없이 정책을 둘러보고, 필요한 조건을 추가하며 나에게 맞는 정책을 찾아보세요.",
  robots: { index: false, follow: false },
};

export default function ConditionsPage() {
  return (
    <SiteShell active="conditions">
      <main id="main-content" className="conditions-main">
        <header className="conditions-intro">
          <p className="page-label">내 조건</p>
          <h1>둘러보고, 내 조건으로 좁혀보세요</h1>
          <p>먼저 관심 있는 정책을 찾아보세요. 생년월일을 추가하면 확인된 연령 조건부터 비교해드려요.</p>
          <div className="inline-security-note">
            <span aria-hidden="true">✓</span>
            입력 내용은 조건 비교에만 사용하고 자동 저장하지 않아요
          </div>
          <noscript><p className="mt-5 text-sm text-rose-800">조건 입력을 사용하려면 브라우저의 JavaScript를 켜주세요.</p></noscript>
        </header>
        <ConditionForm today={getSeoulDate(new Date())} />
      </main>
    </SiteShell>
  );
}
