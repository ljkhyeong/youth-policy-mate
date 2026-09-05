"use client";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { memberApi, type BasicConditions, type PolicyChecks } from "@/features/member/member-api";

export function PolicyCheckResults({ input }: { input: BasicConditions }) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => { headingRef.current?.focus(); }, []);
  const [page, setPage] = useState(1);
  const [response, setResponse] = useState<PolicyChecks | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    memberApi<PolicyChecks>(`checks?page=${page}`, { method: "POST", body: input, signal: controller.signal })
      .then(result => { if (!controller.signal.aborted) setResponse(result); })
      .catch(() => { if (!controller.signal.aborted) setError("정책 조건을 확인하지 못했어요. 입력 내용은 유지되니 다시 시도해주세요."); });
    return () => controller.abort();
  }, [input, page, retry]);
  function loadPage(next: number) {
    setResponse(null); setError(""); setPage(next); setRetry(value => value + 1);
  }
  return <section className="policy-check-results" aria-label="실제 정책 조건 확인 결과">
    <h2 ref={headingRef} tabIndex={-1}>정책별로 확인할 조건</h2>
    <p>현재 수집한 일부 정책의 원문을 확인해요. 기준일·추가 요건을 검토하기 전에는 신청 가능 여부를 확정할 수 없어요.</p>
    {error && <div role="alert"><p>{error}</p><button type="button" className="button-secondary" onClick={() => loadPage(page)}>다시 확인하기</button></div>}
    {!response && !error && <p role="status">실제 정책의 조건을 확인하고 있어요.</p>}
    {response?.items.length === 0 && <p role="status">현재 확인할 정책이 없어요. 내 조건이 불충족이라는 뜻은 아니에요.</p>}
    {response && <p className="field-help">수집한 정책 {response.total}건 · {response.page}페이지</p>}
    {response?.items.map(policy => <article className="member-panel" key={`${policy.policyNumber}-${policy.revision}`}>
      <span className="review-label">추가 확인 필요</span><h3><Link href={`/policies/${policy.policyNumber}`}>{policy.title}</Link></h3>
      <p>{policy.explanation}</p><p className="policy-period">신청기간: {policy.applicationPeriod}</p>
      <details><summary>확인할 항목과 원문 보기</summary><div className="policy-check-details">
        {policy.checks.map(check => <section key={check.label}><h4>{check.label}</h4><p className="field-help">입력: {check.providedValue}</p><p>{check.explanation}</p><blockquote>{check.evidence}</blockquote></section>)}
        <a href={policy.sourceUrl} target="_blank" rel="noopener noreferrer">공식 원문 확인 (새 창)</a>
      </div></details>
      <Link href={`/policies/${policy.policyNumber}`} className="text-link">지원 내용·저장하기</Link>
    </article>)}
    {response && <nav className="member-toolbar" aria-label="정책 확인 페이지">
      <button className="button-secondary" type="button" disabled={page === 1} onClick={() => loadPage(page - 1)}>이전</button>
      <span>{page}페이지</span><button className="button-secondary" type="button" disabled={!response.hasNext} onClick={() => loadPage(page + 1)}>다음</button>
    </nav>}
  </section>;
}
