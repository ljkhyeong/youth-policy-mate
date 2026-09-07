// 관리자 화면은 전체 이동으로 이전 조회 결과를 재사용하지 않는다.
import { PageState } from "@/components/page-state";
import type { ExceptionDetail, ExceptionPage, LoadFailure } from "./load-collection-exceptions";

export const COLLECTION_PATH = "/admin/collection-exceptions";
const outcomeLabels = { INVALID_ITEM: "항목 검증 실패", STORE_FAILED: "저장 실패" } as const;
const timestamp = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
});
const dateLabel = (value: string | null) => value ? timestamp.format(new Date(value)) : "기록 없음";

export function CollectionFailure({ status, retryHref }: { status: LoadFailure; retryHref: string }) {
  if (status === "unauthenticated") return <PageState kind="error" label="로그인 필요" title="관리자 계정으로 로그인하세요"
    description="지정된 관리자만 수집 예외를 확인할 수 있습니다."
    actions={<a className="button-primary" href="/login?next=admin">로그인</a>} />;
  if (status === "forbidden") return <PageState kind="error" label="접근 제한" title="관리자 권한이 없습니다"
    description="현재 계정으로는 수집 예외를 확인할 수 없습니다. 관리자 계정과 권한 설정을 확인해주세요."
    actions={<a className="button-secondary" href="/my">내 계정 확인</a>} />;
  if (status === "missing") return <PageState kind="empty" label="항목 없음" title="현재 실패 목록에 없는 항목입니다"
    description="재처리가 완료됐거나 존재하지 않는 항목입니다. 최신 목록을 확인해주세요."
    actions={<a className="button-primary" href={COLLECTION_PATH}>실패 목록 보기</a>} />;
  if (status === "invalid") return <PageState kind="error" label="조회 조건 오류" title="조회 주소를 확인해주세요"
    description="실패 목록에서 항목을 다시 선택해주세요."
    actions={<a className="button-primary" href={COLLECTION_PATH}>실패 목록 보기</a>} />;
  return <PageState kind="error" title="수집 예외를 불러오지 못했습니다" description="잠시 후 다시 시도해주세요."
    actions={<a className="button-primary" href={retryHref}>다시 불러오기</a>} />;
}

export function ExceptionList({ data }: { data: ExceptionPage }) {
  if (!data.items.length) return <PageState kind="empty" label="실패 항목 없음"
    title={data.page > 1 ? "이 페이지에 남은 실패 항목이 없습니다" : "확인할 실패 항목이 없습니다"}
    description="현재 항목 검증·저장 실패 목록을 기준으로 합니다. 페이지 요청 실패는 포함하지 않습니다."
    actions={<a className="button-secondary" href={COLLECTION_PATH}>{data.page > 1 ? "첫 페이지 보기" : "새로고침"}</a>} />;
  return <>
    <div className="member-toolbar"><p>{data.page}페이지 · {data.items.length}건</p>
      <a className="text-link" href={`${COLLECTION_PATH}?page=${data.page}`}>새로고침</a></div>
    <ul className="policy-list" aria-label="수집 실패 목록">
      {data.items.map(item => <li className="policy-card" key={`${item.runId}/${item.itemIndex}`}>
        <p className="policy-eyebrow"><span>{outcomeLabels[item.outcome]}</span><span>처리 {item.attempts}회</span></p>
        <h2><a href={`${COLLECTION_PATH}/${item.runId}/${item.itemIndex}?page=${data.page}`}>
          {item.policyNumber ? `정책 ${item.policyNumber}` : "정책번호 확인 불가"}</a></h2>
        <p className="policy-period">수집 {item.pageNumber}페이지 · {item.itemIndex + 1}번째 항목</p>
        <p className="policy-period">마지막 처리: {dateLabel(item.lastAttemptAt)} (서울)</p>
      </li>)}
    </ul>
    <nav className="policy-pagination" aria-label="실패 목록 페이지">
      {data.page > 1 && <a className="button-secondary" href={`${COLLECTION_PATH}?page=${data.page - 1}`}>이전</a>}
      <span aria-current="page">{data.page}페이지</span>
      {data.hasNext && data.page < 1000 && <a className="button-secondary" href={`${COLLECTION_PATH}?page=${data.page + 1}`}>다음</a>}
    </nav>
  </>;
}

export function ExceptionContent({ data }: { data: ExceptionDetail }) {
  const { item, currentPolicy } = data;
  return <>
    <section className="member-panel" aria-labelledby="failure-heading">
      <h2 id="failure-heading">{outcomeLabels[item.outcome]}</h2>
      <dl className="exception-facts">
        <dt>정책번호</dt><dd>{item.policyNumber ?? "확인 불가"}</dd>
        <dt>마지막 처리</dt><dd>{dateLabel(item.lastAttemptAt)} (서울)</dd>
        <dt>처리 횟수</dt><dd>{item.attempts}회</dd>
        <dt>수집 위치</dt><dd>{item.pageNumber}페이지 · {item.itemIndex + 1}번째 항목</dd>
        <dt>수집 실행 ID</dt><dd>{item.runId}</dd>
      </dl>
      <p className="field-help">세부 실패 사유는 저장되어 있지 않습니다. 원본과 현재 내용을 확인해주세요.</p>
    </section>
    <section className="member-panel" aria-labelledby="current-heading">
      <h2 id="current-heading">현재 공개 내용</h2>
      {currentPolicy ? <>
        <h3>{currentPolicy.content.title}</h3>
        <p className="field-help">현재 개정 {currentPolicy.revision} · 수집 {dateLabel(currentPolicy.collectedAt)} (서울)</p>
        <p className="field-help">조회 시점의 내용이며 실패 당시의 이전 개정과 다를 수 있습니다.</p>
        <p className="exception-text">{currentPolicy.content.description}</p>
        <dl className="exception-facts">
          <dt>운영 기관</dt><dd>{currentPolicy.content.organization}</dd>
          <dt>정책 분야</dt><dd>{currentPolicy.content.category}</dd>
          <dt>신청 기간</dt><dd>{currentPolicy.content.applicationPeriod}</dd>
        </dl>
        {currentPolicy.content.sections.map((section, index) => <section className="exception-section" key={index}>
          <h4>{section.title}</h4><p className="exception-text">{section.text}</p>
        </section>)}
        <a className="text-link" href={`/policies/${currentPolicy.policyNumber}`}>공개 정책 상세 보기</a>
      </> : <p>{item.policyNumber ? "같은 정책번호로 공개된 내용이 없습니다." : "정책번호를 확인할 수 없어 공개 내용과 연결하지 않았습니다."}</p>}
    </section>
    <section className="member-panel" aria-labelledby="raw-heading">
      <h2 id="raw-heading">수집 원본</h2>
      <pre className="exception-raw" tabIndex={0} aria-label="수집 원본 JSON">{data.rawPolicyJson}</pre>
    </section>
  </>;
}
