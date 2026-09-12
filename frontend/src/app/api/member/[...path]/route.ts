import { NextRequest } from "next/server";
import type { components } from "@/generated/policy-api";

export const dynamic = "force-dynamic";

// 회원 쿠키는 고정된 Spring API에만 중계한다. 임의 경로·호스트·인증 헤더를 전달하지 않는다.
async function handle(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const path = (await context.params).path.join("/");
  const method = request.method;
  const unsubscribe = /^email-unsubscribe\/[A-Za-z0-9_-]{43}$/.test(path);
  const question = /^policy-questions\/([0-9]{1,100})$/.exec(path);
  const prefill = /^policy-prefill\/([0-9]{1,100})$/.exec(path);
  const evaluation = /^policy-evaluation\/([0-9]{1,100})$/.exec(path);
  const collectionReplay = /^collection-replays\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/([0-9])$/i.exec(path);
  const correctionResolution = /^policy-corrections\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/resolutions$/i.exec(path);
  const correction = path === "policy-corrections" || Boolean(correctionResolution);
  const ruleDraft = /^policy-rule-reviews\/[0-9]{20}\/drafts$/.test(path);
  const ruleFile = /^policy-rule-reviews\/[0-9]{20}\/versions\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(path);
  const rulePublish = /^policy-rule-reviews\/[0-9]{20}\/versions\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/publish$/i.test(path);
  const anonymous = unsubscribe || path === "checks" || Boolean(question || evaluation || prefill);
  const allowed = (path === "session" && method === "GET")
    || (path === "logout" && method === "POST")
    || (path === "account" && method === "DELETE")
    || (unsubscribe && method === "POST")
    || (path === "checks" && method === "POST")
    || (Boolean(question) && method === "GET")
    || (Boolean(evaluation || prefill) && method === "POST")
    || (correction && method === "POST")
    || (Boolean(collectionReplay) && method === "POST")
    || ((ruleDraft || rulePublish) && method === "POST")
    || (ruleFile && method === "GET")
    || (path === "conditions" && ["GET", "PUT", "DELETE"].includes(method))
    || (path === "policies" && method === "GET")
    || (/^policies\/[0-9]{1,100}\/changes$/.test(path) && method === "GET")
    || (/^policies\/[0-9]{1,100}$/.test(path) && ["PUT", "DELETE"].includes(method))
    || (path === "email-settings" && ["GET", "PUT", "DELETE"].includes(method))
    || (["email-verification", "email-verification/confirm"].includes(path) && method === "POST")
    || (path === "notifications" && method === "GET")
    || (path === "notifications/read-all" && method === "POST")
    || (/^notifications\/[0-9a-f-]{36}\/read$/.test(path) && method === "POST");
  if (!allowed) return Response.json({ message: "지원하지 않는 요청이에요." }, { status: 404 });
  const frontendOrigin = new URL(process.env.PUBLIC_APP_URL || process.env.APP_FRONTEND_URL || "http://127.0.0.1:3000").origin;
  if (method !== "GET" && request.headers.get("origin") !== frontendOrigin) {
    return Response.json({ message: "현재 화면에서 다시 요청해주세요." }, { status: 403 });
  }
  try {
    let body: string | undefined;
    if (["PUT", "POST"].includes(method)) {
      const reader = request.body?.getReader();
      const chunks: Uint8Array[] = []; let length = 0;
      if (reader) {
        while (true) {
          const next = await reader.read();
          if (next.done) break;
          length += next.value.byteLength;
          if (length > (ruleDraft ? 524288 : 16384)) { await reader.cancel(); return Response.json({ message: "입력 내용이 너무 길어요." }, { status: 413 }); }
          chunks.push(next.value);
        }
      }
      if (length) { const bytes = new Uint8Array(length); let offset = 0; for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; } body = new TextDecoder().decode(bytes); }
    }
    const base = process.env.POLICY_API_BASE_URL || "http://127.0.0.1:8080";
    const apiPath = unsubscribe ? `/api/v1/${path}` : collectionReplay ? `/api/v1/admin/collection-exceptions/${collectionReplay[1]}/${collectionReplay[2]}/replays`
      : correction || ruleDraft || ruleFile || rulePublish ? `/api/v1/admin/${path}`
      : question ? `/api/v1/policies/${question[1]}/questions`
      : prefill ? `/api/v1/policies/${prefill[1]}/question-prefill`
      : evaluation ? `/api/v1/policies/${evaluation[1]}/evaluation` : path === "checks" ? "/api/v1/policies/checks" : ["session", "logout"].includes(path) ? `/api/v1/${path}` : `/api/v1/me/${path}`;
    const url = new URL(apiPath, base);
    if (path === "checks") {
      for (const key of ["page", "q", "sort", "recruitmentStatus"]) {
        const value = request.nextUrl.searchParams.get(key);
        if (value !== null) url.searchParams.set(key, value);
      }
    }
    if (path === "notifications") {
      for (const key of ["page", "pageSize", "filter"]) {
        const value = request.nextUrl.searchParams.get(key);
        if (value !== null) url.searchParams.set(key, value);
      }
    }
    const headers: Record<string, string> = { Accept: "application/json" };
    if (body) headers["Content-Type"] = "application/json";
    if (unsubscribe) {
      const form: components["schemas"]["EmailUnsubscribeForm"] = { "List-Unsubscribe": "One-Click" };
      body = new URLSearchParams(form).toString();
      headers["Content-Type"] = "application/x-www-form-urlencoded";
    }
    const cookie = request.headers.get("cookie")?.split(";").map(value => value.trim()).find(value => value.startsWith("YPM_SESSION="));
    if (cookie && cookie.length < 1024 && !anonymous) headers.Cookie = cookie;
    const csrf = request.headers.get("x-csrf-token");
    if (csrf && csrf.length < 512 && !anonymous) headers["X-CSRF-TOKEN"] = csrf;
    const response = await fetch(url, { method, headers, body, cache: "no-store", redirect: "error", signal: AbortSignal.timeout(8000) });
    const resultHeaders = new Headers({ "Cache-Control": "no-store" });
    const contentType = response.headers.get("content-type");
    if (contentType) resultHeaders.set("Content-Type", contentType);
    if (!anonymous) for (const value of response.headers.getSetCookie()) resultHeaders.append("Set-Cookie", value);
    return new Response(response.body, { status: response.status, headers: resultHeaders });
  } catch { return Response.json({ message: "서버에 잠시 연결할 수 없어요. 다시 시도해주세요." }, { status: 503, headers: { "Cache-Control": "no-store" } }); }
}

export { handle as GET, handle as PUT, handle as DELETE, handle as POST };
