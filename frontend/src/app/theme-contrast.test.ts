import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

// globals.css의 :root 토큰(밝은 화면)과 그 안의 @variant dark 블록(어두운 화면)을 읽는다.
const css = readFileSync(new URL("./globals.css", import.meta.url), "utf8");
const lightStart = css.indexOf(":root {");
const darkStart = css.indexOf("@variant dark {", lightStart);
const darkEnd = css.indexOf("}", darkStart);
const readTokens = (block: string) =>
  new Map([...block.matchAll(/--([a-z-]+):\s*([^;]+);/g)].map(([, name, value]) => [name, value.trim()]));
const light = readTokens(css.slice(lightStart, darkStart));
const darkOverrides = readTokens(css.slice(darkStart, darkEnd));
const themes = new Map([["밝은", light], ["어두운", new Map([...light, ...darkOverrides])]]);

// 실제 화면에서 겹치는 글자와 바탕. brand-line은 선택 영역, danger-tint는 오류 입력칸 바탕이다.
const textPairs = [
  ["ink", "page"], ["ink-soft", "page"], ["muted", "page"], ["pen", "page"], ["pen-strong", "page"],
  ["stamp", "page"], ["stamp-before", "page"], ["stamp-unsure", "page"], ["stamp-closed", "page"],
  ["positive", "page"], ["caution", "page"], ["danger", "page"],
  ["ink", "surface-sunken"], ["muted", "surface-sunken"], ["pen", "surface-sunken"],
  ["on-brand", "brand"], ["on-brand", "brand-strong"], ["brand-strong", "brand-tint"],
  ["caution", "caution-tint"], ["danger", "danger-tint"], ["ink", "danger-tint"], ["positive", "positive-tint"],
  ["ink", "marker"], ["ink", "brand-line"],
] as const;
// 입력 테두리와 키보드 초점은 바탕과 3:1 이상이어야 한다.
const boundaryPairs = [["line-strong", "page"], ["focus-ring", "page"], ["danger-line", "page"]] as const;

type Rgba = [number, number, number, number];

function parseColor(value: string | undefined): Rgba {
  const hex = value?.match(/^#([0-9a-f]{6})$/i)?.[1];
  if (hex) return [0, 2, 4].map(i => parseInt(hex.slice(i, i + 2), 16)).concat(1) as Rgba;
  const rgba = value?.match(/^rgba?\(([^)]+)\)$/)?.[1].split(",").map(Number);
  if (rgba) return [rgba[0], rgba[1], rgba[2], rgba[3] ?? 1];
  throw new Error(`색 값을 읽을 수 없습니다: ${value}`);
}

// 반투명 색(형광펜 띠)은 종이 바탕에 겹친 색으로 비교한다.
function solidColor(tokens: Map<string, string>, name: string): Rgba {
  const [r, g, b, a] = parseColor(tokens.get(name));
  const [pr, pg, pb] = parseColor(tokens.get("page"));
  return [r * a + pr * (1 - a), g * a + pg * (1 - a), b * a + pb * (1 - a), 1];
}

const luminance = ([r, g, b]: Rgba) => [r, g, b]
  .map(channel => channel / 255)
  .map(c => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  .reduce((sum, c, i) => sum + c * [0.2126, 0.7152, 0.0722][i], 0);

function contrast(tokens: Map<string, string>, foreground: string, background: string) {
  const [high, low] = [luminance(solidColor(tokens, foreground)), luminance(solidColor(tokens, background))].sort((a, b) => b - a);
  return (high + 0.05) / (low + 0.05);
}

const failures = (tokens: Map<string, string>, pairs: readonly (readonly [string, string])[], minimum: number) => pairs
  .map(([foreground, background]) => ({ pair: `${foreground} / ${background}`, ratio: contrast(tokens, foreground, background) }))
  .filter(({ ratio }) => ratio < minimum)
  .map(({ pair, ratio }) => `${pair} ${ratio.toFixed(2)}:1`);

describe.each([...themes])("%s 화면 색", (_, tokens) => {
  it("글자는 바탕과 4.5:1, 입력 테두리·초점은 3:1 이상이다", () => {
    expect(failures(tokens, textPairs, 4.5)).toEqual([]);
    expect(failures(tokens, boundaryPairs, 3)).toEqual([]);
  });
});

it("어두운 화면이 밝은 화면의 색·그림 토큰을 모두 바꾼다", () => {
  const visualTokens = [...light].filter(([, value]) => /^(#|rgba?\(|url\()/.test(value)).map(([name]) => name);
  expect(visualTokens.length).toBeGreaterThan(20);
  expect(visualTokens.filter(name => !darkOverrides.has(name))).toEqual([]);
});
