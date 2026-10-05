"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { SavedPolicyChanges } from "./saved-policy-changes";
import { RecruitmentBadge, RecruitmentExplanation, RecruitmentOptions, type RecruitmentFilter } from "@/features/policies/policy-recruitment";
import type { SavedPolicies } from "./member-api";
import { formatPolicyPeriod } from "@/features/policies/policy-period";

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
      {visible.map(policy => <article className="member-panel saved-policy" data-dated={calendar && policy.deadline.date ? true : undefined} key={policy.policyNumber}>
        {calendar && policy.deadline.date && <DeadlineDate date={policy.deadline.date} />}
        <div className="saved-policy-body">
          <div className="policy-card-top"><RecruitmentBadge recruitment={policy.recruitment} /></div>
          <h2><Link href={`/policies/${policy.policyNumber}`}>{policy.title}</Link></h2>
          <p className="policy-period"><strong>신청기간</strong><span>{formatPolicyPeriod(policy.applicationPeriod)}</span></p>
          <p className="saved-policy-note">{policy.deadline.note}</p>
          {policy.savedRevision !== policy.currentRevision && <>
            <p className="member-change">저장한 뒤 정책 내용이 바뀌었어요. 최신 안내를 확인해주세요.</p>
            <SavedPolicyChanges key={`${policy.savedRevision}:${policy.currentRevision}`} policyNumber={policy.policyNumber} />
          </>}
          {calendar && policy.deadline.date && policy.recruitment.status !== "CLOSED" && <p className="field-help">마감 7·3·1일 전 ‘알림’ 탭에 안내해요. 지난 알림 날짜는 건너뛰어요.</p>}
          <RecruitmentExplanation recruitment={policy.recruitment} />
          <div className="policy-card-actions">
            <Link href={`/policies/${policy.policyNumber}`} className="text-link" aria-label={`${policy.title} 지원 내용 보기`}>지원 내용 보기</Link>
            <button className="text-button" type="button" disabled={busy} onClick={() => onRemove(policy.policyNumber)}>저장 해제</button>
          </div>
        </div>
      </article>)}
    </div>
  </>;
}

const weekdays = ["일", "월", "화", "수", "목", "금", "토"];

// 날짜만 있는 마감을 브라우저 시간대로 옮기지 않도록 날짜 문자열에서 요일을 계산한다.
function DeadlineDate({ date }: { date: string }) {
  const [year, month, day] = date.split("-").map(Number);
  const weekday = weekdays[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
  return <p className="member-deadline"><span>{year}</span><strong>{`${date.slice(5, 7)}.${date.slice(8, 10)}`}</strong><span>{weekday}요일 마감</span></p>;
}
