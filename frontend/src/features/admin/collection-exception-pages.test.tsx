import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import CollectionPage from "@/app/admin/collection-exceptions/page";
import CollectionDetailPage from "@/app/admin/collection-exceptions/[runId]/[itemIndex]/page";
import PageFailuresPage from "@/app/admin/collection-exceptions/pages/page";
import { loadCollectionException, loadCollectionExceptions, loadCollectionPageFailures, type ExceptionDetail, type PageFailureList } from "./load-collection-exceptions";

vi.mock("./load-collection-exceptions", async importOriginal => ({
  ...await importOriginal<typeof import("./load-collection-exceptions")>(),
  loadCollectionException: vi.fn(), loadCollectionExceptions: vi.fn(), loadCollectionPageFailures: vi.fn(),
}));
afterEach(() => vi.resetAllMocks());
const run = "10000000-0000-0000-0000-000000000001";
const detail: ExceptionDetail = {
  item: { runId: run, itemIndex: 0, pageNumber: 2, outcome: "INVALID_ITEM", attempts: 2,
    lastAttemptAt: "2026-09-07T00:00:00Z", policyNumber: "123" },
  rawPolicyJson: '{"plcyNo":9007199254740993,"plcyNm":"<script>unsafe()</script>"}',
  currentPolicy: { policyNumber: "123", revision: 3, collectedAt: "2026-09-07T00:00:00Z",
    content: { title: "현재 공개 제목", description: "공개 안내", organization: "운영 기관", category: "교육",
      applicationPeriod: "상시", sections: [{ title: "지원 내용", text: "확인된 지원 내용" }],
      links: [], regionCodes: [], sourceModifiedAtText: "" } },
};

