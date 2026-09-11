import type { Metadata } from "next";
import { SiteShell } from "@/components/site-shell";
import { ConditionForm } from "@/features/conditions/condition-form";
import { getSeoulDate } from "@/lib/seoul-date";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "내 조건 입력 · 청년정책메이트",
  description: "생년월일·거주지·취업상태를 입력하고 정책별 신청 조건을 확인하세요. 로그인하면 입력한 조건을 저장할 수 있어요.",
  robots: { index: false, follow: false },
};

export default function ConditionsPage() {
  return (
    <SiteShell active="conditions">
      <main id="main-content" className="conditions-main">
        <header className="conditions-intro">
          <p className="page-label">내 조건</p>
          <h1>내 조건을<br className="sm:hidden" /> 입력하세요</h1>
          <p>생년월일로 정책별 연령 조건을 비교할 수 있어요. 거주·취업·소득은 추가 확인이 필요해요.</p>
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
