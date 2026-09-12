import { afterEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { DELETE, GET, POST, PUT } from "./route";

const base = "http://127.0.0.1:3000";
const context = (path: string) => ({ params: Promise.resolve({ path: path.split("/") }) });
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); });

describe("개인 API 중계", () => {
  it("수신 해제는 토큰만 표준 폼으로 전달하고 쿠키·CSRF·쿼리·입력 본문을 제외한다", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 200, headers: { "Set-Cookie": "YPM_SESSION=unexpected" } }));
    vi.stubGlobal("fetch", fetch);
    const path = `email-unsubscribe/${"A".repeat(43)}`;
    const response = await POST(new NextRequest(`${base}/api/member/${path}?email=private`, { method: "POST",
      headers: { origin: base, cookie: "YPM_SESSION=private", "X-CSRF-TOKEN": "private" }, body: "untrusted body" }), context(path));
    expect(response.status).toBe(200);
    expect(fetch.mock.calls[0][0].pathname).toBe(`/api/v1/${path}`);
    expect(fetch.mock.calls[0][0].search).toBe("");
    expect(fetch.mock.calls[0][1].headers).toEqual({ Accept: "application/json", "Content-Type": "application/x-www-form-urlencoded" });
    expect(fetch.mock.calls[0][1].body).toBe("List-Unsubscribe=One-Click");
    expect(response.headers.get("set-cookie")).toBeNull();
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect((await GET(new NextRequest(`${base}/api/member/${path}`), context(path))).status).toBe(404);
    expect((await POST(new NextRequest(`${base}/api/member/${path}`, { method: "POST" }), context(path))).status).toBe(403);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it("탈퇴는 본인 세션의 DELETE만 허용하고 다른 회원 식별자를 전달하지 않는다", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204, headers: { "Set-Cookie": "YPM_SESSION=; Path=/; Max-Age=0" } }));
    vi.stubGlobal("fetch", fetch);
    const headers = { origin: base, cookie: "YPM_SESSION=member", "X-CSRF-TOKEN": "confirmed" };
    const result = await DELETE(new NextRequest(`${base}/api/member/account?memberId=other`, { method: "DELETE", headers }), context("account"));
    expect(result.status).toBe(204);
    expect(fetch.mock.calls[0][0].pathname).toBe("/api/v1/me/account");
    expect(fetch.mock.calls[0][0].search).toBe("");
    expect(fetch.mock.calls[0][1].headers).toMatchObject({ Cookie: "YPM_SESSION=member", "X-CSRF-TOKEN": "confirmed" });
    expect(result.headers.get("cache-control")).toBe("no-store");
    expect(result.headers.get("set-cookie")).toContain("Max-Age=0");
    expect((await POST(new NextRequest(`${base}/api/member/account`, { method: "POST", headers }), context("account"))).status).toBe(404);
    expect((await DELETE(new NextRequest(`${base}/api/member/account`, { method: "DELETE", headers: { origin: "https://other.test" } }), context("account"))).status).toBe(403);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it("운영 공개 주소의 요청만 허용하고 실행 환경의 내부 API 주소를 사용한다", async () => {
    vi.stubEnv("PUBLIC_APP_URL", "https://policy.example.test");
    vi.stubEnv("POLICY_API_BASE_URL", "http://youth-policy-api:8080");
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 })); vi.stubGlobal("fetch", fetch);
    const response = await PUT(new NextRequest(`${base}/api/member/email-settings`, {
      method: "PUT", headers: { origin: "https://policy.example.test", "X-CSRF-TOKEN": "token" }, body: JSON.stringify({ enabled: false }),
    }), context("email-settings"));
    expect(response.status).toBe(204);
    expect(fetch.mock.calls[0][0].href).toBe("http://youth-policy-api:8080/api/v1/me/email-settings");
    expect((await PUT(new NextRequest(`${base}/api/member/email-settings`, { method: "PUT", headers: { origin: base } }), context("email-settings"))).status).toBe(403);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it("규칙 파일 요청만 용량을 늘리고 관리자 경로·세션·CSRF를 제한해 중계한다", async () => {
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(Response.json({}))); vi.stubGlobal("fetch", fetch);
    const root = "policy-rule-reviews/99990000000000000001";
    const version = `${root}/versions/10000000-0000-0000-0000-000000000001`;
    const body = JSON.stringify({ definitionJson: "가".repeat(12000) });
    const headers = { origin: base, cookie: "other=private; YPM_SESSION=admin", "x-csrf-token": "csrf", authorization: "private" };
    expect((await POST(new NextRequest(`${base}/api/member/${root}/drafts?private=ignored`, { method: "POST", headers, body }), context(`${root}/drafts`))).status).toBe(200);
    expect(fetch.mock.calls[0][0].pathname).toBe(`/api/v1/admin/${root}/drafts`);
    expect(fetch.mock.calls[0][0].search).toBe("");
    expect(fetch.mock.calls[0][1].headers).toEqual({ Accept: "application/json", "Content-Type": "application/json", Cookie: "YPM_SESSION=admin", "X-CSRF-TOKEN": "csrf" });
    expect((await POST(new NextRequest(`${base}/api/member/${version}/publish`, { method: "POST", headers, body }), context(`${version}/publish`))).status).toBe(413);
    expect((await POST(new NextRequest(`${base}/api/member/${root}/drafts`, { method: "POST", headers, body: "x".repeat(524289) }), context(`${root}/drafts`))).status).toBe(413);
    expect((await GET(new NextRequest(`${base}/api/member/${version}`, { headers }), context(version))).status).toBe(200);
    expect(fetch.mock.calls[1][0].pathname).toBe(`/api/v1/admin/${version}`);
    expect((await POST(new NextRequest(`${base}/api/member/${version}`, { method: "POST", headers }), context(version))).status).toBe(404);
    expect((await GET(new NextRequest(`${base}/api/member/${root}/drafts`), context(`${root}/drafts`))).status).toBe(404);
    expect((await POST(new NextRequest(`${base}/api/member/${root}/drafts`, { method: "POST", headers: { origin: "https://other.example" } }), context(`${root}/drafts`))).status).toBe(403);
    expect(fetch).toHaveBeenCalledTimes(2);
  });
  it("관리자 항목 재처리는 지정 경로의 POST만 허용하고 세션·CSRF를 전달한다", async () => {
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(Response.json({ outcome: "APPLIED" })));
    vi.stubGlobal("fetch", fetch);
    const path = "collection-replays/10000000-0000-0000-0000-000000000001/0";
    const body = '{"reason":"저장 오류 조치"}';
    await POST(new NextRequest(`${base}/api/member/${path}`, { method: "POST", headers: {
      origin: base, cookie: "YPM_SESSION=admin", "X-CSRF-TOKEN": "confirmed",
    }, body }), context(path));
    expect(fetch.mock.calls[0][0].pathname).toBe("/api/v1/admin/collection-exceptions/10000000-0000-0000-0000-000000000001/0/replays");
    expect(fetch.mock.calls[0][1].headers.Cookie).toBe("YPM_SESSION=admin");
    expect(fetch.mock.calls[0][1].headers["X-CSRF-TOKEN"]).toBe("confirmed");
    expect(fetch.mock.calls[0][1].body).toBe(body);
    expect((await GET(new NextRequest(`${base}/api/member/${path}`), context(path))).status).toBe(404);
    expect((await POST(new NextRequest(`${base}/api/member/${path}`, { method: "POST", headers: { origin: "https://other.example" } }), context(path))).status).toBe(403);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
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

  it("생년월일 답변 변환은 본문만 전달하고 쿠키·CSRF·쿼리·응답 쿠키를 제외한다", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ answers: [] }, { headers: { "Set-Cookie": "YPM_SESSION=unexpected" } }));
    vi.stubGlobal("fetch", fetch);
    const path = "policy-prefill/123";
    const body = JSON.stringify({ revision: 1, ruleVersion: "reviewed", birthDate: "2000-01-01" });
    const response = await POST(new NextRequest(`${base}/api/member/${path}?birthDate=2000-01-01`, {
      method: "POST", headers: { origin: base, cookie: "YPM_SESSION=private", "X-CSRF-TOKEN": "private" }, body,
    }), context(path));
    const [url, request] = fetch.mock.calls[0];
    expect(url.pathname).toBe("/api/v1/policies/123/question-prefill");
    expect(url.search).toBe("");
    expect(request.body).toBe(body);
    expect(request.headers).not.toHaveProperty("Cookie");
    expect(request.headers).not.toHaveProperty("X-CSRF-TOKEN");
    expect(response.headers.get("set-cookie")).toBeNull();
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect((await GET(new NextRequest(`${base}/api/member/${path}`), context(path))).status).toBe(404);
    expect((await POST(new NextRequest(`${base}/api/member/${path}`, { method: "POST" }), context(path))).status).toBe(403);
    expect(fetch).toHaveBeenCalledTimes(1);
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

  it("관심 정책 변경 조회는 회원 경로의 GET만 허용하며 비교 기준 쿼리를 임의로 전달하지 않는다", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({})); vi.stubGlobal("fetch", fetch);
    const path = "policies/20260903005400113371/changes";
    const response = await GET(new NextRequest(`${base}/api/member/${path}?memberId=other&revision=999`, {
      headers: { cookie: "other=private; YPM_SESSION=member" },
    }), context(path));
    const [url, request] = fetch.mock.calls[0];
    expect(url.pathname).toBe(`/api/v1/me/${path}`);
    expect(url.search).toBe("");
    expect(request.headers).toEqual({ Accept: "application/json", Cookie: "YPM_SESSION=member" });
    expect(request.cache).toBe("no-store");
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect((await POST(new NextRequest(`${base}/api/member/${path}`, { method: "POST", headers: { origin: base } }), context(path))).status).toBe(404);
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it("알림 페이지와 필터만 회원 API에 전달하고 회원 식별자 쿼리는 제외한다", async () => {
    const fetch = vi.fn().mockResolvedValue(Response.json({ items: [], unreadCount: 0 })); vi.stubGlobal("fetch", fetch);
    const response = await GET(new NextRequest(`${base}/api/member/notifications?page=3&pageSize=20&filter=UNREAD&memberId=other`, {
      headers: { cookie: "other=private; YPM_SESSION=member", Authorization: "private" },
    }), context("notifications"));
    const [url, request] = fetch.mock.calls[0];
    expect(url.pathname).toBe("/api/v1/me/notifications");
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: "3", pageSize: "20", filter: "UNREAD" });
    expect(request.headers).toEqual({ Accept: "application/json", Cookie: "YPM_SESSION=member" });
    expect(request.cache).toBe("no-store");
    expect(response.headers.get("cache-control")).toBe("no-store");
  });

  it("모두 읽음은 같은 출처의 POST만 허용하고 회원 쿠키와 CSRF를 전달한다", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 })); vi.stubGlobal("fetch", fetch);
    const path = "notifications/read-all";
    const response = await POST(new NextRequest(`${base}/api/member/${path}?memberId=other&filter=UNREAD&page=2`, {
      method: "POST", headers: { origin: base, cookie: "YPM_SESSION=member", "X-CSRF-TOKEN": "confirmed" },
    }), context(path));
    const [url, request] = fetch.mock.calls[0];
    expect(url.pathname).toBe("/api/v1/me/notifications/read-all");
    expect(url.search).toBe("");
    expect(request.headers).toEqual({ Accept: "application/json", Cookie: "YPM_SESSION=member", "X-CSRF-TOKEN": "confirmed" });
    expect(response.status).toBe(204);
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect((await GET(new NextRequest(`${base}/api/member/${path}`), context(path))).status).toBe(404);
    expect((await POST(new NextRequest(`${base}/api/member/${path}`, { method: "POST", headers: { origin: "https://external.example" } }), context(path))).status).toBe(403);
    expect(fetch).toHaveBeenCalledTimes(1);
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