describe("관리자 수집 예외 화면", () => {
  it("페이지 실패의 HTTP 상태·응답 보관 여부를 구분하고 전용 목록의 페이지를 유지한다", async () => {
    const item: PageFailureList["items"][number] = { runId: run, pageNumber: 7, state: "FETCH_FAILED", reason: "HTTP_ERROR",
      httpStatus: 429, startedAt: "2026-09-07T00:00:00Z", dispatchedAt: null, receivedAt: null, responseStored: false };
    vi.mocked(loadCollectionPageFailures).mockResolvedValue({ status: "available", data: { items: [item,
      { ...item, runId: "20000000-0000-0000-0000-000000000002", state: "INVALID_RESPONSE", reason: "INVALID_LIST_RESPONSE",
        httpStatus: null, receivedAt: item.startedAt, responseStored: true }], page: 2, pageSize: 20, hasNext: true } });
    const html = renderToStaticMarkup(await PageFailuresPage({ searchParams: Promise.resolve({ page: "2" }) }));
    expect(loadCollectionPageFailures).toHaveBeenCalledWith(2);
    expect(html).toContain("요청 한도 초과");
    expect(html).toContain("HTTP 429");
    expect(html).toContain("보관된 응답 없음");
    expect(html).toContain("응답 보관됨");
    expect(html).toContain("보관된 응답을 검토한 뒤 재처리할 수 있습니다.");
    expect(html).toContain('href="/admin/collection-exceptions/pages?page=3"');
    expect(html).toContain('href="/admin/collection-exceptions/pages?page=1"');
    expect(html).toContain('href="/admin/collection-exceptions"');
    expect(html).not.toContain("실패 시각");
  });

  it("페이지 수집 실패의 빈 뒷 페이지와 조회 장애를 구분한다", async () => {
    vi.mocked(loadCollectionPageFailures).mockResolvedValue({ status: "available", data: { items: [], page: 2, pageSize: 20, hasNext: false } });
    const props = { searchParams: Promise.resolve({ page: "2" }) };
    const empty = renderToStaticMarkup(await PageFailuresPage(props));
    expect(empty).toContain("이 페이지에 남은 수집 실패가 없습니다");
    expect(empty).toContain('href="/admin/collection-exceptions/pages"');
    vi.mocked(loadCollectionPageFailures).mockResolvedValue({ status: "unavailable" });
    const failed = renderToStaticMarkup(await PageFailuresPage(props));
    expect(failed).toContain("수집 예외를 불러오지 못했습니다");
    expect(failed).toContain('href="/admin/collection-exceptions/pages?page=2"');
    expect(failed).not.toContain("이 페이지에 남은 수집 실패가 없습니다");
  });
  it("목록의 페이지를 상세와 이전·다음 이동에 유지한다", async () => {
    vi.mocked(loadCollectionExceptions).mockResolvedValue({ status: "available", data: {
      items: [detail.item], page: 2, pageSize: 20, hasNext: true,
    } });
    const html = renderToStaticMarkup(await CollectionPage({ searchParams: Promise.resolve({ page: "2" }) }));
    expect(loadCollectionExceptions).toHaveBeenCalledWith(2);
    expect(html).toContain(`href="/admin/collection-exceptions/${run}/0?page=2"`);
    expect(html).toContain('href="/admin/collection-exceptions?page=1"');
    expect(html).toContain('href="/admin/collection-exceptions?page=3"');
    expect(html).toContain("항목 검증 실패");
    expect(html).not.toContain("unsafe()");
  });

  it("상세 원본의 큰 숫자를 보존하고 HTML은 실행하지 않는 텍스트로 렌더링한다", async () => {
    vi.mocked(loadCollectionException).mockResolvedValue({ status: "available", data: detail });
    const html = renderToStaticMarkup(await CollectionDetailPage({ params: Promise.resolve({ runId: run, itemIndex: "0" }), searchParams: Promise.resolve({ page: "2" }) }));
    expect(html).toContain('href="/admin/collection-exceptions?page=2"');
    expect(html).toContain("현재 공개 제목");
    expect(html).toContain("확인된 지원 내용");
    expect(html).toContain("9007199254740993");
    expect(html).toContain("&lt;script&gt;unsafe()&lt;/script&gt;");
    expect(html).not.toContain("<script>");
    expect(html).toContain('href="/policies/123"');
  });

  it("정책번호가 없을 때 공개 내용을 임의로 연결하지 않는다", async () => {
    vi.mocked(loadCollectionException).mockResolvedValue({ status: "available", data: {
      ...detail, item: { ...detail.item, policyNumber: null }, currentPolicy: null,
    } });
    const html = renderToStaticMarkup(await CollectionDetailPage({ params: Promise.resolve({ runId: run, itemIndex: "0" }), searchParams: Promise.resolve({}) }));
    expect(html).toContain("정책번호를 확인할 수 없어 공개 내용과 연결하지 않았습니다.");
    expect(html).not.toContain('href="/policies/123"');
  });

  it.each([
    ["unauthenticated", "관리자 계정으로 로그인하세요"],
    ["forbidden", "관리자 권한이 없습니다"],
    ["unavailable", "수집 예외를 불러오지 못했습니다"],
    ["missing", "현재 실패 목록에 없는 항목입니다"],
  ] as const)("%s 상태를 빈 실패 목록과 구분한다", async (status, title) => {
    vi.mocked(loadCollectionExceptions).mockResolvedValue({ status });
    const html = renderToStaticMarkup(await CollectionPage({ searchParams: Promise.resolve({ page: "2" }) }));
    expect(html).toContain(title);
    expect(html).not.toContain("확인할 실패 항목이 없습니다");
    if (status === "unauthenticated") expect(html).toContain('href="/login?next=admin"');
    if (status === "unavailable") expect(html).toContain('href="/admin/collection-exceptions?page=2"');
  });

  it("재처리로 비어 있는 뒷 페이지에서 첫 페이지로 돌아간다", async () => {
    vi.mocked(loadCollectionExceptions).mockResolvedValue({ status: "available", data: {
      items: [], page: 2, pageSize: 20, hasNext: false,
    } });
    const html = renderToStaticMarkup(await CollectionPage({ searchParams: Promise.resolve({ page: "2" }) }));
    expect(html).toContain("이 페이지에 남은 실패 항목이 없습니다");
    expect(html).toContain("첫 페이지 보기");
  });
});
