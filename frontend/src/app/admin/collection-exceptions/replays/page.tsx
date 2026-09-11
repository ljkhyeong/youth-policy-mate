import { PageState } from "@/components/page-state";
import { collectionPage, loadCollectionReplays } from "@/features/admin/load-collection-exceptions";
import { CollectionFailure, CollectionNavigation, REPLAYS_PATH, collectionTime } from "@/features/admin/collection-exception-view";
import { replayLabels } from "@/features/admin/collection-replay-labels";

export default async function CollectionReplaysPage({ searchParams }: { searchParams: Promise<{ page?: string | string[] }> }) {
  const page = collectionPage((await searchParams).page);
  const result = await loadCollectionReplays(page);
  return <>
    <header><p className="page-label">운영 관리</p><h1>수집 오류·보정</h1><p>관리자가 실행한 재처리 사유와 결과를 확인합니다.</p></header>
    <CollectionNavigation active="replays" />
    {result.status !== "available" ? <CollectionFailure status={result.status} retryHref={`${REPLAYS_PATH}?page=${page}`} />
      : !result.data.items.length ? <PageState kind="empty" title="재처리 이력이 없습니다"
        description={page > 1 ? "첫 페이지에서 최근 처리 이력을 확인하세요." : "관리자가 완료한 재처리 결과가 여기에 표시됩니다."}
        actions={<a className="button-secondary" href={REPLAYS_PATH}>{page > 1 ? "첫 페이지 보기" : "새로고침"}</a>} />
        : <>
          <ul className="policy-list" aria-label="관리자 재처리 이력">
            {result.data.items.map(item => <li className="policy-card" key={item.requestId}>
              <p className="policy-eyebrow"><span>{replayLabels[item.outcome]}</span><span>처리 {item.attempt}회</span></p>
              <h2>{item.policyNumber ? `정책 ${item.policyNumber}` : "정책번호 확인 불가"}</h2>
              <p className="exception-text">{item.reason}</p>
              <dl className="exception-facts">
                <dt>기록 시각</dt><dd>{collectionTime(item.processedAt)} (서울)</dd>
                <dt>처리 후 버전</dt><dd>{item.policyRevision ?? "반영 없음"}</dd>
                <dt>작업자 ID</dt><dd>{item.actorId}</dd>
                <dt>수집 위치</dt><dd>{item.runId} · {item.itemIndex + 1}번째 항목</dd>
                <dt>재처리 요청 ID</dt><dd>{item.requestId}</dd>
              </dl>
            </li>)}
          </ul>
          <nav className="policy-pagination" aria-label="재처리 이력 페이지">
            {page > 1 && <a className="button-secondary" href={`${REPLAYS_PATH}?page=${page - 1}`}>이전</a>}
            <span aria-current="page">{page}페이지</span>
            {result.data.hasNext && page < 1000 && <a className="button-secondary" href={`${REPLAYS_PATH}?page=${page + 1}`}>다음</a>}
          </nav>
        </>}
  </>;
}
