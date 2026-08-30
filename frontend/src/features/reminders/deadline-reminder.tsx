import type { ApplicationPeriodView, DeadlineReminderView, ReminderOutcomeView } from "./deadline-reminder-view";

export const REMINDER_OUTCOME_LABELS: Record<ReminderOutcomeView, string> = {
  CANDIDATES_AVAILABLE: "확인할 알림 후보가 있습니다",
  NO_CONFIRMED_DEADLINE: "확인된 마감 날짜가 없습니다",
  RECRUITMENT_CLOSED: "모집이 마감되어 후보가 없습니다",
  NO_REMAINING_DATES: "남은 알림 후보 날짜가 없습니다",
};

function ExactTime({ value, timeZone }: { value: string; timeZone: string }) {
  const display = new Intl.DateTimeFormat("ko-KR", {
    timeZone, year: "numeric", month: "long", day: "numeric",
    hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23",
  }).format(new Date(value));

  return <><time dateTime={value}>{display}</time> <span className="text-xs text-stone-600">({timeZone})</span></>;
}

function ApplicationPeriod({ period }: { period: ApplicationPeriodView }) {
  switch (period.kind) {
    case "DATES":
      return <>
        <p className="text-xs font-semibold text-stone-600">날짜형 신청기간 · 시작일과 종료일 포함</p>
        <p className="mt-2 font-semibold tabular-nums"><time dateTime={period.startsOnInclusive}>{period.startsOnInclusive}</time> ~ <time dateTime={period.endsOnInclusive}>{period.endsOnInclusive}</time></p>
        <p className="mt-2 text-xs leading-6 text-stone-600">정확한 접수 시각은 확인되지 않았습니다. 종료일을 자정이나 23:59 마감으로 바꾸지 않습니다.</p>
      </>;
    case "TIMES":
      return <>
        <p className="text-xs font-semibold text-stone-600">시각형 신청기간 · 원문 시간대</p>
        <dl className="mt-3 space-y-3 text-sm leading-7">
          <div><dt className="text-xs text-stone-600">접수 시작 · 해당 시각 포함</dt><dd><ExactTime value={period.opensAtInclusive} timeZone={period.openingTimeZone} /></dd></div>
          <div><dt className="text-xs text-stone-600">접수 마감 · 해당 시각부터 종료</dt><dd><ExactTime value={period.closesAtExclusive} timeZone={period.closingTimeZone} /></dd></div>
          <div><dt className="text-xs text-stone-600">같은 마감 순간 · 서울 시간</dt><dd className="font-semibold"><ExactTime value={period.closesAtExclusive} timeZone="Asia/Seoul" /></dd></div>
        </dl>
      </>;
    case "ROLLING":
      return <p className="text-sm leading-7">상시 모집입니다. 고정된 마감 날짜를 만들지 않습니다.</p>;
    case "UNTIL_EXHAUSTED":
      return <p className="text-sm leading-7">예산·인원 소진 시 종료됩니다. 소진 날짜를 임의로 정하지 않습니다.</p>;
    case "CLOSED":
      return <p className="text-sm leading-7">원문에서 모집 마감을 확인했지만 정확한 마감 날짜·시각은 기록되지 않았습니다.</p>;
    case "UNRESOLVED":
      return <><p className="text-sm font-semibold text-amber-900">신청기간 확인 필요</p><p className="mt-2 text-sm leading-7">{period.reason}</p></>;
  }
}

