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
    <div className="policy-eyebrow"><span>{policy.category || "청년 정책"}</span><span>{policy.organization || "온통청년 제공"}</span></div>
    <h2><Link href={`/policies/${policy.policyNumber}`}>{policy.title}</Link></h2>
    {policy.questionnaireAvailable && <span className="policy-question-badge">조건 확인 질문 있음</span>}
    <p className="policy-description">{policy.description || "자세한 지원 내용을 확인해보세요."}</p>
    <p className="policy-period"><strong>신청기간</strong><span>{policy.applicationPeriod}</span></p>
    <PolicyRecruitment recruitment={policy.recruitment} />
    <div className="policy-card-actions">
      <Link href={`/policies/${policy.policyNumber}`} className="text-link" aria-label={`${policy.title} 상세 보기`}>상세 보기 <span aria-hidden="true">↗</span></Link>
      {policy.questionnaireAvailable && <Link href={`/policies/${policy.policyNumber}#policy-questions`} className="text-link" aria-label={`${policy.title} 질문에 답하기`}>질문에 답하기 <span aria-hidden="true">→</span></Link>}
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
    </header>
    {policy.sourceNotices.map((notice) => <aside className="policy-notice" key={notice.sourceUrl} aria-label={notice.title}>
      <strong>{notice.title}</strong>
      <p>{notice.description}</p>
      <p><a className="text-link" href={notice.sourceUrl} target="_blank" rel="noopener noreferrer">{notice.sourceLabel} <span aria-hidden="true">↗</span><span className="sr-only"> (새 창)</span></a></p>
    </aside>)}
    <SavePolicyButton key={policy.policyNumber} policyNumber={policy.policyNumber} />
    <PolicyQuestionnaire key={`${policy.policyNumber}-${policy.revision}`} policyNumber={policy.policyNumber} />
    <aside className="policy-notice">
      <strong>신청 자격은 추가 확인이 필요해요</strong>
      <p>아래는 온통청년에서 수집한 안내예요. 신청 조건과 예외는 해당 모집 공고에서 확인해주세요.</p>
    </aside>
    <div className="policy-sections">
      {content.sections.map((section, index) => <section key={index}>
        <h2>{section.title}</h2><p>{section.text}</p>
      </section>)}
      <section>
        <h2>공식 안내 확인하기</h2>
        <div className="policy-official-links">
          <a href={policy.sourceUrl} className="button-primary" target="_blank" rel="noopener noreferrer">온통청년에서 보기 <span aria-hidden="true">↗</span><span className="sr-only"> (새 창)</span></a>
          {content.links.map((link) => <a key={link.url} href={link.url} className="button-secondary" target="_blank" rel="noopener noreferrer">{link.label}<span className="sr-only"> (새 창)</span></a>)}
        </div>
      </section>
    </div>
    <footer className="policy-source">
      <p>출처: 온통청년</p>
      <p>수집 시각: <time dateTime={policy.collectedAt}>{collectedTime(policy.collectedAt)}</time> (서울)</p>
      {content.sourceModifiedAtText && <p>온통청년 수정일: {content.sourceModifiedAtText}</p>}
      <p>수집 이후 내용이 달라질 수 있어요. 실제 신청은 공식 신청처에서 진행해주세요.</p>
    </footer>
  </article>;
}
