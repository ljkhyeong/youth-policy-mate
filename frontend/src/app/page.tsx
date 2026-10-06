import Link from "next/link";
import { SiteShell } from "@/components/site-shell";
import { publicMetadata, siteName, siteDescription } from "@/lib/public-metadata";
import { CategoryChips, policyCategories, type PolicyCategoryKey } from "@/features/policies/policy-category";
import { policySituations } from "@/features/policies/policy-situations";
import { PolicyPeriodText } from "@/features/policies/policy-period-text";
import { RecruitmentBadge } from "@/features/policies/policy-recruitment";
import { loadCategoryCounts, loadPolicies } from "./policies/load-policies";

export const dynamic = "force-dynamic";
export function generateMetadata() { return publicMetadata("/", siteName, siteDescription); }

const searchTopics = ["창업", "취업", "장학금", "대출"];

export default async function HomePage() {
  const [open, counts] = await Promise.all([loadPolicies("", 1, false, "OPEN", 4), loadCategoryCounts()]);
  // 수를 불러오지 못하면 상황과 분야 이름만 표시한다.
  const countOf = (key: PolicyCategoryKey) => counts.status === "available" ? counts.data.items.find(item => item.category === key)?.count : undefined;
  const categoryName = (key: PolicyCategoryKey) => policyCategories.find(item => item.key === key)?.label ?? "";
  return (
    <SiteShell active="home">
      <main id="main-content" className="home-main">
        <div className="home-layout">
          <div className="home-start">
            <section aria-labelledby="intro-title" className="home-intro">
              <h1 id="intro-title">읽기 어려운 청년 정책 공고,<br /><mark>읽기 쉽게</mark> 정리해 뒀어요.</h1>
              <p>{counts.status === "available" ? `서울 청년이 볼 만한 정책 ${counts.data.total}건을 모았어요.` : "서울 청년이 볼 만한 정책을 모았어요."} 해당하는 칸에 표시하면 맞는 분야만 보여드려요.</p>
            </section>

            {/* 상황은 분야 필터다. 표시한 분야를 모두 담아 정책 목록으로 이동하고, 표시하지 않으면 전체 목록을 보여준다. */}
            <form className="situation-form" action="/policies">
              <fieldset>
                <legend>□ 해당하는 칸에 표시하세요 (여러 개 가능)</legend>
                {policySituations.map(situation => {
                  const count = countOf(situation.category);
                  return <label key={situation.category} className="situation-option">
                    <input type="checkbox" name="category" value={situation.category} />
                    <span className="paper-check" aria-hidden="true" />
                    <span className="situation-text">
                      <strong>{situation.label}</strong>
                      <span>{typeof count === "number" ? `${categoryName(situation.category)} · ${count}건` : categoryName(situation.category)}</span>
                    </span>
                  </label>;
                })}
              </fieldset>
              <div className="situation-actions">
                <button type="submit" className="button-primary">표시한 칸으로 정책 보기</button>
                <Link href="/policies" className="text-link">{counts.status === "available" ? `고르지 않고 전체 ${counts.data.total}건 보기` : "고르지 않고 전체 정책 보기"}</Link>
              </div>
            </form>

            <section className="home-search" aria-label="정책 검색">
              <label htmlFor="home-query" className="home-search-label">정책 이름이나 내용으로 찾기</label>
              <form className="policy-search" action="/policies" role="search">
                <input id="home-query" type="search" name="q" maxLength={80} placeholder="장학금, 취업, 대출…" />
                <button type="submit" className="button-primary">찾기</button>
              </form>
              <nav className="home-topics" aria-label="정책 검색어 바로가기">
                <span>찾아보기</span>
                {searchTopics.map(topic => <Link key={topic} href={`/policies?q=${encodeURIComponent(topic)}`}>{topic}</Link>)}
              </nav>
            </section>
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
                  <p className="policy-meta">{policy.organization || "온통청년 제공"} · <CategoryChips category={policy.category} /></p>
                  <RecruitmentBadge recruitment={policy.recruitment} />
                </div>
                <Link href={`/policies/${policy.policyNumber}`}>{policy.title}</Link>
                <p className="policy-period"><strong>신청기간</strong><PolicyPeriodText period={policy.applicationPeriod} recruitment={policy.recruitment} /></p>
              </li>)}
            </ul> : <p className="home-open-empty">지금 접수 기간인 정책이 없어요. <Link className="text-link" href="/policies?recruitmentStatus=ROLLING">상시 모집 정책 보기</Link></p>}
            <p className="field-help">접수 상태는 서울 날짜 기준이에요. 실제 접수 여부는 공식 신청처에서 확인해주세요.</p>
          </section>}
        </div>

        <section className="home-followup" aria-label="정책 이용 안내">
          <div>
            <h2>생년월일로 연령 조건 맞춰보기</h2>
            <p>로그인 없이 이용하고, 적은 내용은 저장하지 않아요.</p>
            <Link className="text-link" href="/conditions">내 조건 적기</Link>
          </div>
          <div>
            <h2>관심 있는 정책은 저장해두세요</h2>
            <p>로그인하면 마감일을 달력에 표시하고 알림을 보내드려요.</p>
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
