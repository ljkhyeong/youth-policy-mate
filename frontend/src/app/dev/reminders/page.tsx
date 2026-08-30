import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { SiteShell } from "@/components/site-shell";
import { ReminderPreview } from "./reminder-preview";

export const metadata: Metadata = {
  title: "마감 알림 후보 미리보기 · 청년정책메이트",
  robots: { index: false, follow: false },
};

export default function ReminderPreviewPage() {
  if (process.env.NODE_ENV !== "development") notFound();

  return (
    <SiteShell>
      <main id="main-content" className="flex-1 py-12 sm:py-16">
        <p className="mb-3 text-sm font-semibold text-teal-800">개발 전용 · 인공 예시</p>
        <h1 className="text-3xl leading-relaxed font-bold tracking-tight">마감과 알림 후보 미리보기</h1>
        <p className="mt-4 max-w-2xl text-sm leading-7 text-stone-600">
          미리 정한 신청기간과 후보 날짜로 표시를 점검합니다. 실제 정책·서버 응답이 아니며 현재 날짜로 계산하지 않습니다.
          정책을 저장하거나 알림을 예약·발송하지 않고, 내 조건이나 앞선 질문의 답변도 가져오지 않습니다.
        </p>
        <ReminderPreview />
        <Link href="/dev/eligibility" prefetch={false} className="text-link mt-6">자격 결과 미리보기로 이동 →</Link>
      </main>
    </SiteShell>
  );
}
