import { PageState } from "@/components/page-state";
import { CollectionFailure, collectionTime } from "./collection-exception-view";
import type { AiRunPage, LoadFailure } from "./load-collection-exceptions";
import { RULE_REVIEWS_PATH } from "./policy-rule-review-view";

export const AI_RUNS_PATH = "/admin/collection-exceptions/ai";
const states = {
  RUNNING: "실행 중", COMPLETED: "초안 생성 완료", RETRY_PENDING: "재시도 대기", INTERRUPTED: "중단 후 재개 대기",
  REVIEW_REQUIRED: "확인 필요", SUPERSEDED: "이전 공고·요청", LEASE_EXPIRED: "실행 시간 초과",
} satisfies Record<AiRunPage["items"][number]["state"], string>;
export const aiRunFilters = { ALL: "전체", ...states };
type AiRunFilter = keyof typeof aiRunFilters;
export type AiRunSearch = { page?: string | string[]; filter?: string | string[]; query?: string | string[] };
export function aiRunSearch(params: AiRunSearch) {
  const filter: AiRunFilter = typeof params.filter === "string" && Object.hasOwn(aiRunFilters, params.filter)
    ? params.filter as AiRunFilter : "ALL";
  return { filter, query: typeof params.query === "string" ? params.query.trim() : "" };
}
export function aiRunHref(page: number, filter: string, query: string) {
  return `${AI_RUNS_PATH}?${new URLSearchParams({ page: String(page), filter, query })}`;
}

const results: Record<string, string> = {
  DRAFT_CREATED: "초안 생성", SOURCE_CHANGED: "원문 변경으로 초안 미등록", REQUEST_SUPERSEDED: "후속 요청으로 초안 미등록",
  INVALID_DEFINITION: "규칙 형식 오류", INVALID_REFERENCE: "원문·요청 참조 오류", VERSION_CONFLICT: "규칙 버전 중복",
  WORKER_INTERRUPTED: "실행 중단", GENERATION_FAILED: "추출 처리 실패", STATUS_UNAVAILABLE: "처리 결과 조회 실패",
};
const phases: Record<string, string> = {
  HELD: "예산 예약", DISPATCHED: "청구 확인 대기", OUTCOME_UNKNOWN: "결과 미확인 · 예산 예약 유지",
  SETTLED: "정산 완료", CANCELLED: "호출 전 취소", RELEASED_NO_CHARGE: "무과금 확인",
};

export function AiRunFailure({ status, retryHref }: { status: LoadFailure; retryHref: string }) {
  if (status === "unauthenticated" || status === "forbidden") return <CollectionFailure status={status} retryHref={retryHref} />;
  return <PageState kind="error" title={status === "invalid" ? "검색어·상태·페이지를 확인해주세요" : "AI 추출 내역을 불러오지 못했습니다"}
    description="잠시 후 다시 조회해주세요." actions={<a className="button-primary" href={retryHref}>다시 불러오기</a>} />;
}

export function AiRunList({ data, filter, query }: { data: AiRunPage; filter: AiRunFilter; query: string }) {
  return <>
    <section className="member-panel" aria-label="자동 실행 설정">
      <h2>자동 실행 {data.automationEnabled ? "켜짐" : "꺼짐"}</h2>
      <p>{data.automationEnabled ? "AI 설정과 예산·일일 한도에 따라 처리합니다." : "대기 작업은 자동으로 재개되지 않습니다."}</p>
      <p className="field-help">자동 실행을 시도한 요청만 표시합니다. 초안은 조건 검토 후 별도로 적용합니다.</p>
    </section>
    <div className="member-toolbar"><p>검색 결과 {data.total}건 · {data.page}페이지</p>
      <a className="text-link" href={aiRunHref(data.page, filter, query)}>새로고침</a></div>
    <p className="field-help">조회 {collectionTime(data.checkedAt)} (서울) · 요청별 마지막 시도 기준</p>
    {data.items.length ? <ul className="policy-list" aria-label="AI 추출 목록">
      {data.items.map(item => <li className="policy-card" key={item.requestId}>
        <p className="policy-eyebrow"><span>{states[item.state]}</span><span>시도 {item.attempt}회</span></p>
        <h2>{item.title}</h2>
        <p className="policy-period">추출 기준 개정 {item.revision} · 현재 개정 {item.currentRevision}</p>
        {!item.sourceMatches && <p className="field-help">현재 원문과 다릅니다. 최신 공고를 확인하세요.</p>}
        {!item.latestRequest && <p className="field-help">이후 추출 요청이 있습니다.</p>}
        {item.state === "LEASE_EXPIRED" && <p className="field-help">실행 기한이 지났습니다. 결과와 비용 상태를 확인하세요.</p>}
        <p className="policy-period">시작 {collectionTime(item.startedAt)} (서울)</p>
        <p className="policy-period">현재 추출 결과: {item.candidateStatus ? results[item.candidateStatus] ?? "결과 확인 필요" : "저장된 결과 없음"}</p>
        <p className="policy-period">비용: {item.reservationPhase ? phases[item.reservationPhase] ?? "확인 필요" : "예약 내역 없음"}</p>
        <div className="form-actions"><a className="text-link" href={`${RULE_REVIEWS_PATH}/${item.policyNumber}`}>
          {item.candidateStatus === "DRAFT_CREATED" ? "초안 검토" : "공고·조건 검토"}</a></div>
        <details className="exception-raw"><summary>처리 내역</summary><dl className="exception-facts">
          <dt>정책번호</dt><dd>{item.policyNumber}</dd>
          <dt>요청 ID</dt><dd>{item.requestId}</dd>
          <dt>시도 종료</dt><dd>{collectionTime(item.finishedAt)}</dd>
          <dt>시도 결과</dt><dd>{item.resultCode ? results[item.resultCode] ?? phases[item.resultCode] ?? item.resultCode : "기록 없음"}</dd>
          <dt>공급자 응답</dt><dd>{item.responseStored ? "보관됨" : "보관된 응답 없음"}</dd>
        </dl></details>
      </li>)}
    </ul> : <PageState kind="empty" title={data.page > 1 ? "이 페이지에 추출 내역이 없습니다" : "조회 조건에 맞는 추출 내역이 없습니다"}
      description="자동 실행 전이거나 조회 조건에 맞는 기록이 없는 상태입니다."
      actions={<a className="button-secondary" href={data.page > 1 ? aiRunHref(1, filter, query) : AI_RUNS_PATH}>{data.page > 1 ? "첫 페이지 보기" : "전체 내역 보기"}</a>} />}
    {(data.page > 1 || data.hasNext) && <nav className="policy-pagination" aria-label="AI 추출 목록 페이지">
      {data.page > 1 && <a className="button-secondary" href={aiRunHref(data.page - 1, filter, query)}>이전</a>}
      <span aria-current="page">{data.page}페이지</span>
      {data.hasNext && data.page < 1000 && <a className="button-secondary" href={aiRunHref(data.page + 1, filter, query)}>다음</a>}
    </nav>}
  </>;
}
