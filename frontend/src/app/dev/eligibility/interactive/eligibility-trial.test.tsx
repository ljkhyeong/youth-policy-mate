import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ELIGIBILITY_EXAMPLES } from "@/app/dev/eligibility/eligibility-preview-data";
import type { operations } from "@/generated/preview-api";
import { EligibilityTrial } from "./eligibility-trial";
import { evaluateTrialAction } from "./actions";
import { evaluateTrial, loadTrialQuestions, type TrialOutcome, type TrialQuestions, type TrialRequest } from "./trial-api";
import { INITIAL_TRIAL_STATE, trialReducer } from "./trial-state";

const request: TrialRequest = {
  questionSet: "INITIAL", employmentQuestionSet: "INITIAL", employmentChoice: "DOES_NOT_APPLY",
  incomeQuestionSet: "INITIAL", incomeChoice: "BETWEEN_20M_30M",
};
// 기존 표시 예시를 사용한다. 비교 규칙은 서버 테스트에서 확인한다.
const example = ELIGIBILITY_EXAMPLES[1];
const response: operations["evaluateDevelopmentEligibilityAnswers"]["responses"][200]["content"]["application/json"] = {
  dataKind: "SYNTHETIC", questionSet: "INITIAL", example: { ...example, recruitment: { status: "OPEN", explanation: "별도 모집 안내" } },
};
const evidence = { sourceReference: "인공 원문", location: "요건", excerpt: "인공 조건 문구" };
const questions: TrialQuestions = {
  dataKind: "SYNTHETIC", questionSets: [{
    id: "INITIAL", label: "처음 질문", policyId: "sample", policyRevision: "revision-1", fixedInputs: "고정 출생·거주 예시",
    employmentDescription: "서버의 취업 정의", employmentRequirement: "서버의 취업 기준일·요건", employmentEvidence: evidence,
    incomeDescription: "서버의 소득 정의·대상·기간·단위", incomeRequirement: "서버의 허용 소득 범위", incomeEvidence: evidence,
  }],
  employmentChoices: [{ value: "UNANSWERED", label: "선택 안 함" }, { value: "UNKNOWN", label: "모름" }],
  incomeChoices: [{ value: "UNANSWERED", label: "선택 안 함" }, { value: "ZERO", label: "0원" }],
};
const success: TrialOutcome = { status: "available", example };

afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); });

describe("인공 답변의 화면 요청 상태", () => {
  it("답변 변경·초기화 시 결과를 지우고 늦은 성공·실패는 반영하지 않는다", () => {
    const pending = trialReducer(INITIAL_TRIAL_STATE, { type: "start", requestId: 1 });
    const completed = trialReducer(pending, { type: "finish", requestId: 1, outcome: success });
    const answers = { employmentChoice: "UNKNOWN", incomeChoice: "ZERO" } as const;
    const changed = trialReducer(completed, { type: "change", answers });
    expect(changed).toEqual({ answers, evaluation: { status: "idle" } });
    expect(trialReducer(completed, { type: "reset" })).toEqual(INITIAL_TRIAL_STATE);
    for (const state of [trialReducer(pending, { type: "change", answers }), trialReducer(pending, { type: "reset" })]) {
      for (const outcome of [success, { status: "unavailable" } as const]) {
        expect(trialReducer(state, { type: "finish", requestId: 1, outcome })).toBe(state);
      }
    }
  });

  it("이전 요청 응답은 무시하고 현재 요청의 실패에서도 선택 답변은 유지한다", () => {
    const answers = { employmentChoice: request.employmentChoice, incomeChoice: request.incomeChoice };
    const changed = trialReducer(INITIAL_TRIAL_STATE, { type: "change", answers });
    const pending = trialReducer(changed, { type: "start", requestId: 2 });
    expect(trialReducer(pending, { type: "finish", requestId: 1, outcome: success })).toBe(pending);
    expect(trialReducer(pending, { type: "finish", requestId: 1, outcome: { status: "unavailable" } })).toBe(pending);
    const failed = trialReducer(pending, { type: "finish", requestId: 2, outcome: { status: "unavailable" } });
    expect(failed).toEqual({ answers, evaluation: { status: "unavailable" } });
    const retry = trialReducer(failed, { type: "start", requestId: 3 });
    expect(trialReducer(retry, { type: "finish", requestId: 3, outcome: success }).evaluation).toEqual(success);
  });
});

