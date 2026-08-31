import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { SiteShell } from "@/components/site-shell";
import { EligibilityPreview } from "./eligibility-preview";
import { ELIGIBILITY_EXAMPLES } from "./eligibility-preview-data";

export const metadata: Metadata = {
  title: "자격 결과 미리보기 · 청년정책메이트",
  robots: { index: false, follow: false },
};

export default function EligibilityPreviewPage() {
  if (process.env.NODE_ENV !== "development") notFound();

  return (
    <SiteShell>
      <main id="main-content" className="flex-1 py-12 sm:py-16">
        <p className="mb-3 text-sm font-semibold text-teal-800">개발 전용 · 인공 예시</p>
        <h1 className="text-3xl leading-relaxed font-bold tracking-tight">자격 결과와 근거 미리보기</h1>
        <p className="mt-4 max-w-2xl text-sm leading-7 text-stone-600">
          미리 정한 결과로 표시를 점검하는 화면입니다. 정책·입력 값·근거는 모두 인공 자료이며 실제 정책 추천이 아닙니다.
          내 조건이나 앞선 질문의 답변을 가져오지 않으며, 서버를 호출하거나 자격을 계산하지 않습니다.
        </p>
        <EligibilityPreview examples={ELIGIBILITY_EXAMPLES} />
        <Link href="/dev/eligibility/server" prefetch={false} className="text-link mt-6">서버 계산 연결 화면으로 이동 →</Link>
        <Link href="/dev/income" prefetch={false} className="text-link mt-6">소득 질문 미리보기로 이동 →</Link>
      </main>
    </SiteShell>
  );
}
