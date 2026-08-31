import type { Metadata } from "next";
import Link from "next/link";
import { PageState } from "@/components/page-state";
import { SiteShell } from "@/components/site-shell";
import { ReminderPreview } from "../reminder-preview";
import { loadReminderExamples } from "./load-reminder-examples";
import { RetryPreview } from "@/components/dev-preview/retry-preview";

export const dynamic = "force-dynamic";
export const metadata: Metadata = {
  title: "서버 계산 연결 · 청년정책메이트",
  robots: { index: false, follow: false },
};

export default async function ServerReminderPreviewPage() {
  const loaded = await loadReminderExamples();

  return <SiteShell>
    <main id="main-content" className="flex-1 py-12 sm:py-16">
      <p className="mb-3 text-sm font-semibold text-teal-800">개발 전용 · 서버 계산 · 인공 예시</p>
      <h1 className="text-3xl leading-relaxed font-bold tracking-tight">마감 알림 후보 서버 연결</h1>
      <p className="mt-4 max-w-2xl text-sm leading-7 text-stone-600">
        Spring 서버가 고정 인공 신청기간과 2026-08-31 기준 시각으로 계산한 결과입니다.
        실제 정책 조회·회원 정보·저장·예약·발송은 사용하지 않습니다. 화면에서 날짜를 다시 계산하지 않습니다.
      </p>
      <div className="mt-6 border-l-2 border-teal-800 pl-4 text-sm leading-7">
        서버 실행: <code className="font-mono text-xs">npm run dev:preview-api</code>
        <p className="text-xs text-stone-600">인증키·DB 없이 별도 실행합니다. 예시 선택은 이미 받은 결과의 표시만 바꿉니다.</p>
      </div>
      {loaded.status === "available" ? <>
        <p className="mt-6 text-sm font-semibold text-teal-900" role="status">서버 계산 결과를 받았습니다 · 인공 자료</p>
        <ReminderPreview examples={loaded.examples} />
      </> : <div className="mt-9">
        <PageState kind="error" title="개발 서버 결과를 불러오지 못했습니다."
          description="위 명령으로 미리보기 서버를 실행한 뒤 다시 불러와주세요. 연결 실패는 알림 후보가 없다는 뜻이 아니며 고정 예시로 대신 표시하지 않습니다."
          actions={<RetryPreview />} />
      </div>}
      <Link href="/dev/reminders" prefetch={false} className="text-link mt-6">서버 없는 고정 미리보기로 이동 →</Link>
    </main>
  </SiteShell>;
}
