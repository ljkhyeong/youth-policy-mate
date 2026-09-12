import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { MemberPolicyList } from "./member-policy-list";
import type { SavedPolicies } from "./member-api";

const at = "2026-09-12T00:00:00Z";
const item: SavedPolicies["items"][number] = {
  policyNumber: "99990000000000000001", title: "저장한 정책", savedRevision: 1, currentRevision: 2, savedAt: at,
  applicationPeriod: "상시 접수", deadline: { date: null, note: "상시 접수라 마감 알림을 제공하지 않아요." },
  recruitment: { status: "ROLLING", explanation: "상시 접수 안내", evaluatedAt: at },
};
const base = { calendar: true, filter: "" as const, onFilterChange: vi.fn(), busy: false, onRemove: vi.fn() };

describe("내 정책 마감 일정", () => {
  it("상시 접수에 가짜 마감일이나 날짜 확인 필요 안내를 만들지 않는다", () => {
    const html = renderToStaticMarkup(<MemberPolicyList {...base} policies={[item]} />);
    expect(html).toContain('data-status="ROLLING"');
    expect(html).toContain("상시 접수라 마감 알림을 제공하지 않아요");
    expect(html).not.toContain("마감일 확인 필요");
    expect(html).not.toContain("member-deadline");
    expect(html).toContain("저장한 뒤 정책 내용이 바뀌었어요");
  });

  it("서버의 종료 상태와 날짜를 그대로 표시하고 지난 마감의 알림을 약속하지 않는다", () => {
    const closed = { ...item, title: "종료 정책", deadline: { date: "2026-09-11", note: "공식 안내 확인" },
      recruitment: { status: "CLOSED" as const, explanation: "공고의 마감 시각이 지났어요.", evaluatedAt: at } };
    const html = renderToStaticMarkup(<MemberPolicyList {...base} policies={[item, closed]} filter="CLOSED" />);
    expect(html).toContain("2026-09-11 마감");
    expect(html).toContain("공고의 마감 시각이 지났어요");
    expect(html).not.toContain("상시 접수라 마감 알림");
    expect(html).not.toContain("지난 알림 날짜는 건너뛰어요");
    expect(html).toContain("1건 · 접수 상태는 조회 시점 기준입니다");
  });

  it("필터 결과 없음과 저장한 정책 없음을 구분하고 관심 정책 탭에는 일정 필터를 적용하지 않는다", () => {
    const filtered = renderToStaticMarkup(<MemberPolicyList {...base} policies={[item]} filter="OPEN" />);
    expect(filtered).toContain("선택한 접수 상태의 정책이 없습니다");
    expect(filtered).toContain("전체 일정 보기");
    expect(filtered).not.toContain("저장한 정책이 아직 없어요");
    const empty = renderToStaticMarkup(<MemberPolicyList {...base} policies={[]} />);
    expect(empty).toContain("저장한 정책이 아직 없어요");
    const saved = renderToStaticMarkup(<MemberPolicyList {...base} policies={[item]} calendar={false} filter="OPEN" busy />);
    expect(saved).toContain("저장한 정책");
    expect(saved).not.toContain('id="calendar-status"');
    expect(saved).toContain('disabled=""');
  });
});
