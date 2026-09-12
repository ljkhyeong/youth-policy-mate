import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import AiRunsPage from "@/app/admin/collection-exceptions/ai/page";
import { loadAiRuns, type AiRunPage } from "./load-collection-exceptions";

vi.mock("./load-collection-exceptions", async original => ({
  ...await original<typeof import("./load-collection-exceptions")>(), loadAiRuns: vi.fn(),
}));
afterEach(() => vi.resetAllMocks());
const at = "2026-09-12T00:00:00Z";
const item: AiRunPage["items"][number] = {
  requestId: "10000000-0000-0000-0000-000000000001", policyNumber: "99990000000000000001", title: "<script>청년 지원</script>",
  revision: 1, currentRevision: 2, sourceMatches: false, latestRequest: false, attempt: 2, state: "LEASE_EXPIRED",
  startedAt: at, finishedAt: null, resultCode: null, candidateStatus: "DRAFT_CREATED", reservationPhase: "OUTCOME_UNKNOWN", responseStored: true,
};
const data: AiRunPage = { items: [item], page: 2, pageSize: 20, total: 41, hasNext: true, checkedAt: at, automationEnabled: false };

describe("관리자 AI 추출 화면", () => {
  it("검색 조건을 유지하고 마지막 시도와 현재 결과·원문 상태를 구분한다", async () => {
    vi.mocked(loadAiRuns).mockResolvedValue({ status: "available", data });
    const html = renderToStaticMarkup(await AiRunsPage({ searchParams: Promise.resolve({ page: "2", filter: "LEASE_EXPIRED", query: "청년 & 지원" }) }));
    expect(loadAiRuns).toHaveBeenCalledWith(2, "LEASE_EXPIRED", "청년 & 지원");
    for (const text of ["자동 실행 꺼짐", "검색 결과 41건", "실행 시간 초과", "시도 2회", "현재 원문과 다릅니다", "이후 추출 요청이 있습니다",
      "현재 추출 결과: 초안 생성", "결과 미확인 · 예산 예약 유지", "초안 검토", "보관됨"]) expect(html).toContain(text);
    expect(html).toContain(`/rules/${item.policyNumber}`);
    expect(html).toContain("?page=3&amp;filter=LEASE_EXPIRED&amp;query=");
    expect(html).toContain("&lt;script&gt;청년 지원&lt;/script&gt;");
    expect(html).not.toContain("<script>");
    expect(html).not.toContain("재호출</button>");
  });

  it("조회 실패와 권한 오류에서 자동 실행 설정이나 빈 결과를 추정하지 않는다", async () => {
    for (const [status, text] of [["unavailable", "불러오지 못했습니다"], ["unauthenticated", "관리자 계정으로 로그인하세요"], ["forbidden", "관리자 권한이 없습니다"], ["invalid", "검색어·상태·페이지를 확인해주세요"]] as const) {
      vi.mocked(loadAiRuns).mockResolvedValue({ status });
      const html = renderToStaticMarkup(await AiRunsPage({ searchParams: Promise.resolve({}) }));
      expect(html).toContain(text);
      expect(html).not.toContain("조회 조건에 맞는 추출 내역이 없습니다");
      expect(html).not.toContain("자동 실행 꺼짐");
    }
  });

  it("빈 뒷 페이지에서 검색 조건을 유지한 첫 페이지 이동을 제공한다", async () => {
    vi.mocked(loadAiRuns).mockResolvedValue({ status: "available", data: { ...data, items: [], hasNext: false, total: 1, automationEnabled: true } });
    const html = renderToStaticMarkup(await AiRunsPage({ searchParams: Promise.resolve({ page: "2", filter: "bad", query: "지원" }) }));
    expect(loadAiRuns).toHaveBeenCalledWith(2, "ALL", "지원");
    expect(html).toContain("자동 실행 켜짐");
    expect(html).toContain("AI 설정과 예산·일일 한도에 따라 처리합니다");
    expect(html).toContain("이 페이지에 추출 내역이 없습니다");
    expect(html).toContain("첫 페이지 보기");
    expect(html).not.toContain(">다음</a>");
  });
});
