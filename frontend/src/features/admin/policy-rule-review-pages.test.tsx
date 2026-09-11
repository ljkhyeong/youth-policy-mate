import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import RuleReviewsPage from "@/app/admin/collection-exceptions/rules/page";
import RuleReviewPage from "@/app/admin/collection-exceptions/rules/[policyNumber]/page";
import { loadRuleReview, loadRuleReviews, type RuleReviewDetail } from "./load-collection-exceptions";

vi.mock("./load-collection-exceptions", async original => ({
  ...await original<typeof import("./load-collection-exceptions")>(), loadRuleReview: vi.fn(), loadRuleReviews: vi.fn(),
}));
afterEach(() => vi.resetAllMocks());

const number = "99990000000000000001";
const at = "2026-09-12T00:00:00Z";
const item: RuleReviewDetail["item"] = { policyNumber: number, title: "원문이 바뀐 정책", revision: 3, status: "SOURCE_CHANGED", collectedAt: at, draftCount: 1 };
const content: RuleReviewDetail["currentPolicy"]["content"] = { title: item.title, description: "현재 안내", organization: "기관", category: "교육",
  applicationPeriod: "기간 확인", sections: [], links: [], regionCodes: [], sourceModifiedAtText: "" };
const detail: RuleReviewDetail = {
  item, checkedAt: at, rawPolicyJson: '{"plcyNm":"<script>원문</script>"}',
  currentPolicy: { policyNumber: number, revision: 3, content, collectedAt: at, sourceCapturedAt: at, correctionId: null,
    previousRevision: { revision: 2, sourceCapturedAt: at, content: { ...content, title: "직전 제목" }, correctionId: null } },
  versions: [{ id: "10000000-0000-0000-0000-000000000001", ruleVersion: "review-v1", state: "CURRENT", sourceMatches: false,
    validFrom: at, validUntil: "2027-01-01T00:00:00Z", scope: "2026년 공고", reason: "공식 조건 확인", sourceUrl: "https://example.com/notice",
    questions: [{ id: "age", label: "연령 조건", help: "기준일 확인", options: [{ value: "YES", label: "해당" }] }],
    remainingChecks: ["증빙 확인"], createdAt: at, changeReason: "연간 조건 등록" }],
};

describe("관리자 조건 검토 화면", () => {
  it("검색·상태·페이지를 상세 이동과 목록 이동에 유지한다", async () => {
    vi.mocked(loadRuleReviews).mockResolvedValue({ status: "available", data: { items: [item], page: 2, pageSize: 20, total: 41, hasNext: true, checkedAt: at } });
    const params = { page: "2", filter: "SOURCE_CHANGED", query: "청년 & 지원" };
    const list = renderToStaticMarkup(await RuleReviewsPage({ searchParams: Promise.resolve(params) }));
    expect(loadRuleReviews).toHaveBeenCalledWith(2, params.filter, params.query);
    expect(list).toContain("검색 결과 41건");
    expect(list).toContain("초안 1개");
    expect(list).toContain(`${number}?page=2&amp;filter=SOURCE_CHANGED&amp;query=`);
    expect(list).toContain("?page=3&amp;filter=SOURCE_CHANGED&amp;query=");
    vi.mocked(loadRuleReview).mockResolvedValue({ status: "available", data: detail });
    const view = renderToStaticMarkup(await RuleReviewPage({ params: Promise.resolve({ policyNumber: number }), searchParams: Promise.resolve(params) }));
    expect(view).toContain("rules?page=2&amp;filter=SOURCE_CHANGED&amp;query=");
    expect(view).toContain("직전 제목");
    expect(view).toContain("질문 제공을 중단했습니다");
    expect(view).toContain("현재 지정 버전");
    expect(view).toContain("현재 원문과 다름 · 재검토 필요");
    expect(view).toContain("직전 공개 버전 기준");
    expect(view).toContain("증빙 확인");
    expect(view).toContain("&lt;script&gt;원문&lt;/script&gt;");
    expect(view).not.toContain("<script>");
  });

  it("조회 실패·권한 오류를 검토할 정책 없음으로 표시하지 않는다", async () => {
    for (const [status, message] of [["unavailable", "불러오지 못했습니다"], ["unauthenticated", "관리자 계정으로 로그인하세요"], ["forbidden", "관리자 권한이 없습니다"]] as const) {
      vi.mocked(loadRuleReviews).mockResolvedValue({ status });
      const html = renderToStaticMarkup(await RuleReviewsPage({ searchParams: Promise.resolve({}) }));
      expect(html).toContain(message);
      expect(html).not.toContain("조회 조건에 맞는 정책이 없습니다");
    }
    vi.mocked(loadRuleReview).mockResolvedValue({ status: "missing" });
    const html = renderToStaticMarkup(await RuleReviewPage({ params: Promise.resolve({ policyNumber: "invalid" }), searchParams: Promise.resolve({}) }));
    expect(html).toContain("검토할 정책을 찾을 수 없습니다");
    expect(html).not.toContain("/rules/invalid");
  });

  it("미등록 정책과 빈 뒷 페이지에서 다음 행동을 안내한다", async () => {
    vi.mocked(loadRuleReview).mockResolvedValue({ status: "available", data: { ...detail, item: { ...item, status: "MISSING" }, versions: [] } });
    const view = renderToStaticMarkup(await RuleReviewPage({ params: Promise.resolve({ policyNumber: number }), searchParams: Promise.resolve({}) }));
    expect(view).toContain("등록된 질문이 없습니다");
    expect(view).toContain("공고의 조건과 예외를 확인해 질문 초안을 등록하세요");
    vi.mocked(loadRuleReviews).mockResolvedValue({ status: "available", data: { items: [], page: 2, pageSize: 20, total: 1, hasNext: false, checkedAt: at } });
    const list = renderToStaticMarkup(await RuleReviewsPage({ searchParams: Promise.resolve({ page: "2", filter: "bad" }) }));
    expect(loadRuleReviews).toHaveBeenCalledWith(2, "REVIEW", "");
    expect(list).toContain("이 페이지에 정책이 없습니다");
    expect(list).toContain("첫 페이지 보기");
    expect(list).not.toContain(">다음</a>");
  });
});
