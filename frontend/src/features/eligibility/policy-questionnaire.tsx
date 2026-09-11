"use client";

import { useEffect, useRef, useState } from "react";
import { readConfirmedBirth, clearConfirmedBirth } from "@/features/conditions/confirmed-birth";
import type { components } from "@/generated/policy-api";
import { memberApi, MemberApiError } from "@/features/member/member-api";

type Questionnaire = components["schemas"]["PolicyQuestionnaire"];
type Evaluation = components["schemas"]["PolicyEvaluation"];
type EvaluationRequest = components["schemas"]["PolicyEvaluationRequest"];

export function PolicyQuestionnaire({ policyNumber }: { policyNumber: string }) {
  const sectionRef = useRef<HTMLElement>(null);
  useEffect(() => {
    if (window.location.hash === "#policy-questions") sectionRef.current?.focus({ preventScroll: true });
  }, [policyNumber]);
  const [questions, setQuestions] = useState<Questionnaire | null>(null);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [changed, setChanged] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    memberApi<Questionnaire>(`policy-questions/${policyNumber}`, { signal: controller.signal }).then(value => {
      if (!controller.signal.aborted) setQuestions(value);
    }).catch(() => { if (!controller.signal.aborted) setError("추가 질문을 불러오지 못했어요. 다시 시도해주세요."); });
    return () => controller.abort();
  }, [policyNumber, attempt]);
  function reload(policyChanged = false) {
    setQuestions(null); setError(""); setChanged(policyChanged); setAttempt(value => value + 1);
  }
  return <section ref={sectionRef} id="policy-questions" className="policy-questionnaire" aria-labelledby="policy-question-heading" tabIndex={-1}>
    <h2 id="policy-question-heading">신청 조건 확인</h2>
    {changed && <p role="status" className="question-feedback">정책이나 질문이 바뀌어 이전 답변을 지웠어요. 새 질문에 답해주세요.</p>}
    {!questions && !error && <p role="status">질문을 불러오고 있어요.</p>}
    {error && <div role="alert"><p>{error}</p><button type="button" className="button-secondary" onClick={() => reload()}>질문 다시 불러오기</button></div>}
    {questions && (questions.available
      ? <QuestionForm key={`${attempt}-${questions.revision}-${questions.ruleVersion}`} questions={questions} onChanged={() => reload(true)} />
      : <p>{questions.reason}</p>)}
  </section>;
}

