"use client";
import { useId, useLayoutEffect, useSyncExternalStore } from "react";
import {
  chooseTheme, currentThemeChoice, restoreStoredTheme, subscribeThemeChoice, themeStorageKey, type ThemeChoice,
} from "@/lib/theme-preference";

const options: readonly { value: ThemeChoice; label: string }[] = [
  { value: "system", label: "시스템" }, { value: "light", label: "밝게" }, { value: "dark", label: "어둡게" },
];

// 서버는 저장된 선택을 모르므로 "시스템"으로 그리고, 하이드레이션 뒤 실제 선택으로 바꾼다. 화면 색은 <head> 스크립트가 먼저 적용한다.
const serverChoice = (): ThemeChoice => "system";

export function ThemeSwitch() {
  const choice = useSyncExternalStore(subscribeThemeChoice, currentThemeChoice, serverChoice);
  const name = useId();
  return (
    <fieldset className="theme-switch">
      <legend>화면</legend>
      {options.map(option => (
        <label key={option.value}>
          <input type="radio" name={name} value={option.value} checked={choice === option.value}
            onChange={() => chooseTheme(option.value)} />
          <span>{option.label}</span>
        </label>
      ))}
    </fieldset>
  );
}

// 모든 화면에서 저장된 선택을 유지한다. 바닥글이 없는 관리자·로그인 화면도 포함한다.
export function ThemeSync() {
  // 개발 모드 Strict Mode 재마운트는 <html>에서 React가 관리하지 않는 속성을 지우므로 그리기 전에 다시 적용한다.
  useLayoutEffect(() => {
    restoreStoredTheme();
    const sync = (event: StorageEvent) => {
      if (event.key === themeStorageKey || event.key === null) restoreStoredTheme();
    };
    window.addEventListener("storage", sync);
    return () => window.removeEventListener("storage", sync);
  }, []);
  return null;
}
