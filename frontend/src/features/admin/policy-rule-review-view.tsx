import { PageState } from "@/components/page-state";
import { CollectionFailure, RevisionComparison, collectionTime } from "./collection-exception-view";
import type { LoadFailure, RuleReviewDetail, RuleReviewPage } from "./load-collection-exceptions";
import { RuleDraftForm, RuleVersionControls } from "./policy-rule-actions";

export const RULE_REVIEWS_PATH = "/admin/collection-exceptions/rules";
const statuses = {
  SOURCE_CHANGED: "원문 변경", EXPIRED: "기간 만료", MISSING: "조건 미등록", SCHEDULED: "적용 전", ACTIVE: "적용 중",
} satisfies Record<RuleReviewPage["items"][number]["status"], string>;
export const reviewFilters = { REVIEW: "검토 필요", ALL: "전체", ...statuses };
type ReviewFilter = keyof typeof reviewFilters;
export type ReviewSearch = { page?: string | string[]; filter?: string | string[]; query?: string | string[] };
export function reviewSearch(params: ReviewSearch) {
  const filter: ReviewFilter = typeof params.filter === "string" && Object.hasOwn(reviewFilters, params.filter)
    ? params.filter as ReviewFilter : "REVIEW";
  return { filter, query: typeof params.query === "string" ? params.query.trim() : "" };
}
export function reviewHref(page: number, filter: string, query: string, number?: string) {
  return `${RULE_REVIEWS_PATH}${number ? `/${number}` : ""}?${new URLSearchParams({ page: String(page), filter, query })}`;
}

export function RuleReviewFailure({ status, retryHref }: { status: LoadFailure; retryHref: string }) {
  if (status === "unauthenticated" || status === "forbidden") return <CollectionFailure status={status} retryHref={retryHref} />;
  return <PageState kind={status === "missing" ? "empty" : "error"}
    title={status === "missing" ? "검토할 정책을 찾을 수 없습니다" : status === "invalid" ? "검색어·상태·페이지를 확인해주세요" : "조건 검토 내용을 불러오지 못했습니다"}
    description="조건 검토 목록을 새로 불러와 확인해주세요."
    actions={<><a className="button-primary" href={retryHref}>다시 불러오기</a><a className="button-secondary" href={RULE_REVIEWS_PATH}>검토 목록 보기</a></>} />;
}

export function RuleReviewList({ data, filter, query }: { data: RuleReviewPage; filter: ReviewFilter; query: string }) {
  return <>
    <p className="field-help">원문 변경·기간 만료·조건 미등록 순서입니다. 조건 검토 상태는 접수 여부와 다릅니다.</p>
    <div className="member-toolbar"><p>검색 결과 {data.total}건 · {data.page}페이지</p>
      <a className="text-link" href={reviewHref(data.page, filter, query)}>새로고침</a></div>
    <p className="field-help">상태 확인 {collectionTime(data.checkedAt)} (서울)</p>
    {data.items.length ? <ul className="policy-list" aria-label="조건 검토 목록">
      {data.items.map(item => <li className="policy-card" key={item.policyNumber}>
        <p className="policy-eyebrow"><span>{statuses[item.status]}</span>{item.draftCount > 0 && <span>초안 {item.draftCount}개</span>}</p>
        <h2><a href={reviewHref(data.page, filter, query, item.policyNumber)}>{item.title}</a></h2>
        <p className="policy-period">정책번호 {item.policyNumber} · 공개 버전 {item.revision}</p>
        <p className="policy-period">수집 {collectionTime(item.collectedAt)} (서울)</p>
      </li>)}
    </ul> : <PageState kind="empty" title={data.page > 1 ? "이 페이지에 정책이 없습니다" : "조회 조건에 맞는 정책이 없습니다"}
      description="검색어와 상태를 바꾸거나 첫 페이지를 확인해주세요."
      actions={<a className="button-secondary" href={reviewHref(1, filter, query)}>첫 페이지 보기</a>} />}
    <nav className="policy-pagination" aria-label="조건 검토 페이지">
      {data.page > 1 && <a className="button-secondary" href={reviewHref(data.page - 1, filter, query)}>이전</a>}
      <span aria-current="page">{data.page}페이지</span>
      {data.hasNext && data.page < 1000 && <a className="button-secondary" href={reviewHref(data.page + 1, filter, query)}>다음</a>}
    </nav>
  </>;
}

const guidance = {
  SOURCE_CHANGED: "원문이 바뀌어 질문 제공을 중단했습니다. 변경 내용과 공식 공고를 확인해 새 규칙을 등록하세요.",
  EXPIRED: "규칙 적용 기간이 끝나 질문 제공을 중단했습니다. 다음 공고의 연도·기간·조건을 확인하세요.",
  MISSING: "적용한 규칙이 없습니다. 공고의 조건과 예외를 확인해 질문 초안을 등록하세요.",
  SCHEDULED: "규칙 적용 시작 전입니다. 시작 시각과 최신 원문을 확인하세요.",
  ACTIVE: "현재 원문과 적용 기간이 일치해 질문을 제공하고 있습니다. 최종 자격은 기관 심사가 필요합니다.",
} satisfies Record<RuleReviewDetail["item"]["status"], string>;
const versionLabels = { CURRENT: "현재 지정 버전", DRAFT: "미적용 초안", PREVIOUS: "이전 적용 버전" };

