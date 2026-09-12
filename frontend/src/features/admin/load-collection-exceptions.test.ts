import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cookies } from "next/headers";
import { collectionPage, loadCollectionException, loadCollectionExceptions, loadCollectionPageFailures, loadPolicyCorrections, loadCorrectionPolicy, loadRuleReviews, loadRuleReview, loadAiRuns } from "./load-collection-exceptions";

vi.mock("next/headers", () => ({ cookies: vi.fn() }));
const run = "10000000-0000-0000-0000-000000000001";
beforeEach(() => vi.mocked(cookies).mockResolvedValue({ get: (name: string) => name === "YPM_SESSION" ? { name, value: "fixture-session" } : undefined } as Awaited<ReturnType<typeof cookies>>));
afterEach(() => { vi.unstubAllGlobals(); vi.resetAllMocks(); });

describe("관리자 수집 예외 서버 조회", () => {
  it("AI 추출 검색 조건과 관리자 세션을 전용 조회 API로 전달한다", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ items: [] })); vi.stubGlobal("fetch", fetch);
    await loadAiRuns(2, "LEASE_EXPIRED", "청년 & 지원");
    const [url, options] = fetch.mock.calls[0];
    expect(url.pathname).toBe("/api/v1/admin/policy-ai-runs");
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: "2", pageSize: "20", filter: "LEASE_EXPIRED", query: "청년 & 지원" });
    expect(options.cache).toBe("no-store");
    expect(options.headers.Cookie).toBe("YPM_SESSION=fixture-session");
  });
  it("조건 검토의 검색어를 인코딩하고 잘못된 정책번호는 전송하지 않는다", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ items: [] })); vi.stubGlobal("fetch", fetch);
    await loadRuleReviews(2, "SOURCE_CHANGED", "청년 & 지원");
    const [url, options] = fetch.mock.calls[0];
    expect(url.pathname).toBe("/api/v1/admin/policy-rule-reviews");
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: "2", pageSize: "20", filter: "SOURCE_CHANGED", query: "청년 & 지원" });
    expect(options.cache).toBe("no-store");
    expect(options.headers.Cookie).toBe("YPM_SESSION=fixture-session");
    expect(await loadRuleReview("../me")).toEqual({ status: "missing" });
    expect(fetch).toHaveBeenCalledTimes(1);
    await loadRuleReview("123");
    expect(fetch.mock.calls[1][0].pathname).toBe("/api/v1/admin/policy-rule-reviews/123");
  });
  it("페이지 수집 실패는 전용 API에 페이지 인수와 세션을 전달한다", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ items: [], page: 2, pageSize: 20, hasNext: false }));
    vi.stubGlobal("fetch", fetch);
    expect((await loadCollectionPageFailures(2)).status).toBe("available");
    const [url, options] = fetch.mock.calls[0];
    expect(url.pathname).toBe("/api/v1/admin/collection-exceptions/pages");
    expect(url.searchParams.get("page")).toBe("2");
    expect(options.headers.Cookie).toBe("YPM_SESSION=fixture-session");
    expect(options.cache).toBe("no-store");
  });
  it("관리자 API에 세션 쿠키만 보내고 응답 캐시와 리다이렉트를 허용하지 않는다", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ items: [], page: 2, pageSize: 20, hasNext: false }));
    vi.stubGlobal("fetch", fetch);
    expect((await loadCollectionExceptions(2)).status).toBe("available");
    const [url, options] = fetch.mock.calls[0];
    expect(url.pathname).toBe("/api/v1/admin/collection-exceptions");
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: "2", pageSize: "20" });
    expect(options.headers).toEqual({ Accept: "application/json", Cookie: "YPM_SESSION=fixture-session" });
    expect(options.cache).toBe("no-store");
    expect(options.redirect).toBe("error");
  });

  it("세션이 없으면 로그인 안내를 반환하고 서버를 호출하지 않는다", async () => {
    vi.mocked(cookies).mockResolvedValue({ get: () => undefined } as unknown as Awaited<ReturnType<typeof cookies>>);
    const fetch = vi.fn(); vi.stubGlobal("fetch", fetch);
    expect(await loadCollectionExceptions(1)).toEqual({ status: "unauthenticated" });
    expect(fetch).not.toHaveBeenCalled();
  });

  it.each([[401, "unauthenticated"], [403, "forbidden"], [404, "missing"], [400, "invalid"], [503, "unavailable"]] as const)(
    "%i 응답을 %s 상태로 구분하고 서버 오류 원문은 반환하지 않는다", async (status, expected) => {
      vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("private-server-error", { status })));
      expect(await loadCollectionException(run, "0")).toEqual({ status: expected });
    });

  it("잘못된 상세 주소는 서버 경로에 섞지 않고 페이지 범위를 정리한다", async () => {
    const fetch = vi.fn(); vi.stubGlobal("fetch", fetch);
    expect(await loadCollectionException("../me", "0")).toEqual({ status: "missing" });
    expect(await loadCollectionException(run, "10")).toEqual({ status: "missing" });
    expect(fetch).not.toHaveBeenCalled();
    expect(collectionPage("2")).toBe(2);
    for (const value of ["0", "1001", "1.5", "NaN", ["1", "2"], undefined]) expect(collectionPage(value)).toBe(1);
  });

  it("연결 실패나 JSON이 아닌 응답을 빈 목록으로 바꾸지 않는다", async () => {
    const fetch = vi.fn().mockRejectedValueOnce(new Error("connection-failed"))
      .mockResolvedValueOnce(new Response("<html>error</html>"));
    vi.stubGlobal("fetch", fetch);
    expect(await loadCollectionExceptions(1)).toEqual({ status: "unavailable" });
    expect(await loadCollectionExceptions(1)).toEqual({ status: "unavailable" });
  });
});


it("보정 이력과 공개 정책을 고정 관리자 경로에서 읽고 잘못된 정책번호는 거절한다", async () => {
  const fetch = vi.fn().mockResolvedValue(Response.json({})); vi.stubGlobal("fetch", fetch);
  await loadPolicyCorrections(2);
  await loadCorrectionPolicy("123");
  expect(fetch.mock.calls.map(call => call[0].pathname)).toEqual(["/api/v1/admin/policy-corrections", "/api/v1/admin/policy-corrections/policies/123"]);
  expect(await loadCorrectionPolicy("../me")).toEqual({ status: "invalid" });
  expect(fetch).toHaveBeenCalledTimes(2);
});
