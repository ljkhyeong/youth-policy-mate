"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { PolicyCheckResults } from "./policy-check-results";
import { ConditionMemberControls } from "@/features/member/condition-member-controls";
import type { BasicConditions } from "@/features/member/member-api";
import {
  EMPTY_CONDITION_DRAFT, EMPLOYMENT_OPTIONS, SEOUL_DISTRICTS, validateConditionDraft,
  type ConditionDraft, type ConditionDraftErrors,
} from "./condition-draft";

export function ConditionForm({ today }: { today: string }) {
  const [draft, setDraft] = useState<ConditionDraft>(EMPTY_CONDITION_DRAFT);
  const [errors, setErrors] = useState<ConditionDraftErrors>({});
  const [confirmed, setConfirmed] = useState(false);
  const [notice, setNotice] = useState("");
  const [showResults, setShowResults] = useState(false);
  const suggestedOnce = useRef(false);
  const suggestBirthDate = useCallback((birthDate: string) => {
    if (suggestedOnce.current) return;
    suggestedOnce.current = true;
    setDraft(previous => previous.birthDate ? previous : { ...previous, birthDate });
  }, []);
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
    setShowResults(false);
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
  const input = useMemo(() => ({ ...draft, employmentStatus: draft.employmentStatus as BasicConditions["employmentStatus"] }), [draft]);
  function loadSaved(value: ConditionDraft) { setDraft(value); setErrors({}); edit(); }

  return (
    <section className="condition-panel" aria-label="내 조건 입력과 확인">
      <ol className="form-progress" aria-label="입력 단계">
        <li aria-current={!confirmed ? "step" : undefined} data-active={!confirmed}>
          <span>1</span><strong>조건 입력</strong>
        </li>
        <li aria-hidden="true" className="progress-line" />
        <li aria-current={confirmed ? "step" : undefined} data-active={confirmed}>
          <span>2</span><strong>내용 확인</strong>
        </li>
      </ol>

      <p role="status" className="form-notice">{notice}</p>

      {confirmed ? (
        <div className="confirmation-view">
          <span className="confirmation-icon" aria-hidden="true">✓</span>
          <p className="confirmation-label">입력이 끝났어요</p>
          <h2 ref={summaryRef} tabIndex={-1} className="confirmation-title">아래 내용이 맞는지 확인해주세요</h2>
          <dl className="summary-list">
            <div className="summary-row"><dt>생년월일 · 양력</dt><dd>{draft.birthDate.replaceAll("-", ". ")}</dd></div>
            <div className="summary-row"><dt>주민등록상 거주지</dt><dd>서울특별시 {draft.district}</dd></div>
            <div className="summary-row"><dt>주된 취업상태</dt><dd>{employmentLabel}</dd></div>
          </dl>
          <div className="availability-note">
            <span aria-hidden="true">i</span>
            <div>
              <p>정책별 신청 조건을 확인하세요</p>
              <p>확인 버튼을 누르면 입력 내용을 전송해 이번 확인에만 사용해요. 확인할 수 없는 조건은 ‘추가 확인 필요’로 표시해요.</p>
            </div>
          </div>
          <p className="data-retention-note">새로고침하면 입력 내용이 지워져요. 로그인 후 저장 버튼을 눌러 보관할 수 있어요.</p>
          <div className="form-actions">
            <button type="button" className="button-primary button-block" onClick={() => setShowResults(true)}>이 조건으로 정책 확인하기</button>
            <button type="button" className="button-secondary button-block" onClick={edit}>입력 내용 수정하기</button>
            <button type="button" className="text-button" onClick={reset}>입력 내용 모두 지우기</button>
          </div>
          <ConditionMemberControls input={input} onLoad={loadSaved} />
          {showResults && <PolicyCheckResults input={input} />}
        </div>
      ) : (
        <form ref={formRef} onSubmit={submit} noValidate method="post" autoComplete="off">
          <ConditionMemberControls onLoad={loadSaved} onSuggestBirthDate={suggestBirthDate} />
          <div className="form-heading">
            <h2>기본 조건</h2>
            <p>세 항목을 입력한 뒤 한 번 더 확인할 수 있어요.</p>
          </div>
          {Object.values(errors).some(Boolean) && <p role="alert" className="form-error-summary">표시된 입력 항목을 확인해주세요.</p>}

          {/* 기본 폼 제출로 개인정보가 전송되지 않도록 name을 두지 않고 화면 상태만 사용한다. */}
          <div className="form-field">
            <label htmlFor="birthDate">생년월일 <span className="font-normal text-stone-500">· 양력</span></label>
            <input
              id="birthDate" type="date" min="0001-01-01" max={today} required
              value={draft.birthDate}
              onInput={(event) => change("birthDate", event.currentTarget.value)}
              onChange={(event) => change("birthDate", event.target.value)}
              aria-invalid={Boolean(errors.birthDate)} aria-describedby={`birthDate-help${errors.birthDate ? " birthDate-error" : ""}`}
            />
            <p id="birthDate-help" className="field-help">음력 생일이라면 양력 날짜로 입력해주세요.</p>
            {errors.birthDate && <p id="birthDate-error" className="field-error">{errors.birthDate}</p>}
          </div>

          <div className="form-field">
            <label htmlFor="district">서울 거주 자치구</label>
            <select id="district" required value={draft.district} onChange={(event) => change("district", event.target.value)} aria-invalid={Boolean(errors.district)} aria-describedby={`district-help${errors.district ? " district-error" : ""}`}>
              <option value="">자치구 선택</option>
              {SEOUL_DISTRICTS.map((district) => <option key={district} value={district}>{district}</option>)}
            </select>
            <p id="district-help" className="field-help">학교나 직장이 아닌 주민등록상 거주지예요.</p>
            {errors.district && <p id="district-error" className="field-error">{errors.district}</p>}
          </div>

          <div className="form-field">
            <label htmlFor="employmentStatus">주된 취업상태</label>
            <select id="employmentStatus" required value={draft.employmentStatus} onChange={(event) => change("employmentStatus", event.target.value)} aria-invalid={Boolean(errors.employmentStatus)} aria-describedby={`employmentStatus-help${errors.employmentStatus ? " employmentStatus-error" : ""}`}>
              <option value="">취업상태 선택</option>
              {EMPLOYMENT_OPTIONS.map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
            </select>
            <p id="employmentStatus-help" className="field-help">현재 상황에 가장 가까운 항목을 골라주세요.</p>
            {errors.employmentStatus && <p id="employmentStatus-error" className="field-error">{errors.employmentStatus}</p>}
          </div>

          <div className="form-actions">
            <button type="submit" className="button-primary button-block">
              입력 내용 확인하기 <span className="button-arrow" aria-hidden="true">→</span>
            </button>
            <button type="button" className="text-button" onClick={reset}>입력 내용 모두 지우기</button>
          </div>
        </form>
      )}
    </section>
  );
}
