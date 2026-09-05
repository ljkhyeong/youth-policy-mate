import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { components } from "@/generated/policy-api";
import PoliciesPage from "./page";
import { loadPolicies } from "./load-policies";

vi.mock("./load-policies", () => ({ loadPolicies: vi.fn() }));
afterEach(() => vi.resetAllMocks());

const policy: components["schemas"]["PolicySummary"] = {
  policyNumber: "123", title: "시험 지원", description: "지원 안내", category: "교육",
  organization: "시험 기관", applicationPeriod: "공식 안내 확인", collectedAt: "2026-09-05T01:00:00Z",
  questionnaireAvailable: true,
};

describe("정책 목록의 공통요건 질문 탐색", () => {
  it("검색·페이지 이동에 필터를 유지하고 필터 전환은 첫 페이지로 돌아간다", async () => {
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [policy], page: 2, pageSize: 20, total: 41, hasNext: true } });
    const html = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({ q: "시험&지원", page: "2", questionsOnly: "true" }) }));
    expect(loadPolicies).toHaveBeenCalledWith("시험&지원", 2, true);
    expect(html).toContain('type="hidden" name="questionsOnly" value="true"');
    const query = encodeURIComponent("시험&지원");
    expect(html).toContain(`href="/policies?q=${query}&amp;page=3&amp;questionsOnly=true"`);
    expect(html).toContain(`href="/policies?q=${query}&amp;page=1"`);
    expect(html).toContain(`href="/policies?q=${query}&amp;page=1&amp;questionsOnly=true"`);
    expect(html).toContain('href="/policies/123#policy-questions"');
    expect(html).toContain("41건");
  });

  it("질문이 없는 정책에는 질문 이동을 표시하지 않고 전체 조회를 유지한다", async () => {
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [{ ...policy, questionnaireAvailable: false }], page: 1, pageSize: 20, total: 1, hasNext: false } });
    const html = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({}) }));
    expect(loadPolicies).toHaveBeenCalledWith("", 1, false);
    expect(html).toContain('href="/policies/123"');
    expect(html).not.toContain('href="/policies/123#policy-questions"');
    expect(html).not.toContain('type="hidden" name="questionsOnly"');
  });

  it("질문 필터의 빈 검색 결과를 자격 불충족으로 안내하지 않는다", async () => {
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [], page: 1, pageSize: 20, total: 0, hasNext: false } });
    const html = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({ questionsOnly: "true" }) }));
    expect(html).toContain("검색 조건에 맞는 질문 제공 정책이 없어요");
    expect(html).toContain("검색·필터 초기화");
    expect(html).toContain("최종 자격과 접수 기간은 별도로 확인해주세요");
  });

  it("목록이 줄어든 뒤의 빈 페이지에서는 검색과 필터를 유지해 처음으로 돌아간다", async () => {
    vi.mocked(loadPolicies).mockResolvedValue({ status: "available", data: { items: [], page: 2, pageSize: 20, total: 1, hasNext: false } });
    const html = renderToStaticMarkup(await PoliciesPage({ searchParams: Promise.resolve({ q: "시험", page: "2", questionsOnly: "true" }) }));
    expect(html).toContain("첫 페이지 보기");
    expect(html).toContain(`href="/policies?q=${encodeURIComponent("시험")}&amp;page=1&amp;questionsOnly=true"`);
    expect(html).not.toContain("검색 조건에 맞는 질문 제공 정책이 없어요");
  });
});
