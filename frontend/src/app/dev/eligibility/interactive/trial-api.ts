import type { operations } from "@/generated/preview-api";
import type { EligibilityExampleView } from "@/features/eligibility/eligibility-result-view";
import { toEligibilityExampleView } from "@/features/eligibility/eligibility-api-view";

export type TrialQuestions = operations["getDevelopmentEligibilityQuestions"]["responses"][200]["content"]["application/json"];
export type TrialRequest = operations["evaluateDevelopmentEligibilityAnswers"]["requestBody"]["content"]["application/json"];
type TrialResponse = operations["evaluateDevelopmentEligibilityAnswers"]["responses"][200]["content"]["application/json"];
export type TrialOutcome = { status: "available"; example: EligibilityExampleView } | { status: "unavailable" };

const API_URL = "http://127.0.0.1:8081/api/dev/eligibility-trial";

// 서버 컴포넌트·Server Action에서만 사용한다. 쿠키·회원 정보·자유 입력 필드를 전달하지 않는다.
export async function loadTrialQuestions(): Promise<TrialQuestions | null> {
  if (process.env.NODE_ENV !== "development") return null;
  try {
    const response = await fetch(API_URL, { cache: "no-store", redirect: "error", signal: AbortSignal.timeout(5000) });
    if (!response.ok || !response.headers.get("content-type")?.includes("application/json")) return null;
    const body = await response.json() as TrialQuestions;
    if (body.dataKind !== "SYNTHETIC" || !body.questionSets?.length || !body.employmentChoices?.length || !body.incomeChoices?.length) return null;
    return body;
  } catch { return null; }
}

export async function evaluateTrial(request: TrialRequest): Promise<TrialOutcome> {
  if (process.env.NODE_ENV !== "development") return { status: "unavailable" };
  try {
    // 호출 인자에 다른 필드가 있어도 계약에 있는 예시 코드만 중계한다.
    const body: TrialRequest = {
      questionSet: request.questionSet, employmentQuestionSet: request.employmentQuestionSet,
      employmentChoice: request.employmentChoice, incomeQuestionSet: request.incomeQuestionSet, incomeChoice: request.incomeChoice,
    };
    const response = await fetch(API_URL, {
      method: "POST", cache: "no-store", redirect: "error", signal: AbortSignal.timeout(5000),
      headers: { "Content-Type": "application/json", Accept: "application/json" }, body: JSON.stringify(body),
    });
    if (!response.ok || !response.headers.get("content-type")?.includes("application/json")) return { status: "unavailable" };
    const result = await response.json() as TrialResponse;
    if (result.dataKind !== "SYNTHETIC" || result.questionSet !== request.questionSet) return { status: "unavailable" };
    return { status: "available", example: toEligibilityExampleView(result.example) };
  } catch { return { status: "unavailable" }; }
}
