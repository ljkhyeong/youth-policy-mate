"use client";

import { useEffect, useRef, useState } from "react";

// 개발 화면의 표시용 자료다. 서버 DTO나 실제 정책의 취업 정의가 아니다.
const EXAMPLE_QUESTIONS = [
  { policyId: "preview-only", revision: "example-1", conditionId: "employment", referenceDate: "2026-08-15", definition: "근무처와 근로계약을 맺고 일하고 있는 상태", label: "처음 질문" },
  { policyId: "preview-only", revision: "example-2", conditionId: "employment", referenceDate: "2026-08-30", definition: "근무처와 근로계약을 맺고 일하고 있는 상태", label: "기준일 변경 예시" },
] as const;

const ANSWERS = [
  { value: "applies", label: "해당함", description: "기준일에 위 사실에 해당해요." },
  { value: "does-not-apply", label: "해당하지 않음", description: "기준일에 위 사실에 해당하지 않아요." },
  { value: "unknown", label: "모름", description: "기준일의 상황이나 사실의 의미를 더 확인해야 해요." },
] as const;

type PreviewAnswer = (typeof ANSWERS)[number]["value"];
type ExampleQuestion = (typeof EXAMPLE_QUESTIONS)[number];

export function EmploymentQuestionPreview() {
  const [exampleIndex, setExampleIndex] = useState(0);
  const [notice, setNotice] = useState("");
  const question = EXAMPLE_QUESTIONS[exampleIndex];

  return (
    <div className="mt-9">
      <div role="group" aria-label="질문 변경 점검" className="flex flex-wrap gap-x-3 border-b border-stone-300">
        {EXAMPLE_QUESTIONS.map((example, index) => (
          <button key={example.revision} type="button" className="preview-choice" aria-pressed={index === exampleIndex}
            onClick={() => {
              if (index === exampleIndex) return;
              setExampleIndex(index);
              setNotice("질문 개정과 기준일을 바꾸고 이전 답변을 지웠어요.");
            }}>
            {example.label}
          </button>
        ))}
      </div>
      <p className="mt-3 text-xs leading-6 text-stone-600">예시를 바꾸면 선택·확인한 답변이 초기화됩니다. 원래 예시로 돌아와도 복원하지 않습니다.</p>
      <p role="status" className="mt-3 min-h-7 text-sm leading-7 text-teal-900">{notice}</p>
      {/* 질문의 식별 정보·정의·기준일이 달라지면 입력과 확인 상태를 함께 새로 만든다. */}
      <EmploymentQuestionForm key={JSON.stringify(question)} question={question} />
    </div>
  );
}

