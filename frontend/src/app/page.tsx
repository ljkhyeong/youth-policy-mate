import Link from "next/link";
import { SiteShell } from "@/components/site-shell";
import { publicMetadata, siteName, siteDescription } from "@/lib/public-metadata";

export const dynamic = "force-dynamic";
export function generateMetadata() { return publicMetadata("/", siteName, siteDescription); }

const searchTopics = ["일자리", "주거", "장학금", "저축"];

export default function HomePage() {
  return (
    <SiteShell active="home">
      <main id="main-content" className="home-main">
        <div className="home-start">
          <section aria-labelledby="intro-title" className="home-intro">
            <p className="location-label">서울 청년을 위한 정책 안내</p>
            <h1 id="intro-title">필요한 지원을 찾고,<br />신청 조건까지 확인하세요.</h1>
            <p>일자리부터 주거·교육·생활비까지.<br />공식 정책 안내를 한곳에서 살펴보세요.</p>
            <form className="policy-search" action="/policies" role="search">
              <label htmlFor="home-query" className="sr-only">정책 검색</label>
              <input id="home-query" type="search" name="q" maxLength={80} placeholder="어떤 지원을 찾고 있나요?" />
              <button type="submit" className="button-primary">검색</button>
            </form>
            <nav className="home-topics" aria-label="정책 검색어 바로가기">
              <span>찾아보기</span>
              {searchTopics.map(topic => <Link key={topic} href={`/policies?q=${encodeURIComponent(topic)}`}>{topic}</Link>)}
            </nav>
            <Link className="text-link" href="/policies">전체 정책 보기</Link>
          </section>

          <section className="start-panel" aria-labelledby="start-title">
            <h2 id="start-title">정책별 연령 비교</h2>
            <p className="start-description">생년월일로 연령 조건을 비교하고, 정책별 질문으로 다른 조건도 확인하세요.</p>
            <ul className="condition-list" aria-label="입력할 기본 조건">
              <li><strong>생년월일</strong><small>양력 기준</small></li>
              <li><strong>서울 거주지</strong><small>주민등록상 주소</small></li>
              <li><strong>취업상태</strong><small>현재 주된 상태</small></li>
            </ul>
            <Link href="/conditions" className="button-primary button-block">내 조건 입력하기</Link>
            <p className="privacy-note">로그인 없이 이용할 수 있어요.<br />입력 내용은 조건 비교에만 사용하고 자동 저장하지 않아요.</p>
          </section>
        </div>

        <section className="home-followup" aria-label="정책 이용 안내">
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
