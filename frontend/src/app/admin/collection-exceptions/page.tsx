import { collectionPage, loadCollectionExceptions } from "@/features/admin/load-collection-exceptions";
import { COLLECTION_PATH, CollectionFailure, CollectionNavigation, ExceptionList } from "@/features/admin/collection-exception-view";

export default async function CollectionPage({ searchParams }: { searchParams: Promise<{ page?: string | string[] }> }) {
  const page = collectionPage((await searchParams).page);
  const result = await loadCollectionExceptions(page);
  return <>
    <header><p className="page-label">운영 관리</p><h1>수집 오류·보정</h1><p>항목 검증·저장에 실패한 자료를 확인합니다.</p></header>
    <CollectionNavigation active="items" />
    {result.status === "available" ? <ExceptionList data={result.data} />
      : <CollectionFailure status={result.status} retryHref={`${COLLECTION_PATH}?page=${page}`} />}
  </>;
}
