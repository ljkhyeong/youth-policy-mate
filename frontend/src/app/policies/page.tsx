import type { Metadata } from "next";
import Link from "next/link";
import { SiteShell } from "@/components/site-shell";
import { PageState } from "@/components/page-state";
import { loadPolicies } from "./load-policies";
import { PolicyCard } from "./policy-content";
import { RetryPolicies } from "./retry-policies";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "정책 찾기 · 청년정책메이트", description: "온통청년에서 확인한 청년 정책의 지원 내용과 신청 안내를 찾아보세요." };

export default async function PoliciesPage({ searchParams }: { searchParams: Promise<{ q?: string; page?: string; questionsOnly?: string }> }) {
  const params = await searchParams;
  const query = typeof params.q === "string" ? params.q.trim().slice(0, 80) : "";
  const requestedPage = Number(params.page ?? 1);
  const page = Number.isInteger(requestedPage) && requestedPage >= 1 && requestedPage <= 1000 ? requestedPage : 1;
  const questionsOnly = params.questionsOnly === "true";
  const result = await loadPolicies(query, page, questionsOnly);
  const pageHref = (next: number, filtered = questionsOnly) => {
    const search = new URLSearchParams({ q: query, page: String(next) });
    if (filtered) search.set("questionsOnly", "true");
    return `/policies?${search}`;
  };

  return <SiteShell active="policies"><main id="main-content" className="policies-main">
    <header className="policies-heading"><p className="page-label">서울 청년 정책</p><h1>필요한 지원을 찾아보세요</h1><p>지원 내용부터 신청 방법까지, 로그인 없이 살펴보세요.</p></header>
    <form className="policy-search" action="/policies" role="search">
      {questionsOnly && <input type="hidden" name="questionsOnly" value="true" />}
      <label htmlFor="policy-query" className="sr-only">정책 제목과 설명 검색</label>
      <input key={query} id="policy-query" type="search" name="q" defaultValue={query} maxLength={80} placeholder="장학금, 일자리, 주거…" />
      <button type="submit" className="button-primary">검색</button>
    </form>
    <nav className="policy-filters" aria-label="공통요건 질문 필터">
      <Link href={pageHref(1, false)} aria-current={!questionsOnly ? "page" : undefined}>전체 정책</Link>
      <Link href={pageHref(1, true)} aria-current={questionsOnly ? "page" : undefined}>공통요건 질문 있음</Link>
    </nav>
    <p className="field-help">질문이 있는 정책은 일부 공통요건을 답변으로 비교할 수 있어요. 신청 자격 충족이나 접수 중이라는 뜻은 아니에요.</p>
    <p className="policy-coverage">현재 일부 정책부터 제공하고 있어요. 검색 결과에 없는 정책은 <a href="https://www.youthcenter.go.kr/" target="_blank" rel="noopener noreferrer">온통청년<span className="sr-only"> (새 창)</span></a>에서 확인해주세요.</p>
    {result.status === "available" ? <>
      <div className="policy-results-heading"><p role="status">{query ? `‘${query}’ 검색 결과` : questionsOnly ? "공통요건 질문이 있는 정책" : "확인한 정책"} <strong>{result.data.total}건</strong></p>{(query || questionsOnly) && <Link href="/policies" className="text-link">검색·필터 초기화</Link>}</div>
      {result.data.items.length > 0 ? <div className="policy-list">{result.data.items.map(policy => <PolicyCard key={policy.policyNumber} policy={policy} />)}</div>
        : result.data.total > 0 ? <PageState kind="empty" title="이 페이지에 표시할 정책이 없어요" description="목록이 변경됐을 수 있어요. 검색어와 필터를 유지한 채 첫 페이지에서 다시 확인해주세요." actions={<Link href={pageHref(1)} className="button-secondary">첫 페이지 보기</Link>} />
        : <PageState kind="empty" title="표시할 정책이 없어요" description={questionsOnly ? "현재 검색 범위에 공통요건 질문이 있는 정책이 없어요. 내 조건이 불충족이라는 뜻은 아니에요. 검색어를 바꾸거나 필터를 해제해주세요." : "검색어를 바꾸거나 전체 목록을 확인해주세요. 아직 모든 정책을 제공하고 있지는 않아요."} actions={<Link href="/policies" className="button-secondary">전체 정책 보기</Link>} />}
      {(page > 1 || result.data.hasNext) && <nav aria-label="정책 목록 페이지" className="policy-pagination">
        {page > 1 && <Link href={pageHref(page - 1)} className="button-secondary">이전</Link>}
        <span>{page}페이지</span>
        {result.data.hasNext && <Link href={pageHref(page + 1)} className="button-secondary">다음</Link>}
      </nav>}
    </> : <PageState kind="error" title="정책을 불러오지 못했어요" description="잠시 후 다시 불러와주세요. 연결 문제로 목록을 확인할 수 없는 상태예요." actions={<RetryPolicies />} />}
  </main></SiteShell>;
}
