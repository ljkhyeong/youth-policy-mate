import type { Metadata } from "next";
import { SiteShell } from "@/components/site-shell";
import { ConditionForm } from "@/features/conditions/condition-form";
import { getSeoulDate } from "@/lib/seoul-date";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "내 조건 입력 · 청년정책메이트",
  description: "기본 조건으로 실제 정책의 확인할 요건을 살펴봅니다. 로그인 후 확인한 조건을 계정에 저장할 수 있습니다.",
  robots: { index: false, follow: false },
};

export default function ConditionsPage() {
  return (
    <SiteShell active="conditions">
      <main id="main-content" className="conditions-main">
        <header className="conditions-intro">
          <p className="page-label">내 조건</p>
          <h1>기본 조건 3가지만<br className="sm:hidden" /> 알려주세요</h1>
          <p>입력을 확인한 뒤 실제 정책의 요건을 살펴볼 수 있어요.</p>
          <div className="inline-security-note">
            <span aria-hidden="true">✓</span>
            정책 확인을 요청할 때만 전송하고 자동 저장하지 않아요
          </div>
          <noscript><p className="mt-5 text-sm text-rose-800">조건 입력을 사용하려면 브라우저의 JavaScript를 켜주세요.</p></noscript>
        </header>
        <ConditionForm today={getSeoulDate(new Date())} />
      </main>
    </SiteShell>
  );
}
