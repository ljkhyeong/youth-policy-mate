import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { cache } from "react";
import { SiteShell } from "@/components/site-shell";
import { PageState } from "@/components/page-state";
import { loadPolicy } from "../load-policies";
import { PolicyArticle } from "../policy-content";
import { RetryPolicies } from "../retry-policies";

export const dynamic = "force-dynamic";
const readPolicy = cache(loadPolicy);
type Props = { params: Promise<{ policyNumber: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const result = await readPolicy((await params).policyNumber);
  return result.status === "available"
    ? { title: `${result.data.content.title} · 청년정책메이트`, description: result.data.content.description.slice(0, 150) }
    : { title: "정책 확인 · 청년정책메이트", robots: { index: false, follow: false } };
}

export default async function PolicyPage({ params }: Props) {
  const result = await readPolicy((await params).policyNumber);
  if (result.status === "missing") notFound();
  return <SiteShell active="policies"><main id="main-content" className="policies-main">
    <Link href="/policies" className="text-link policy-back">← 정책 목록</Link>
    {result.status === "available" ? <PolicyArticle policy={result.data} />
      : <PageState kind="error" title="정책 내용을 불러오지 못했어요" description="잠시 후 다시 불러와주세요." actions={<RetryPolicies />} />}
  </main></SiteShell>;
}
