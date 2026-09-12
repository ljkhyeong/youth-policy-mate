import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { consumeLoginDestination, getLoginDestination, readLoginDestination, rememberLoginDestination } from "./login-destination";

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
  it("정책 로그인은 현재 링크의 정책번호로 복귀한다", () => {
    rememberLoginDestination(true);
    sessionStorage.setItem("ypm-pending-policy", "456");
    rememberLoginDestination(false, "123");
    expect(consumeLoginDestination()).toBe("/policies/123");
  });
  it("일반 로그인은 이전 관리자·정책 이동 기록을 사용하지 않는다", () => {
    sessionStorage.setItem("ypm-login-destination", "admin");
    sessionStorage.setItem("ypm-pending-policy", "123");
    rememberLoginDestination(false);
    expect(consumeLoginDestination()).toBe("/my");
  });
  it("복귀 경로는 관리자 우선이며 정책 로그인은 새 탭에서도 결정할 수 있다", () => {
    expect(getLoginDestination(true, "123")).toBe("/admin/collection-exceptions");
    expect(getLoginDestination(false, "123")).toBe("/policies/123");
    rememberLoginDestination(false, "123");
    expect(consumeLoginDestination()).toBe("/policies/123");
    expect(consumeLoginDestination()).toBe("/my");
  });
  it.each(["../admin", "https://external.example", "1?next=admin", "1".repeat(101), ""])("잘못된 정책번호 %s는 복귀 주소로 사용하지 않는다", policy => {
    expect(getLoginDestination(false, policy)).toBe("/my");
    rememberLoginDestination(false, policy);
    expect(consumeLoginDestination()).toBe("/my");
  });
  it("로그인 실패 후 복귀 주소를 확인해도 완료 전까지 기록을 유지한다", () => {
    rememberLoginDestination(false, "123");
    expect(readLoginDestination()).toBe("/policies/123");
    expect(consumeLoginDestination()).toBe("/policies/123");
    rememberLoginDestination(true);
    expect(readLoginDestination()).toBe("/admin/collection-exceptions");
    expect(consumeLoginDestination()).toBe("/admin/collection-exceptions");
  });
  it("저장된 임의 URL이나 잘못된 정책번호로 이동하지 않는다", () => {
    sessionStorage.setItem("ypm-login-destination", "https://external.example");
    sessionStorage.setItem("ypm-pending-policy", "../admin");
    expect(consumeLoginDestination()).toBe("/my");
  });
});
