import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { SiteShell } from "@/components/site-shell";
import { EmploymentQuestionPreview } from "./employment-question-preview";

export const metadata: Metadata = {
  title: "추가 확인 질문 미리보기 · 청년정책메이트",
  robots: { index: false, follow: false },
};

export default function EmploymentPreviewPage() {
  if (process.env.NODE_ENV !== "development") notFound();

  return (
    <SiteShell>
      <main id="main-content" className="flex-1 py-12 sm:py-16">
        <p className="mb-3 text-sm font-semibold text-teal-800">개발 전용 · 인공 예시</p>
        <h1 className="text-3xl leading-relaxed font-bold tracking-tight">추가 확인 질문 미리보기</h1>
        <p className="mt-4 max-w-2xl text-sm leading-7 text-stone-600">
          질문과 답변 흐름을 점검하는 화면입니다. 아래 정의·날짜·근거는 실제 정책이 아닌 인공 자료이며,
          어떤 답변을 골라도 신청 가능 여부를 판정하지 않습니다.
        </p>
        <EmploymentQuestionPreview />
      </main>
    </SiteShell>
  );
}
