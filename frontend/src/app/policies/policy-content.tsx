import Link from "next/link";
import { openDaysLeft, PolicyRecruitment, RecruitmentBadge, RecruitmentExplanation } from "@/features/policies/policy-recruitment";
import { CategoryChips } from "@/features/policies/policy-category";
import { PolicyPeriodText } from "@/features/policies/policy-period-text";
import type { components } from "@/generated/policy-api";
import { PolicyQuestionnaire } from "@/features/eligibility/policy-questionnaire";
import { SavePolicyButton } from "@/features/member/save-policy-button";

type Summary = components["schemas"]["PolicySummary"];
type Detail = components["schemas"]["PolicyDetailResponse"];

function collectedTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul", year: "numeric", month: "long", day: "numeric", hour: "2-digit", minute: "2-digit",
  }).format(new Date(value));
}

export function PolicyCard({ policy }: { policy: Summary }) {
  return <article className="policy-card">
    <div className="policy-card-top">
      <p className="policy-meta">{policy.organization || "온통청년 제공"} · <CategoryChips category={policy.category} /></p>
      <RecruitmentBadge recruitment={policy.recruitment} />
    </div>
    <h2><Link href={`/policies/${policy.policyNumber}`}>{policy.title}</Link></h2>
    <p className="policy-description">{policy.description || "자세한 지원 내용을 확인해보세요."}</p>
    <p className="policy-period"><strong>신청기간</strong><PolicyPeriodText period={policy.applicationPeriod} recruitment={policy.recruitment} /></p>
    <RecruitmentExplanation recruitment={policy.recruitment} />
    <div className="policy-card-actions">
      <Link href={`/policies/${policy.policyNumber}`} className="text-link" aria-label={`${policy.title} 지원 내용 보기`}>지원 내용 보기</Link>
      {policy.questionnaireAvailable && <Link href={`/policies/${policy.policyNumber}#policy-questions`} className="text-link" aria-label={`${policy.title} 질문에 답하기`}>질문에 답하기</Link>}
    </div>
  </article>;
}

// 여백 메모. 넓은 화면에서는 본문 오른쪽 여백 칸에, 좁은 화면에서는 본문 아래에 놓인다.
function MarginNote({ children }: { children: React.ReactNode }) {
  return <p className="margin-note">{children}</p>;
}

// 참여·지원 제한을 다루는 절에는 확인 메모를 붙인다. 절 제목에서만 판단하고 내용을 지어내지 않는다.
const restrictionTitle = /제한|제외/;

export function PolicyArticle({ policy }: { policy: Detail }) {
  const content = policy.content;
  const daysLeft = openDaysLeft(policy.recruitment);
  return <article className="policy-article">
    <header className="policy-detail-heading">
      <p className="policy-meta">{content.organization || "온통청년 제공"} · <CategoryChips category={content.category} /></p>
      <h1>{content.title}</h1>
      <p className="policy-lead">{content.description}</p>
      <div className="policy-date-panel"><p>신청 기간</p><strong><PolicyPeriodText period={content.applicationPeriod} recruitment={policy.recruitment} /></strong>
        <PolicyRecruitment recruitment={policy.recruitment} />
        {daysLeft !== null && <MarginNote>{daysLeft === 0 ? "오늘 마감이에요" : `${daysLeft}일 남았어요`}</MarginNote>}
      </div>
      <SavePolicyButton key={policy.policyNumber} policyNumber={policy.policyNumber} />
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
      {policy.sourceConditions.length > 0 && <section aria-labelledby="policy-stated-conditions" className="policy-glance">
        <h2 id="policy-stated-conditions">□ 한눈에 · 온통청년 표기 조건</h2>
        <dl className="policy-stated-conditions">
          {policy.sourceConditions.map((condition) => <div key={condition.label}><dt>{condition.label}</dt><dd><mark>{condition.value}</mark></dd></div>)}
        </dl>
        <p className="field-help">온통청년에 등록된 값이라 공고 원문과 다를 수 있고, 여기에 없는 조건이 있을 수 있어요. 기준일과 예외는 공식 안내에서 확인해주세요.</p>
        <MarginNote>판정이 아니라 온통청년에 적힌 값이에요</MarginNote>
      </section>}
      {content.sections.map((section, index) => <section key={index}>
        <h2>{section.title}</h2><p>{section.text}</p>
        {restrictionTitle.test(section.title) && <MarginNote>해당하면 참여가 제한될 수 있어요. 신청 전에 확인하세요</MarginNote>}
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
    <footer className="policy-source">
      <p>출처: 온통청년</p>
      <p>수집 시각: <time dateTime={policy.collectedAt}>{collectedTime(policy.collectedAt)}</time> (서울)</p>
      {content.sourceModifiedAtText && <p>온통청년 수정일: {content.sourceModifiedAtText}</p>}
      <p>정책 신청은 공식 신청처에서 진행해주세요.</p>
    </footer>
  </article>;
}
