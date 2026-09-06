"use client";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { memberApi, type BasicConditions, type PolicyChecks } from "@/features/member/member-api";
import type { operations } from "@/generated/policy-api";

type CheckSort = NonNullable<operations["checkPolicyConditions"]["parameters"]["query"]>["sort"];
const outcomeLabels = { MET: "충족", NOT_MET: "불충족", UNKNOWN: "추가 확인 필요" };

export function PolicyCheckResults({ input }: { input: BasicConditions }) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => { headingRef.current?.focus(); }, []);
  const [page, setPage] = useState(1);
  const [draftQuery, setDraftQuery] = useState("");
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<CheckSort>("AGE_MATCH");
  const [response, setResponse] = useState<PolicyChecks | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    const params = new URLSearchParams({ page: String(page), q: query, sort: sort || "AGE_MATCH" });
    memberApi<PolicyChecks>(`checks?${params}`, { method: "POST", body: input, signal: controller.signal })
      .then(result => { if (!controller.signal.aborted) setResponse(result); })
      .catch(() => { if (!controller.signal.aborted) setError("정책 조건을 확인하지 못했어요. 입력 내용은 유지되니 다시 시도해주세요."); });
    return () => controller.abort();
  }, [input, page, query, sort, retry]);
  function loadPage(next: number) {
    setResponse(null); setError(""); setPage(next); setRetry(value => value + 1);
  }
  return <section className="policy-check-results" aria-label="정책 조건 확인 결과">
    <h2 ref={headingRef} tabIndex={-1}>내 조건으로 정책 찾기</h2>
    <p>검토된 정책의 연령 조건을 비교해요. 거주·취업·소득 등 다른 조건과 현재 접수 여부는 따로 확인해주세요.</p>
    <form className="policy-search" role="search" aria-label="조건 결과에서 정책 검색" onSubmit={event => {
      event.preventDefault(); setQuery(draftQuery.trim()); loadPage(1);
    }}>
      <label className="sr-only" htmlFor="condition-policy-query">정책명·내용 검색</label>
      <input id="condition-policy-query" type="search" maxLength={80} value={draftQuery}
        onChange={event => setDraftQuery(event.target.value)} placeholder="정책명이나 내용을 검색하세요" />
      <button type="submit" className="button-primary">검색</button>
    </form>
    <div className="member-toolbar">
      <label htmlFor="condition-policy-sort">정렬</label>
      <select id="condition-policy-sort" className="condition-policy-sort" value={sort} onChange={event => {
        setSort(event.target.value as CheckSort); loadPage(1);
      }}>
        <option value="AGE_MATCH">연령 조건 충족 우선</option><option value="RECENT">최근 수집순</option>
      </select>
      {query && <button type="button" className="button-secondary" onClick={() => {
        setDraftQuery(""); setQuery(""); loadPage(1);
      }}>검색 초기화</button>}
    </div>
    <p className="field-help">연령 조건 충족 → 미확인 → 불충족 순으로 볼 수 있어요. 미확인·불충족 정책도 제외하지 않아요.</p>
    {error && <div role="alert"><p>{error}</p><button type="button" className="button-secondary" onClick={() => loadPage(page)}>다시 확인하기</button></div>}
    {!response && !error && <p role="status">신청 조건을 불러오고 있어요.</p>}
    {response?.items.length === 0 && <p role="status">{query ? "검색어에 맞는 정책이 없어요. 다른 검색어로 찾아보세요." : "현재 확인할 정책이 없어요. 내 조건이 불충족이라는 뜻은 아니에요."}</p>}
    {response && <p className="field-help" role="status">{query && `‘${query}’ 검색 결과 · `}정책 {response.total}건 · {response.page}페이지</p>}
    {response?.items.map(policy => <article className="member-panel" key={`${policy.policyNumber}-${policy.revision}`}>
      <span className="review-label">전체 자격: 추가 확인 필요</span><h3><Link href={`/policies/${policy.policyNumber}`}>{policy.title}</Link></h3>
      <p>{policy.explanation}</p><p className="policy-period">신청기간: {policy.applicationPeriod}</p>
      {policy.questionnaireAvailable && <div className="policy-question-next">
        <span className="policy-question-badge">조건 확인 질문 있음</span>
        <p>질문에 답하면 일부 신청 조건을 확인할 수 있어요.</p>
        <Link href={`/policies/${policy.policyNumber}#policy-questions`} className="text-link" aria-label={`${policy.title} 질문에 답하기`}>질문에 답하기 →</Link>
      </div>}
      <details><summary>조건별 결과와 근거 보기</summary><div className="policy-check-details">
        {policy.checks.map(check => <section key={check.label}><h4>{check.label} · {outcomeLabels[check.outcome]}</h4><p className="field-help">입력: {check.providedValue}</p><p>{check.explanation}</p><blockquote>{check.evidence}</blockquote></section>)}
        <a href={policy.sourceUrl} target="_blank" rel="noopener noreferrer">공식 안내 보기 (새 창)</a>
      </div></details>
      <Link href={`/policies/${policy.policyNumber}`} className="text-link">지원 내용 보기</Link>
    </article>)}
    {response && response.total > 0 && <nav className="member-toolbar" aria-label="정책 확인 페이지">
      <button className="button-secondary" type="button" disabled={page === 1} onClick={() => loadPage(page - 1)}>이전</button>
      <span>{page}페이지</span><button className="button-secondary" type="button" disabled={!response.hasNext || page >= 1000} onClick={() => loadPage(page + 1)}>다음</button>
    </nav>}
  </section>;
}
