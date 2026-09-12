"use client";

import Link from "next/link";
import { SavedPolicyChanges } from "./saved-policy-changes";
import { PolicyRecruitment, RecruitmentOptions, type RecruitmentFilter } from "@/features/policies/policy-recruitment";
import type { SavedPolicies } from "./member-api";

export function MemberPolicyList({ policies, calendar, filter, onFilterChange, busy, onRemove }: {
  policies: SavedPolicies["items"]; calendar: boolean; filter: RecruitmentFilter;
  onFilterChange: (value: RecruitmentFilter) => void; busy: boolean; onRemove: (number: string) => void;
}) {
  if (!policies.length) return <section className="member-panel"><h2>저장한 정책이 아직 없어요</h2><p>정책 상세에서 저장 버튼을 눌러주세요.</p><Link href="/policies" className="button-primary">정책 찾기</Link></section>;
  const visible = calendar && filter ? policies.filter(policy => policy.recruitment.status === filter) : policies;
  return <>
    {calendar && <section className="member-panel" aria-label="마감 일정 검색">
      <div className="rule-review-field"><label htmlFor="calendar-status">접수 상태</label>
        <select className="member-calendar-filter" id="calendar-status" value={filter} onChange={event => onFilterChange(event.target.value as RecruitmentFilter)}><RecruitmentOptions /></select></div>
      <p className="field-help">가까운 마감순이며, 마감된 정책은 마지막에 표시합니다.</p>
      <p role="status">{visible.length}건 · 접수 상태는 조회 시점 기준입니다.</p>
    </section>}
    {visible.length === 0 && <section className="member-panel"><h2>선택한 접수 상태의 정책이 없습니다</h2>
      <button className="button-secondary" type="button" onClick={() => onFilterChange("")}>전체 일정 보기</button></section>}
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
