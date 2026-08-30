// 개발 화면의 표시용 자료다. 실제 정책 기준, 사용자 소득, 서버 DTO가 아니다.
export type IncomePreviewAnswer = { kind: "range"; label: string } | { kind: "unknown" };

type IncomeChoice = {
  id: string;
  answer: IncomePreviewAnswer;
  description: string;
};

export type IncomePreviewQuestion = {
  policyId: string;
  revision: string;
  conditionId: string;
  label: string;
  subject: string;
  definition: string;
  periodStart: string;
  periodEnd: string;
  calculation: string;
  unit: string;
  choices: readonly IncomeChoice[];
  refinement?: { previousAnswer: string; boundary: string };
};

const INITIAL_CHOICES: readonly IncomeChoice[] = [
  { id: "zero", answer: { kind: "range", label: "0원" }, description: "해당 기간에 이 예시에서 정한 소득이 없는 경우예요." },
  { id: "up-to-20m", answer: { kind: "range", label: "0원 초과 ~ 20,000,000원 이하" }, description: "0원은 제외하고 2,000만 원은 포함해요." },
  { id: "20m-to-30m", answer: { kind: "range", label: "20,000,000원 초과 ~ 30,000,000원 이하" }, description: "2,000만 원은 제외하고 3,000만 원은 포함해요." },
  { id: "over-30m", answer: { kind: "range", label: "30,000,000원 초과" }, description: "3,000만 원을 넘는 구간이에요. 상한은 묻지 않아요." },
  { id: "unknown", answer: { kind: "unknown" }, description: "해당 기간의 소득이나 정의를 더 확인해야 해요." },
];

const BASE_QUESTION = {
  policyId: "preview-only-income",
  revision: "example-1",
  conditionId: "income",
  subject: "본인 소득만",
  definition: "이 예시에서는 해당 기간에 받은 모든 근무처의 급여와 상여금을 세금 공제 전 금액으로 합칩니다. 가족 소득이나 사업소득은 합치지 않습니다.",
  periodStart: "2025-01-01",
  periodEnd: "2025-12-31",
  calculation: "해당 기간의 합계 · 월평균 아님",
  unit: "원 (KRW)",
  choices: INITIAL_CHOICES,
};

export const INCOME_EXAMPLES: readonly IncomePreviewQuestion[] = [
  { ...BASE_QUESTION, label: "처음 질문" },
  { ...BASE_QUESTION, label: "대상 기간 변경 예시", revision: "example-2", periodStart: "2024-01-01", periodEnd: "2024-12-31" },
  {
    ...BASE_QUESTION,
    label: "구간 추가 확인 예시",
    refinement: { previousAnswer: "20,000,000원 초과 ~ 30,000,000원 이하", boundary: "25,000,000원 이하" },
    choices: [
      { id: "20m-to-25m", answer: { kind: "range", label: "20,000,000원 초과 ~ 25,000,000원 이하" }, description: "2,500만 원과 같으면 이 구간이에요." },
      { id: "25m-to-30m", answer: { kind: "range", label: "25,000,000원 초과 ~ 30,000,000원 이하" }, description: "2,500만 원은 제외하고 3,000만 원은 포함해요." },
      { id: "unknown", answer: { kind: "unknown" }, description: "더 좁은 구간은 아직 알 수 없어요." },
    ],
  },
];

export function incomeAnswerLabel(answer: IncomePreviewAnswer) {
  return answer.kind === "unknown" ? "모름" : answer.label;
}
