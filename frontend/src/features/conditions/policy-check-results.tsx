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
  return <section className="policy-check-results" aria-label="정책 조건 확인 결과">
    <h2 ref={headingRef} tabIndex={-1}>정책별로 확인할 조건</h2>
    <p>일부 정책의 신청 조건을 안내해요. 연령 기준일과 추가 조건을 확인해야 신청 가능 여부를 알 수 있어요.</p>
    {error && <div role="alert"><p>{error}</p><button type="button" className="button-secondary" onClick={() => loadPage(page)}>다시 확인하기</button></div>}
    {!response && !error && <p role="status">신청 조건을 불러오고 있어요.</p>}
    {response?.items.length === 0 && <p role="status">현재 확인할 정책이 없어요. 내 조건이 불충족이라는 뜻은 아니에요.</p>}
    {response && <p className="field-help">정책 {response.total}건 · {response.page}페이지</p>}
    {response?.items.map(policy => <article className="member-panel" key={`${policy.policyNumber}-${policy.revision}`}>
      <span className="review-label">추가 확인 필요</span><h3><Link href={`/policies/${policy.policyNumber}`}>{policy.title}</Link></h3>
      <p>{policy.explanation}</p><p className="policy-period">신청기간: {policy.applicationPeriod}</p>
      {policy.questionnaireAvailable && <div className="policy-question-next">
        <span className="policy-question-badge">조건 확인 질문 있음</span>
        <p>질문에 답하면 일부 신청 조건을 확인할 수 있어요.</p>
        <Link href={`/policies/${policy.policyNumber}#policy-questions`} className="text-link" aria-label={`${policy.title} 질문에 답하기`}>질문에 답하기 →</Link>
      </div>}
      <details><summary>신청 조건과 공식 안내 보기</summary><div className="policy-check-details">
        {policy.checks.map(check => <section key={check.label}><h4>{check.label}</h4><p className="field-help">입력: {check.providedValue}</p><p>{check.explanation}</p><blockquote>{check.evidence}</blockquote></section>)}
        <a href={policy.sourceUrl} target="_blank" rel="noopener noreferrer">공식 안내 보기 (새 창)</a>
      </div></details>
      <Link href={`/policies/${policy.policyNumber}`} className="text-link">지원 내용 보기</Link>
    </article>)}
    {response && <nav className="member-toolbar" aria-label="정책 확인 페이지">
      <button className="button-secondary" type="button" disabled={page === 1} onClick={() => loadPage(page - 1)}>이전</button>
      <span>{page}페이지</span><button className="button-secondary" type="button" disabled={!response.hasNext} onClick={() => loadPage(page + 1)}>다음</button>
    </nav>}
  </section>;
}
