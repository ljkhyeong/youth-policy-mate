import type { Metadata } from "next";
import { SiteShell } from "@/components/site-shell";
import { ConditionForm } from "@/features/conditions/condition-form";
import { getSeoulDate } from "@/lib/seoul-date";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "내 조건 입력 · 청년정책메이트",
  description: "로그인 없이 기본 조건을 입력하고 확인합니다. 현재 입력 내용은 서버에 전송하거나 저장하지 않습니다.",
  robots: { index: false, follow: false },
};

export default function ConditionsPage() {
  return (
    <SiteShell active="conditions">
      <main id="main-content" className="conditions-main">
        <header className="conditions-intro">
          <p className="page-label">내 조건</p>
          <h1>기본 조건 3가지만<br className="sm:hidden" /> 알려주세요</h1>
          <p>입력한 내용은 이 화면에서 확인할 때만 사용해요.</p>
          <div className="inline-security-note">
            <span aria-hidden="true">✓</span>
            서버로 보내거나 저장하지 않아요
          </div>
          <noscript><p className="mt-5 text-sm text-rose-800">조건 입력을 사용하려면 브라우저의 JavaScript를 켜주세요.</p></noscript>
        </header>
        <ConditionForm today={getSeoulDate(new Date())} />
      </main>
    </SiteShell>
  );
}