function QuestionForm({ questions, onChanged }: { questions: Questionnaire; onChanged: () => void }) {
  const [prefillNotice, setPrefillNotice] = useState("");
  const [started, setStarted] = useState(false);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [result, setResult] = useState<Evaluation | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const request = useRef<AbortController | null>(null);
  const resultHeading = useRef<HTMLHeadingElement>(null);
  useEffect(() => () => request.current?.abort(), []);
  useEffect(() => { if (result) resultHeading.current?.focus(); }, [result]);
  function change(id: string, value: string) {
    request.current?.abort(); request.current = null;
    setBusy(false); setError(""); setResult(null);
    setAnswers(previous => ({ ...previous, [id]: value }));
  }
  async function start() {
    setStarted(true);
    const birthDate = readConfirmedBirth();
    if (!birthDate) return;
    const controller = new AbortController(); request.current = controller;
    const body: components["schemas"]["PolicyPrefillRequest"] = {
      revision: questions.revision, ruleVersion: questions.ruleVersion, birthDate,
    };
    try {
      const response = await memberApi<components["schemas"]["PolicyPrefill"]>(`policy-prefill/${questions.policyNumber}`,
        { method: "POST", body, signal: controller.signal });
      if (controller.signal.aborted || readConfirmedBirth() !== birthDate) return;
      if (response.answers.length) {
        setAnswers(Object.fromEntries(response.answers.map(answer => [answer.questionId, answer.value])));
        setPrefillNotice("내 조건의 생년월일로 출생일 답변을 채웠어요. 변경할 수 있어요.");
      }
    } catch (failure) {
      if (controller.signal.aborted) return;
      if (failure instanceof MemberApiError && failure.status === 409) { onChanged(); return; }
      setPrefillNotice("내 조건을 불러오지 못했어요. 아래 질문에 직접 답해주세요.");
    }
  }
  async function evaluate() {
    request.current?.abort();
    const controller = new AbortController(); request.current = controller;
    setBusy(true); setError(""); setResult(null);
    const body: EvaluationRequest = { revision: questions.revision, ruleVersion: questions.ruleVersion,
      answers: Object.entries(answers).filter(([, value]) => value).map(([questionId, value]) => ({ questionId, value })) };
    try {
      const response = await memberApi<Evaluation>(`policy-evaluation/${questions.policyNumber}`, { method: "POST", body, signal: controller.signal });
      if (!controller.signal.aborted) setResult(response);
    } catch (failure) {
      if (controller.signal.aborted) return;
      if (failure instanceof MemberApiError && failure.status === 409) { onChanged(); return; }
      setError("답변을 확인하지 못했어요. 입력한 답변은 그대로 있으니 다시 시도해주세요.");
    } finally { if (request.current === controller && !controller.signal.aborted) setBusy(false); }
  }
  return <>
    <p className="question-scope">{questions.scope}</p>
    <p>{questions.reason}</p>
    <p className="question-privacy">답변은 저장하지 않아요. 확인한 생년월일이 있으면 출생일 답변에 사용하며, 새로고침하면 지워져요.</p>
    {!started ? <button type="button" className="button-primary" onClick={() => void start()}>질문에 답하기</button> : <form onSubmit={event => { event.preventDefault(); void evaluate(); }}>
      <p className="question-privacy">모르는 항목은 비워두거나 ‘모르겠어요’를 선택하세요.</p>
      {prefillNotice && <p role="status">{prefillNotice}</p>}
      <div className="policy-question-fields">
        {questions.questions.map((question, index) => <div className="policy-question-field" key={question.id}>
          <label htmlFor={`question-${question.id}`}><span>{index + 1}.</span> {question.label}</label>
          <p id={`help-${question.id}`}>{question.help}</p>
          <select id={`question-${question.id}`} aria-describedby={`help-${question.id}`} value={answers[question.id] || ""} onChange={event => change(question.id, event.target.value)}>
            <option value="">선택해주세요</option>
            {question.options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </div>)}
      </div>
      <div className="question-actions">
        <button type="submit" className="button-primary" disabled={busy}>{busy ? "조건 비교 중…" : "조건 비교"}</button>
        <button type="button" className="button-secondary" onClick={() => { request.current?.abort(); request.current = null; clearConfirmedBirth(); setPrefillNotice(""); setAnswers({}); setResult(null); setError(""); setBusy(false); }}>답변 지우기</button>
      </div>
      {busy && <p role="status">입력한 답변을 확인하고 있어요.</p>}
      {error && <p role="alert" className="question-feedback">{error}</p>}
    </form>}
    {result && <div className="policy-question-result">
      <h3 ref={resultHeading} tabIndex={-1}>조건 확인 결과</h3>
      <PolicyQuestionResult result={result} />
    </div>}
    <a className="text-link" href={questions.sourceUrl} target="_blank" rel="noopener noreferrer">신청 조건 공식 안내 <span aria-hidden="true">↗</span><span className="sr-only"> (새 창)</span></a>
  </>;
}

export function PolicyQuestionResult({ result }: { result: Evaluation }) {
  const label = { ELIGIBLE: "확인한 조건 충족", INELIGIBLE: "충족하지 못한 조건 있음", NEEDS_REVIEW: "추가 확인 필요" }[result.commonCriteriaStatus];
  const outcome = { MET: "충족", NOT_MET: "불충족", UNKNOWN: "추가 확인" };
  return <>
    <p className="question-result-label">{label}</p>
    <p>{result.explanation}</p>
    <ul className="question-checks">{result.checks.map(check => <li key={check.label}>
      <div><strong>{check.label}</strong><span data-outcome={check.outcome}>{outcome[check.outcome]}</span></div>
      <p>내 답변: {check.providedValue}</p><p>{check.explanation}</p>
      <details><summary>{check.label} 근거 보기</summary><p>{check.evidence}</p></details>
    </li>)}</ul>
    <div className="question-remaining"><strong>최종 신청 자격 · 추가 확인 필요</strong><ul>{result.remainingChecks.map(check => <li key={check}>{check}</li>)}</ul></div>
  </>;
}
