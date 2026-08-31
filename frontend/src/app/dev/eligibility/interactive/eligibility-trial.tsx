"use client";

import { useReducer, useRef, useState } from "react";
import { EligibilityResult, Evidence } from "@/features/eligibility/eligibility-result";
import { evaluateTrialAction } from "./actions";
import type { TrialQuestions } from "./trial-api";
import { INITIAL_TRIAL_STATE, trialReducer } from "./trial-state";

export function EligibilityTrial({ questions }: { questions: TrialQuestions }) {
  const [index, setIndex] = useState(0);
  const [notice, setNotice] = useState("");
  const question = questions.questionSets[index];
  return <div className="mt-8">
    <div role="group" aria-label="재판정 질문 버전" className="flex flex-wrap gap-x-3 border-b border-stone-300">
      {questions.questionSets.map((item, nextIndex) => <button type="button" key={item.id} className="preview-choice" aria-pressed={index === nextIndex}
        onClick={() => {
          if (index === nextIndex) return;
          setIndex(nextIndex);
          setNotice("질문 개정·정의·기준을 바꾸고 이전 답변과 결과를 지웠습니다. 돌아와도 복원하지 않습니다.");
        }}>{item.label}</button>)}
    </div>
    <p role="status" className="mt-3 min-h-7 text-sm leading-7 text-teal-900">{notice}</p>
    {/* 질문 내용이 바뀌면 진행 중인 요청을 포함한 이전 화면 상태를 재사용하지 않는다. */}
    <TrialForm key={JSON.stringify(question)} question={question} questions={questions} />
  </div>;
}

function TrialForm({ question, questions }: { question: TrialQuestions["questionSets"][number]; questions: TrialQuestions }) {
  const [state, dispatch] = useReducer(trialReducer, INITIAL_TRIAL_STATE);
  const nextRequestId = useRef(0);
  const pending = state.evaluation.status === "pending";

  async function calculate() {
    const requestId = ++nextRequestId.current;
    dispatch({ type: "start", requestId });
    try {
      const outcome = await evaluateTrialAction({
        questionSet: question.id, employmentQuestionSet: question.id, incomeQuestionSet: question.id, ...state.answers,
      });
      dispatch({ type: "finish", requestId, outcome });
    } catch {
      dispatch({ type: "finish", requestId, outcome: { status: "unavailable" } });
    }
  }

  return <>
    <div className="mt-4 border-l-2 border-teal-800 pl-4 text-sm leading-7">
      <p className="font-semibold">{question.policyRevision} · {question.policyId}</p>
      <p className="mt-1 text-stone-600">{question.fixedInputs}</p>
    </div>
    <div className="mt-6 grid items-start gap-6 lg:grid-cols-2">
      <section className="condition-panel min-w-0" aria-labelledby="trial-employment-heading">
        <h2 id="trial-employment-heading" className="text-xl font-bold">취업 사실 · 인공 질문</h2>
        <p className="mt-3 text-sm leading-7">{question.employmentDescription}</p>
        <p className="mt-3 border-l-2 border-stone-300 pl-3 text-sm font-semibold leading-7">{question.employmentRequirement}</p>
        <Evidence evidence={question.employmentEvidence} label="취업 질문" />
        <fieldset className="mt-6">
          <legend className="text-sm font-semibold">취업 예시 답변</legend>
          <div className="mt-3 grid gap-2">
            {questions.employmentChoices.map((choice) => <label key={choice.value} className="flex min-h-12 cursor-pointer items-center gap-3 rounded-sm border border-stone-300 p-3 has-checked:border-teal-800 has-checked:bg-teal-50">
              <input type="radio" name="trial-employment" value={choice.value} checked={state.answers.employmentChoice === choice.value} className="size-4 accent-teal-800"
                onChange={() => dispatch({ type: "change", answers: { ...state.answers, employmentChoice: choice.value } })} />
              <span className="text-sm leading-6">{choice.label}</span>
            </label>)}
          </div>
        </fieldset>
      </section>
      <section className="condition-panel min-w-0" aria-labelledby="trial-income-heading">
        <h2 id="trial-income-heading" className="text-xl font-bold">소득 구간 · 인공 질문</h2>
        <p className="mt-3 text-sm leading-7">{question.incomeDescription}</p>
        <p className="mt-3 border-l-2 border-stone-300 pl-3 text-sm font-semibold leading-7">{question.incomeRequirement}</p>
        <Evidence evidence={question.incomeEvidence} label="소득 질문" />
        <fieldset className="mt-6" aria-describedby="trial-income-help">
          <legend className="text-sm font-semibold">소득 예시 답변</legend>
          <p id="trial-income-help" className="mt-2 text-xs leading-6 text-stone-600">실제 소득 대신 점검할 구간을 고르세요. ‘이하’는 끝 금액을 포함하고 ‘초과’는 포함하지 않습니다.</p>
          <div className="mt-3 grid gap-2">
            {questions.incomeChoices.map((choice) => <label key={choice.value} className="flex min-h-12 cursor-pointer items-center gap-3 rounded-sm border border-stone-300 p-3 has-checked:border-teal-800 has-checked:bg-teal-50">
              <input type="radio" name="trial-income" value={choice.value} checked={state.answers.incomeChoice === choice.value} className="size-4 shrink-0 accent-teal-800"
                onChange={() => dispatch({ type: "change", answers: { ...state.answers, incomeChoice: choice.value } })} />
              <span className="text-sm leading-6 tabular-nums">{choice.label}</span>
            </label>)}
          </div>
        </fieldset>
      </section>
    </div>
    <div className="mt-6 flex flex-wrap items-center gap-5">
      <button type="button" className="button-primary disabled:cursor-wait disabled:opacity-60" disabled={pending} onClick={calculate}>
        {pending ? "서버에서 계산 중…" : "이 답변으로 서버에서 판정하기"}
      </button>
      <button type="button" className="text-link" onClick={() => dispatch({ type: "reset" })}>답변과 결과 지우기</button>
    </div>
    <p className="mt-3 text-xs leading-6 text-stone-600">선택 안 함·모름도 계산할 수 있습니다. 답변을 바꾸면 결과를 지우며, 다시 버튼을 눌러야 새 답변을 보냅니다.</p>
    <div className="mt-6" aria-busy={pending}>
      {state.evaluation.status === "idle" && <p role="status" className="text-sm leading-7 text-stone-600">현재 답변의 계산 결과가 없습니다. 예시를 선택한 뒤 판정 버튼을 눌러주세요.</p>}
      {pending && <p role="status" className="text-sm leading-7 text-teal-900">인공 답변을 서버에서 계산하고 있습니다.</p>}
      {state.evaluation.status === "unavailable" && <p role="alert" className="border-l-2 border-rose-700 pl-4 text-sm leading-7 text-rose-900">계산 결과를 받지 못했습니다. 개발 서버를 확인하고 판정 버튼을 다시 눌러주세요. 선택한 답변은 유지했으며 이전 결과로 대신하지 않습니다.</p>}
      {state.evaluation.status === "available" && <>
        <p role="status" className="mb-4 text-sm font-semibold text-teal-900">현재 인공 답변의 서버 계산 결과입니다.</p>
        <EligibilityResult result={state.evaluation.example.result} recruitment={state.evaluation.example.recruitment} />
      </>}
    </div>
  </>;
}
