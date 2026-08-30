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
      <main id="main-content" className="grid flex-1 items-start gap-10 py-12 lg:grid-cols-[0.85fr_1.25fr] lg:gap-16 lg:py-16">
        <div className="lg:pt-6">
          <p className="mb-5 text-sm font-semibold text-teal-800">로그인 없이 시작하는 내 조건</p>
          <h1 className="text-3xl leading-[1.4] font-bold tracking-tight sm:text-4xl">나에게 맞는 정책,<br />내 조건부터.</h1>
          <p className="mt-5 max-w-sm text-base leading-8 text-stone-600">생년월일, 서울 거주지, 취업상태를 입력하고 확인해보세요.</p>
          <aside className="mt-8 border-l-2 border-stone-300 pl-5 text-sm leading-7 text-stone-600" aria-label="현재 이용 범위">
            <h2 className="mb-2 font-semibold text-stone-800">지금 할 수 있는 일</h2>
            <p>기본 조건 입력·확인·수정까지 가능해요. 실제 정책 추천과 회원 저장은 정책 데이터와 로그인을 연결한 뒤 제공할 예정입니다.</p>
          </aside>
          <noscript><p className="mt-5 text-sm text-rose-800">조건 입력을 사용하려면 브라우저의 JavaScript를 켜주세요.</p></noscript>
        </div>
        <ConditionForm today={getSeoulDate(new Date())} />
      </main>
    </SiteShell>
  );
}
