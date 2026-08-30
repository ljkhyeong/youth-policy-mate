"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  EMPTY_CONDITION_DRAFT, EMPLOYMENT_OPTIONS, SEOUL_DISTRICTS, validateConditionDraft,
  type ConditionDraft, type ConditionDraftErrors,
} from "./condition-draft";

export function ConditionForm({ today }: { today: string }) {
  const [draft, setDraft] = useState<ConditionDraft>(EMPTY_CONDITION_DRAFT);
  const [errors, setErrors] = useState<ConditionDraftErrors>({});
  const [confirmed, setConfirmed] = useState(false);
  const [notice, setNotice] = useState("");
  const formRef = useRef<HTMLFormElement>(null);
  const summaryRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (confirmed) summaryRef.current?.focus();
  }, [confirmed]);

  function change(field: keyof ConditionDraft, value: string) {
    setDraft((previous) => ({ ...previous, [field]: value }));
    setErrors((previous) => ({ ...previous, [field]: undefined }));
    setNotice("");
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateConditionDraft(draft, today);
    setErrors(nextErrors);
    const firstField = Object.keys(nextErrors)[0];
    if (firstField) {
      formRef.current?.querySelector<HTMLElement>(`#${firstField}`)?.focus();
      return;
    }
    setConfirmed(true);
    setNotice("");
  }

  function edit() {
    setConfirmed(false);
    requestAnimationFrame(() => formRef.current?.querySelector<HTMLInputElement>("#birthDate")?.focus());
  }

  function reset() {
    setDraft(EMPTY_CONDITION_DRAFT);
    setErrors({});
    setNotice("입력 내용을 모두 지웠어요.");
    edit();
  }

  const employmentLabel = EMPLOYMENT_OPTIONS.find(({ value }) => value === draft.employmentStatus)?.label;

  return (
    <section className="condition-panel" aria-label="내 조건 입력과 확인">
      <ol className="mb-8 flex gap-5 border-b border-stone-200 pb-5 text-sm" aria-label="입력 단계">
        <li aria-current={!confirmed ? "step" : undefined} className={!confirmed ? "font-bold text-teal-900" : "text-stone-500"}>1. 기본 조건 입력</li>
        <li aria-current={confirmed ? "step" : undefined} className={confirmed ? "font-bold text-teal-900" : "text-stone-500"}>2. 내용 확인</li>
      </ol>

      <p role="status" className="text-sm text-teal-800">{notice}</p>

      {confirmed ? (
        <div>
          <p className="mb-2 text-sm font-semibold text-teal-800">입력 내용 확인</p>
          <h2 ref={summaryRef} tabIndex={-1} className="text-2xl font-bold tracking-tight focus:outline-none">내 기본 조건을 확인해보세요.</h2>
          <dl className="my-8 divide-y divide-stone-200 border-y border-stone-200 text-sm">
            <div className="summary-row"><dt>생년월일 · 양력</dt><dd>{draft.birthDate.replaceAll("-", ". ")}</dd></div>
            <div className="summary-row"><dt>주민등록상 거주지</dt><dd>서울특별시 {draft.district}</dd></div>
            <div className="summary-row"><dt>주된 취업상태</dt><dd>{employmentLabel}</dd></div>
          </dl>
          <div className="border-l-2 border-teal-800 bg-teal-50 px-5 py-4 text-sm leading-7 text-teal-950">
            <p className="font-semibold">정책 추천은 아직 준비 중이에요.</p>
            <p>지금은 입력한 내용만 확인할 수 있어요. 정책 데이터가 연결되기 전에는 신청 가능 여부나 추천 목록을 표시하지 않습니다.</p>
          </div>
          <p className="mt-5 text-xs leading-6 text-stone-600">입력 내용은 서버로 보내거나 저장하지 않았어요. 이 화면을 새로고침하면 초기화됩니다.</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <button type="button" className="button-primary" onClick={edit}>입력 내용 수정하기</button>
            <button type="button" className="button-secondary" onClick={reset}>모두 지우기</button>
          </div>
        </div>
      ) : (
        <form ref={formRef} onSubmit={submit} noValidate method="post" autoComplete="off">
          <h2 className="text-xl font-bold tracking-tight">기본 조건</h2>
          <p className="mt-2 text-sm leading-6 text-stone-600">세 항목을 입력하면 내용을 한 번 더 확인할 수 있어요.</p>
          {Object.values(errors).some(Boolean) && <p role="alert" className="mt-5 text-sm font-semibold text-rose-800">아래 표시된 입력 항목을 확인해주세요.</p>}

          {/* 기본 폼 제출로 개인정보가 전송되지 않도록 name을 두지 않고 화면 상태만 사용한다. */}
          <div className="form-field">
            <label htmlFor="birthDate">생년월일 <span className="font-normal text-stone-500">· 양력</span></label>
            <p id="birthDate-help" className="field-help">생일이 음력이라면 양력 생년월일을 확인해 입력해주세요.</p>
            <input
              id="birthDate" type="date" min="0001-01-01" max={today} required
              value={draft.birthDate}
              onInput={(event) => change("birthDate", event.currentTarget.value)}
              onChange={(event) => change("birthDate", event.target.value)}
              aria-invalid={Boolean(errors.birthDate)} aria-describedby={`birthDate-help${errors.birthDate ? " birthDate-error" : ""}`}
            />
            {errors.birthDate && <p id="birthDate-error" className="field-error">{errors.birthDate}</p>}
          </div>

          <div className="form-field">
            <label htmlFor="district">서울 거주 자치구</label>
            <p id="district-help" className="field-help">학교나 직장 위치가 아닌 주민등록상 거주지를 선택해주세요.</p>
            <select id="district" required value={draft.district} onChange={(event) => change("district", event.target.value)} aria-invalid={Boolean(errors.district)} aria-describedby={`district-help${errors.district ? " district-error" : ""}`}>
              <option value="">자치구 선택</option>
              {SEOUL_DISTRICTS.map((district) => <option key={district} value={district}>{district}</option>)}
            </select>
            {errors.district && <p id="district-error" className="field-error">{errors.district}</p>}
          </div>

          <div className="form-field">
            <label htmlFor="employmentStatus">주된 취업상태</label>
            <p id="employmentStatus-help" className="field-help">현재 상황에 가장 가까운 항목을 선택해주세요.</p>
            <select id="employmentStatus" required value={draft.employmentStatus} onChange={(event) => change("employmentStatus", event.target.value)} aria-invalid={Boolean(errors.employmentStatus)} aria-describedby={`employmentStatus-help${errors.employmentStatus ? " employmentStatus-error" : ""}`}>
              <option value="">취업상태 선택</option>
              {EMPLOYMENT_OPTIONS.map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
            </select>
            {errors.employmentStatus && <p id="employmentStatus-error" className="field-error">{errors.employmentStatus}</p>}
          </div>

          <p className="mt-7 border-t border-stone-200 pt-5 text-xs leading-6 text-stone-600">지금은 이 화면에서만 입력값을 사용해요. 서버 전송·회원 저장은 하지 않으며, 새로고침하면 입력 내용이 초기화됩니다.</p>
          <div className="mt-6 flex flex-wrap gap-3">
            <button type="submit" className="button-primary">입력 내용 확인하기 <span aria-hidden="true">→</span></button>
            <button type="button" className="button-secondary" onClick={reset}>모두 지우기</button>
          </div>
        </form>
      )}
    </section>
  );
}
