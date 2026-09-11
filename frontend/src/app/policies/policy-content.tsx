import Link from "next/link";
import { PolicyRecruitment } from "@/features/policies/policy-recruitment";
import type { components } from "@/generated/policy-api";
import { PolicyQuestionnaire } from "@/features/eligibility/policy-questionnaire";
import { SavePolicyButton } from "@/features/member/save-policy-button";

type Summary = components["schemas"]["PolicySummary"];
type Detail = components["schemas"]["PolicyDetailResponse"];

export function collectedTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul", year: "numeric", month: "long", day: "numeric", hour: "2-digit", minute: "2-digit",
  }).format(new Date(value));
}

export function PolicyCard({ policy }: { policy: Summary }) {
  return <article className="policy-card">
    <div className="policy-card-top"><span className="policy-category">{policy.category || "청년 정책"}</span><PolicyRecruitment recruitment={policy.recruitment} compact /></div>
    <h2><Link href={`/policies/${policy.policyNumber}`}>{policy.title}</Link></h2>
    <p className="policy-organization">{policy.organization || "온통청년 제공"}</p>
    <p className="policy-description">{policy.description || "자세한 지원 내용을 확인해보세요."}</p>
    <p className="policy-period"><strong>신청기간</strong><span>{policy.applicationPeriod}</span></p>
    <div className="policy-card-actions">
      <Link href={`/policies/${policy.policyNumber}`} className="text-link" aria-label={`${policy.title} 상세 보기`}>상세 보기</Link>
      {policy.questionnaireAvailable && <Link href={`/policies/${policy.policyNumber}#policy-questions`} className="text-link" aria-label={`${policy.title} 질문에 답하기`}>질문에 답하기</Link>}
    </div>
  </article>;
}

export function PolicyArticle({ policy }: { policy: Detail }) {
  const content = policy.content;
  return <article className="policy-article">
    <header className="policy-detail-heading">
      <div className="policy-eyebrow"><span>{content.category || "청년 정책"}</span><span>{content.organization || "온통청년 제공"}</span></div>
      <h1>{content.title}</h1>
      <p className="policy-lead">{content.description}</p>
      <div className="policy-date-panel"><p>신청 기간</p><strong>{content.applicationPeriod}</strong>
        <PolicyRecruitment recruitment={policy.recruitment} />
      </div>
      <nav className="policy-detail-nav" aria-label="정책 상세 바로가기">
        <a href="#policy-support">지원 내용</a>
        <a href="#policy-official">공식 안내</a>
        <a href="#policy-questions">내 조건 확인</a>
      </nav>
    </header>
    {policy.sourceNotices.map((notice) => <aside className="policy-notice" key={`${notice.sourceUrl}:${notice.title}`} aria-label={notice.title}>
      <strong>{notice.title}</strong>
      <p>{notice.description}</p>
      <p><a className="text-link" href={notice.sourceUrl} target="_blank" rel="noopener noreferrer">{notice.sourceLabel} <span aria-hidden="true">↗</span><span className="sr-only"> (새 창)</span></a></p>
    </aside>)}
    <aside className="policy-notice">
      <strong>신청 전 공식 공고를 확인하세요</strong>
      <p>온통청년에서 수집한 내용이에요. 최신 신청 조건과 예외는 공식 모집 공고를 확인해주세요.</p>
    </aside>
    <div id="policy-support" tabIndex={-1} className="policy-sections">
      {content.sections.map((section, index) => <section key={index}>
        <h2>{section.title}</h2><p>{section.text}</p>
      </section>)}
      <section id="policy-official" tabIndex={-1}>
        <h2>공식 안내</h2>
        <div className="policy-official-links">
          <a href={policy.sourceUrl} className="button-primary" target="_blank" rel="noopener noreferrer">온통청년에서 보기 <span aria-hidden="true">↗</span><span className="sr-only"> (새 창)</span></a>
          {content.links.map((link) => <a key={link.url} href={link.url} className="button-secondary" target="_blank" rel="noopener noreferrer">{link.label}<span className="sr-only"> (새 창)</span></a>)}
        </div>
      </section>
    </div>
    <PolicyQuestionnaire key={`${policy.policyNumber}-${policy.revision}`} policyNumber={policy.policyNumber} />
    <SavePolicyButton key={policy.policyNumber} policyNumber={policy.policyNumber} />
    <footer className="policy-source">
      <p>출처: 온통청년</p>
      <p>수집 시각: <time dateTime={policy.collectedAt}>{collectedTime(policy.collectedAt)}</time> (서울)</p>
      {content.sourceModifiedAtText && <p>온통청년 수정일: {content.sourceModifiedAtText}</p>}
      <p>정책 신청은 공식 신청처에서 진행해주세요.</p>
    </footer>
  </article>;
}
