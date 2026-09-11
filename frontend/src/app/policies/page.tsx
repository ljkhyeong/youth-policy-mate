import type { Metadata } from "next";
import Link from "next/link";
import { SiteShell } from "@/components/site-shell";
import { PageState } from "@/components/page-state";
import { loadPolicies } from "./load-policies";
import { PolicyCard } from "./policy-content";
import { recruitmentLabels, RecruitmentOptions, type RecruitmentFilter } from "@/features/policies/policy-recruitment";
import { RetryPolicies } from "./retry-policies";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "정책 찾기 · 청년정책메이트", description: "온통청년에서 확인한 청년 정책의 지원 내용과 신청 안내를 찾아보세요." };

export default async function PoliciesPage({ searchParams }: { searchParams: Promise<{ q?: string; page?: string; questionsOnly?: string; recruitmentStatus?: string }> }) {
  const params = await searchParams;
  const query = typeof params.q === "string" ? params.q.trim().slice(0, 80) : "";
  const requestedPage = Number(params.page ?? 1);
  const page = Number.isInteger(requestedPage) && requestedPage >= 1 && requestedPage <= 1000 ? requestedPage : 1;
  const questionsOnly = params.questionsOnly === "true";
  const recruitmentStatus: RecruitmentFilter = typeof params.recruitmentStatus === "string" && Object.hasOwn(recruitmentLabels, params.recruitmentStatus)
    ? params.recruitmentStatus as RecruitmentFilter : "";
  const result = await loadPolicies(query, page, questionsOnly, recruitmentStatus);
  const pageHref = (next: number, filtered = questionsOnly) => {
    const search = new URLSearchParams({ q: query, page: String(next) });
    if (filtered) search.set("questionsOnly", "true");
    if (recruitmentStatus) search.set("recruitmentStatus", recruitmentStatus);
    return `/policies?${search}`;
  };

  return <SiteShell active="policies"><main id="main-content" className="policies-main policy-catalog">
    <header className="policies-heading"><div><p className="page-label">서울 청년 정책</p><h1>정책 찾기</h1></div><Link href="/conditions" className="text-link">내 조건 입력</Link></header>
    <section className="policy-search-panel" aria-label="정책 검색과 필터">
      <form id="policy-search-form" className="policy-search" action="/policies" role="search">
        {questionsOnly && <input type="hidden" name="questionsOnly" value="true" />}
        <label htmlFor="policy-query" className="sr-only">정책명·내용 검색</label>
        <input key={query} id="policy-query" type="search" name="q" defaultValue={query} maxLength={80} placeholder="장학금, 일자리, 주거…" />
        <div className="policy-status-filter">
          <label htmlFor="policy-recruitment-status" className="sr-only">접수 상태</label>
          <select key={recruitmentStatus} id="policy-recruitment-status" name="recruitmentStatus" form="policy-search-form"
            className="condition-policy-sort" defaultValue={recruitmentStatus}><RecruitmentOptions /></select>
        </div>
        <button type="submit" className="button-primary">검색</button>
      </form>
      <nav className="policy-filters" aria-label="조건 확인 질문 필터">
        <Link href={pageHref(1, false)} aria-current={!questionsOnly ? "page" : undefined}>전체 정책</Link>
        <Link href={pageHref(1, true)} aria-current={questionsOnly ? "page" : undefined}>질문 있는 정책</Link>
      </nav>
      {questionsOnly && <p className="field-help">질문으로 일부 신청 조건을 비교할 수 있어요. 최종 자격과 접수 여부는 공식 신청처에서 확인해주세요.</p>}
    </section>
    <p className="policy-coverage">서울 청년 대상 정책 일부를 제공해요. 전체 정책은 <a href="https://www.youthcenter.go.kr/" target="_blank" rel="noopener noreferrer">온통청년<span className="sr-only"> (새 창)</span></a>에서 확인하세요.</p>
    {result.status === "available" ? <>
      <div className="policy-results-heading"><p role="status">{query ? `‘${query}’ 검색 결과` : questionsOnly ? "조건 확인 질문이 있는 정책" : "전체 정책"} {recruitmentStatus && ` · ${recruitmentLabels[recruitmentStatus]}`} <strong>{result.data.total}건</strong></p>{(query || questionsOnly || recruitmentStatus) && <Link href="/policies" className="text-link">검색·필터 초기화</Link>}</div>
      {result.data.items.length > 0 ? <div className="policy-list">{result.data.items.map(policy => <PolicyCard key={policy.policyNumber} policy={policy} />)}</div>
        : result.data.total > 0 ? <PageState kind="empty" title="이 페이지에 표시할 정책이 없어요" description="첫 페이지에서 다시 확인해주세요. 검색어와 필터는 유지돼요." actions={<Link href={pageHref(1)} className="button-secondary">첫 페이지 보기</Link>} />
        : <PageState kind="empty" title="표시할 정책이 없어요" description={recruitmentStatus ? "선택한 접수 상태에 맞는 정책이 없어요. 검색어를 바꾸거나 필터를 해제해주세요." : questionsOnly ? "질문이 있는 정책 중 검색 결과가 없어요. 검색어를 바꾸거나 필터를 해제해주세요." : "검색어를 바꾸거나 전체 정책을 확인해주세요."} actions={<Link href="/policies" className="button-secondary">전체 정책 보기</Link>} />}
      {(page > 1 || result.data.hasNext) && <nav aria-label="정책 목록 페이지" className="policy-pagination">
        {page > 1 && <Link href={pageHref(page - 1)} className="button-secondary">이전</Link>}
        <span>{page}페이지</span>
        {result.data.hasNext && <Link href={pageHref(page + 1)} className="button-secondary">다음</Link>}
      </nav>}
    </> : <PageState kind="error" title="정책을 불러오지 못했어요" description="잠시 후 다시 시도해주세요." actions={<RetryPolicies />} />}
  </main></SiteShell>;
}
