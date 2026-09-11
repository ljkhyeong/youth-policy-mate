// 관리자 화면은 전체 이동으로 이전 조회 결과를 재사용하지 않는다.
import { PageState } from "@/components/page-state";
import type { ExceptionDetail, ExceptionPage, LoadFailure } from "./load-collection-exceptions";
import { CollectionReplayForm } from "./collection-replay-form";

export const COLLECTION_PATH = "/admin/collection-exceptions";
export const PAGE_FAILURES_PATH = `${COLLECTION_PATH}/pages`;
export const REPLAYS_PATH = `${COLLECTION_PATH}/replays`;
export const CORRECTIONS_PATH = `${COLLECTION_PATH}/corrections`;
const outcomeLabels = { INVALID_ITEM: "항목 검증 실패", STORE_FAILED: "저장 실패", CORRECTION_CONFLICT: "보정 충돌" } as const;
const timestamp = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
});
const dateLabel = (value: string | null) => value ? timestamp.format(new Date(value)) : "기록 없음";
export { dateLabel as collectionTime };

export function CollectionNavigation({ active }: { active: "items" | "pages" | "replays" | "corrections" }) {
  return <nav className="policy-filters mb-6" aria-label="수집 예외 종류">
    <a href={COLLECTION_PATH} aria-current={active === "items" ? "page" : undefined}>항목 처리</a>
    <a href={PAGE_FAILURES_PATH} aria-current={active === "pages" ? "page" : undefined}>페이지 수집</a>
    <a href={REPLAYS_PATH} aria-current={active === "replays" ? "page" : undefined}>재처리 이력</a>
    <a href={CORRECTIONS_PATH} aria-current={active === "corrections" ? "page" : undefined}>보정 관리</a>
  </nav>;
}

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
    description="항목 검증·저장 실패와 보정 충돌 목록입니다. 페이지 요청 실패는 ‘페이지 수집’에서 확인하세요."
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
      <p className="field-help">{item.outcome === "CORRECTION_CONFLICT" ? "보정한 항목의 새 원본을 확인해야 합니다. 보정 관리에서 충돌을 해소한 뒤 이 항목을 재처리해주세요." : "세부 실패 사유는 저장되어 있지 않습니다. 원본과 현재 내용을 확인해주세요."}</p>
    </section>
    {currentPolicy && <RevisionComparison policy={currentPolicy} />}
    <section className="member-panel" aria-labelledby="current-heading">
      <h2 id="current-heading">현재 공개 내용</h2>
      {currentPolicy ? <>
        <h3>{currentPolicy.content.title}</h3>
        {currentPolicy.correctionId && <p>관리자 보정 적용</p>}
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
        <div className="form-actions"><a className="text-link" href={`/policies/${currentPolicy.policyNumber}`}>공개 화면 보기</a>
          <a className="text-link" href={`${CORRECTIONS_PATH}?policyNumber=${currentPolicy.policyNumber}`}>정책 보정 관리</a></div>
      </> : <p>{item.policyNumber ? "같은 정책번호로 공개된 내용이 없습니다." : "정책번호를 확인할 수 없어 공개 내용과 연결하지 않았습니다."}</p>}
    </section>
    <section className="member-panel" aria-labelledby="raw-heading">
      <h2 id="raw-heading">수집 원본</h2>
      <pre className="exception-raw" tabIndex={0} aria-label="수집 원본 JSON">{data.rawPolicyJson}</pre>
    </section>
    <CollectionReplayForm runId={item.runId} itemIndex={item.itemIndex} attempts={item.attempts} />
  </>;
}

type CurrentPolicy = NonNullable<ExceptionDetail["currentPolicy"]>;
type PolicyContent = CurrentPolicy["content"];
const comparisonFields = {
  title: "정책명", description: "정책 설명", category: "정책 분야", organization: "운영 기관",
  applicationPeriod: "신청 기간", sections: "상세 안내", links: "공식 링크",
  regionCodes: "지역 코드", sourceModifiedAtText: "온통청년 수정일",
} satisfies Record<keyof PolicyContent, string>;

function RevisionComparison({ policy }: { policy: CurrentPolicy }) {
  const previous = policy.previousRevision;
  if (!previous) return <section className="member-panel" aria-labelledby="revision-heading">
    <h2 id="revision-heading">이전 개정 비교</h2><p>비교할 이전 개정이 없습니다.</p>
  </section>;
  const fields = Object.keys(comparisonFields) as (keyof PolicyContent)[];
  const changed = fields.filter(field => JSON.stringify(previous.content[field]) !== JSON.stringify(policy.content[field]));
  const unchanged = fields.filter(field => !changed.includes(field));
  return <section className="member-panel" aria-labelledby="revision-heading">
    <h2 id="revision-heading">이전 개정 비교</h2>
    <p className="field-help">현재 공개 내용과 바로 이전 개정을 비교합니다. 수집 실패 당시 내용과는 다를 수 있습니다.</p>
    <p className="field-help">이전 개정 {previous.revision} · 원본 수집 {dateLabel(previous.sourceCapturedAt)} (서울)<br />
      현재 개정 {policy.revision} · 원본 수집 {dateLabel(policy.sourceCapturedAt)} (서울)</p>
    <p>{changed.length ? `변경된 항목 ${changed.length}개` : "표시 항목의 변경이 없습니다."}</p>
    {changed.map(field => <section className="exception-section revision-field" key={field}>
      <h3>{comparisonFields[field]}</h3>
      <dl className="revision-values">
        <div><dt>이전 · 개정 {previous.revision}</dt><dd><RevisionValue value={previous.content[field]} /></dd></div>
        <div><dt>현재 · 개정 {policy.revision}</dt><dd><RevisionValue value={policy.content[field]} /></dd></div>
      </dl>
    </section>)}
    {unchanged.length > 0 && <details className="revision-unchanged">
      <summary>동일한 항목 {unchanged.length}개</summary>
      {unchanged.map(field => <section className="exception-section revision-field" key={field}>
        <h3>{comparisonFields[field]}</h3><RevisionValue value={policy.content[field]} />
      </section>)}
    </details>}
  </section>;
}

function RevisionValue({ value }: { value: PolicyContent[keyof PolicyContent] }) {
  if (typeof value === "string") return <p className="exception-text">{value || "내용 없음"}</p>;
  if (!value.length) return <p>내용 없음</p>;
  return <ul className="revision-list">{value.map((entry, index) => <li key={index}>
    {typeof entry === "string" ? entry : "text" in entry ? <>
      <strong>{entry.title}</strong><p className="exception-text">{entry.text}</p>
    </> : <><strong>{entry.label}</strong><p className="exception-text">{entry.url}</p></>}
  </li>)}</ul>;
}
