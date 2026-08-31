import type { ConditionResultView, EligibilityResultView, EligibilityStatusView, EvidenceView, RecruitmentView } from "./eligibility-result-view";

export const ELIGIBILITY_STATUS_LABELS: Record<EligibilityStatusView, string> = {
  ELIGIBLE: "입력 조건 기준 신청 가능",
  INELIGIBLE: "조건 불충족",
  NEEDS_REVIEW: "추가 확인 필요",
};

const STATUS_COLORS: Record<EligibilityStatusView, string> = {
  ELIGIBLE: "border-teal-800 text-teal-950",
  INELIGIBLE: "border-rose-700 text-rose-950",
  NEEDS_REVIEW: "border-amber-700 text-amber-950",
};

const evaluationTime = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul", year: "numeric", month: "long", day: "numeric",
  hour: "2-digit", minute: "2-digit", hourCycle: "h23",
});

export function Evidence({ evidence, label }: { evidence: EvidenceView; label: string }) {
  return (
    <details className="mt-5 border-t border-stone-200 pt-2 text-sm">
      <summary className="min-h-11 cursor-pointer py-3 font-semibold text-teal-900">{label} 근거 보기</summary>
      <dl className="mt-2 space-y-3 border-l-2 border-stone-300 pl-4 text-xs leading-6">
        <div><dt className="text-stone-600">근거 자료 식별자</dt><dd>{evidence.sourceReference}</dd></div>
        <div><dt className="text-stone-600">자료 안의 위치</dt><dd>{evidence.location}</dd></div>
        <div><dt className="text-stone-600">기록된 문구</dt><dd className="whitespace-pre-wrap">{evidence.excerpt ?? "기록된 발췌문 없음 · 자료 위치를 확인해주세요."}</dd></div>
      </dl>
    </details>
  );
}

function ConditionResult({ condition }: { condition: ConditionResultView }) {
  const label = condition.outcome === "MET" ? "충족" : condition.outcome === "NOT_MET" ? "불충족" : "추가 확인 필요";
  const colors = condition.outcome === "MET" ? "bg-teal-50 text-teal-900" : condition.outcome === "NOT_MET" ? "bg-rose-50 text-rose-900" : "bg-amber-50 text-amber-900";

  return (
    <li className="py-7 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-center gap-3">
        <h3 className="text-lg font-bold">{condition.label}</h3>
        <span className={`rounded-sm px-3 py-1 text-xs font-semibold ${colors}`}>{label}</span>
      </div>
      {condition.uncertainty && (
        <p className="mt-3 text-sm font-semibold text-amber-900">
          {condition.uncertainty === "MISSING_USER_INPUT" ? "사용자 정보 추가 확인" : "정책 조건 해석 필요"}
        </p>
      )}
      <p className="mt-3 text-sm leading-7 text-stone-700">{condition.explanation}</p>
      <dl className="mt-5 grid gap-5 text-sm leading-7 sm:grid-cols-2">
        <div className="sm:col-span-2"><dt className="text-xs text-stone-600">적용한 조건</dt><dd className="mt-1 font-semibold">{condition.appliedCondition}</dd></div>
        <div><dt className="text-xs text-stone-600">비교에 사용한 값</dt><dd className="mt-1 tabular-nums">{condition.comparedValue ?? "이 결과에 기록된 비교 값 없음"}</dd></div>
        <div><dt className="text-xs text-stone-600">정책의 적용 기준일</dt><dd className="mt-1 tabular-nums">{condition.referenceDate ? <time dateTime={condition.referenceDate}>{condition.referenceDate}</time> : "이 결과에 기록된 기준일 없음"}</dd></div>
      </dl>
      <Evidence evidence={condition.evidence} label={condition.label} />
    </li>
  );
}

