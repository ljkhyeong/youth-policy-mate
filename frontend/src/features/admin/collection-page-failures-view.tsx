import { PageState } from "@/components/page-state";
import { collectionTime, PAGE_FAILURES_PATH } from "./collection-exception-view";
import type { PageFailureList } from "./load-collection-exceptions";

type PageFailure = PageFailureList["items"][number];
const reasons: Record<PageFailure["reason"], string> = {
  API_KEY_MISSING: "API 인증 설정 없음",
  HTTP_ERROR: "HTTP 응답 오류",
  NON_JSON_RESPONSE: "JSON 응답 아님",
  SECRET_IN_RESPONSE: "인증정보 포함으로 저장 차단",
  REQUEST_INTERRUPTED: "요청 처리 중단",
  REQUEST_OR_RESPONSE_FAILED: "요청·응답 처리 실패",
  RESPONSE_STORE_FAILED: "응답 저장 실패",
  INVALID_LIST_RESPONSE: "정책 목록 형식 오류",
  UNKNOWN: "실패 원인 확인 필요",
};

export function CollectionPageFailures({ data }: { data: PageFailureList }) {
  if (!data.items.length) return <PageState kind="empty" label="페이지 수집 실패 없음"
    title={data.page > 1 ? "이 페이지에 남은 수집 실패가 없습니다" : "확인할 페이지 수집 실패가 없습니다"}
    description="항목별 검증·저장 실패는 ‘항목 처리’에서 확인하세요."
    actions={<a className="button-secondary" href={PAGE_FAILURES_PATH}>{data.page > 1 ? "첫 페이지 보기" : "새로고침"}</a>} />;
  return <>
    <div className="member-toolbar"><p>{data.page}페이지 · {data.items.length}건</p>
      <a className="text-link" href={`${PAGE_FAILURES_PATH}?page=${data.page}`}>새로고침</a></div>
    <ul className="policy-list" aria-label="페이지 수집 실패 목록">
      {data.items.map(item => <li key={item.runId} className="policy-card">
        <p className="policy-eyebrow"><span>{item.state === "FETCH_FAILED" ? "페이지 수집 실패" : "목록 형식 오류"}</span>
          <span>{item.responseStored ? "응답 저장됨" : "보관된 응답 없음"}</span></p>
        <h2>수집 {item.pageNumber}페이지</h2>
        <p className="policy-lead">{item.reason === "HTTP_ERROR" && item.httpStatus === 429 ? "요청 한도 초과" : reasons[item.reason]}
          {item.httpStatus !== null && ` (HTTP ${item.httpStatus})`}</p>
        <dl className="exception-facts">
          <dt>요청 시작</dt><dd>{collectionTime(item.startedAt)} (서울)</dd>
          <dt>요청 전송</dt><dd>{collectionTime(item.dispatchedAt)}{item.dispatchedAt && " (서울)"}</dd>
          <dt>응답 저장</dt><dd>{collectionTime(item.receivedAt)}{item.receivedAt && " (서울)"}</dd>
          <dt>수집 실행 ID</dt><dd>{item.runId}</dd>
        </dl>
        <p className="field-help">{item.responseStored
          ? "보관된 응답을 검토한 뒤 재처리할 수 있습니다. 응답 자체가 잘못된 경우 새 수집이 필요할 수 있습니다."
          : "저장된 응답이 없습니다. 기존 요청의 종료 여부와 호출량을 확인한 뒤 다시 수집해주세요."}</p>
      </li>)}
    </ul>
    <nav className="policy-pagination" aria-label="페이지 수집 실패 목록 페이지">
      {data.page > 1 && <a className="button-secondary" href={`${PAGE_FAILURES_PATH}?page=${data.page - 1}`}>이전</a>}
      <span aria-current="page">{data.page}페이지</span>
      {data.hasNext && data.page < 1000 && <a className="button-secondary" href={`${PAGE_FAILURES_PATH}?page=${data.page + 1}`}>다음</a>}
    </nav>
  </>;
}
