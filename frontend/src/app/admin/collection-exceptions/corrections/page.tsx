import { collectionPage, loadPolicyCorrections, loadCorrectionPolicy, type CorrectionItem } from "@/features/admin/load-collection-exceptions";
import { CollectionFailure, CollectionNavigation, CORRECTIONS_PATH, collectionTime } from "@/features/admin/collection-exception-view";
import { PolicyCorrectionForm } from "@/features/admin/policy-correction-form";

const fieldLabels = { TITLE: "정책명", ORGANIZATION: "운영 기관" } as const;
const statusLabels = { ACTIVE: "보정 적용 중", CONFLICT: "새 원본 확인 필요", RELEASED: "처리 완료" } as const;

export default async function PolicyCorrectionsPage({ searchParams }: { searchParams: Promise<{ page?: string | string[]; policyNumber?: string | string[] }> }) {
  const query = await searchParams;
  const page = collectionPage(query.page);
  const number = typeof query.policyNumber === "string" ? query.policyNumber.trim() : "";
  const [result, policy] = await Promise.all([loadPolicyCorrections(page), number ? loadCorrectionPolicy(number) : null]);
  return <>
    <header><p className="page-label">운영 관리</p><h1>수집 예외</h1><p>정책명·운영 기관을 수정하고, 새 수집 원본과 충돌한 내용을 확인합니다.</p></header>
    <CollectionNavigation active="corrections" />
    {result.status !== "available" ? <CollectionFailure status={result.status} retryHref={CORRECTIONS_PATH} /> : <>
      <section className="member-panel" aria-labelledby="correction-heading">
        <h2 id="correction-heading">정책 보정</h2>
        <p className="field-help">정책명·운영 기관 중 한 항목을 보정할 수 있습니다. 수집 원본과 작업 이력은 보관합니다.</p>
        <form action={CORRECTIONS_PATH} className="form-field">
          <label htmlFor="correction-policy">정책번호</label>
          <input id="correction-policy" name="policyNumber" inputMode="numeric" pattern="[0-9]{1,100}" maxLength={100} required defaultValue={number} />
          <button className="button-secondary" type="submit">공개 정책 조회</button>
        </form>
        {policy?.status === "available" ? <>
          <h3>{policy.data.content.title}</h3><p className="field-help">현재 개정 {policy.data.revision}</p>
          {policy.data.correctionId ? <p>이미 보정이 적용되어 있습니다. 아래 이력에서 보정을 해제하거나 충돌을 처리해주세요.</p>
            : <PolicyCorrectionForm policy={policy.data} />}
        </> : policy && (policy.status === "missing" || policy.status === "invalid"
          ? <p role="status">공개된 정책을 찾을 수 없습니다. 정책번호를 확인해주세요.</p>
          : <CollectionFailure status={policy.status} retryHref={`${CORRECTIONS_PATH}?policyNumber=${encodeURIComponent(number)}`} />)}
      </section>
      <section aria-labelledby="correction-history-heading"><h2 id="correction-history-heading">보정 이력</h2>
        {!result.data.items.length ? <p>이 페이지에 보정 이력이 없습니다.</p> : <ul className="policy-list" aria-label="정책 보정 이력">
          {result.data.items.map(item => <CorrectionCard key={item.id} item={item} />)}
        </ul>}
        <nav className="policy-pagination" aria-label="보정 이력 페이지">
          {page > 1 && <a className="button-secondary" href={`${CORRECTIONS_PATH}?page=${page - 1}`}>이전</a>}
          <span aria-current="page">{page}페이지</span>
          {result.data.hasNext && page < 1000 && <a className="button-secondary" href={`${CORRECTIONS_PATH}?page=${page + 1}`}>다음</a>}
        </nav>
      </section>
    </>}
  </>;
}

function CorrectionCard({ item }: { item: CorrectionItem }) {
  return <li className="policy-card">
    <p className="policy-eyebrow"><span>{statusLabels[item.status]}</span><span>{fieldLabels[item.field]}</span></p>
    <h3><a href={`${CORRECTIONS_PATH}?policyNumber=${item.policyNumber}`}>정책 {item.policyNumber}</a></h3>
    <dl className="exception-facts">
      <dt>보정 전 원본</dt><dd>{item.sourceValue || "내용 없음"}</dd>
      <dt>보정 값</dt><dd>{item.value}</dd>
      <dt>보정 사유</dt><dd>{item.reason}</dd>
      <dt>적용 개정</dt><dd>{item.appliedRevision}</dd>
      <dt>작업자 ID</dt><dd>{item.actorId}</dd>
      <dt>기록 시각</dt><dd>{collectionTime(item.createdAt)} (서울)</dd>
    </dl>
    {item.status === "CONFLICT" && <>
      <p><strong>새 원본 값: {item.reviewValue || "내용 없음"}</strong></p>
      <details className="revision-unchanged"><summary>반영할 원본 내용 확인</summary>
        <h4>{item.reviewContent.title}</h4>
        <p className="exception-text">{item.reviewContent.description}</p>
        <dl className="exception-facts"><dt>운영 기관</dt><dd>{item.reviewContent.organization}</dd>
          <dt>정책 분야</dt><dd>{item.reviewContent.category}</dd><dt>신청 기간</dt><dd>{item.reviewContent.applicationPeriod}</dd>
          <dt>지역 코드</dt><dd>{item.reviewContent.regionCodes.join(", ") || "내용 없음"}</dd>
          <dt>온통청년 수정일</dt><dd>{item.reviewContent.sourceModifiedAtText || "기록 없음"}</dd></dl>
        {item.reviewContent.sections.map((section, index) => <section key={index} className="exception-section">
          <h4>{section.title}</h4><p className="exception-text">{section.text}</p>
        </section>)}
        {item.reviewContent.links.map((link, index) => <p key={index} className="exception-text">{link.label}: {link.url}</p>)}
      </details>
    </>}
    {item.status !== "RELEASED" ? <PolicyCorrectionForm correction={item} /> : <dl className="exception-facts">
      <dt>처리 결과</dt><dd>{item.resolution === "KEEP" ? "새 원본 기준 보정 유지" : "보정 해제 · 원본 적용"}</dd>
      <dt>처리 사유</dt><dd>{item.resolvedReason}</dd><dt>처리 후 개정</dt><dd>{item.resolvedRevision}</dd>
      <dt>처리자 ID</dt><dd>{item.resolvedBy}</dd><dt>처리 시각</dt><dd>{collectionTime(item.resolvedAt)} (서울)</dd>
    </dl>}
  </li>;
}
