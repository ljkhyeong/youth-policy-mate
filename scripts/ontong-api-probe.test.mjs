import assert from "node:assert/strict";
import { mkdtemp, readFile, readdir, rm, stat } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { probeOntong, saveCapture } from "./ontong-api-probe.mjs";

const apiKey = "synthetic-test-key+/=";

test("키가 없으면 외부 요청을 보내지 않는다", async () => {
  let calls = 0;
  await assert.rejects(probeOntong({ apiKey: "" }, async () => { calls += 1; }), /ONTONG_API_KEY가 없습니다/);
  assert.equal(calls, 0);
});

test("목록 한 번만 요청하고 JSON 원문을 검증 완료로 표시하지 않는다", async () => {
  // 실제 온통청년 계약과 무관한 인공 자료다. 큰 숫자와 빈 값의 원문 보존만 확인한다.
  const rawBody = '{"synthetic":true,"number":9007199254740993,"empty":null,"text":"서울"}\n';
  let calls = 0;
  const capture = await probeOntong({ apiKey }, async (url, options) => {
    calls += 1;
    assert.equal(url.origin + url.pathname, "https://www.youthcenter.go.kr/go/ythip/getPlcy");
    assert.deepEqual(Object.fromEntries(url.searchParams), {
      pageNum: "1", pageSize: "1", pageType: "1", rtnType: "json", apiKeyNm: apiKey,
    });
    assert.equal(options.redirect, "error");
    assert.ok(options.signal instanceof AbortSignal);
    return new Response(rawBody, { headers: { "content-type": "application/json" } });
  });
  assert.equal(calls, 1);
  assert.equal(capture.reviewStatus, "UNVERIFIED");
  assert.equal(capture.response.rawBody, rawBody);
  assert.equal(capture.response.byteLength, Buffer.byteLength(rawBody));
  assert.equal(JSON.stringify(capture).includes(apiKey), false);
});

test("상세 정책번호와 지역 필터를 명세의 요청 이름으로 전달한다", async () => {
  const capture = await probeOntong({ apiKey, policyNumber: "synthetic-policy", region: "11000" }, async (url) => {
    assert.equal(url.searchParams.get("pageType"), "2");
    assert.equal(url.searchParams.get("plcyNo"), "synthetic-policy");
    assert.equal(url.searchParams.get("zipCd"), "11000");
    return Response.json({ synthetic: true, error: "성공 계약으로 간주하면 안 되는 인공 응답" });
  });
  assert.equal(capture.reviewStatus, "UNVERIFIED");
});

test("작은 표본은 최대 10건만 요청하고 범위를 벗어나면 요청 전에 거절한다", async () => {
  await probeOntong({ apiKey, pageSize: 10 }, async (url) => {
    assert.equal(url.searchParams.get("pageSize"), "10");
    return Response.json({ synthetic: true });
  });
  for (const pageSize of [0, 11, 1.5, NaN]) {
    await assert.rejects(probeOntong({ apiKey, pageSize }, async () => assert.fail("외부 요청이 발생하면 안 된다")), /1부터 10/);
  }
});

test("HTTP 오류와 HTML 응답은 본문을 드러내지 않고 중단한다", async () => {
  for (const status of [400, 200]) {
    await assert.rejects(probeOntong({ apiKey }, async () => new Response(`<html>${apiKey}</html>`, { status })), (error) => {
      assert.match(error.message, /HTTP/);
      assert.equal(error.message.includes(apiKey), false);
      return true;
    });
  }
});

test("키가 반사된 JSON과 URL을 포함한 연결 오류를 노출하지 않는다", async () => {
  for (const reflected of [apiKey, encodeURIComponent(apiKey)]) {
    await assert.rejects(probeOntong({ apiKey }, async () => Response.json({ reflected })), /인증키가 포함/);
  }
  const escapedKey = 'synthetic-key-"\\';
  await assert.rejects(probeOntong({ apiKey: escapedKey }, async () => Response.json({ reflected: escapedKey })), /인증키가 포함/);
  await assert.rejects(probeOntong({ apiKey }, async (url) => { throw new Error(String(url)); }), (error) => {
    assert.match(error.message, /응답을 확보하지 못했습니다/);
    assert.equal(error.message.includes("apiKeyNm"), false);
    assert.equal(error.message.includes(apiKey), false);
    return true;
  });
});

test("큰 응답은 읽기를 취소하고 저장하지 않는다", async () => {
  let cancelled = false;
  const body = new ReadableStream({
    start(controller) { controller.enqueue(new Uint8Array(1024 * 1024 + 1)); },
    cancel() { cancelled = true; },
  });
  await assert.rejects(probeOntong({ apiKey }, async () => new Response(body)), /1 MiB 제한/);
  assert.equal(cancelled, true);
});

test("응답 파일은 기존 결과를 덮어쓰지 않고 소유자만 읽도록 저장한다", async (t) => {
  const directory = await mkdtemp(join(tmpdir(), "youth-policy-probe-test-"));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const capture = await probeOntong({ apiKey }, async () => Response.json({ synthetic: true }));
  const filename = await saveCapture(capture, directory);
  await saveCapture(capture, directory);
  assert.equal((await readdir(directory)).length, 2);
  assert.deepEqual(JSON.parse(await readFile(filename, "utf8")), capture);
  assert.equal((await stat(filename)).mode & 0o777, 0o600);
});
