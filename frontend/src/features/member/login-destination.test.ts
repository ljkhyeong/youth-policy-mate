import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { consumeLoginDestination, rememberLoginDestination } from "./login-destination";

beforeEach(() => {
  const values = new Map<string, string>();
  vi.stubGlobal("sessionStorage", { getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value), removeItem: (key: string) => values.delete(key) });
});
afterEach(() => vi.unstubAllGlobals());

describe("로그인 후 이동", () => {
  it("관리자 로그인을 마치면 목록으로 돌아가고 이전 이동 기록을 지운다", () => {
    sessionStorage.setItem("ypm-pending-policy", "123");
    rememberLoginDestination(true);
    expect(consumeLoginDestination()).toBe("/admin/collection-exceptions");
    expect(sessionStorage.getItem("ypm-login-destination")).toBeNull();
    expect(sessionStorage.getItem("ypm-pending-policy")).toBeNull();
  });
  it("일반 로그인은 관리자 이동 기록을 지우고 기존 정책 저장 흐름을 유지한다", () => {
    rememberLoginDestination(true);
    sessionStorage.setItem("ypm-pending-policy", "123");
    rememberLoginDestination(false);
    expect(consumeLoginDestination()).toBe("/policies/123");
  });
  it("저장된 임의 URL이나 잘못된 정책번호로 이동하지 않는다", () => {
    sessionStorage.setItem("ypm-login-destination", "https://external.example");
    sessionStorage.setItem("ypm-pending-policy", "../admin");
    expect(consumeLoginDestination()).toBe("/my");
  });
});