export function EligibilityResult({ result, recruitment }: { result: EligibilityResultView; recruitment: RecruitmentView }) {
  return (
    <section className="condition-panel" aria-label="자격 결과와 근거">
      <div className="grid gap-7 border-b border-stone-200 pb-7 lg:grid-cols-[1.7fr_1fr]">
        <div className={`border-l-2 pl-5 ${STATUS_COLORS[result.status]}`}>
          <p className="text-xs font-semibold">입력 조건에 대한 자격 안내</p>
          <h2 className="mt-2 text-2xl leading-relaxed font-bold tracking-tight">{ELIGIBILITY_STATUS_LABELS[result.status]}</h2>
          <p className="mt-3 text-sm leading-7 text-stone-700">{result.explanation}</p>
        </div>
        <aside className="min-w-0 bg-stone-50 p-5" aria-label="모집 상태">
          <p className="text-xs text-stone-600">모집 상태 · 자격 안내와 별개</p>
          <p className="mt-2 text-lg font-bold">{recruitment.label}</p>
          <p className="mt-2 text-sm leading-7 text-stone-600">{recruitment.explanation}</p>
        </aside>
      </div>
      <p className="mt-5 text-xs leading-6 text-stone-600">공식 자격 인증이나 선정 보장이 아닙니다. 실제 신청 전에는 모집 기간과 기관의 최신 원문을 확인해야 합니다.</p>

      <section className="mt-7 border-y border-stone-200 py-5" aria-label="정책 검토 상태">
        <h2 className="text-sm font-bold">정책 검토 · {result.policyReview.completion === "COMPLETE" ? "완료" : "미완료"}</h2>
        {result.policyReview.completion === "INCOMPLETE" && (
          <>
            <p className="mt-2 text-sm leading-7 text-amber-900">아직 확인하지 못한 조건·예외가 있습니다. 아래 항목만으로 전체 자격을 확정할 수 없습니다.</p>
            <ul className="mt-4 space-y-5">
              {result.policyReview.pendingIssues.map((issue, index) => (
                <li key={`${issue.evidence.sourceReference}:${issue.evidence.location}:${index}`} className="border-l-2 border-amber-700 pl-4">
                  <p className="text-sm leading-7">{issue.explanation}</p>
                  <Evidence evidence={issue.evidence} label={`미확인 사항 ${index + 1}`} />
                </li>
              ))}
            </ul>
          </>
        )}
      </section>

      <h2 className="mt-8 text-xl font-bold">항목별 결과</h2>
      {result.conditions.length > 0 ? (
        <ul className="mt-6 divide-y divide-stone-200">{result.conditions.map((condition) => <ConditionResult key={condition.conditionId} condition={condition} />)}</ul>
      ) : <p className="mt-4 text-sm leading-7 text-stone-600">표시할 항목별 결과가 없습니다. 조건이 없다는 이유로 신청 가능한 것으로 처리하지 않습니다.</p>}

      <footer className="mt-8 border-t border-stone-200 pt-5">
        <h2 className="text-sm font-semibold">이 결과의 기준 정보</h2>
        <dl className="mt-4 grid gap-4 text-xs leading-6 sm:grid-cols-2">
          <div><dt className="text-stone-600">정책 식별자</dt><dd>{result.basis.policyId}</dd></div>
          <div><dt className="text-stone-600">정책 개정</dt><dd>{result.basis.policyRevision}</dd></div>
          <div><dt className="text-stone-600">판정 규칙 버전</dt><dd>{result.basis.ruleVersion}</dd></div>
          <div><dt className="text-stone-600">판정 시각 · 서울 시간</dt><dd><time dateTime={result.basis.evaluatedAt}>{evaluationTime.format(new Date(result.basis.evaluatedAt))}</time></dd></div>
        </dl>
        <p className="mt-4 text-xs leading-6 text-stone-600">판정 시각과 각 조건의 적용 기준일은 서로 다릅니다. 정책 개정이나 입력이 바뀌면 다시 판정해야 합니다.</p>
      </footer>
    </section>
  );
}
