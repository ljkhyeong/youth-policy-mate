"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { SavedPolicyChanges } from "./saved-policy-changes";
import { PolicyRecruitment, RecruitmentOptions, type RecruitmentFilter } from "@/features/policies/policy-recruitment";
import type { SavedPolicies } from "./member-api";

export function MemberPolicyList({ policies, calendar, query, changedOnly, filter, onSearch, onFilterChange, busy, onRemove }: {
  policies: SavedPolicies["items"]; calendar: boolean; filter: RecruitmentFilter;
  query: string; changedOnly: boolean; onSearch: (search: { q: string; changed: boolean; status: RecruitmentFilter }) => void;
  onFilterChange: (value: RecruitmentFilter) => void; busy: boolean; onRemove: (number: string) => void;
}) {
  const [search, setSearch] = useState({ applied: query, draft: query });
  const queryInput = useRef<HTMLInputElement>(null);
  if (search.applied !== query) setSearch({ applied: query, draft: query });
  if (!policies.length) return <section className="member-panel"><h2>저장한 정책이 아직 없어요</h2><p>정책 상세에서 저장 버튼을 눌러주세요.</p><Link href="/policies" className="button-primary">정책 찾기</Link></section>;
  const term = query.toLowerCase();
  const visible = policies.filter(policy => (!calendar || !filter || policy.recruitment.status === filter)
    && (!changedOnly || policy.savedRevision !== policy.currentRevision)
    && policy.title.toLowerCase().includes(term));
  const resetSearch = () => {
    setSearch({ applied: query, draft: "" });
    queryInput.current?.focus();
    onSearch({ q: "", changed: false, status: calendar ? "" : filter });
  };
  return <>
    <section className="member-panel" aria-label="저장한 정책 검색">
      <form className="policy-search member-policy-search" role="search" aria-label="저장한 정책명 검색" onSubmit={event => {
        event.preventDefault(); onSearch({ q: search.draft, changed: changedOnly, status: filter });
      }}>
        <label htmlFor="saved-policy-query" className="sr-only">저장한 정책명</label>
        <input ref={queryInput} id="saved-policy-query" type="search" maxLength={80} value={search.draft} placeholder="정책명 검색"
          onChange={event => setSearch({ applied: query, draft: event.target.value })} />
        <button type="submit" className="button-primary">검색</button>
        <button type="button" className="button-secondary" onClick={resetSearch}>초기화</button>
      </form>
      <label className="member-change-filter"><input type="checkbox" checked={changedOnly}
        onChange={event => onSearch({ q: search.draft, changed: event.target.checked, status: filter })} />변경된 정책만 보기</label>
      <p className="field-help">저장 당시와 현재 공고의 개정이 다른 정책을 표시합니다.</p>
      {calendar && <div aria-label="마감 일정 검색">
        <div className="rule-review-field"><label htmlFor="calendar-status">접수 상태</label>
        <select className="member-calendar-filter" id="calendar-status" value={filter} onChange={event => onFilterChange(event.target.value as RecruitmentFilter)}><RecruitmentOptions /></select></div>
        <p className="field-help">가까운 마감순 · 마감된 정책은 마지막 · 접수 상태는 조회 시점 기준</p>
      </div>}
      <p role="status">{visible.length}건 / 저장한 정책 {policies.length}건{query && ` · 검색어: ${query}`}</p>
    </section>
    {visible.length === 0 && <section className="member-panel"><h2>검색 조건에 맞는 정책이 없어요</h2>
      <p>정책명을 바꾸거나 검색 조건을 초기화해주세요.</p>
      <button className="button-secondary" type="button" onClick={resetSearch}>검색 조건 초기화</button></section>}
    <div className="member-list">
      {visible.map(policy => <article className="member-panel" key={policy.policyNumber}>
        <PolicyRecruitment recruitment={policy.recruitment} compact />
        {calendar && policy.deadline.date && <p className="member-deadline">{policy.deadline.date} 마감</p>}
        <h2><Link href={`/policies/${policy.policyNumber}`}>{policy.title}</Link></h2>
        <p className="policy-period">신청기간: {policy.applicationPeriod}</p>
        <p>{policy.deadline.note}</p>
        {policy.savedRevision !== policy.currentRevision && <>
          <p className="member-change">저장한 뒤 정책 내용이 바뀌었어요. 최신 안내를 확인해주세요.</p>
          <SavedPolicyChanges key={`${policy.savedRevision}:${policy.currentRevision}`} policyNumber={policy.policyNumber} />
        </>}
        {calendar && policy.deadline.date && policy.recruitment.status !== "CLOSED" && <p className="field-help">마감 7·3·1일 전 ‘알림’ 탭에 안내해요. 지난 알림 날짜는 건너뛰어요.</p>}
        <button className="text-button" type="button" disabled={busy} onClick={() => onRemove(policy.policyNumber)}>저장 해제</button>
      </article>)}
    </div>
  </>;
}
