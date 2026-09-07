import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import CollectionPage from "@/app/admin/collection-exceptions/page";
import CollectionDetailPage from "@/app/admin/collection-exceptions/[runId]/[itemIndex]/page";
import PageFailuresPage from "@/app/admin/collection-exceptions/pages/page";
import PolicyCorrectionsPage from "@/app/admin/collection-exceptions/corrections/page";
import CollectionReplaysPage from "@/app/admin/collection-exceptions/replays/page";
import { loadCollectionException, loadCollectionExceptions, loadCollectionPageFailures, loadCollectionReplays, loadPolicyCorrections, loadCorrectionPolicy, type ExceptionDetail, type CorrectionItem, type PageFailureList } from "./load-collection-exceptions";

vi.mock("./load-collection-exceptions", async importOriginal => ({
  ...await importOriginal<typeof import("./load-collection-exceptions")>(),
  loadCollectionException: vi.fn(), loadCollectionExceptions: vi.fn(), loadCollectionPageFailures: vi.fn(), loadCollectionReplays: vi.fn(), loadPolicyCorrections: vi.fn(), loadCorrectionPolicy: vi.fn(),
}));
afterEach(() => vi.resetAllMocks());
const run = "10000000-0000-0000-0000-000000000001";
const detail: ExceptionDetail = {
  item: { runId: run, itemIndex: 0, pageNumber: 2, outcome: "INVALID_ITEM", attempts: 2,
    lastAttemptAt: "2026-09-07T00:00:00Z", policyNumber: "123" },
  rawPolicyJson: '{"plcyNo":9007199254740993,"plcyNm":"<script>unsafe()</script>"}',
  currentPolicy: { policyNumber: "123", revision: 3, collectedAt: "2026-09-07T00:00:00Z",
    sourceCapturedAt: "2026-09-06T00:00:00Z", correctionId: null, previousRevision: null,
    content: { title: "현재 공개 제목", description: "공개 안내", organization: "운영 기관", category: "교육",
      applicationPeriod: "상시", sections: [{ title: "지원 내용", text: "확인된 지원 내용" }],
      links: [], regionCodes: [], sourceModifiedAtText: "" } },
};

