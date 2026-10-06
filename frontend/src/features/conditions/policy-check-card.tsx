import Link from "next/link";
import type { PolicyChecks } from "@/features/member/member-api";
import { PolicyPeriodText } from "@/features/policies/policy-period-text";
import { RecruitmentBadge, RecruitmentExplanation } from "@/features/policies/policy-recruitment";

const outcomeLabels = { MET: "충족", NOT_MET: "불충족", UNKNOWN: "추가 확인 필요" };

// 생년월일 없이 둘러볼 때 서버 설명은 모든 정책에 같은 안내라서 카드마다 반복하지 않는다.
export function PolicyCheckCard({ policy, showExplanation }: { policy: PolicyChecks["items"][number]; showExplanation: boolean }) {
  const compared = policy.checks.some(check => check.outcome !== "UNKNOWN");
  return <article className="policy-card check-card">
    <div className="policy-card-top">
      <span className="comparison-chip" data-compared={compared || undefined}>{compared ? "연령 비교됨 · 다른 조건 확인 필요" : "조건 확인 전"}</span>
      <RecruitmentBadge recruitment={policy.recruitment} />
    </div>
    <h3><Link href={`/policies/${policy.policyNumber}`}>{policy.title}</Link></h3>
    {showExplanation && <p className="check-explanation">{policy.explanation}</p>}
    <p className="policy-period"><strong>신청기간</strong><PolicyPeriodText period={policy.applicationPeriod} recruitment={policy.recruitment} /></p>
    <RecruitmentExplanation recruitment={policy.recruitment} />
    {policy.questionnaireAvailable && <div className="policy-question-next">
      <span className="policy-question-badge">신청 조건 질문</span>
      <p>질문으로 다른 신청 조건도 비교해보세요.</p>
      <Link href={`/policies/${policy.policyNumber}#policy-questions`} className="text-link" aria-label={`${policy.title} 질문에 답하기`}>질문에 답하기 →</Link>
    </div>}
    <details className="check-details"><summary>조건별 결과와 근거 보기</summary><div className="policy-check-details">
      {policy.checks.map(check => <section key={check.label}><h4>{check.label} · {outcomeLabels[check.outcome]}</h4><p className="field-help">입력: {check.providedValue}</p><p>{check.explanation}</p><blockquote>{check.evidence}</blockquote></section>)}
      <a href={policy.sourceUrl} target="_blank" rel="noopener noreferrer">공식 안내 보기 (새 창)</a>
    </div></details>
    <div className="policy-card-actions">
      <Link href={`/policies/${policy.policyNumber}`} className="text-link" aria-label={`${policy.title} 지원 내용 보기`}>지원 내용 보기</Link>
    </div>
  </article>;
}
