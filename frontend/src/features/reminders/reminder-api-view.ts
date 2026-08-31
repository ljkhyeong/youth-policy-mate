import type { components, operations } from "@/generated/preview-api";
import type { ApplicationPeriodView, DeadlineReminderView, ReminderExampleView } from "./deadline-reminder-view";

export type ReminderExamplesResponse = operations["listDevelopmentReminderExamples"]["responses"][200]["content"]["application/json"];
type PeriodResponse = components["schemas"]["ApplicationPeriodResponse"];
type RecruitmentStatus = components["schemas"]["ReminderRecruitment"]["status"];

const RECRUITMENT_LABELS: Record<RecruitmentStatus, string> = {
  BEFORE_OPENING: "모집 전",
  OPEN: "접수 기간",
  CLOSED: "모집 마감",
  ROLLING: "상시 모집",
  UNTIL_EXHAUSTED: "예산·인원 소진 시 종료",
  UNKNOWN: "신청기간 확인 필요",
};

function requiredValue(value: string | null): string {
  if (typeof value !== "string" || value.length === 0) throw new Error("표시에 필요한 기간 정보가 없습니다.");
  return value;
}

function toPeriodView(period: PeriodResponse): ApplicationPeriodView {
  switch (period.kind) {
    case "DATES": return {
      kind: period.kind, startsOnInclusive: requiredValue(period.startsOnInclusive), endsOnInclusive: requiredValue(period.endsOnInclusive),
    };
    case "TIMES": return {
      kind: period.kind,
      opensAtInclusive: requiredValue(period.opensAtInclusive), openingTimeZone: requiredValue(period.openingTimeZone),
      closesAtExclusive: requiredValue(period.closesAtExclusive), closingTimeZone: requiredValue(period.closingTimeZone),
    };
    case "UNRESOLVED": return { kind: period.kind, reason: requiredValue(period.reason) };
    case "ROLLING": case "UNTIL_EXHAUSTED": case "CLOSED": return { kind: period.kind };
    default: throw new Error("지원하지 않는 신청기간 형식입니다.");
  }
}

export function toReminderExamplesView(response: ReminderExamplesResponse): readonly ReminderExampleView[] {
  if (response.dataKind !== "SYNTHETIC" || !Array.isArray(response.examples) || response.examples.length === 0) {
    throw new Error("개발용 인공 자료 응답을 확인할 수 없습니다.");
  }
  return response.examples.map((example: components["schemas"]["ReminderExample"]) => {
    const result = example.result;
    const dates: DeadlineReminderView["dates"] = result.dates.map((candidate) => {
      const days = candidate.daysBeforeDeadline;
      if (days !== 7 && days !== 3 && days !== 1) throw new Error("지원하지 않는 알림 후보 간격입니다.");
      return { ...candidate, daysBeforeDeadline: days };
    });
    return {
      id: example.id, label: example.label, description: example.description,
      result: {
        outcome: result.outcome, explanation: result.explanation, applicationPeriod: toPeriodView(result.applicationPeriod),
        deadlineOnSeoul: result.deadlineOnSeoul, dates, basis: result.basis,
        recruitment: { label: RECRUITMENT_LABELS[result.recruitment.status], explanation: result.recruitment.explanation },
      },
    };
  });
}
