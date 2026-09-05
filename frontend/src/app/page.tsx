import Link from "next/link";
import { SiteShell } from "@/components/site-shell";

export default function HomePage() {
  return (
    <SiteShell active="home">
      <main id="main-content" className="home-main">
        <section aria-labelledby="intro-title" className="home-intro">
          <p className="location-label"><span aria-hidden="true" />서울 청년 정책</p>
          <h1 id="intro-title">나에게 맞는 정책,<br />조건부터 간단하게.</h1>
          <p>생년월일·거주지·취업상태를 입력하고 정책별 신청 조건을 확인하세요.</p>
        </section>

        <section className="start-panel" aria-labelledby="start-title">
          <div className="start-panel-heading">
            <div>
              <p>약 1분이면 돼요</p>
              <h2 id="start-title">기본 조건 3개만 알려주세요</h2>
            </div>
            <span className="start-count" aria-label="입력 항목 3개">3</span>
          </div>
          <ol className="condition-list" aria-label="입력할 기본 조건">
            <li><span>1</span><strong>생년월일</strong><small>양력 기준</small></li>
            <li><span>2</span><strong>서울 거주지</strong><small>주민등록상 주소</small></li>
            <li><span>3</span><strong>취업상태</strong><small>현재 주된 상태</small></li>
          </ol>
          <Link href="/conditions" className="button-primary button-block">
            내 조건 입력하기
            <span className="button-arrow" aria-hidden="true">→</span>
          </Link>
          <p className="privacy-note">
            <LockIcon />로그인 없이 이용할 수 있어요. 입력 내용은 자동 저장되지 않아요.
          </p>
        </section>

        <aside aria-labelledby="status-title" className="service-status">
          <span className="status-dot" aria-hidden="true" />
          <div>
            <p className="status-label">이용 안내</p>
            <h2 id="status-title">지원 내용을 먼저 살펴보세요</h2>
            <p>공식 정책 안내와 일부 정책의 조건 확인 질문을 볼 수 있어요. 로그인하면 관심 정책과 마감 일정·서비스 내 알림을 관리해요.</p>
            <Link className="text-link" href="/policies">정책 찾아보기 →</Link>
          </div>
        </aside>
      </main>
    </SiteShell>
  );
}

function LockIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" fill="none">
      <rect x="5" y="10" width="14" height="10" rx="2.5" stroke="currentColor" strokeWidth="1.8" />
      <path d="M8.5 10V7.5a3.5 3.5 0 0 1 7 0V10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}
