// 화면 색 선택. 기기 설정을 따르는 "system"은 저장하지 않고 data-theme도 붙이지 않아 CSS가 prefers-color-scheme을 따르게 한다.
// 고른 값은 개인정보가 아닌 화면 표시 설정이라 이 브라우저의 localStorage에만 둔다.
export const themeStorageKey = "ypm-theme";

export type ThemeChoice = "system" | "light" | "dark";
type FixedTheme = Exclude<ThemeChoice, "system">;

const isFixedTheme = (value: unknown): value is FixedTheme => value === "light" || value === "dark";

// <head>에서 첫 화면을 그리기 전에 실행한다. 저장소를 쓸 수 없거나 값이 올바르지 않으면 기기 설정을 따른다.
export const themeInitScript =
  `try{var t=localStorage.getItem(${JSON.stringify(themeStorageKey)});if(t==="light"||t==="dark")document.documentElement.dataset.theme=t}catch(e){}`;

const listeners = new Set<() => void>();
const notify = () => listeners.forEach(listener => listener());

function applyTheme(theme: FixedTheme | undefined) {
  const root = document.documentElement;
  if (theme) root.dataset.theme = theme;
  else delete root.dataset.theme;
}

function storedTheme(): FixedTheme | undefined {
  try {
    const value = localStorage.getItem(themeStorageKey);
    return isFixedTheme(value) ? value : undefined;
  } catch {
    return undefined;
  }
}

// 지금 화면에 적용된 선택. 저장에 실패해도 이 화면에서 고른 값을 그대로 보여준다.
export function currentThemeChoice(): ThemeChoice {
  const theme = document.documentElement.dataset.theme;
  return isFixedTheme(theme) ? theme : "system";
}

export function chooseTheme(choice: ThemeChoice) {
  const theme = choice === "system" ? undefined : choice;
  applyTheme(theme);
  try {
    if (theme) localStorage.setItem(themeStorageKey, theme);
    else localStorage.removeItem(themeStorageKey);
  } catch {
    // 저장하지 못하면 이 화면에만 적용하고, 다음 화면부터는 기기 설정을 따른다.
  }
  notify();
}

// 저장된 값을 다시 적용한다. 다른 탭에서 바꿨을 때와 개발 모드 재마운트로 <html> 속성이 지워졌을 때 쓴다.
export function restoreStoredTheme() {
  applyTheme(storedTheme());
  notify();
}

export function subscribeThemeChoice(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}
