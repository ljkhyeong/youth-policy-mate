import type { TrialOutcome, TrialRequest } from "./trial-api";

export type TrialAnswers = Pick<TrialRequest, "employmentChoice" | "incomeChoice">;
type Evaluation = { status: "idle" } | { status: "pending"; requestId: number } | TrialOutcome;
export type TrialState = { answers: TrialAnswers; evaluation: Evaluation };
export const INITIAL_TRIAL_STATE: TrialState = {
  answers: { employmentChoice: "UNANSWERED", incomeChoice: "UNANSWERED" }, evaluation: { status: "idle" },
};
type TrialAction =
  | { type: "change"; answers: TrialAnswers }
  | { type: "reset" }
  | { type: "start"; requestId: number }
  | { type: "finish"; requestId: number; outcome: TrialOutcome };

// 자격 규칙이 아니라 화면의 입력·요청 상태만 관리한다.
export function trialReducer(state: TrialState, action: TrialAction): TrialState {
  switch (action.type) {
    case "change": return { answers: action.answers, evaluation: { status: "idle" } };
    case "reset": return INITIAL_TRIAL_STATE;
    case "start": return { ...state, evaluation: { status: "pending", requestId: action.requestId } };
    case "finish":
      if (state.evaluation.status !== "pending" || state.evaluation.requestId !== action.requestId) return state;
      return { ...state, evaluation: action.outcome };
  }
}
