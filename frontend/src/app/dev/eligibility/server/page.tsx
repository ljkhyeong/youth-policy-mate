import type { Metadata } from "next";
import Link from "next/link";
import { RetryPreview } from "@/components/dev-preview/retry-preview";
import { PageState } from "@/components/page-state";
import { SiteShell } from "@/components/site-shell";
import { EligibilityPreview } from "../eligibility-preview";
import { loadEligibilityExamples } from "./load-eligibility-examples";

export const dynamic = "force-dynamic";
export const metadata: Metadata = {
  title: "자격 판정 서버 연결 · 청년정책메이트",
  robots: { index: false, follow: false },
};

export default async function ServerEligibilityPreviewPage() {
  const loaded = await loadEligibilityExamples();
  return <SiteShell>
    <main id="main-content" className="flex-1 py-12 sm:py-16">
      <p className="mb-3 text-sm font-semibold text-teal-800">개발 전용 · 서버 계산 · 인공 예시</p>
      <h1 className="text-3xl leading-relaxed font-bold tracking-tight">자격 판정과 근거 서버 연결</h1>
      <p className="mt-4 max-w-2xl text-sm leading-7 text-stone-600">
        Spring 서버가 고정 인공 규칙과 답변으로 계산한 결과입니다. 실제 정책 추천이나 개인의 자격 판정이 아닙니다.
        내 조건·질문 답변을 읽거나 전송하지 않으며, 화면에서 자격을 다시 계산하지 않습니다.
      </p>
      <div className="mt-6 border-l-2 border-teal-800 pl-4 text-sm leading-7">
        서버 실행: <code className="font-mono text-xs">npm run dev:preview-api</code>
        <p className="text-xs text-stone-600">인증키·DB 없이 실행합니다. 계산 시점은 서울 2026-08-31 00:30으로 고정하며 항목별 기준일은 따로 표시합니다.</p>
      </div>
      {loaded.status === "available" ? <>
        <p className="mt-6 text-sm font-semibold text-teal-900" role="status">서버 계산 결과를 받았습니다 · 인공 자료</p>
        <EligibilityPreview examples={loaded.examples} />
      </> : <div className="mt-9">
        <PageState kind="error" title="개발 서버 결과를 불러오지 못했습니다."
          description="위 명령으로 미리보기 서버를 실행한 뒤 다시 불러와주세요. 연결 실패는 조건 불충족이나 추가 확인 판정이 아니며 고정 예시로 대신 표시하지 않습니다."
          actions={<RetryPreview />} />
      </div>}
      <Link href="/dev/eligibility/interactive" prefetch={false} className="text-link mt-6">인공 답변을 바꾸며 재판정하기 →</Link>
      <Link href="/dev/eligibility" prefetch={false} className="text-link mt-6">서버 없는 고정 미리보기로 이동 →</Link>
    </main>
  </SiteShell>;
}
