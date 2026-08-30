import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { SiteShell } from "@/components/site-shell";
import { StatePreview } from "./state-preview";

export const metadata: Metadata = {
  title: "상태 화면 미리보기 · 청년정책메이트",
  robots: { index: false, follow: false },
};

export default function StatePreviewPage() {
  if (process.env.NODE_ENV !== "development") notFound();

  return (
    <SiteShell>
      <main id="main-content" className="flex-1 py-12 sm:py-16">
        <p className="mb-3 text-sm font-semibold text-teal-800">개발 전용 · 화면 점검</p>
        <h1 className="text-3xl leading-relaxed font-bold tracking-tight">상태 화면 미리보기</h1>
        <p className="mt-4 max-w-2xl text-sm leading-7 text-stone-600">아래는 동작을 점검하기 위한 예시입니다. 실제 정책 조회나 자격 판정 결과가 아니며, 버튼을 눌러도 외부 API를 호출하지 않습니다.</p>
        <StatePreview />
      </main>
    </SiteShell>
  );
}
