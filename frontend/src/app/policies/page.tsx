import type { Metadata } from "next";
import Link from "next/link";
import { cache } from "react";
import { SiteShell } from "@/components/site-shell";
import { PageState } from "@/components/page-state";
import { PageCount } from "@/components/page-count";
import { loadPolicies, type PolicyListQuery } from "./load-policies";
import { PolicyCard } from "./policy-content";
import { recruitmentLabels, RecruitmentOptions, type RecruitmentFilter } from "@/features/policies/policy-recruitment";
import { policyCategories, type PolicyCategoryKey } from "@/features/policies/policy-category";
import { isSeoulDistrict, SEOUL_DISTRICTS, type SeoulDistrictName } from "@/features/policies/seoul-district";
import { RetryPolicies } from "./retry-policies";
import { publicMetadata } from "@/lib/public-metadata";

export const dynamic = "force-dynamic";
const title = "정책 찾기 · 청년정책메이트";
const description = "온통청년에서 확인한 청년 정책의 지원 내용과 신청 안내를 찾아보세요.";
// React cache는 인수를 참조로 비교해 배열·객체 인수는 매번 새 조회가 되므로, 메타데이터와 본문이 한 조회를 공유하도록 문자열 키로 받는다.
const readPolicies = cache((serialized: string) => loadPolicies(JSON.parse(serialized) as PolicyListQuery));
type Params = { q?: string; page?: string; questionsOnly?: string; recruitmentStatus?: string; category?: string | string[]; district?: string };
type Props = { searchParams: Promise<Params> };

function filters(params: Params) {
  const query = typeof params.q === "string" ? params.q.trim().slice(0, 80) : "";
  const requestedPage = Number(params.page ?? 1);
  const page = Number.isInteger(requestedPage) && requestedPage >= 1 && requestedPage <= 1000 ? requestedPage : 1;
  const questionsOnly = params.questionsOnly === "true";
  const recruitmentStatus: RecruitmentFilter = typeof params.recruitmentStatus === "string" && Object.hasOwn(recruitmentLabels, params.recruitmentStatus)
    ? params.recruitmentStatus as RecruitmentFilter : "";
  // 분야는 여러 개를 고를 수 있다. 알 수 없는 값은 무시하고 화면 순서로 정리해 주소를 안정적으로 만든다.
  const requested = [params.category ?? []].flat();
  const categories = policyCategories.map(item => item.key).filter(key => requested.includes(key));
  // 지역은 서울 자치구 표시 이름 하나다. 목록 밖 값은 무시한다.
  // 빈 문자열 리터럴이 string으로 넓혀지지 않게 타입을 적는다.
  const district: SeoulDistrictName | "" = isSeoulDistrict(params.district) ? params.district : "";
  return { query, page, questionsOnly, recruitmentStatus, categories, district };
}

const listKey = (selected: ReturnType<typeof filters>) => JSON.stringify({ ...selected, pageSize: 20 } satisfies PolicyListQuery);

export async function generateMetadata({ searchParams }: Props): Promise<Metadata> {
  const selected = filters(await searchParams);
  const { query, page, questionsOnly, recruitmentStatus, categories, district } = selected;
  if (query || questionsOnly || recruitmentStatus || categories.length || district) return { title, description, robots: { index: false, follow: true } };
  const result = await readPolicies(listKey(selected));
  if (result.status !== "available" || result.data.items.length === 0) return { title, description, robots: { index: false, follow: false } };
  return publicMetadata(page === 1 ? "/policies" : `/policies?page=${page}`, title, description);
}

