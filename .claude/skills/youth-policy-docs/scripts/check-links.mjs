#!/usr/bin/env node
// Markdown의 상대 경로 링크와 제목 앵커를 확인한다. 외부 URL은 확인하지 않는다.
// 사용: node check-links.mjs [파일.md ...] — 인자가 없으면 Git이 추적하는 모든 .md를 검사한다.
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, statSync } from "node:fs";
import { dirname, resolve, relative } from "node:path";

const root = execFileSync("git", ["rev-parse", "--show-toplevel"], { encoding: "utf8" }).trim();
const files = process.argv.slice(2).length
  ? process.argv.slice(2).map(file => resolve(file))
  : execFileSync("git", ["ls-files", "-z", "*.md"], { cwd: root, encoding: "utf8" })
    .split("\0").filter(Boolean).map(file => resolve(root, file));

// 코드 블록과 인라인 코드 안의 링크·제목은 제외한다.
function proseLines(text) {
  let fence = null;
  return text.split("\n").map(line => {
    const marker = line.match(/^\s*(`{3,}|~{3,})/);
    if (marker) {
      if (!fence) fence = marker[1][0];
      else if (marker[1][0] === fence) fence = null;
      return "";
    }
    return fence ? "" : line.replace(/`[^`]*`/g, "");
  });
}

// GitHub 제목 앵커 규칙: 소문자화, 문자·숫자·공백·하이픈·밑줄 외 제거, 공백은 하이픈, 중복은 -1, -2.
const anchorCache = new Map();
function anchors(file) {
  if (anchorCache.has(file)) return anchorCache.get(file);
  const seen = new Map();
  const result = new Set();
  for (const line of proseLines(readFileSync(file, "utf8"))) {
    const heading = line.match(/^#{1,6}\s+(.+?)\s*#*\s*$/);
    if (!heading) continue;
    const text = heading[1].replace(/\[([^\]]*)\]\([^)]*\)/g, "$1").replace(/<[^>]+>/g, "");
    const base = text.toLowerCase().replace(/[^\p{L}\p{N}\p{M} _-]/gu, "").replace(/ /g, "-");
    const count = seen.get(base) ?? 0;
    seen.set(base, count + 1);
    result.add(count ? `${base}-${count}` : base);
  }
  anchorCache.set(file, result);
  return result;
}

const problems = [];
for (const file of files) {
  if (!existsSync(file)) { problems.push(`${relative(root, file)}: 파일 없음`); continue; }
  proseLines(readFileSync(file, "utf8")).forEach((line, index) => {
    for (const match of line.matchAll(/!?\[[^\]]*\]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)/g)) {
      const link = match[1];
      if (/^[a-z][a-z0-9+.-]*:/i.test(link)) continue;
      const [path, anchor] = link.split("#");
      let target = file;
      if (path) {
        try { target = resolve(dirname(file), decodeURIComponent(path)); } catch { target = resolve(dirname(file), path); }
      }
      const where = `${relative(root, file)}:${index + 1}`;
      if (!existsSync(target)) { problems.push(`${where} → ${link} (경로 없음)`); continue; }
      if (anchor && target.endsWith(".md") && statSync(target).isFile()
        && !anchors(target).has(decodeURIComponent(anchor).toLowerCase())) {
        problems.push(`${where} → ${link} (제목 앵커 없음)`);
      }
    }
  });
}

if (problems.length) {
  console.log(problems.join("\n"));
  console.log(`링크 문제 ${problems.length}건 / 검사한 문서 ${files.length}개`);
  process.exit(1);
}
console.log(`링크 문제 없음 / 검사한 문서 ${files.length}개`);
