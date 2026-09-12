"use client";

import { useId, useState } from "react";
import { RuleActionForm } from "./policy-rule-action-form";
import { moveRuleCase, ruleDraftJson, seoulDateTime, type RuleDefinition, type RuleCheck, type RuleCase } from "./policy-rule-editor-model";

const outcomeLabels = { MET: "조건 충족", NOT_MET: "조건 불충족", UNKNOWN: "추가 확인" };

function Field({ label, value, onChange, multiline = false, required = true, type = "text", maxLength }: {
  label: string; value: string; onChange: (value: string) => void; multiline?: boolean; required?: boolean; type?: string; maxLength?: number;
}) {
  const id = useId();
  const props = { id, value, required, maxLength, onChange: (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => onChange(event.target.value) };
  return <div className="form-field"><label htmlFor={id}>{label}</label>
    {multiline ? <textarea {...props} rows={2} /> : <input {...props} type={type} />}</div>;
}

function TimeField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  const id = useId();
  return <div className="form-field"><label htmlFor={id}>{label} (서울)</label>
    <input id={id} type="datetime-local" step="0.001" required value={seoulDateTime(value)}
      onChange={event => onChange(event.target.value ? `${event.target.value}+09:00` : "")} /></div>;
}

export function RuleEditor({ definition, revision, contentHash }: { definition: RuleDefinition; revision: number; contentHash: string }) {
  const [rule, setRule] = useState<RuleDefinition>(() => ({ ...definition, ruleVersion: "" }));
  const [sourceReviewed, setSourceReviewed] = useState(definition.contentHash === contentHash);
  const patch = (change: Partial<RuleDefinition>) => setRule(current => ({ ...current, ...change }));
  const checkChange = (index: number, check: RuleCheck) => patch({ checks: rule.checks.map((item, at) => at === index ? check : item) });
  const age = rule.ageBinding;
  const birth = rule.birthBinding;
  const period = rule.periodNotice;
  return <section className="rule-editor" aria-label="규칙 편집">
    <h3>수정해서 새 초안 만들기</h3>
    <p className="field-help">기준 버전: {definition.ruleVersion}. 수정본은 별도 초안으로 저장합니다. 저장 후 최신 내용에서 검토하고 적용하세요.</p>
    <RuleActionForm policyNumber={rule.policyNumber} revision={revision}
      draftJson={ruleDraftJson(rule, definition.ruleVersion, contentHash, sourceReviewed)}>
      <Field label="새 버전명" value={rule.ruleVersion} maxLength={rule.monthly ? 72 : 80} onChange={value => patch({ ruleVersion: value })} />
      <p className="field-help">기존과 다른 버전명을 입력하세요. 예: 2026-09-v2</p>
      {definition.contentHash !== contentHash && <label className="rule-review-confirm"><input type="checkbox" checked={sourceReviewed}
        onChange={event => setSourceReviewed(event.target.checked)} />현재 공고의 조건·예외를 검토했으며, 수정본을 현재 원문에 연결합니다.</label>}
      <Field label="공고·검토 범위" value={rule.scope} onChange={value => patch({ scope: value })} />
      <Field label="근거 공고 주소" type="url" value={rule.sourceUrl} onChange={value => patch({ sourceUrl: value })} />
      <div className="rule-editor-grid">
        <TimeField label="규칙 적용 시작" value={rule.validFrom} onChange={value => patch({ validFrom: value })} />
        <TimeField label="규칙 적용 종료" value={rule.validUntil} onChange={value => patch({ validUntil: value })} />
      </div>
      <p className="field-help">시작 시각부터 적용하고 종료 시각부터 중단합니다. 공고의 접수 기간과 구분하세요.</p>
      <details className="exception-section"><summary>질문·결과 안내</summary>
        <Field label="질문 시작 안내" multiline value={rule.reason} onChange={value => patch({ reason: value })} />
        <Field label="판정 결과 안내" multiline value={rule.explanation} onChange={value => patch({ explanation: value })} />
        {rule.remainingChecks.map((text, index) => <Field key={index} label={`기관 확인 항목 ${index + 1}`} multiline value={text}
          onChange={value => patch({ remainingChecks: rule.remainingChecks.map((item, at) => at === index ? value : item) })} />)}
      </details>
      <details className="exception-section"><summary>질문·선택지 수정 ({rule.questions.length}개)</summary>
        <p className="field-help">기존 질문과 선택지의 문구를 수정합니다. 질문·선택지 추가나 식별자 변경은 규칙 파일에서 처리하세요.</p>
        {rule.questions.map((question, index) => {
          const update = (change: Partial<typeof question>) => patch({ questions: rule.questions.map((item, at) => at === index ? { ...item, ...change } : item) });
          return <fieldset className="rule-editor-group" key={question.id}><legend>질문 {index + 1} · {question.id}</legend>
            <Field label="질문" value={question.label} onChange={value => update({ label: value })} />
            <Field label="질문 도움말" multiline required={false} value={question.help} onChange={value => update({ help: value })} />
            {question.options.map((option, position) => <Field key={option.value} label={`선택지 ${position + 1} · ${option.value}`} value={option.label}
              onChange={value => update({ options: question.options.map((item, at) => at === position ? { ...item, label: value } : item) })} />)}
          </fieldset>;
        })}
      </details>
      <details className="exception-section"><summary>판정 기준 수정 ({rule.checks.length}개)</summary>
        <p className="field-help">위에서 처음 일치한 기준을 적용합니다. 한 기준의 질문은 모두 충족해야 하며, 같은 질문의 선택지는 하나만 해당해도 됩니다.</p>
        {rule.checks.map((check, index) => <CheckEditor key={index} check={check} questions={rule.questions}
          boundQuestion={age?.questionId === check.questionId || birth?.questionId === check.questionId}
          onChange={value => checkChange(index, value)} />)}
      </details>
      {(age || birth || period || rule.ageNotice != null) && <details className="exception-section"><summary>연령·시기별 안내 수정</summary>
        {age && <>
          <p className="field-help">만 나이 비교 질문: {age.questionId}. 범위를 바꾸면 해당 질문의 선택지 문구도 맞춰주세요.</p>
          <div className="rule-editor-grid">
            <Field label="최소 만 나이" type="number" value={Number.isNaN(age.minimumInclusive) ? "" : String(age.minimumInclusive)} onChange={value => patch({ ageBinding: { ...age, minimumInclusive: value === "" ? NaN : Number(value) } })} />
            <Field label="최대 만 나이 (없으면 비움)" type="number" required={false} value={age.maximumInclusive == null ? "" : String(age.maximumInclusive)} onChange={value => patch({ ageBinding: { ...age, maximumInclusive: value === "" ? null : Number(value) } })} />
          </div>
          <Field label="연령 기준일 (비우면 조회 당일·서울)" type="date" required={false} value={age.referenceDate ?? ""} onChange={value => patch({ ageBinding: { ...age, referenceDate: value || null } })} />
        </>}
        {birth && <>
          <p className="field-help">출생일 비교 질문: {birth.questionId}. 양 끝 날짜를 포함합니다.</p>
          <Field label="출생일 범위 시작" type="date" required={false} value={birth.minimumInclusive ?? ""} onChange={value => patch({ birthBinding: { ...birth, minimumInclusive: value || null } })} />
          <Field label="출생일 범위 종료" type="date" required={false} value={birth.maximumInclusive ?? ""} onChange={value => patch({ birthBinding: { ...birth, maximumInclusive: value || null } })} />
        </>}
        {rule.ageNotice != null && <Field label="연령 비교 안내" multiline required={false} value={rule.ageNotice} onChange={value => patch({ ageNotice: value })} />}
        {period && <>
          <TimeField label="접수 시작 안내 기준" value={period.opensAt} onChange={value => patch({ periodNotice: { ...period, opensAt: value } })} />
          <TimeField label="접수 종료 안내 기준" value={period.closesAt} onChange={value => patch({ periodNotice: { ...period, closesAt: value } })} />
          <Field label="접수 전 안내" multiline value={period.before} onChange={value => patch({ periodNotice: { ...period, before: value } })} />
          <Field label="접수 중 안내" multiline value={period.open} onChange={value => patch({ periodNotice: { ...period, open: value } })} />
          <Field label="접수 종료 안내" multiline value={period.closed} onChange={value => patch({ periodNotice: { ...period, closed: value } })} />
        </>}
      </details>}
      <details className="exception-section"><summary>유지되는 별도 설정</summary>
        <p className="field-help">월별 버전, 답변 표시, 연령 선택지 연결, 답변별 추가 안내는 그대로 저장합니다. 변경하려면 규칙 파일을 사용하세요.</p>
        <pre className="exception-raw" tabIndex={0}>{JSON.stringify({ monthly: rule.monthly, ageBinding: age, birthBinding: birth,
          remainingVariant: rule.remainingVariant, providedAnswers: rule.checks.map(check => ({ questionId: check.questionId, providedAnswers: check.providedAnswers, separator: check.separator })) }, null, 2)}</pre>
      </details>
    </RuleActionForm>
  </section>;
}