it("정책 보정은 지정한 생성·해소 POST만 관리자 API로 보낸다", async () => {
  const fetch = vi.fn().mockImplementation(() => Promise.resolve(Response.json({ status: "ACTIVE" })));
  vi.stubGlobal("fetch", fetch);
  const paths = ["policy-corrections", "policy-corrections/10000000-0000-0000-0000-000000000001/resolutions"];
  for (const path of paths) {
    await POST(new NextRequest(`${base}/api/member/${path}`, { method: "POST", headers: { origin: base, cookie: "YPM_SESSION=admin", "X-CSRF-TOKEN": "confirmed" }, body: "{}" }), context(path));
    expect((await GET(new NextRequest(`${base}/api/member/${path}`), context(path))).status).toBe(404);
  }
  expect(fetch.mock.calls.map(call => call[0].pathname)).toEqual(paths.map(path => `/api/v1/admin/${path}`));
  expect(fetch.mock.calls[1][1].headers.Cookie).toBe("YPM_SESSION=admin");
  expect(fetch.mock.calls[1][1].headers["X-CSRF-TOKEN"]).toBe("confirmed");
  const invalid = "policy-corrections/not-an-id/resolutions";
  expect((await POST(new NextRequest(`${base}/api/member/${invalid}`, { method: "POST", headers: { origin: base } }), context(invalid))).status).toBe(404);
  expect(fetch).toHaveBeenCalledTimes(2);
});
