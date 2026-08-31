import type { Metadata } from "next";
import Link from "next/link";
import { SiteShell } from "@/components/site-shell";
import { PageState } from "@/components/page-state";
import { RetryPreview } from "@/components/dev-preview/retry-preview";
import { EligibilityTrial } from "./eligibility-trial";
import { loadTrialQuestions } from "./trial-api";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "인공 답변 재판정 · 청년정책메이트", robots: { index: false, follow: false } };

export default async function EligibilityTrialPage() {
  const questions = await loadTrialQuestions();
  return <SiteShell><main id="main-content" className="flex-1 py-12 sm:py-16">
    <p className="mb-3 text-sm font-semibold text-teal-800">개발 전용 · 인공 답변 · 서버 재판정</p>
    <h1 className="text-3xl leading-relaxed font-bold tracking-tight">답변을 바꾸고 판정 근거 확인하기</h1>
    <p className="mt-4 max-w-2xl text-sm leading-7 text-stone-600">실제 개인정보 대신 정해진 예시 답변만 서버에 보냅니다. ‘내 조건’과 기존 질문 화면의 답변은 읽지 않습니다. 저장·로그인·실제 정책 추천은 없으며 새로고침하면 초기화됩니다.</p>
    <p className="mt-4 text-sm leading-7">서버 실행: <code className="font-mono text-xs">npm run dev:preview-api</code><br />질문·근거와 계산 시점은 인공 자료로 고정합니다.</p>
    {questions ? <EligibilityTrial questions={questions} /> : <div className="mt-8"><PageState kind="error" title="인공 질문을 불러오지 못했습니다."
      description="개발 서버를 실행한 뒤 다시 불러와주세요. 연결 실패를 자격 결과로 표시하지 않습니다." actions={<RetryPreview />} /></div>}
    <Link href="/dev/eligibility/server" prefetch={false} className="text-link mt-8">고정 답변의 서버 결과 보기 →</Link>
  </main></SiteShell>;
}
