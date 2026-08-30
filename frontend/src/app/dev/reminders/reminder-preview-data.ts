import type { DeadlineReminderView } from "@/features/reminders/deadline-reminder-view";

// 서버 계산 결과의 표시를 점검하는 고정 인공 자료다. 실제 정책·API 응답이 아니다.
const dateResult: DeadlineReminderView = {
  outcome: "CANDIDATES_AVAILABLE",
  explanation: "알림 후보 날짜입니다. 발송 시각·저장 상태·수신 조건 확인 전에는 예약이나 발송에 사용할 수 없습니다. 오늘 후보는 발송 시각을 추가 확인해야 합니다.",
  applicationPeriod: { kind: "DATES", startsOnInclusive: "2026-08-20", endsOnInclusive: "2026-09-07" },
  deadlineOnSeoul: "2026-09-07",
  recruitment: {
    label: "접수 기간 · 날짜 기준",
    explanation: "서울 날짜 기준으로 신청기간에 포함됩니다. 정확한 접수 시각은 확인되지 않았으므로 공식 신청처의 운영 시간을 확인해야 합니다.",
  },
  dates: [
    { daysBeforeDeadline: 7, date: "2026-08-31", status: "TODAY_REQUIRES_SEND_TIME_CHECK" },
    { daysBeforeDeadline: 3, date: "2026-09-04", status: "FUTURE_DATE" },
    { daysBeforeDeadline: 1, date: "2026-09-06", status: "FUTURE_DATE" },
  ],
  basis: {
    policyId: "sample-deadline-policy",
    policyRevision: "sample-revision-1",
    evaluatedAt: "2026-08-30T15:30:00Z",
    evaluatedOnSeoul: "2026-08-31",
    sourceReference: "sample-period-source · 실제 정책 원문 아님",
    sourceLocation: "인공 자료 · 신청기간 항목",
    sourceExcerpt: "인공 문구: 신청기간은 2026년 8월 20일부터 9월 7일까지입니다. 접수 시각은 기재하지 않았습니다.",
  },
};

