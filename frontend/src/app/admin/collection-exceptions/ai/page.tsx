import { CollectionNavigation } from "@/features/admin/collection-exception-view";
import { collectionPage, loadAiRuns } from "@/features/admin/load-collection-exceptions";
import { AI_RUNS_PATH, AiRunFailure, AiRunList, aiRunFilters, aiRunHref, aiRunSearch, type AiRunSearch } from "@/features/admin/policy-ai-runs-view";

export const metadata = { title: "AI 추출 · 청년정책메이트" };
export default async function AiRunsPage({ searchParams }: { searchParams: Promise<AiRunSearch> }) {
  const params = await searchParams;
  const page = collectionPage(params.page);
  const { filter, query } = aiRunSearch(params);
  const result = await loadAiRuns(page, filter, query);
  return <>
    <header><p className="page-label">운영 관리</p><h1>AI 추출</h1><p>자동 추출 상태와 생성된 초안을 확인합니다.</p></header>
    <CollectionNavigation active="ai" />
    <form action={AI_RUNS_PATH} className="member-panel rule-review-search">
      <div className="rule-review-field"><label htmlFor="ai-query">정책명·정책번호</label>
        <input id="ai-query" name="query" type="search" maxLength={100} defaultValue={query} /></div>
      <div className="rule-review-field"><label htmlFor="ai-filter">마지막 시도 상태</label>
        <select id="ai-filter" name="filter" defaultValue={filter}>
          {Object.entries(aiRunFilters).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select></div>
      <div className="form-actions"><button className="button-primary" type="submit">검색</button>
        <a className="text-link" href={AI_RUNS_PATH}>검색 초기화</a></div>
    </form>
    {result.status === "available" ? <AiRunList data={result.data} filter={filter} query={query} />
      : <AiRunFailure status={result.status} retryHref={aiRunHref(page, filter, query)} />}
  </>;
}