function CheckEditor({ check, questions, boundQuestion, onChange }: {
  check: RuleCheck; questions: RuleDefinition["questions"]; boundQuestion: boolean; onChange: (check: RuleCheck) => void;
}) {
  const update = (change: Partial<RuleCheck>) => onChange({ ...check, ...change });
  const rowChange = (index: number, change: Partial<RuleCase>) => update({ cases: check.cases.map((row, at) => at === index ? { ...row, ...change } : row) });
  return <details className="rule-editor-group"><summary>{check.label}</summary>
    <Field label="판정 항목명" value={check.label} onChange={value => update({ label: value })} />
    <Field label="원문 근거" multiline value={check.evidence} onChange={value => update({ evidence: value })} />
    <Field label="일치하는 기준이 없을 때 안내" multiline value={check.unknownExplanation} onChange={value => update({ unknownExplanation: value })} />
    {check.cases.map((row, index) => <fieldset key={index} className="rule-editor-group"><legend>{index + 1}번째 기준</legend>
      {Object.entries(row.when).map(([questionId, values]) => {
        const question = questions.find(item => item.id === questionId)!;
        return <fieldset key={questionId} className="rule-condition"><legend>{question.label}</legend>
          {[...question.options, { value: "", label: "미응답" }].map(option => <label className="rule-review-confirm" key={option.value}>
            <input type="checkbox" checked={values.includes(option.value)} onChange={event => rowChange(index, { when: { ...row.when,
              [questionId]: event.target.checked ? [...values, option.value] : values.filter(value => value !== option.value) } })} />{option.label}</label>)}
          {!values.length && <p role="alert">선택지를 하나 이상 지정하세요.</p>}
          {Object.keys(row.when).length > 1 && <button className="text-link" type="button" onClick={() => rowChange(index, {
            when: Object.fromEntries(Object.entries(row.when).filter(([id]) => id !== questionId)),
          })}>이 질문 조건 삭제</button>}
        </fieldset>;
      })}
      {!boundQuestion && Object.keys(row.when).length < questions.length && <label className="rule-condition-picker">질문 조건 추가
        <select value="" onChange={event => { if (event.target.value) rowChange(index, { when: { ...row.when, [event.target.value]: [""] } }); }}>
          <option value="">질문 선택</option>{questions.filter(question => !(question.id in row.when)).map(question => <option key={question.id} value={question.id}>{question.label}</option>)}
        </select></label>}
      <label className="rule-condition-picker">판정 결과<select value={row.outcome} onChange={event => rowChange(index, { outcome: event.target.value as RuleCase["outcome"] })}>
        {Object.entries(outcomeLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></label>
      <Field label="판정 사유" multiline value={row.explanation} onChange={value => rowChange(index, { explanation: value })} />
      <div className="form-actions">
        <button className="text-link" type="button" disabled={index === 0} onClick={() => onChange(moveRuleCase(check, index, -1))}>위로</button>
        <button className="text-link" type="button" disabled={index === check.cases.length - 1} onClick={() => onChange(moveRuleCase(check, index, 1))}>아래로</button>
        <button className="text-link" type="button" disabled={check.cases.length === 1} onClick={() => update({ cases: check.cases.filter((_, at) => at !== index) })}>기준 삭제</button>
      </div>
    </fieldset>)}
    <button className="button-secondary" type="button" onClick={() => update({ cases: [...check.cases,
      { when: { [check.questionId]: [""] }, outcome: "UNKNOWN", explanation: "" }] })}>판정 기준 추가</button>
  </details>;
}
