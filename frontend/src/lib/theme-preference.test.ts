import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  chooseTheme, currentThemeChoice, restoreStoredTheme, subscribeThemeChoice, themeInitScript, themeStorageKey,
} from "./theme-preference";

let root: { dataset: Record<string, string | undefined> };
let stored: Map<string, string>;

function storage({ blocked = false } = {}) {
  const guard = () => { if (blocked) throw new DOMException("저장소 차단", "SecurityError"); };
  return {
    getItem: (key: string) => { guard(); return stored.get(key) ?? null; },
    setItem: (key: string, value: string) => { guard(); stored.set(key, value); },
    removeItem: (key: string) => { guard(); stored.delete(key); },
  };
}

const runInitScript = () => new Function(themeInitScript)();

beforeEach(() => {
  root = { dataset: {} };
  stored = new Map();
  vi.stubGlobal("document", { documentElement: root });
  vi.stubGlobal("localStorage", storage());
});
afterEach(() => vi.unstubAllGlobals());

describe("첫 화면 전 화면 색 적용", () => {
  it("저장된 밝게·어둡게만 적용하고 그 밖의 값은 기기 설정을 따른다", () => {
    stored.set(themeStorageKey, "dark");
    runInitScript();
    expect(root.dataset.theme).toBe("dark");

    for (const value of ["system", "sepia", ""]) {
      root.dataset = {};
      stored.set(themeStorageKey, value);
      runInitScript();
      expect(root.dataset.theme).toBeUndefined();
    }
  });

  it("저장소를 쓸 수 없어도 오류 없이 기기 설정을 따른다", () => {
    vi.stubGlobal("localStorage", storage({ blocked: true }));
    expect(runInitScript).not.toThrow();
    expect(root.dataset.theme).toBeUndefined();
  });
});

describe("화면 색 선택", () => {
  it("고른 값을 바로 적용해 저장하고, 시스템을 고르면 저장값과 속성을 지운다", () => {
    const listener = vi.fn();
    const unsubscribe = subscribeThemeChoice(listener);

    chooseTheme("dark");
    expect(root.dataset.theme).toBe("dark");
    expect(stored.get(themeStorageKey)).toBe("dark");
    expect(currentThemeChoice()).toBe("dark");

    chooseTheme("system");
    expect(root.dataset.theme).toBeUndefined();
    expect(stored.has(themeStorageKey)).toBe(false);
    expect(currentThemeChoice()).toBe("system");
    expect(listener).toHaveBeenCalledTimes(2);
    unsubscribe();
  });

  it("저장하지 못해도 이 화면에는 고른 값을 적용한다", () => {
    vi.stubGlobal("localStorage", storage({ blocked: true }));
    chooseTheme("light");
    expect(root.dataset.theme).toBe("light");
    expect(currentThemeChoice()).toBe("light");
  });

  it("다른 탭에서 바꾼 저장값을 다시 적용한다", () => {
    chooseTheme("dark");
    stored.set(themeStorageKey, "light");
    restoreStoredTheme();
    expect(root.dataset.theme).toBe("light");

    stored.delete(themeStorageKey);
    restoreStoredTheme();
    expect(currentThemeChoice()).toBe("system");
  });
});