export function DeadlineReminder({ result }: { result: DeadlineReminderView }) {
  return (
    <section className="condition-panel" aria-label="마감과 알림 후보">
      <div className="grid gap-6 border-b border-stone-200 pb-6 lg:grid-cols-[1.7fr_1fr]">
        <div>
          <p className="text-xs font-semibold text-teal-800">마감 알림 후보 · 예약 아님</p>
          <h2 className="mt-2 text-2xl leading-relaxed font-bold tracking-tight">{REMINDER_OUTCOME_LABELS[result.outcome]}</h2>
          <p className="mt-3 text-sm leading-7 text-stone-700">{result.explanation}</p>
        </div>
        <aside className="min-w-0 bg-stone-50 p-5" aria-label="계산 기준">
          <p className="text-xs text-stone-600">계산 기준일 · 서울 날짜</p>
          <p className="mt-2 font-mono text-xl font-semibold"><time dateTime={result.basis.evaluatedOnSeoul}>{result.basis.evaluatedOnSeoul}</time></p>
          <p className="mt-2 text-xs leading-6 text-stone-600">아래의 ‘오늘’은 이 날짜를 뜻합니다. 현재 시각에 맞춰 자동 갱신하지 않습니다.</p>
        </aside>
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <section aria-label="신청기간과 마감">
          <h3 className="mb-4 text-sm font-bold">신청기간과 마감</h3>
          <ApplicationPeriod period={result.applicationPeriod} />
          <dl className="mt-5 border-l-2 border-teal-800 pl-4">
            <dt className="text-xs text-stone-600">알림 날짜 계산에 사용한 마감일 · 서울 날짜</dt>
            <dd className="mt-2 font-mono text-xl font-semibold">
              {result.deadlineOnSeoul ? <time dateTime={result.deadlineOnSeoul}>{result.deadlineOnSeoul}</time> : <span className="font-sans text-base">확인된 날짜 없음</span>}
            </dd>
          </dl>
        </section>
        <aside className="min-w-0 border-l border-stone-200 pl-5" aria-label="모집 상태">
          <h3 className="text-xs text-stone-600">계산 시점의 모집 상태 · 개인 자격과 별개</h3>
          <p className="mt-2 text-lg font-bold">{result.recruitment.label}</p>
          <p className="mt-2 text-sm leading-7 text-stone-600">{result.recruitment.explanation}</p>
        </aside>
      </div>

      <h3 className="mt-8 text-lg font-bold">알림 후보 날짜</h3>
      <p className="mt-2 text-xs leading-6 text-stone-600">D-7·D-3·D-1은 서울 마감일의 7일·3일·1일 전입니다. 아래 날짜는 발송 시각이 아닙니다.</p>
      {result.dates.length > 0 ? (
        <ol aria-label="알림 후보 날짜" className="mt-5 divide-y divide-stone-200 border-y border-stone-200">
          {result.dates.map((candidate) => (
            <li key={candidate.daysBeforeDeadline} className="grid grid-cols-[3.5rem_1fr] gap-4 py-5 sm:grid-cols-[4.5rem_1fr]">
              <span className="font-mono text-xl font-semibold text-teal-900">D-{candidate.daysBeforeDeadline}</span>
              <div className="min-w-0">
                <time dateTime={candidate.date} className="font-mono text-xl font-semibold">{candidate.date}</time>
                {candidate.status === "TODAY_REQUIRES_SEND_TIME_CHECK" ? (
                  <><p className="mt-2 text-sm font-semibold text-amber-900">오늘 후보 · 발송 시각 확인 필요</p><p className="mt-1 text-xs leading-6 text-stone-600">즉시 발송 대상이나 이미 놓친 알림으로 판단하지 않습니다.</p></>
                ) : <p className="mt-2 text-sm text-stone-600">미래 후보 · 예약 미확정</p>}
              </div>
            </li>
          ))}
        </ol>
      ) : <p className="mt-5 border-l-2 border-stone-300 bg-stone-50 p-5 text-sm leading-7">표시할 후보 날짜가 없습니다. 위 사유를 확인해주세요. 후보가 없다는 것만으로 기존 예약이 취소된 것은 아닙니다.</p>}
      <p className="mt-5 text-sm leading-7 text-stone-700">예약·발송 전에는 발송 시각, 정책 저장 상태와 채널별 수신 조건을 확인해야 합니다. 이메일은 별도 활성화가 필요합니다.</p>

      <details className="mt-7 border-t border-stone-200 pt-2">
        <summary className="min-h-11 cursor-pointer py-3 text-sm font-semibold text-teal-900">신청기간 근거와 계산 정보 보기</summary>
        <dl className="mt-3 grid gap-5 text-xs leading-6 sm:grid-cols-2">
          <div><dt className="text-stone-600">정책 식별자</dt><dd>{result.basis.policyId}</dd></div>
          <div><dt className="text-stone-600">정책 개정</dt><dd>{result.basis.policyRevision}</dd></div>
          <div><dt className="text-stone-600">근거 자료 식별자</dt><dd>{result.basis.sourceReference}</dd></div>
          <div><dt className="text-stone-600">자료 안의 위치</dt><dd>{result.basis.sourceLocation}</dd></div>
          <div className="sm:col-span-2"><dt className="text-stone-600">기록된 문구</dt><dd className="whitespace-pre-wrap">{result.basis.sourceExcerpt ?? "기록된 발췌문 없음 · 자료 위치를 확인해주세요."}</dd></div>
          <div className="sm:col-span-2"><dt className="text-stone-600">계산 시각 · 수집 시각과 별개</dt><dd><ExactTime value={result.basis.evaluatedAt} timeZone="Asia/Seoul" /></dd></div>
        </dl>
        <p className="mt-4 text-xs leading-6 text-stone-600">전달받은 기간·모집 상태·후보 날짜를 표시합니다. 정책 개정이나 마감이 바뀌면 새 결과가 필요하며 이 화면에서 다시 계산하지 않습니다.</p>
      </details>
    </section>
  );
}
