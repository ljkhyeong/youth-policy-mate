import { collectionPage, loadCollectionException } from "@/features/admin/load-collection-exceptions";
import { COLLECTION_PATH, CollectionFailure, ExceptionContent } from "@/features/admin/collection-exception-view";

type Props = { params: Promise<{ runId: string; itemIndex: string }>; searchParams: Promise<{ page?: string | string[] }> };

export default async function CollectionDetailPage({ params, searchParams }: Props) {
  const { runId, itemIndex } = await params;
  const page = collectionPage((await searchParams).page);
  const result = await loadCollectionException(runId, itemIndex);
  return <>
    <a className="text-link policy-back" href={`${COLLECTION_PATH}?page=${page}`}>← 실패 목록</a>
    <header><p className="page-label">운영 관리</p><h1>수집 실패 상세</h1><p>수집 원본과 공개 개정의 변경 내용을 확인합니다.</p></header>
    {result.status === "available" ? <ExceptionContent data={result.data} />
      : <CollectionFailure status={result.status} retryHref={`${COLLECTION_PATH}/${encodeURIComponent(runId)}/${encodeURIComponent(itemIndex)}?page=${page}`} />}
  </>;
}
