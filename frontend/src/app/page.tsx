import Link from "next/link";
import { SiteShell } from "@/components/site-shell";
import { publicMetadata, siteName, siteDescription } from "@/lib/public-metadata";
import { CategoryIcon, categoryKey, categoryLabel, policyCategories } from "@/features/policies/policy-category";
import { formatPolicyPeriod } from "@/features/policies/policy-period";
import { RecruitmentBadge } from "@/features/policies/policy-recruitment";
import { loadPolicies } from "./policies/load-policies";

export const dynamic = "force-dynamic";
export function generateMetadata() { return publicMetadata("/", siteName, siteDescription); }

const searchTopics = ["창업", "취업", "장학금", "대출"];

export default async function HomePage() {
  const open = await loadPolicies("", 1, false, "OPEN", 4);
  return (
    <SiteShell active="home">
      <main id="main-content" className="home-main">
        <div className="home-start">
          <section aria-labelledby="intro-title" className="home-intro">
            <p className="location-label">서울 청년 정책</p>
            <h1 id="intro-title">어떤 지원이<br />필요하세요?</h1>
            <p>분야를 고르거나 검색해서 공식 정책 안내를 확인하세요.</p>
            <form className="policy-search" action="/policies" role="search">
              <label htmlFor="home-query" className="sr-only">정책 검색</label>
              <input id="home-query" type="search" name="q" maxLength={80} placeholder="장학금, 취업, 대출…" />
              <button type="submit" className="button-primary">검색</button>
            </form>
            <nav className="home-topics" aria-label="정책 검색어 바로가기">
              <span>찾아보기</span>
              {searchTopics.map(topic => <Link key={topic} href={`/policies?q=${encodeURIComponent(topic)}`}>{topic}</Link>)}
            </nav>
          </section>

          <nav className="category-tiles" aria-label="분야로 찾기">
            {policyCategories.map(category => <Link key={category.key} href={`/policies?category=${category.key}`} className="category-tile" data-category={category.key}>
              <CategoryIcon category={category.key} />{category.label}
            </Link>)}
            <Link href="/policies" className="category-tile" data-category="ALL"><CategoryIcon category="ALL" />전체 정책</Link>
          </nav>
        </div>

        {/* 조회에 실패하면 빈 결과로 오해하지 않도록 영역을 표시하지 않는다. */}
        {open.status === "available" && <section className="home-open" aria-labelledby="open-title">
          <div className="home-section-heading">
            <h2 id="open-title">지금 접수 중인 정책</h2>
            {open.data.total > 0 && <Link className="text-link" href="/policies?recruitmentStatus=OPEN">{open.data.total}건 모두 보기</Link>}
          </div>
          {open.data.items.length > 0 ? <ul className="home-open-list">
            {open.data.items.map(policy => <li key={policy.policyNumber} className="home-open-card">
              <div className="policy-card-top">
                <span className="category-chip" data-category={categoryKey(policy.category)}>{categoryLabel(policy.category)}</span>
                <RecruitmentBadge recruitment={policy.recruitment} />
              </div>
              <Link href={`/policies/${policy.policyNumber}`}>{policy.title}</Link>
              <p>{policy.organization || "온통청년 제공"}</p>
              <p className="policy-period"><strong>신청기간</strong><span>{formatPolicyPeriod(policy.applicationPeriod)}</span></p>
            </li>)}
          </ul> : <p className="home-open-empty">지금 접수 기간인 정책이 없어요. <Link className="text-link" href="/policies?recruitmentStatus=ROLLING">상시 모집 정책 보기</Link></p>}
          <p className="field-help">접수 상태는 서울 날짜 기준이에요. 실제 접수 여부는 공식 신청처에서 확인해주세요.</p>
        </section>}

        <section className="home-followup" aria-label="정책 이용 안내">
          <Link href="/conditions" className="condition-banner">
            <strong>내 조건으로 연령 비교</strong>
            <span>생년월일로 정책별 연령 조건을 확인해요. 로그인 없이 이용하고, 입력 내용은 자동 저장하지 않아요.</span>
            <span className="condition-banner-action">조건 입력하기 <span aria-hidden="true">→</span></span>
          </Link>
          <div>
            <h2>관심 있는 정책은 저장해두세요</h2>
            <p>로그인하면 정책을 저장하고 마감일과 알림을 확인할 수 있어요.</p>
            <Link className="text-link" href="/my">내 정책 보기</Link>
          </div>
          <div>
            <h2>신청 전, 공식 공고를 확인하세요</h2>
            <p>일부 정책의 조건만 비교할 수 있어요. 최종 신청 자격과 접수 여부는 공식 신청처에서 확인해주세요.</p>
          </div>
        </section>
      </main>
    </SiteShell>
  );
}
