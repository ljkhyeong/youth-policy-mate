import { afterEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { GET, POST, PUT } from "./route";

const base = "http://127.0.0.1:3000";
const context = (path: string) => ({ params: Promise.resolve({ path: path.split("/") }) });
afterEach(() => vi.unstubAllGlobals());

describe("개인 API 중계", () => {
  it("허용되지 않은 경로와 다른 출처의 변경 요청은 서버에 보내지 않는다", async () => {
    const fetch = vi.fn(); vi.stubGlobal("fetch", fetch);
    expect((await GET(new NextRequest(`${base}/api/member/actuator/env`), context("actuator/env"))).status).toBe(404);
    expect((await PUT(new NextRequest(`${base}/api/member/policies/123`, { method: "PUT", headers: { origin: "https://external.example" } }), context("policies/123"))).status).toBe(403);
    expect(fetch).not.toHaveBeenCalled();
  });

  it("로그인 쿠키와 CSRF만 고정 서버에 보내며 응답과 세션 쿠키를 공용 캐시에서 제외한다", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204, headers: { "Set-Cookie": "YPM_SESSION=new; Path=/; HttpOnly" } }));
    vi.stubGlobal("fetch", fetch);
    const response = await PUT(new NextRequest(`${base}/api/member/policies/123`, { method: "PUT", headers: {
      origin: base, cookie: "analytics=private; YPM_SESSION=member", "X-CSRF-TOKEN": "confirmed", Authorization: "Bearer private",
    } }), context("policies/123"));
    const [url, request] = fetch.mock.calls[0];
    expect(url.pathname).toBe("/api/v1/me/policies/123");
    expect(request.headers).toEqual({ Accept: "application/json", Cookie: "YPM_SESSION=member", "X-CSRF-TOKEN": "confirmed" });
    expect(request.cache).toBe("no-store");
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(response.headers.get("set-cookie")).toContain("YPM_SESSION=new");
  });

  it("정책 질문과 비교 요청은 정해진 공개 경로로만 보내며 회원 정보를 양방향에서 제외한다", async () => {
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(Response.json({}, { headers: { "Set-Cookie": "YPM_SESSION=unexpected" } })));
    vi.stubGlobal("fetch", fetch);
    const question = await GET(new NextRequest(`${base}/api/member/policy-questions/123`, { headers: { cookie: "YPM_SESSION=private" } }), context("policy-questions/123"));
    expect(fetch.mock.calls[0][0].pathname).toBe("/api/v1/policies/123/questions");
    expect(fetch.mock.calls[0][1].headers.Cookie).toBeUndefined();
    expect(question.headers.get("set-cookie")).toBeNull();
    await POST(new NextRequest(`${base}/api/member/policy-evaluation/123`, { method: "POST", headers: { origin: base, cookie: "YPM_SESSION=private", "X-CSRF-TOKEN": "private" }, body: "{}" }), context("policy-evaluation/123"));
    expect(fetch.mock.calls[1][0].pathname).toBe("/api/v1/policies/123/evaluation");
    expect(fetch.mock.calls[1][1].headers).toEqual({ Accept: "application/json", "Content-Type": "application/json" });
    expect((await GET(new NextRequest(`${base}/api/member/policy-evaluation/123`), context("policy-evaluation/123"))).status).toBe(404);
    expect((await POST(new NextRequest(`${base}/api/member/policy-evaluation/123`, { method: "POST" }), context("policy-evaluation/123"))).status).toBe(403);
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("이메일 확인과 동의는 회원 경로로만 전달하고 확인 코드 조회는 허용하지 않는다", async () => {
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(new Response(null, { status: 204 })));
    vi.stubGlobal("fetch", fetch);
    await POST(new NextRequest(`${base}/api/member/email-verification/confirm`, { method: "POST", headers: {
      origin: base, cookie: "YPM_SESSION=member", "X-CSRF-TOKEN": "confirmed", "Content-Type": "application/json",
    }, body: JSON.stringify({ code: "12345678" }) }), context("email-verification/confirm"));
    expect(fetch.mock.calls[0][0].pathname).toBe("/api/v1/me/email-verification/confirm");
    expect(fetch.mock.calls[0][1].headers.Cookie).toBe("YPM_SESSION=member");
    expect(fetch.mock.calls[0][1].headers["X-CSRF-TOKEN"]).toBe("confirmed");
    expect((await GET(new NextRequest(`${base}/api/member/email-verification/confirm`), context("email-verification/confirm"))).status).toBe(404);
    await PUT(new NextRequest(`${base}/api/member/email-settings`, { method: "PUT", headers: { origin: base }, body: '{"enabled":false}' }), context("email-settings"));
    expect(fetch.mock.calls[1][0].pathname).toBe("/api/v1/me/email-settings");
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("조건 검색·정렬·접수 상태만 쿼리로 전달하고 생년월일은 본문에 유지한다", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ items: [] })); vi.stubGlobal("fetch", fetch);
    const body = JSON.stringify({ birthDate: "2000-01-02", district: "강남구", employmentStatus: "NOT_EMPLOYED" });
    const response = await POST(new NextRequest(`${base}/api/member/checks?page=2&q=이사비&sort=RECENT&recruitmentStatus=CLOSED&birthDate=2000-01-02`, {
      method: "POST", headers: { origin: base, "Content-Type": "application/json" }, body,
    }), context("checks"));
    const [url, request] = fetch.mock.calls[0];
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: "2", q: "이사비", sort: "RECENT", recruitmentStatus: "CLOSED" });
    expect(request.body).toBe(body);
    expect(request.cache).toBe("no-store");
    expect(response.headers.get("cache-control")).toBe("no-store");
  });

  it("조건 확인에는 회원 쿠키를 전달하지 않고 개인정보를 포함한 큰 본문은 거절한다", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ items: [] })); vi.stubGlobal("fetch", fetch);
    await POST(new NextRequest(`${base}/api/member/checks?page=2`, { method: "POST", headers: { origin: base, cookie: "YPM_SESSION=private" }, body: "{}" }), context("checks"));
    expect(fetch.mock.calls[0][0].searchParams.get("page")).toBe("2");
    expect(fetch.mock.calls[0][1].headers.Cookie).toBeUndefined();
    const response = await POST(new NextRequest(`${base}/api/member/checks`, { method: "POST", headers: { origin: base }, body: "a".repeat(16385) }), context("checks"));
    expect(response.status).toBe(413);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
