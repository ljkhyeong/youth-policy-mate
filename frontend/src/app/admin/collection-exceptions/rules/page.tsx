import { AdminSearchForm, CollectionNavigation, type AdminSearch } from "@/features/admin/collection-exception-view";
import { collectionPage, loadRuleReviews } from "@/features/admin/load-collection-exceptions";
import { RULE_REVIEWS_PATH, RuleReviewFailure, RuleReviewList, reviewFilters, reviewHref, reviewSearch } from "@/features/admin/policy-rule-review-view";

export const metadata = { title: "조건 검토 · 청년정책메이트" };
export default async function RuleReviewsPage({ searchParams }: { searchParams: Promise<AdminSearch> }) {
  const params = await searchParams;
  const page = collectionPage(params.page);
  const { filter, query } = reviewSearch(params);
  const result = await loadRuleReviews(page, filter, query);
  return <>
    <header><p className="page-label">운영 관리</p><h1>조건 검토</h1><p>공고 변경과 적용 기간을 확인하고 질문을 검토합니다.</p></header>
    <CollectionNavigation active="rules" />
    <AdminSearchForm id="review" action={RULE_REVIEWS_PATH} filterLabel="검토 상태" filters={reviewFilters} filter={filter} query={query} />
    {result.status === "available" ? <RuleReviewList data={result.data} filter={filter} query={query} />
      : <RuleReviewFailure status={result.status} retryHref={reviewHref(page, filter, query)} />}
  </>;
}