export const REMINDER_EXAMPLES: readonly {
  id: string;
  label: string;
  description: string;
  result: DeadlineReminderView;
}[] = [
  {
    id: "dates", label: "날짜형 · 오늘 후보",
    description: "예시의 오늘은 2026-08-31입니다. 날짜만 확인된 마감과 오늘·미래 후보를 구분합니다.",
    result: dateResult,
  },
  {
    id: "times", label: "시각형 · 시간대",
    description: "원문 마감은 UTC 9월 6일 18:00, 같은 순간의 서울 날짜는 9월 7일입니다. 알림 후보는 서울 날짜 기준입니다.",
    result: {
      ...dateResult,
      applicationPeriod: {
        kind: "TIMES", opensAtInclusive: "2026-08-20T00:00:00Z", openingTimeZone: "UTC",
        closesAtExclusive: "2026-09-06T18:00:00Z", closingTimeZone: "UTC",
      },
      recruitment: { label: "접수 중 · 시각 기준", explanation: "확인한 접수 시작 시각 이후이고 마감 시각 전입니다. 실제 접수 상태는 공식 신청처에서 확인해야 합니다." },
      basis: { ...dateResult.basis, policyRevision: "sample-revision-2", sourceExcerpt: "인공 문구: 2026-08-20 00:00 UTC 접수 시작, 2026-09-06 18:00 UTC 접수 마감." },
    },
  },
  {
    id: "unresolved", label: "마감일 미확인",
    description: "본문과 신청기간 항목의 마감일이 충돌합니다. 한쪽 날짜를 골라 후보를 만들지 않습니다.",
    result: {
      ...dateResult, outcome: "NO_CONFIRMED_DEADLINE", deadlineOnSeoul: null, dates: [],
      explanation: "확인된 신청 마감 날짜가 없어 후보를 만들지 않았습니다. 원문의 날짜 충돌을 먼저 확인해야 합니다.",
      applicationPeriod: { kind: "UNRESOLVED", reason: "신청기간 항목은 9월 7일, 본문은 9월 10일로 서로 다릅니다." },
      recruitment: { label: "신청기간 확인 필요", explanation: "충돌한 마감 날짜를 아직 확인하지 못했습니다. 현재 모집 상태도 확정하지 않습니다." },
      basis: { ...dateResult.basis, policyRevision: "sample-revision-3", sourceExcerpt: "인공 문구: 신청기간 항목 ‘9월 7일 마감’, 본문 ‘9월 10일 마감’." },
    },
  },
  {
    id: "rolling", label: "상시 모집",
    description: "상시라는 안내만으로 날짜를 정하지 않습니다. 지금 접수할 수 있는지는 별도 확인 사항입니다.",
    result: {
      ...dateResult, outcome: "NO_CONFIRMED_DEADLINE", deadlineOnSeoul: null, dates: [],
      explanation: "상시 모집으로 확인되어 고정된 마감 날짜와 알림 후보가 없습니다.",
      applicationPeriod: { kind: "ROLLING" },
      recruitment: { label: "상시 모집", explanation: "원문에서 상시 모집으로 확인했습니다. 현재 접수 여부는 공식 신청처에서 확인해야 합니다." },
      basis: { ...dateResult.basis, policyRevision: "sample-revision-4", sourceExcerpt: "인공 문구: 상시 모집." },
    },
  },
  {
    id: "exhausted", label: "소진 시 종료",
    description: "예산·인원 소진 날짜와 현재 소진 여부를 알 수 없습니다. 남은 예산이나 마감 날짜를 추정하지 않습니다.",
    result: {
      ...dateResult, outcome: "NO_CONFIRMED_DEADLINE", deadlineOnSeoul: null, dates: [],
      explanation: "예산·인원 소진 시 종료되므로 확인된 마감 날짜와 알림 후보가 없습니다.",
      applicationPeriod: { kind: "UNTIL_EXHAUSTED" },
      recruitment: { label: "예산·인원 소진 시 종료", explanation: "예산·인원 소진 시 끝나는 모집입니다. 소진 여부와 현재 접수 여부를 공식 신청처에서 확인해야 합니다." },
      basis: { ...dateResult.basis, policyRevision: "sample-revision-5", sourceExcerpt: "인공 문구: 예산 소진 시 종료." },
    },
  },
  {
    id: "closed", label: "모집 마감",
    description: "예시 기준일에 이미 모집이 끝났습니다. 확인된 원래 기간은 보존하되 후보는 표시하지 않습니다.",
    result: {
      ...dateResult, outcome: "RECRUITMENT_CLOSED", deadlineOnSeoul: "2026-08-30", dates: [],
      explanation: "모집이 마감되어 알림 후보를 만들지 않았습니다.",
      applicationPeriod: { kind: "DATES", startsOnInclusive: "2026-08-20", endsOnInclusive: "2026-08-30" },
      recruitment: { label: "모집 마감", explanation: "서울 날짜 기준으로 확인한 신청 종료일이 지났습니다." },
      basis: { ...dateResult.basis, policyRevision: "sample-revision-6", sourceExcerpt: "인공 문구: 신청기간은 2026년 8월 20일부터 8월 30일까지입니다." },
    },
  },
  {
    id: "no-remaining", label: "후보 날짜 모두 지남",
    description: "모집은 아직 마감되지 않았지만 D-7·D-3·D-1 날짜는 모두 지났습니다. 지난 후보를 오늘로 옮기지 않습니다.",
    result: {
      ...dateResult, outcome: "NO_REMAINING_DATES", deadlineOnSeoul: "2026-08-31", dates: [],
      explanation: "D-7·D-3·D-1 날짜가 모두 지났습니다. 지난 날짜의 알림을 오늘로 옮기지 않습니다.",
      applicationPeriod: { kind: "DATES", startsOnInclusive: "2026-08-20", endsOnInclusive: "2026-08-31" },
      basis: { ...dateResult.basis, policyRevision: "sample-revision-7", sourceExcerpt: "인공 문구: 신청기간은 2026년 8월 20일부터 8월 31일까지입니다. 정확한 마감 시각은 기재하지 않았습니다." },
    },
  },
];