function EmploymentQuestionForm({ question }: { question: ExampleQuestion }) {
  const [answer, setAnswer] = useState<PreviewAnswer | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const [error, setError] = useState(false);
  const [notice, setNotice] = useState("");
  const firstAnswerRef = useRef<HTMLInputElement>(null);
  const summaryRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (confirmed) summaryRef.current?.focus();
  }, [confirmed]);

  function returnToAnswers() {
    setConfirmed(false);
    requestAnimationFrame(() => firstAnswerRef.current?.focus());
  }

  return (
    <section className="condition-panel mt-4" aria-label="취업 사실 질문과 답변">
      <div className="grid gap-9 lg:grid-cols-[1.1fr_1fr] lg:gap-12">
        <div>
          <p className="text-xs font-semibold tracking-wide text-teal-800">취업 사실 · 질문 예시</p>
          <h2 className="mt-3 text-2xl leading-relaxed font-bold tracking-tight">기준일에 근로계약을 맺고 일하고 있나요?</h2>
          <dl className="mt-6 divide-y divide-stone-200 border-y border-stone-200 text-sm">
            <div className="py-4"><dt className="text-stone-600">기준일 · 인공 예시</dt><dd className="mt-2 text-lg font-semibold tabular-nums"><time dateTime={question.referenceDate}>{question.referenceDate.replaceAll("-", ". ")}</time></dd></div>
            <div className="py-4"><dt className="text-stone-600">이 질문에서 확인할 사실</dt><dd className="mt-2 font-semibold leading-7">{question.definition}</dd></div>
            <div className="py-4"><dt className="text-stone-600">질문이 필요한 이유</dt><dd className="mt-2 leading-7">주된 취업상태 하나만으로 특정 날짜의 재직 여부를 알 수 없어, 이 사실을 따로 확인하는 흐름입니다.</dd></div>
          </dl>
          <aside className="mt-6 border-l-2 border-teal-800 bg-teal-50 px-5 py-4 text-sm leading-7" aria-label="근거 표시 예시">
            <p className="font-semibold text-teal-950">근거 표시 예시 · 실제 정책 원문 아님</p>
            <blockquote className="mt-2 text-stone-700">이 예시에서는 기준일에 근로계약을 맺고 일하고 있는지를 묻습니다.</blockquote>
            <p className="mt-3 text-xs text-stone-600">화면 점검용 인공 자료 · 예시 개정 {question.revision === "example-1" ? "1" : "2"}</p>
          </aside>
        </div>

        <div className="min-w-0 lg:border-l lg:border-stone-200 lg:pl-12">
          {confirmed && answer !== null ? (
            <div>
              <h3 ref={summaryRef} tabIndex={-1} className="text-xl font-bold focus:outline-none">이 질문에 대한 답변</h3>
              <p className="mt-3 text-sm leading-7 text-stone-600">기준일 {question.referenceDate.replaceAll("-", ". ")} · 예시 개정 {question.revision === "example-1" ? "1" : "2"}</p>
              <EmploymentAnswerSummary answer={answer} />
              <button type="button" className="button-primary mt-6" onClick={returnToAnswers}>답변 수정하기</button>
            </div>
          ) : (
            <div>
              <fieldset aria-describedby={`answer-help${error ? " answer-error" : ""}`}>
                <legend className="text-xl font-bold">이 사실에 해당하나요?</legend>
                <p id="answer-help" className="mt-3 text-sm leading-7 text-stone-600">위 정의와 기준일을 읽고 선택해주세요. 모르면 ‘모름’을 선택할 수 있어요.</p>
                {error && <p id="answer-error" role="alert" className="field-error">답변을 선택해주세요. 아직 알 수 없다면 ‘모름’을 선택하세요.</p>}
                <div className="mt-5 grid gap-3">
                  {ANSWERS.map((option, index) => (
                    <label key={option.value} className={`flex min-h-16 cursor-pointer items-start gap-3 rounded-sm border p-4 ${answer === option.value ? "border-teal-800 bg-teal-50" : "border-stone-300 hover:border-stone-500"}`}>
                      {/* 폼 없이 라디오 그룹만 사용해 기본 제출로 답변이 전송되지 않게 한다. */}
                      <input ref={index === 0 ? firstAnswerRef : undefined} type="radio" name="preview-employment-answer" value={option.value}
                        className="mt-1 size-4 shrink-0 accent-teal-800" checked={answer === option.value}
                        onChange={() => { setAnswer(option.value); setError(false); setNotice(""); }} />
                      <span><span className="block text-sm font-semibold">{option.label}</span><span className="mt-1 block text-xs leading-6 text-stone-600">{option.description}</span></span>
                    </label>
                  ))}
                </div>
              </fieldset>
              <button type="button" className="button-primary mt-6" onClick={() => {
                if (answer === null) { setError(true); firstAnswerRef.current?.focus(); return; }
                setNotice("");
                setConfirmed(true);
              }}>답변 확인하기</button>
            </div>
          )}
          <button type="button" className="text-link mt-3" onClick={() => {
            setAnswer(null);
            setError(false);
            setNotice("답변을 지웠어요. 아무 항목도 선택하지 않은 상태입니다.");
            returnToAnswers();
          }}>답변 지우기</button>
          <p role="status" className="mt-3 text-sm leading-7 text-teal-900">{notice}</p>
          <p className="mt-6 border-t border-stone-200 pt-5 text-xs leading-6 text-stone-600">답변은 이 화면에서만 사용하며 서버로 보내거나 저장하지 않습니다. 새로고침하면 사라집니다. ‘내 조건’의 입력값을 가져오거나 바꾸지 않습니다.</p>
        </div>
      </div>
    </section>
  );
}

export function EmploymentAnswerSummary({ answer }: { answer: PreviewAnswer }) {
  return (
    <div className="mt-6 border-y border-stone-200 py-5">
      <p className="text-xs text-stone-600">선택한 답변</p>
      <p className="mt-2 text-2xl font-bold text-teal-950">{ANSWERS.find((option) => option.value === answer)?.label}</p>
      <p className="mt-4 text-sm leading-7 text-stone-700">{answer === "unknown"
        ? "아직 알 수 없는 답변으로 남겼어요. ‘해당하지 않음’으로 바꾸지 않습니다."
        : "질문한 사실에 대한 답변만 확인했어요. 다른 취업 형태나 재학 여부까지 답한 것은 아닙니다."}</p>
      <p className="mt-3 text-sm leading-7 text-stone-600">이 화면은 자격 판정을 하지 않으며, 공식 자격 인증도 아닙니다.</p>
    </div>
  );
}
