"use client";

import { useEffect, useRef, useState } from "react";
import { INCOME_EXAMPLES, incomeAnswerLabel, type IncomePreviewAnswer, type IncomePreviewQuestion } from "./income-preview-data";

export function IncomeQuestionPreview() {
  const [exampleIndex, setExampleIndex] = useState(0);
  const [notice, setNotice] = useState("");
  const question = INCOME_EXAMPLES[exampleIndex];

  return (
    <div className="mt-9">
      <div role="group" aria-label="소득 질문 변경 점검" className="flex flex-wrap gap-x-3 border-b border-stone-300">
        {INCOME_EXAMPLES.map((example, index) => (
          <button key={example.label} type="button" className="preview-choice" aria-pressed={index === exampleIndex}
            onClick={() => {
              if (index === exampleIndex) return;
              setExampleIndex(index);
              setNotice("질문 예시를 바꾸고 이전 답변을 지웠어요. 새 대상 기간과 질문을 확인해주세요.");
            }}>
            {example.label}
          </button>
        ))}
      </div>
      <p className="mt-3 text-xs leading-6 text-stone-600">예시를 바꾸면 선택·확인한 답변이 초기화됩니다. 원래 예시로 돌아와도 복원하지 않습니다.</p>
      <p role="status" className="mt-3 min-h-7 text-sm leading-7 text-teal-900">{notice}</p>
      {/* 정의·대상·기간·선택 구간이 달라지면 이전 답변을 새 질문에 붙이지 않는다. */}
      <IncomeQuestionForm key={JSON.stringify(question)} question={question} />
    </div>
  );
}

