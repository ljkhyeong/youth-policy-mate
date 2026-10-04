import { AdminListFailure, AdminSearchForm, CollectionNavigation, type AdminSearch } from "@/features/admin/collection-exception-view";
import { collectionPage, loadAiRuns } from "@/features/admin/load-collection-exceptions";
import { AI_RUNS_PATH, AiRunList, aiRunFilters, aiRunHref, aiRunSearch } from "@/features/admin/policy-ai-runs-view";

export const metadata = { title: "AI 추출 · 청년정책메이트" };
export default async function AiRunsPage({ searchParams }: { searchParams: Promise<AdminSearch> }) {
  const params = await searchParams;
  const page = collectionPage(params.page);
  const { filter, query } = aiRunSearch(params);
  const result = await loadAiRuns(page, filter, query);
  return <>
    <header><p className="page-label">운영 관리</p><h1>AI 추출</h1><p>자동 추출 상태와 생성된 초안을 확인합니다.</p></header>
    <CollectionNavigation active="ai" />
    <AdminSearchForm id="ai" action={AI_RUNS_PATH} filterLabel="마지막 시도 상태" filters={aiRunFilters} filter={filter} query={query} />
    {result.status === "available" ? <AiRunList data={result.data} filter={filter} query={query} />
      : <AdminListFailure status={result.status} retryHref={aiRunHref(page, filter, query)}
        invalidTitle="검색어·상태·페이지를 확인해주세요" failureTitle="AI 추출 내역을 불러오지 못했습니다" />}
  </>;
}
