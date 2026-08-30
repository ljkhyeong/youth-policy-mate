// 화면 표시용 모델이다. 확정 API DTO나 날짜 계산 규칙으로 사용하지 않는다.
export type ApplicationPeriodView =
  | { kind: "DATES"; startsOnInclusive: string; endsOnInclusive: string }
  | {
    kind: "TIMES";
    opensAtInclusive: string;
    openingTimeZone: string;
    closesAtExclusive: string;
    closingTimeZone: string;
  }
  | { kind: "ROLLING" | "UNTIL_EXHAUSTED" | "CLOSED" }
  | { kind: "UNRESOLVED"; reason: string };

export type ReminderOutcomeView =
  | "CANDIDATES_AVAILABLE"
  | "NO_CONFIRMED_DEADLINE"
  | "RECRUITMENT_CLOSED"
  | "NO_REMAINING_DATES";

export type DeadlineReminderView = {
  outcome: ReminderOutcomeView;
  explanation: string;
  applicationPeriod: ApplicationPeriodView;
  deadlineOnSeoul: string | null;
  recruitment: { label: string; explanation: string };
  dates: readonly {
    daysBeforeDeadline: 7 | 3 | 1;
    date: string;
    status: "FUTURE_DATE" | "TODAY_REQUIRES_SEND_TIME_CHECK";
  }[];
  basis: {
    policyId: string;
    policyRevision: string;
    evaluatedAt: string;
    evaluatedOnSeoul: string;
    sourceReference: string;
    sourceLocation: string;
    sourceExcerpt: string | null;
  };
};