function IncomeQuestionForm({ question }: { question: IncomePreviewQuestion }) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const [error, setError] = useState(false);
  const [notice, setNotice] = useState("");
  const firstAnswerRef = useRef<HTMLInputElement>(null);
  const summaryRef = useRef<HTMLHeadingElement>(null);
  const selected = question.choices.find((choice) => choice.id === selectedId);

  useEffect(() => {
    if (confirmed) summaryRef.current?.focus();
  }, [confirmed]);

  function returnToAnswers() {
    setConfirmed(false);
    requestAnimationFrame(() => firstAnswerRef.current?.focus());
  }

  return (
    <section className="condition-panel mt-4" aria-label="소득 질문과 답변">
      <dl className="grid gap-5 border-b border-stone-200 pb-6 sm:grid-cols-[0.8fr_1.5fr_0.7fr]">
        <div><dt className="text-xs text-stone-600">확인할 대상</dt><dd className="mt-2 font-semibold text-teal-950">{question.subject}</dd></div>
        <div><dt className="text-xs text-stone-600">소득 대상 기간 · 양 끝 날짜 포함</dt><dd className="mt-2 text-sm leading-7 font-semibold tabular-nums"><time dateTime={question.periodStart}>{question.periodStart.replaceAll("-", ". ")}</time> ~ <time dateTime={question.periodEnd}>{question.periodEnd.replaceAll("-", ". ")}</time></dd></div>
        <div><dt className="text-xs text-stone-600">금액 단위</dt><dd className="mt-2 font-semibold text-teal-950">{question.unit}</dd></div>
      </dl>
      <div className="mt-8 grid gap-9 lg:grid-cols-[1.1fr_1fr] lg:gap-12">
        <div>
          <p className="text-xs font-semibold tracking-wide text-teal-800">소득 구간 · 질문 예시</p>
          <h2 className="mt-3 text-2xl leading-relaxed font-bold tracking-tight">{question.refinement ? "같은 기간의 구간을 조금 더 좁혀볼까요?" : "해당 기간의 소득은 어느 구간인가요?"}</h2>
          <dl className="mt-6 divide-y divide-stone-200 border-y border-stone-200 text-sm">
            <div className="py-4"><dt className="text-stone-600">이 질문에서 말하는 소득</dt><dd className="mt-2 font-semibold leading-7">{question.definition}</dd></div>
            <div className="py-4"><dt className="text-stone-600">금액을 확인하는 방식</dt><dd className="mt-2 leading-7">{question.calculation}. 기간이 다른 금액이나 추정한 세전 금액으로 대신하지 마세요. 알 수 없으면 ‘모름’을 선택하세요.</dd></div>
          </dl>
          {question.refinement ? (
            <aside className="mt-6 border-l-2 border-teal-800 bg-teal-50 px-5 py-4 text-sm leading-7" aria-label="구간 추가 확인 상황 예시">
              <p className="font-semibold text-teal-950">미리 정한 추가 확인 상황</p>
              <p className="mt-2 text-stone-700">현재 선택한 답변으로 계산한 결과가 아닙니다. 아래 인공 상황에서 구간을 더 좁혀 묻는 화면만 점검합니다.</p>
              <dl className="mt-4 space-y-3">
                <div><dt className="text-xs text-stone-600">가정한 이전 답변</dt><dd className="mt-1 font-semibold tabular-nums">{question.refinement.previousAnswer}</dd></div>
                <div><dt className="text-xs text-stone-600">인공 정책의 허용 기준</dt><dd className="mt-1 font-semibold tabular-nums">{question.refinement.boundary}</dd></div>
              </dl>
              <p className="mt-4 text-stone-700">가정한 구간이 2,500만 원 경계에 걸쳐 있어 더 좁은 구간이 필요한 상황입니다. 정확한 금액까지 입력할 필요는 없습니다.</p>
            </aside>
          ) : (
            <aside className="mt-6 border-l-2 border-teal-800 bg-teal-50 px-5 py-4 text-sm leading-7" aria-label="소득 질문이 필요한 이유">
              <p className="font-semibold text-teal-950">왜 구간으로 묻나요?</p>
              <p className="mt-2 text-stone-700">취업상태만으로 해당 기간의 소득을 알 수 없기 때문입니다. 먼저 구간만 확인하고, 정책 경계에 걸칠 때 필요한 범위만 더 좁혀 묻는 흐름을 점검합니다.</p>
            </aside>
          )}
          <p className="mt-5 text-xs leading-6 text-stone-600">근거 표시 예시 · 실제 정책 원문 아님<br />화면 점검용 정의·금액·기간 · 예시 개정 {question.revision}<br />가구원 수·보험료·증빙서류는 이 질문에서 받지 않습니다.</p>
        </div>

        <div className="min-w-0 lg:border-l lg:border-stone-200 lg:pl-12">
          {confirmed && selected ? (
            <div>
              <h3 ref={summaryRef} tabIndex={-1} className="text-xl font-bold focus:outline-none">이 질문에 대한 답변</h3>
              <p className="mt-3 text-sm leading-7 text-stone-600">{question.subject} · {question.periodStart.slice(0, 4)}년 합계 · {question.unit}</p>
              <IncomeAnswerSummary answer={selected.answer} />
              <button type="button" className="button-primary mt-6" onClick={returnToAnswers}>답변 수정하기</button>
            </div>
          ) : (
            <div>
              <fieldset aria-describedby={`income-answer-help${error ? " income-answer-error" : ""}`}>
                <legend className="text-xl font-bold">{question.refinement ? "더 좁은 구간을 선택해주세요" : "소득 구간을 선택해주세요"}</legend>
                <p id="income-answer-help" className="mt-3 text-sm leading-7 text-stone-600">실제 소득 대신 점검할 답변을 선택하세요. ‘이하’는 끝 금액을 포함하고 ‘초과’는 포함하지 않습니다.</p>
                {error && <p id="income-answer-error" role="alert" className="field-error">답변을 선택해주세요. 아직 알 수 없다면 ‘모름’을 선택하세요.</p>}
                <div className="mt-5 grid gap-3">
                  {question.choices.map((choice, index) => (
                    <label key={choice.id} className={`flex min-h-16 cursor-pointer items-start gap-3 rounded-sm border p-4 ${selectedId === choice.id ? "border-teal-800 bg-teal-50" : "border-stone-300 hover:border-stone-500"}`}>
                      <input ref={index === 0 ? firstAnswerRef : undefined} type="radio" name="preview-income-answer" value={choice.id}
                        className="mt-1 size-4 shrink-0 accent-teal-800" checked={selectedId === choice.id}
                        onChange={() => { setSelectedId(choice.id); setError(false); setNotice(""); }} />
                      <span><span className="block text-sm leading-6 font-semibold tabular-nums">{incomeAnswerLabel(choice.answer)}</span><span className="mt-1 block text-xs leading-6 text-stone-600">{choice.description}</span></span>
                    </label>
                  ))}
                </div>
              </fieldset>
              <button type="button" className="button-primary mt-6" onClick={() => {
                if (!selected) { setError(true); firstAnswerRef.current?.focus(); return; }
                setNotice("");
                setConfirmed(true);
              }}>답변 확인하기</button>
            </div>
          )}
          <button type="button" className="text-link mt-3" onClick={() => {
            setSelectedId(null);
            setError(false);
            setNotice("답변을 지웠어요. 아무 구간도 선택하지 않은 상태입니다.");
            returnToAnswers();
          }}>답변 지우기</button>
          <p role="status" className="mt-3 text-sm leading-7 text-teal-900">{notice}</p>
          <p className="mt-6 border-t border-stone-200 pt-5 text-xs leading-6 text-stone-600">답변은 이 화면에서만 사용하며 서버로 보내거나 저장하지 않습니다. 새로고침하면 사라집니다. ‘내 조건’의 입력값을 가져오거나 바꾸지 않습니다.</p>
        </div>
      </div>
    </section>
  );
}

export function IncomeAnswerSummary({ answer }: { answer: IncomePreviewAnswer }) {
  return (
    <div className="mt-6 border-y border-stone-200 py-5">
      <p className="text-xs text-stone-600">선택한 답변</p>
      <p className="mt-2 text-xl leading-8 font-bold text-teal-950 tabular-nums">{incomeAnswerLabel(answer)}</p>
      <p className="mt-4 text-sm leading-7 text-stone-700">{answer.kind === "unknown"
        ? "아직 알 수 없는 답변으로 남겼어요. 0원이나 소득 제한 없음으로 처리하지 않습니다."
        : "선택한 구간만 확인했어요. 구간의 중간값이나 끝값을 대표 금액으로 바꾸지 않습니다."}</p>
      <p className="mt-3 text-sm leading-7 text-stone-600">이 화면은 자격 판정을 하지 않으며, 공식 소득 확인도 아닙니다.</p>
    </div>
  );
}