describe("개발용 질문 조회와 재판정 전송", () => {
  it("질문과 선택지는 캐시 없이 조회하며 서버 문구를 유지한다", async () => {
    vi.stubEnv("NODE_ENV", "development");
    const fetchMock = vi.fn().mockResolvedValue(Response.json(questions));
    vi.stubGlobal("fetch", fetchMock);
    expect(await loadTrialQuestions()).toEqual(questions);
    expect(fetchMock).toHaveBeenCalledWith("http://127.0.0.1:8081/api/dev/eligibility-trial", {
      cache: "no-store", redirect: "error", signal: expect.any(AbortSignal),
    });
  });

  it("Server Action은 고정 코드 다섯 개만 전송하고 서버의 추가 확인 결과를 보존한다", async () => {
    vi.stubEnv("NODE_ENV", "development");
    const fetchMock = vi.fn().mockResolvedValue(Response.json(response));
    vi.stubGlobal("fetch", fetchMock);
    const extra = { ...request, privateNote: "전송하면 안 되는 별도 필드" };
    const result = await evaluateTrialAction(extra);
    expect(fetchMock).toHaveBeenCalledWith("http://127.0.0.1:8081/api/dev/eligibility-trial", {
      method: "POST", cache: "no-store", redirect: "error", signal: expect.any(AbortSignal),
      headers: { "Content-Type": "application/json", Accept: "application/json" }, body: JSON.stringify(request),
    });
    expect(result).toMatchObject({ status: "available", example: { result: response.example.result } });
    expect(response.example.result.status).toBe("NEEDS_REVIEW");
  });

  it("빈 질문·조회 실패는 질문을 만들어 대신하지 않는다", async () => {
    vi.stubEnv("NODE_ENV", "development");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(Response.json({ ...questions, questionSets: [] }))
      .mockRejectedValueOnce(new Error("연결 실패")));
    expect(await loadTrialQuestions()).toBeNull();
    expect(await loadTrialQuestions()).toBeNull();
  });

  it("HTTP·JSON·연결 오류와 질문 버전이 다른 응답은 판정으로 표시하지 않는다", async () => {
    vi.stubEnv("NODE_ENV", "development");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(new Response("내부 오류", { status: 503 }))
      .mockResolvedValueOnce(new Response("오류", { headers: { "Content-Type": "application/json" } }))
      .mockRejectedValueOnce(new Error("연결 실패"))
      .mockResolvedValueOnce(Response.json({ ...response, questionSet: "REVISED" })));
    for (let i = 0; i < 4; i++) expect(await evaluateTrial(request)).toEqual({ status: "unavailable" });
  });

  it("운영에서는 질문 조회와 Server Action 모두 백엔드를 호출하지 않는다", async () => {
    vi.stubEnv("NODE_ENV", "production");
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    expect(await loadTrialQuestions()).toBeNull();
    expect(await evaluateTrialAction(request)).toEqual({ status: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

it("서버의 정의·요건·선택지만 표시하고 자유 입력과 초기 판정은 제공하지 않는다", () => {
  const html = renderToStaticMarkup(<EligibilityTrial questions={questions} />);
  expect(html).toContain("서버의 취업 정의");
  expect(html).toContain("서버의 취업 기준일·요건");
  expect(html).toContain("서버의 소득 정의·대상·기간·단위");
  expect(html).toContain("서버의 허용 소득 범위");
  expect(html.match(/type="radio"/g)).toHaveLength(4);
  expect(html.match(/checked=""/g)).toHaveLength(2);
  expect(html).not.toMatch(/type="(?:text|date|number)"/);
  expect(html).toContain("현재 답변의 계산 결과가 없습니다");
});
