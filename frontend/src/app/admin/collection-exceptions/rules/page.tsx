import { CollectionNavigation } from "@/features/admin/collection-exception-view";
import { collectionPage, loadRuleReviews } from "@/features/admin/load-collection-exceptions";
import { RULE_REVIEWS_PATH, RuleReviewFailure, RuleReviewList, reviewFilters, reviewHref, reviewSearch, type ReviewSearch } from "@/features/admin/policy-rule-review-view";

export const metadata = { title: "조건 검토 · 청년정책메이트" };
export default async function RuleReviewsPage({ searchParams }: { searchParams: Promise<ReviewSearch> }) {
  const params = await searchParams;
  const page = collectionPage(params.page);
  const { filter, query } = reviewSearch(params);
  const result = await loadRuleReviews(page, filter, query);
  return <>
    <header><p className="page-label">운영 관리</p><h1>조건 검토</h1><p>공고 변경과 적용 기간을 확인하고 질문을 검토합니다.</p></header>
    <CollectionNavigation active="rules" />
    <form action={RULE_REVIEWS_PATH} className="member-panel rule-review-search">
      <div className="rule-review-field"><label htmlFor="review-query">정책명·정책번호</label>
      <input id="review-query" name="query" type="search" maxLength={100} defaultValue={query} /></div>
      <div className="rule-review-field"><label htmlFor="review-filter">검토 상태</label>
      <select id="review-filter" name="filter" defaultValue={filter}>
        {Object.entries(reviewFilters).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></div>
      <div className="form-actions"><button className="button-primary" type="submit">검색</button>
        <a className="text-link" href={RULE_REVIEWS_PATH}>검색 초기화</a></div>
    </form>
    {result.status === "available" ? <RuleReviewList data={result.data} filter={filter} query={query} />
      : <RuleReviewFailure status={result.status} retryHref={reviewHref(page, filter, query)} />}
  </>;
}
