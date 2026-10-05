import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { components } from "@/generated/policy-api";
import HomePage from "./page";
import { loadPolicies } from "./policies/load-policies";

vi.mock("./policies/load-policies", () => ({ loadPolicies: vi.fn() }));
afterEach(() => { vi.resetAllMocks(); });

const policy: components["schemas"]["PolicySummary"] = {
  policyNumber: "123", title: "청년 주거 지원", description: "지원 안내", category: "주거",
  organization: "시험 기관", applicationPeriod: "20260707 ~ 20261231", collectedAt: "2026-09-05T01:00:00Z",
  questionnaireAvailable: false,
  recruitment: { status: "OPEN", explanation: "접수 기간이에요.", evaluatedAt: "2026-10-05T00:00:00Z", deadlineOnSeoul: "2026-11-17", daysUntilDeadline: 43 },
};

describe("홈의 접수 중인 정책", () => {
  it("접수 기간인 실제 정책을 최대 4건 보여주고 전체 접수 중 목록으로 연결한다", async () => {
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [policy], page: 1, pageSize: 4, total: 5, hasNext: true } });
    const html = renderToStaticMarkup(await HomePage());
    expect(loadPolicies).toHaveBeenCalledWith("", 1, false, "OPEN", 4);
    expect(html).toContain("지금 접수 중인 정책");
    expect(html).toContain('href="/policies/123"');
    expect(html).toContain("2026.07.07 ~ 2026.12.31");
    expect(html).toContain('href="/policies?recruitmentStatus=OPEN"');
    expect(html).toContain("5건 모두 보기");
    expect(html).toContain("D-43");
    expect(html).toContain('href="/policies?category=JOB"');
    expect(html).toContain('href="/policies?category=PARTICIPATION"');
  });

  it("접수 기간인 정책이 없으면 그 사실과 상시 모집 목록을 안내한다", async () => {
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [], page: 1, pageSize: 4, total: 0, hasNext: false } });
    const html = renderToStaticMarkup(await HomePage());
    expect(html).toContain("지금 접수 기간인 정책이 없어요");
    expect(html).toContain('href="/policies?recruitmentStatus=ROLLING"');
    expect(html).not.toContain("모두 보기");
  });

  it("조회에 실패하면 빈 결과로 표시하지 않고 영역을 숨긴다", async () => {
    vi.mocked(loadPolicies).mockResolvedValue({ status: "unavailable" });
    const html = renderToStaticMarkup(await HomePage());
    expect(html).not.toContain("지금 접수 중인 정책");
    expect(html).not.toContain("정책이 없어요");
    expect(html).toContain('href="/conditions"');
  });
});
