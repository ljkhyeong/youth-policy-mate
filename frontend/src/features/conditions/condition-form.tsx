"use client";

import { useCallback, useRef, useState, type FormEvent } from "react";
import { rememberConfirmedBirth, clearConfirmedBirth } from "./confirmed-birth";
import { PolicyCheckResults } from "./policy-check-results";
import { ConditionMemberControls } from "@/features/member/condition-member-controls";
import type { BasicConditions } from "@/features/member/member-api";
import {
  EMPTY_CONDITION_DRAFT, EMPLOYMENT_OPTIONS, SEOUL_DISTRICTS, validateConditionDraft, conditionInput,
  type ConditionDraft, type ConditionDraftErrors,
} from "./condition-draft";

export function ConditionForm({ today }: { today: string }) {
  const [draft, setDraft] = useState<ConditionDraft>(EMPTY_CONDITION_DRAFT);
  const [input, setInput] = useState<BasicConditions>({});
  const [errors, setErrors] = useState<ConditionDraftErrors>({});
  const [notice, setNotice] = useState("");
  const inputRevision = useRef(0);
  const suggestedOnce = useRef(false);
  const formRef = useRef<HTMLFormElement>(null);
  const suggestBirthDate = useCallback((birthDate: string) => {
    if (suggestedOnce.current) return;
    suggestedOnce.current = true;
    setDraft(previous => previous.birthDate ? previous : { ...previous, birthDate });
  }, []);

  function change(field: keyof ConditionDraft, value: string) {
    inputRevision.current += 1;
    if (field === "birthDate") suggestedOnce.current = true;
    clearConfirmedBirth();
    setDraft(previous => ({ ...previous, [field]: value }));
    setErrors(previous => ({ ...previous, [field]: undefined }));
    setNotice("변경한 조건은 ‘조건 반영’을 누르면 적용돼요.");
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
    if (draft.birthDate) rememberConfirmedBirth(draft.birthDate);
    else clearConfirmedBirth();
    setInput(conditionInput(draft));
    setNotice(draft.birthDate ? "생년월일을 반영했어요. 확인된 연령 조건이 맞는 정책부터 보여드려요." : "생년월일 없이 정책을 둘러보고 있어요.");
  }

  function reset() {
    inputRevision.current += 1;
    suggestedOnce.current = true;
    clearConfirmedBirth();
    setDraft(EMPTY_CONDITION_DRAFT); setInput({}); setErrors({});
    setNotice("입력 조건을 지웠어요. 검색어와 접수 상태는 유지돼요.");
  }

  function prepareLoad() {
    const revision = inputRevision.current;
    return (value: ConditionDraft) => {
      if (revision !== inputRevision.current) return false;
      inputRevision.current += 1;
      suggestedOnce.current = true;
      clearConfirmedBirth(); setDraft(value); setErrors({});
      setNotice("불러온 조건을 확인하고 ‘조건 반영’을 눌러주세요.");
      return true;
    };
  }

  const reflected = (input.birthDate ?? "") === draft.birthDate && (input.district ?? "") === draft.district
    && (input.employmentStatus ?? "") === draft.employmentStatus;
  return <section className="condition-panel" aria-label="내 조건 입력과 확인">
    <details>
      <summary>생년월일로 연령 비교하기 (선택)</summary>
    <form ref={formRef} onSubmit={submit} noValidate method="post" autoComplete="off">
      <div className="form-heading"><h2>생년월일로 더 찾아보기</h2></div>
      <p className="field-help">입력은 선택이에요. 아래 정책부터 둘러봐도 좋아요.</p>
      {Object.values(errors).some(Boolean) && <p role="alert" className="form-error-summary">표시된 입력 항목을 확인해주세요.</p>}
      <div className="form-field">
        <label htmlFor="birthDate">생년월일 · 양력 <span className="field-help">(선택)</span></label>
        <input id="birthDate" type="date" min="0001-01-01" max={today} value={draft.birthDate}
          onChange={event => change("birthDate", event.target.value)}
          aria-invalid={Boolean(errors.birthDate)} aria-describedby={`birthDate-help${errors.birthDate ? " birthDate-error" : ""}`} />
        <p id="birthDate-help" className="field-help">생년월일은 확인된 정책의 연령 비교와 상세 질문에 재사용해요. 음력 생일은 양력으로 입력해주세요.</p>
        {errors.birthDate && <p id="birthDate-error" className="field-error">{errors.birthDate}</p>}
      </div>
      <details>
        <summary>거주·취업 정보 추가 (선택)</summary>
        <p className="field-help">이 정보는 저장할 수 있어요. 현재 목록의 자동 비교·정렬에는 사용하지 않으며, 정책별 질문에서 추가 확인해요.</p>
        <div className="form-field">
          <label htmlFor="district">서울 거주 자치구</label>
          <select id="district" value={draft.district} onChange={event => change("district", event.target.value)} aria-invalid={Boolean(errors.district)}>
            <option value="">입력 안 함</option>{SEOUL_DISTRICTS.map(district => <option key={district} value={district}>{district}</option>)}
          </select>
          <p className="field-help">주민등록상 주소를 선택해주세요.</p>
          {errors.district && <p className="field-error">{errors.district}</p>}
        </div>
        <div className="form-field">
          <label htmlFor="employmentStatus">주된 취업상태</label>
          <select id="employmentStatus" value={draft.employmentStatus} onChange={event => change("employmentStatus", event.target.value)} aria-invalid={Boolean(errors.employmentStatus)}>
            <option value="">입력 안 함</option>{EMPLOYMENT_OPTIONS.map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
          </select>
          {errors.employmentStatus && <p className="field-error">{errors.employmentStatus}</p>}
        </div>
      </details>
      <div className="form-actions">
        <button type="submit" className="button-primary">조건 반영</button>
        <button type="button" className="text-button" onClick={reset}>입력 조건 지우기</button>
      </div>
    </form>
    </details>
    <p role="status" className="form-notice">{notice}</p>
    <p className="data-retention-note">조건 반영 시 입력 내용을 전송해 비교에 사용해요. 계정 저장은 별도이며, 저장하지 않은 입력은 새로고침하면 지워져요.</p>
    <details><summary>조건 저장·불러오기</summary>
      <ConditionMemberControls input={reflected && Object.keys(input).length ? input : undefined} prepareLoad={prepareLoad} onSuggestBirthDate={suggestBirthDate} />
    </details>
    <PolicyCheckResults input={input} />
  </section>;
}