export function RuleReviewContent({ data }: { data: RuleReviewDetail }) {
  const policy = data.currentPolicy;
  const expectedRuleVersion = data.versions.find(version => version.state === "CURRENT")?.ruleVersion ?? "none";
  return <>
    <section className="member-panel" aria-labelledby="review-status">
      <h2 id="review-status">{statuses[data.item.status]}</h2><p>{guidance[data.item.status]}</p>
      <p className="field-help">공개 버전 {data.item.revision} · 상태 확인 {collectionTime(data.checkedAt)} (서울)</p>
      <a className="text-link" href={`/policies/${data.item.policyNumber}`}>공개 화면 보기</a>
    </section>
    <p className="field-help">아래 비교는 직전 공개 버전 기준입니다. 기존 질문을 검토할 때 사용한 원문과 다를 수 있습니다.</p>
    <RevisionComparison policy={policy} description="질문을 다시 적용하기 전에 변경된 조건·기간과 공식 공고를 확인하세요." />
    <section className="member-panel" aria-labelledby="review-source">
      <h2 id="review-source">현재 공개 내용</h2>
      <p className="exception-text">{policy.content.description}</p>
      <dl className="exception-facts"><dt>운영 기관</dt><dd>{policy.content.organization}</dd>
        <dt>신청 기간</dt><dd>{policy.content.applicationPeriod}</dd><dt>원본 수집</dt><dd>{collectionTime(policy.sourceCapturedAt)} (서울)</dd></dl>
      {policy.correctionId && <p className="field-help">관리자 보정이 적용된 내용입니다. 수집 원본과 다를 수 있습니다.</p>}
      {policy.content.sections.map((section, index) => <details className="exception-section" key={index}>
        <summary>{section.title}</summary><p className="exception-text">{section.text}</p></details>)}
      <div className="form-actions">{policy.content.links.map((link, index) => <a className="text-link" key={index} href={link.url} target="_blank" rel="noreferrer">{link.label}</a>)}</div>
      <details className="exception-section"><summary>수집 원본 전체 보기</summary>
        <pre className="exception-raw" tabIndex={0} aria-label="수집 원본 JSON">{data.rawPolicyJson}</pre></details>
      <details className="exception-section"><summary>규칙 작성용 원문 정보</summary>
        <dl className="exception-facts"><dt>정책번호</dt><dd>{data.item.policyNumber}</dd><dt>원문 해시</dt><dd><code>{data.contentHash}</code></dd></dl>
        <p className="field-help">파일의 policyNumber·contentHash와 일치해야 합니다. 원문 해시만 바꾸지 말고 조건과 예외를 함께 검토하세요.</p></details>
    </section>
    <RuleDraftForm policyNumber={data.item.policyNumber} revision={data.item.revision} />
    <section className="member-panel" aria-labelledby="review-rules">
      <h2 id="review-rules">등록된 질문·근거</h2>
      <p className="field-help">초안은 검토 후 별도로 적용해야 합니다. 현재 지정 버전과 최근 등록 내역을 최대 21개 표시합니다.</p>
      {!data.versions.length && <p>등록된 질문이 없습니다.</p>}
      {data.versions.map(version => <details className="exception-section" key={version.id} open={version.state === "CURRENT"}>
        <summary>{versionLabels[version.state]} · {version.ruleVersion}</summary>
        <h3>{version.scope}</h3><p>{version.reason}</p>
        <p>{version.sourceMatches ? "현재 원문과 일치" : "현재 원문과 다름 · 재검토 필요"}</p>
        <dl className="exception-facts"><dt>적용 시작</dt><dd>{collectionTime(version.validFrom)} (서울)</dd>
          <dt>적용 종료</dt><dd>{collectionTime(version.validUntil)} (서울)부터 중단</dd>
          <dt>등록 사유</dt><dd>{version.changeReason}</dd></dl>
        <details><summary>등록·적용 이력</summary><dl className="exception-facts">
          <dt>등록 작업자</dt><dd>{version.createdBy}</dd><dt>등록 시각</dt><dd>{collectionTime(version.createdAt)} (서울)</dd>
          <dt>적용 작업자</dt><dd>{version.publishedBy ?? "미적용"}</dd><dt>적용 시각</dt><dd>{collectionTime(version.publishedAt)} (서울)</dd>
          <dt>적용 사유</dt><dd>{version.publishReason ?? "별도 기록 없음"}</dd></dl></details>
        <a className="text-link" href={version.sourceUrl} target="_blank" rel="noreferrer">규칙의 근거 공고 보기</a>
        <ol className="revision-list">{version.questions.map(question => <li key={question.id}>
          <strong>{question.label}</strong><p className="exception-text">{question.help}</p>
          <ul>{question.options.map(option => <li key={option.value}>{option.label}</li>)}</ul>
        </li>)}</ol>
        <h4>기관 확인이 필요한 항목</h4><ul>{version.remainingChecks.map((check, index) => <li key={index}>{check}</li>)}</ul>
        <RuleVersionControls policyNumber={data.item.policyNumber} revision={data.item.revision} expectedRuleVersion={expectedRuleVersion} version={version} />
      </details>)}
    </section>
  </>;
}
