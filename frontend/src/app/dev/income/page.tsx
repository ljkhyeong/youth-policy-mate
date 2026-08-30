import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { SiteShell } from "@/components/site-shell";
import { IncomeQuestionPreview } from "./income-question-preview";

export const metadata: Metadata = {
  title: "소득 질문 미리보기 · 청년정책메이트",
  robots: { index: false, follow: false },
};

export default function IncomePreviewPage() {
  if (process.env.NODE_ENV !== "development") notFound();

  return (
    <SiteShell>
      <main id="main-content" className="flex-1 py-12 sm:py-16">
        <p className="mb-3 text-sm font-semibold text-teal-800">개발 전용 · 인공 예시</p>
        <h1 className="text-3xl leading-relaxed font-bold tracking-tight">소득 질문 미리보기</h1>
        <p className="mt-4 max-w-2xl text-sm leading-7 text-stone-600">
          소득의 대상·기간·단위를 읽고 구간으로 답하는 흐름입니다. 정의와 금액은 실제 정책이 아닌 화면 점검용입니다.
          실제 소득을 입력할 필요가 없으며, 어떤 답변을 골라도 자격을 판정하지 않습니다.
        </p>
        <IncomeQuestionPreview />
        <Link href="/dev/employment" prefetch={false} className="text-link mt-6">취업 질문 미리보기로 이동 →</Link>
      </main>
    </SiteShell>
  );
}