describe("관리자 수집 예외 화면", () => {
  it("재처리 이력의 사유는 텍스트로 표시하고 반영하지 않은 결과에 개정을 붙이지 않는다", async () => {
    vi.mocked(loadCollectionReplays).mockResolvedValue({ status: "available", data: { page: 2, pageSize: 20, hasNext: true,
      items: [{ requestId: run, runId: run, itemIndex: 0, actorId: run, expectedAttempts: 1, attempt: 2,
        reason: "<script>사유</script>", outcome: "STALE", policyNumber: "123", policyRevision: null, processedAt: "2026-09-07T00:00:00Z" }] } });
    const html = renderToStaticMarkup(await CollectionReplaysPage({ searchParams: Promise.resolve({ page: "2" }) }));
    expect(loadCollectionReplays).toHaveBeenCalledWith(2);
    expect(html).toContain("최신 정책 유지");
    expect(html).toContain("반영 없음");
    expect(html).toContain("&lt;script&gt;사유&lt;/script&gt;");
    expect(html).not.toContain("<script>");
    expect(html).toContain('href="/admin/collection-exceptions/replays?page=3"');
    expect(html).toContain('href="/admin/collection-exceptions/replays?page=1"');
  });

  it("재처리 이력의 조회 장애를 빈 이력과 구분한다", async () => {
    vi.mocked(loadCollectionReplays).mockResolvedValue({ status: "unavailable" });
    const html = renderToStaticMarkup(await CollectionReplaysPage({ searchParams: Promise.resolve({ page: "2" }) }));
    expect(html).toContain("수집 예외를 불러오지 못했습니다");
    expect(html).not.toContain("재처리 이력이 없습니다");
    expect(html).toContain('href="/admin/collection-exceptions/replays?page=2"');
  });
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
    expect(html).toContain("비교할 이전 개정이 없습니다.");
  });

  it("변경된 필드를 이전·현재로 비교하고 같은 필드는 접어서 표시한다", async () => {
    const current = detail.currentPolicy!;
    vi.mocked(loadCollectionException).mockResolvedValue({ status: "available", data: { ...detail,
      currentPolicy: { ...current, previousRevision: { revision: 2, correctionId: null, sourceCapturedAt: "2026-09-05T00:00:00Z",
        content: { ...current.content, title: "이전 제목 <script>old()</script>",
          sections: [{ title: "지원 내용", text: "이전 지원 내용" }],
          links: [{ label: "이전 신청처", url: "https://example.org/old" }] } } },
    } });
    const html = renderToStaticMarkup(await CollectionDetailPage({ params: Promise.resolve({ runId: run, itemIndex: "0" }), searchParams: Promise.resolve({}) }));
    expect(html).toContain("변경된 항목 3개");
    expect(html).toContain("이전 · 개정 2");
    expect(html).toContain("현재 · 개정 3");
    expect(html).toContain("이전 제목 &lt;script&gt;old()&lt;/script&gt;");
    expect(html).not.toContain("<script>");
    expect(html).toContain("이전 지원 내용");
    expect(html).toContain("https://example.org/old");
    expect(html).toContain("내용 없음");
    expect(html).toContain('<details class="revision-unchanged"><summary>동일한 항목 6개</summary>');
    expect(html).toContain("이전 개정 2 · 원본 수집");
    expect(html).not.toContain("개정 적용 시각");
  });

  it("개정 번호가 달라도 표시 내용이 같으면 변경 없음을 안내한다", async () => {
    const current = detail.currentPolicy!;
    vi.mocked(loadCollectionException).mockResolvedValue({ status: "available", data: { ...detail,
      currentPolicy: { ...current, previousRevision: { revision: 2, correctionId: null, sourceCapturedAt: current.sourceCapturedAt, content: current.content } },
    } });
    const html = renderToStaticMarkup(await CollectionDetailPage({ params: Promise.resolve({ runId: run, itemIndex: "0" }), searchParams: Promise.resolve({}) }));
    expect(html).toContain("표시 항목의 변경이 없습니다.");
    expect(html).toContain("동일한 항목 9개");
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


describe("정책 보정 화면", () => {
  it("보정 충돌의 원본과 작업 사유를 안전하게 표시하고 함께 반영될 내용을 제공한다", async () => {
    const item: CorrectionItem = { id: run, policyNumber: "123", field: "TITLE", value: "보정 제목", reason: "<script>검토 사유</script>",
      actorId: run, requestedRevision: 1, appliedRevision: 2, createdAt: "2026-09-08T00:00:00Z", status: "CONFLICT",
      sourceValue: "이전 원본", reviewSnapshotId: 3, reviewValue: "새 원본", currentRevision: 2, reviewContent: detail.currentPolicy!.content,
      resolution: null, resolvedBy: null, resolvedReason: null, resolvedRevision: null, resolvedAt: null };
    vi.mocked(loadPolicyCorrections).mockResolvedValue({ status: "available", data: { items: [item], page: 1, pageSize: 20, hasNext: false } });
    vi.mocked(loadCorrectionPolicy).mockResolvedValue({ status: "available", data: { ...detail.currentPolicy!, correctionId: run } });
    const html = renderToStaticMarkup(await PolicyCorrectionsPage({ searchParams: Promise.resolve({ policyNumber: "123" }) }));
    expect(html).toContain("이미 보정이 적용되어 있습니다");
    expect(html).toContain("새 원본 확인 필요");
    expect(html).toContain("반영할 원본 내용 확인");
    expect(html).toContain("확인된 지원 내용");
    expect(html).toContain("&lt;script&gt;검토 사유&lt;/script&gt;");
    expect(html).not.toContain("<script>");
    expect(html).toContain("보정 유지");
    expect(html).toContain("보정 해제 · 원본 적용");
  });

  it("보정 조회에 실패하면 입력 폼과 빈 이력을 표시하지 않는다", async () => {
    vi.mocked(loadPolicyCorrections).mockResolvedValue({ status: "forbidden" });
    const html = renderToStaticMarkup(await PolicyCorrectionsPage({ searchParams: Promise.resolve({}) }));
    expect(html).toContain("관리자 권한이 없습니다");
    expect(html).not.toContain("보정 적용</button>");
    expect(html).not.toContain("이 페이지에 보정 이력이 없습니다");
  });
});
