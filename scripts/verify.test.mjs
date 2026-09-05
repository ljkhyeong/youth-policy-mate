import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { runVerification, verificationStatus } from "./verify.mjs";

function fixture(t, source) {
  const root = mkdtempSync(join(tmpdir(), "ypm-verification-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  execFileSync("git", ["init", "--quiet", root]);
  writeFileSync(join(root, ".gitignore"), ".local/verification/\n");
  writeFileSync(join(root, "package.json"), JSON.stringify({ scripts: { "test:sample": "node sample.cjs" } }));
  writeFileSync(join(root, "sample.cjs"), source);
  execFileSync("git", ["add", "."], { cwd: root });
  return root;
}

test("성공 기록에 명령 인자를 보존하고 수정·신규·삭제 파일을 다음 조회에서 구분한다", t => {
  const root = fixture(t, "console.log(process.argv[2]);\n");
  const { record, exitCode } = runVerification(root, "test:sample", ["공백 있는 인자"]);
  assert.equal(exitCode, 0);
  assert.equal(record.status, "passed");
  assert.match(readFileSync(join(root, record.log), "utf8"), /공백 있는 인자/);
  assert.deepEqual(verificationStatus(root)[0].changedFiles, []);
  writeFileSync(join(root, "sample.cjs"), "console.log('변경');\n");
  writeFileSync(join(root, "new.js"), "// 새 파일\n");
  rmSync(join(root, ".gitignore"));
  const status = verificationStatus(root, "test:sample")[0];
  assert.deepEqual(status.changedFiles.sort(), [".gitignore", "new.js", "sample.cjs"]);
  assert.deepEqual(status.args, ["공백 있는 인자"]);
});

test("실패한 검증은 종료 코드와 로그를 보존하고 성공으로 기록하지 않는다", t => {
  const root = fixture(t, "console.error('검증 실패 예시'); process.exit(7);\n");
  const { record, exitCode } = runVerification(root, "test:sample");
  assert.equal(exitCode, 7);
  assert.equal(record.status, "failed");
  assert.equal(verificationStatus(root)[0].status, "failed");
  assert.match(readFileSync(join(root, record.log), "utf8"), /검증 실패 예시/);
});

test("검증 중 입력 파일이 바뀌면 명령이 성공해도 재확인 상태로 끝낸다", t => {
  const root = fixture(t, "require('node:fs').writeFileSync('changed.js', '// 실행 중 변경');\n");
  const { record, exitCode } = runVerification(root, "test:sample");
  assert.equal(exitCode, 2);
  assert.equal(record.exitCode, 0);
  assert.equal(record.status, "changed");
  assert.deepEqual(record.changedDuringRun, ["changed.js"]);
});
