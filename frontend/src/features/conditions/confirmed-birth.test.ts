import { afterEach, expect, it, vi } from "vitest";
import { rememberConfirmedBirth, readConfirmedBirth, clearConfirmedBirth } from "./confirmed-birth";
import { announceAccountChange } from "@/features/member/account-transitions";

afterEach(() => { clearConfirmedBirth(); vi.unstubAllGlobals(); });
it("계정 전환을 알리면 다른 창 알림을 지원하지 않아도 이전 생년월일을 지운다", () => {
  vi.stubGlobal("BroadcastChannel", undefined);
  rememberConfirmedBirth("2000-01-01");
  announceAccountChange();
  expect(readConfirmedBirth()).toBeNull();
});
