import { CollectionNavigation } from "@/features/admin/collection-exception-view";
import { collectionPage, loadRuleReview } from "@/features/admin/load-collection-exceptions";
import { RuleReviewContent, RuleReviewFailure, reviewHref, reviewSearch, type ReviewSearch } from "@/features/admin/policy-rule-review-view";

export const metadata = { title: "공고 조건 검토 · 청년정책메이트" };
export default async function RuleReviewPage({ params, searchParams }: {
  params: Promise<{ policyNumber: string }>; searchParams: Promise<ReviewSearch>;
}) {
  const { policyNumber } = await params;
  const search = await searchParams;
  const page = collectionPage(search.page);
  const { filter, query } = reviewSearch(search);
  const result = await loadRuleReview(policyNumber);
  return <>
    <header><p className="page-label">운영 관리 · 조건 검토</p><h1>{result.status === "available" ? result.data.item.title : "공고 조건 검토"}</h1></header>
    <CollectionNavigation active="rules" />
    <a className="text-link mb-6 inline-block" href={reviewHref(page, filter, query)}>검토 목록으로</a>
    {result.status === "available" ? <RuleReviewContent data={result.data} />
      : <RuleReviewFailure status={result.status} retryHref={reviewHref(page, filter, query, /^[0-9]{1,100}$/.test(policyNumber) ? policyNumber : undefined)} />}
  </>;
}
