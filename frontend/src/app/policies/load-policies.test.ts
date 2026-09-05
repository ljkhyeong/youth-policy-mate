import { afterEach, describe, expect, it, vi } from "vitest";
import { loadPolicies, loadPolicy } from "./load-policies";

afterEach(() => vi.unstubAllGlobals());

describe("공개 정책 조회", () => {
  it("검색어는 고정 경로의 인수로 전달하고 공개 응답을 재사용하지 않는다", async () => {
    const fetchMock = vi.fn(async () => Response.json({ items: [], page: 2, pageSize: 20, total: 0, hasNext: false }));
    vi.stubGlobal("fetch", fetchMock);
    const result = await loadPolicies("장학금&취업", 2);
    const [address, options] = fetchMock.mock.calls[0] as unknown as [URL, RequestInit];
    expect(address.pathname).toBe("/api/v1/policies");
    expect(address.searchParams.get("q")).toBe("장학금&취업");
    expect(address.searchParams.get("page")).toBe("2");
    expect(options.cache).toBe("no-store");
    expect(result.status).toBe("available");
  });

  it("서버 장애를 빈 검색 결과나 정책 삭제로 바꾸지 않는다", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("내부 오류", { status: 503 })));
    expect(await loadPolicies("", 1)).toEqual({ status: "unavailable" });
    expect(await loadPolicy("20260903005400113371")).toEqual({ status: "unavailable" });
  });

  it("상세 404와 잘못된 정책번호를 구분해 처리하고 임의 경로는 요청하지 않는다", async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 404 }));
    vi.stubGlobal("fetch", fetchMock);
    expect(await loadPolicy("../../actuator/env")).toEqual({ status: "missing" });
    expect(fetchMock).not.toHaveBeenCalled();
    expect(await loadPolicy("0")).toEqual({ status: "missing" });
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});
