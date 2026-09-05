import { createHash, randomUUID } from "node:crypto";
import { execFileSync, spawnSync } from "node:child_process";
import { closeSync, existsSync, lstatSync, mkdirSync, openSync, readFileSync, readdirSync, readlinkSync, renameSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const recordDirectory = ".local/verification";

function git(root, ...args) {
  return execFileSync("git", args, { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trimEnd();
}

function snapshot(root) {
  const paths = git(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z").split("\0").filter(Boolean);
  const files = {};
  for (const path of [...new Set(paths)].sort()) {
    if (path.startsWith(`${recordDirectory}/`)) continue;
    try {
      const fullPath = join(root, path);
      const stat = lstatSync(fullPath);
      const content = stat.isSymbolicLink() ? readlinkSync(fullPath) : readFileSync(fullPath);
      files[path] = createHash("sha256").update(`${stat.mode}:`).update(content).digest("hex");
    } catch (error) {
      if (error.code !== "ENOENT") throw error;
    }
  }
  return files;
}

function changedFiles(before, after) {
  return [...new Set([...Object.keys(before), ...Object.keys(after)])].filter(path => before[path] !== after[path]);
}

function save(path, record) {
  writeFileSync(`${path}.tmp`, `${JSON.stringify(record, null, 2)}\n`);
  renameSync(`${path}.tmp`, path);
}

export function runVerification(root, script, args = []) {
  const scripts = JSON.parse(readFileSync(join(root, "package.json"), "utf8")).scripts;
  if (!/^(test|check|compile|package|build):/.test(script) || !scripts[script]) {
    throw new Error("package.json에 있는 test/check/compile/package/build 스크립트를 지정하세요.");
  }
  const directory = join(root, recordDirectory);
  mkdirSync(directory, { recursive: true });
  const id = `${Date.now()}-${randomUUID().slice(0, 8)}`;
  const recordPath = join(directory, `${id}.json`);
  const logPath = join(directory, `${id}.log`);
  let head = null;
  try { head = git(root, "rev-parse", "--verify", "HEAD"); } catch { /* 최초 커밋 전에도 검증할 수 있다. */ }
  const record = {
    id, script, args, definition: scripts[script], head, node: process.version,
    startedAt: new Date().toISOString(), status: "running", inputs: snapshot(root),
    log: `${recordDirectory}/${id}.log`,
  };
  save(recordPath, record);
  console.log(`검증 시작: npm run ${script} — 로그 ${record.log}`);
  const started = Date.now();
  const log = openSync(logPath, "w");
  let result;
  try {
    result = spawnSync("npm", ["run", script, ...(args.length ? ["--", ...args] : [])], {
      cwd: root, stdio: ["ignore", log, log],
    });
  } finally {
    closeSync(log);
  }
  record.exitCode = result.status ?? 1;
  record.signal = result.signal;
  record.durationMs = Date.now() - started;
  record.finishedAt = new Date().toISOString();
  record.changedDuringRun = changedFiles(record.inputs, snapshot(root));
  record.status = record.exitCode !== 0 ? "failed" : record.changedDuringRun.length ? "changed" : "passed";
  save(recordPath, record);
  console.log(`${record.status === "passed" ? "통과" : record.status === "changed" ? "실행 중 파일 변경 — 재확인 필요" : "실패"}: ${script} (${(record.durationMs / 1000).toFixed(1)}초)`);
  if (record.status === "failed") {
    if (result.error) console.error(result.error.message);
    console.error(readFileSync(logPath, "utf8").trimEnd().split("\n").slice(-20).join("\n"));
  }
  if (record.changedDuringRun.length) console.log(`변경 파일: ${record.changedDuringRun.slice(0, 10).join(", ")}`);
  return { record, exitCode: record.exitCode || (record.status === "changed" ? 2 : 0) };
}

export function verificationStatus(root, script) {
  const directory = join(root, recordDirectory);
  if (!existsSync(directory)) return [];
  const current = snapshot(root);
  const records = [];
  for (const name of readdirSync(directory).filter(name => name.endsWith(".json")).sort().reverse()) {
    const record = JSON.parse(readFileSync(join(directory, name), "utf8"));
    if (script && record.script !== script) continue;
    records.push({ ...record, changedFiles: changedFiles(record.inputs, current), nodeMatches: record.node === process.version });
    if (records.length === 5) break;
  }
  return records;
}

function main() {
  const root = git(process.cwd(), "rev-parse", "--show-toplevel");
  const [script = "status", ...args] = process.argv.slice(2);
  if (script === "status") {
    const records = verificationStatus(root, args[0]);
    if (!records.length) console.log("검증 기록 없음. npm run verify -- <검증 스크립트> [-- 인자]로 실행하세요.");
    for (const record of records) {
      const label = { passed: "통과", failed: "실패", changed: "실행 중 변경", running: "미완료" }[record.status];
      console.log(`${record.startedAt} | ${record.script} ${record.args.join(" ")} | ${label}`);
      console.log(`  현재 파일: ${record.changedFiles.length ? `${record.changedFiles.length}개 변경 (${record.changedFiles.slice(0, 8).join(", ")})` : "동일"} | Node: ${record.nodeMatches ? "동일" : "변경"} | 로그: ${record.log}`);
    }
    return 0;
  }
  return runVerification(root, script, args[0] === "--" ? args.slice(1) : args).exitCode;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try { process.exitCode = main(); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