export default async function PoliciesPage({ searchParams }: Props) {
  const selected = filters(await searchParams);
  const { query, page, questionsOnly, recruitmentStatus, categories, district } = selected;
  const result = await readPolicies(listKey(selected));
  const pageHref = (next: number, filtered = questionsOnly, chosen: readonly PolicyCategoryKey[] = categories) => {
    const search = new URLSearchParams({ q: query, page: String(next) });
    if (filtered) search.set("questionsOnly", "true");
    if (recruitmentStatus) search.set("recruitmentStatus", recruitmentStatus);
    if (district) search.set("district", district);
    for (const key of chosen) search.append("category", key);
    return `/policies?${search}`;
  };
  // 분야 칩은 누를 때마다 해당 분야를 더하거나 뺀다.
  const toggled = (key: PolicyCategoryKey) => policyCategories.map(item => item.key)
    .filter(item => item === key ? !categories.includes(key) : categories.includes(item));
  const categoryNames = policyCategories.filter(item => categories.includes(item.key)).map(item => item.label).join(", ");
  const filtered = Boolean(query || questionsOnly || recruitmentStatus || categories.length > 0 || district);
  // 같은 순위만 남는 접수 상태 필터에서는 최근 수집순이므로 안내하지 않는다.
  const orderNote = !recruitmentStatus ? "접수 중인 정책부터 마감 임박순" : recruitmentStatus === "OPEN" ? "마감 임박순" : null;

  return <SiteShell active="policies"><main id="main-content" className="policies-main policy-catalog">
    <header className="policies-heading"><div><p className="page-label">서울 청년 정책</p><h1>정책 찾기</h1></div><Link href="/conditions" className="text-link">내 조건 입력</Link></header>
    <section className="policy-search-panel" aria-label="정책 검색과 필터">
      <form id="policy-search-form" className="policy-search" action="/policies" role="search">
        {questionsOnly && <input type="hidden" name="questionsOnly" value="true" />}
        {categories.map(key => <input key={key} type="hidden" name="category" value={key} />)}
        <label htmlFor="policy-query" className="sr-only">정책명·내용 검색</label>
        <input key={query} id="policy-query" type="search" name="q" defaultValue={query} maxLength={80} placeholder="장학금, 취업, 대출…" />
        <div className="policy-search-select">
          <label htmlFor="policy-district" className="sr-only">지역</label>
          <select key={district} id="policy-district" name="district" form="policy-search-form"
            className="condition-policy-sort" defaultValue={district}>
            <option value="">전체 지역</option>
            {SEOUL_DISTRICTS.map(name => <option key={name} value={name}>{name}</option>)}
          </select>
        </div>
        <div className="policy-search-select">
          <label htmlFor="policy-recruitment-status" className="sr-only">접수 상태</label>
          <select key={recruitmentStatus} id="policy-recruitment-status" name="recruitmentStatus" form="policy-search-form"
            className="condition-policy-sort" defaultValue={recruitmentStatus}><RecruitmentOptions /></select>
        </div>
        <button type="submit" className="button-primary">검색</button>
      </form>
      {/* 줄 앞 이름표는 눈으로 보는 안내이고, 화면 낭독기는 각 nav의 이름으로 읽는다. */}
      <div className="policy-filter-rows">
        <span className="filter-label" aria-hidden="true">분야</span>
        <nav className="category-filter" aria-label="분야 필터, 여러 개 선택 가능">
          <Link href={pageHref(1, questionsOnly, [])} data-selected={categories.length === 0 || undefined}>
            전체 분야{categories.length === 0 && <span className="sr-only"> 선택됨</span>}</Link>
          {policyCategories.map(item => {
            const chosen = categories.includes(item.key);
            return <Link key={item.key} href={pageHref(1, questionsOnly, toggled(item.key))} data-selected={chosen || undefined}>
              {item.label}{chosen && <span className="sr-only"> 선택됨, 누르면 해제</span>}</Link>;
          })}
        </nav>
        <span className="filter-label" aria-hidden="true">조건 확인</span>
        {/* 질문 필터는 한 칸을 켜고 끈다. */}
        <nav className="policy-filters" aria-label="조건 확인 질문 필터">
          <Link href={pageHref(1, !questionsOnly)} data-selected={questionsOnly || undefined}>
            질문 있는 정책{questionsOnly && <span className="sr-only"> 선택됨, 누르면 해제</span>}</Link>
        </nav>
      </div>
      {district && <p className="field-help">선택한 자치구가 공고 지역에 포함된 정책을 보여줘요. 신청 자격은 공고에서 확인해주세요.</p>}
      {questionsOnly && <p className="field-help">질문으로 일부 신청 조건을 비교할 수 있어요. 최종 자격과 접수 여부는 공식 신청처에서 확인해주세요.</p>}
    </section>
    <p className="policy-coverage">서울 청년 대상 정책 일부를 제공해요. 전체 정책은 <a href="https://www.youthcenter.go.kr/" target="_blank" rel="noopener noreferrer">온통청년<span className="sr-only"> (새 창)</span></a>에서 확인하세요.</p>
    {result.status === "available" ? <>
      <div className="policy-results-heading"><p role="status">{[query ? `‘${query}’ 검색 결과` : questionsOnly ? "조건 확인 질문이 있는 정책" : categoryNames || district ? "" : "전체 정책", categoryNames, district].filter(Boolean).join(" · ")}{recruitmentStatus && ` · ${recruitmentLabels[recruitmentStatus]}`} <strong>{result.data.total}건</strong>{orderNote && result.data.total > 0 && <span className="policy-order-note">{orderNote}</span>}</p>{filtered && <Link href="/policies" className="text-link">검색·필터 초기화</Link>}</div>
      {result.data.items.length > 0 ? <div className="policy-list">{result.data.items.map(policy => <PolicyCard key={policy.policyNumber} policy={policy} />)}</div>
        : result.data.total > 0 ? <PageState kind="empty" title="이 페이지에 표시할 정책이 없어요" description="첫 페이지에서 다시 확인해주세요. 검색어와 필터는 유지돼요." actions={<Link href={pageHref(1)} className="button-secondary">첫 페이지 보기</Link>} />
        : <PageState kind="empty" title="표시할 정책이 없어요" description={recruitmentStatus ? "선택한 접수 상태에 맞는 정책이 없어요. 검색어를 바꾸거나 필터를 해제해주세요." : categories.length ? "선택한 분야에 맞는 정책이 없어요. 검색어를 바꾸거나 전체 분야를 확인해주세요." : district ? "선택한 지역이 포함된 정책이 없어요. 지역을 바꾸거나 전체 지역을 확인해주세요." : questionsOnly ? "질문이 있는 정책 중 검색 결과가 없어요. 검색어를 바꾸거나 필터를 해제해주세요." : "검색어를 바꾸거나 전체 정책을 확인해주세요."} actions={<Link href="/policies" className="button-secondary">전체 정책 보기</Link>} />}
      {(page > 1 || result.data.hasNext) && <nav aria-label="정책 목록 페이지" className="policy-pagination">
        {page > 1 && <Link href={pageHref(page - 1)} className="button-secondary">이전</Link>}
        <PageCount page={page} total={result.data.total} pageSize={result.data.pageSize} />
        {result.data.hasNext && <Link href={pageHref(page + 1)} className="button-secondary">다음</Link>}
      </nav>}
    </> : <PageState kind="error" title="정책을 불러오지 못했어요" description="잠시 후 다시 시도해주세요." actions={<RetryPolicies />} />}
  </main></SiteShell>;
}
