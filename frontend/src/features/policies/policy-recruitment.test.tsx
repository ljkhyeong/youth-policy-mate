import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { deadlineLabel, PolicyRecruitment } from "./policy-recruitment";

describe("공개 정책의 접수 상태", () => {
  it("접수 기간을 신청 가능으로 바꾸지 않고 서버의 시간 제한 설명을 표시한다", () => {
    const html = renderToStaticMarkup(<PolicyRecruitment recruitment={{status:"OPEN", explanation:"마감 시각은 공식 안내를 확인해주세요.", evaluatedAt:"2026-09-06T00:00:00Z", deadlineOnSeoul:null, daysUntilDeadline:null}} />);
    expect(html).toContain("접수 기간");
    expect(html).toContain("마감 시각은 공식 안내를 확인해주세요.");
    expect(html).not.toContain("신청 가능");
  });
  it("미확인을 마감으로 표시하지 않는다", () => {
    const html = renderToStaticMarkup(<PolicyRecruitment recruitment={{status:"UNKNOWN", explanation:"복수 회차 안내를 확인해주세요.", evaluatedAt:"2026-09-06T00:00:00Z", deadlineOnSeoul:null, daysUntilDeadline:null}} />);
    expect(html).toContain("기간 미확인");
    expect(html).toContain("복수 회차 안내를 확인해주세요.");
    expect(html).not.toContain('data-status="CLOSED"');
  });
  it("접수 기간인 정책에만 서버가 계산한 남은 일수를 표시한다", () => {
    expect(deadlineLabel({ status: "OPEN", daysUntilDeadline: 43 })).toBe("D-43");
    expect(deadlineLabel({ status: "OPEN", daysUntilDeadline: 0 })).toBe("오늘 마감");
    for (const recruitment of [{ status: "OPEN" as const, daysUntilDeadline: null }, { status: "OPEN" as const }, { status: "BEFORE_OPENING" as const, daysUntilDeadline: 10 },
      { status: "CLOSED" as const, daysUntilDeadline: -1 }, { status: "OPEN" as const, daysUntilDeadline: -1 }]) expect(deadlineLabel(recruitment)).toBeNull();
    const html = renderToStaticMarkup(<PolicyRecruitment recruitment={{status:"OPEN", explanation:"접수 기간이에요.", evaluatedAt:"2026-10-05T00:00:00Z", deadlineOnSeoul:"2026-11-17", daysUntilDeadline:43}} />);
    expect(html).toContain("D-43");
    expect(html).toContain("마감까지 43일 · 2026.11.17 마감");
  });
  it("새 필드가 없는 이전 서버 응답은 남은 일수 없이 접수 상태만 표시한다", () => {
    const previous = { status: "OPEN", explanation: "접수 기간이에요.", evaluatedAt: "2026-10-05T00:00:00Z" } as unknown as Parameters<typeof PolicyRecruitment>[0]["recruitment"];
    const html = renderToStaticMarkup(<PolicyRecruitment recruitment={previous} />);
    expect(html).toContain("접수 기간");
    expect(html).not.toContain("recruitment-dday");
  });
});
