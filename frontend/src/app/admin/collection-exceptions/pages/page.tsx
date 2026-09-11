import { collectionPage, loadCollectionPageFailures } from "@/features/admin/load-collection-exceptions";
import { CollectionFailure, CollectionNavigation, PAGE_FAILURES_PATH } from "@/features/admin/collection-exception-view";
import { CollectionPageFailures } from "@/features/admin/collection-page-failures-view";

export default async function PageFailuresPage({ searchParams }: { searchParams: Promise<{ page?: string | string[] }> }) {
  const page = collectionPage((await searchParams).page);
  const result = await loadCollectionPageFailures(page);
  return <>
    <header><p className="page-label">운영 관리</p><h1>수집 오류·보정</h1><p>페이지 응답 수신·형식 오류를 확인합니다.</p></header>
    <CollectionNavigation active="pages" />
    {result.status === "available" ? <CollectionPageFailures data={result.data} />
      : <CollectionFailure status={result.status} retryHref={`${PAGE_FAILURES_PATH}?page=${page}`} />}
  </>;
}
