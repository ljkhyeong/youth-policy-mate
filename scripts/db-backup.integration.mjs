import assert from "node:assert/strict";
import { execFileSync, spawn } from "node:child_process";
import { once } from "node:events";
import { mkdtempSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { backupDatabase, postgresImage, waitForPostgres, withRestoredBackup } from "./db-backup.mjs";

const docker = (...args) => execFileSync("docker", args, { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
const sql = (container, database, query, user = "postgres") => docker("exec", container, "psql", "-U", user, "-d", database,
  "-X", "-v", "ON_ERROR_STOP=1", "-Atc", query);
const restoreContainers = () => docker("ps", "-aq", "--filter", "name=ypm-restore-check-").split("\n").filter(Boolean).sort();

test("백업·복구가 데이터·제약·시퀀스를 유지하고 실패·중단 시 임시 DB를 정리한다", { timeout: 180000 }, async t => {
  const directory = mkdtempSync(join(tmpdir(), "ypm-backup-test-"));
  const image = postgresImage();
  const source = docker("run", "--detach", "--rm", "--network", "none", "--env", "POSTGRES_HOST_AUTH_METHOD=trust",
    "--env", "POSTGRES_USER=backup_test", "--env", "POSTGRES_DB=backup_test", image);
  t.after(() => { docker("rm", "--force", "--volumes", source); rmSync(directory, { recursive: true, force: true }); });
  await waitForPostgres(source);
  sql(source, "backup_test", `CREATE TABLE backup_fixture (id serial PRIMARY KEY, content text NOT NULL, encrypted bytea);
    INSERT INTO backup_fixture(content, encrypted) VALUES ('검증용 회원 데이터', decode('00ffab12', 'hex'));
    CREATE TABLE backup_links (fixture_id integer REFERENCES backup_fixture(id));
    INSERT INTO backup_links VALUES (1);`, "backup_test");
  const destination = join(directory, "backup.dump");
  const originalContainers = restoreContainers();

  await t.test("원본을 읽어 비공개 파일로 생성하고 실제 데이터와 제약을 복구한다", async () => {
    assert.equal(backupDatabase(destination, source), destination);
    assert.equal(statSync(destination).mode & 0o777, 0o600);
    assert.equal(readFileSync(destination).subarray(0, 5).toString(), "PGDMP");
    const result = await withRestoredBackup(destination, container => {
      const isolation = JSON.parse(docker("inspect", container))[0];
      assert.equal(isolation.HostConfig.NetworkMode, "none");
      assert.deepEqual(isolation.HostConfig.PortBindings || {}, {});
      assert.deepEqual(isolation.HostConfig.Binds || [], []);
      assert.equal(sql(container, "restore_check", "SELECT content || ':' || encode(encrypted, 'hex') FROM backup_fixture WHERE id = 1"),
        "검증용 회원 데이터:00ffab12");
      assert.equal(sql(container, "restore_check", "INSERT INTO backup_fixture(content) VALUES ('복원 후 추가') RETURNING id").split("\n")[0], "2");
      assert.throws(() => sql(container, "restore_check", "INSERT INTO backup_links VALUES (999)"));
      return sql(container, "restore_check", "SELECT count(*) FROM backup_links");
    }, image);
    assert.equal(result, "1");
    assert.equal(sql(source, "backup_test", "SELECT count(*) FROM backup_fixture", "backup_test"), "1");
    assert.deepEqual(restoreContainers(), originalContainers);
  });

  await t.test("기존 백업을 덮어쓰지 않고 백업 실패의 임시 파일도 제거한다", () => {
    const original = readFileSync(destination);
    assert.throws(() => backupDatabase(destination, source), { code: "EEXIST" });
    assert.deepEqual(readFileSync(destination), original);
    const failedPath = join(directory, "failed.dump");
    assert.throws(() => backupDatabase(failedPath, "ypm-no-such-backup-container"), /백업 생성 실패/);
    assert.deepEqual(readdirSync(directory), ["backup.dump"]);
  });

  await t.test("손상된 파일의 복구는 실패하고 데이터 내용을 로그로 내보내지 않는다", async () => {
    const corrupt = join(directory, "corrupt.dump");
    writeFileSync(corrupt, "검증용 비공개 값");
    await assert.rejects(withRestoredBackup(corrupt, () => assert.fail("복구 실패 후 검증을 실행하면 안 됩니다."), image), error => {
      assert.match(error.message, /백업 복구 실패/);
      assert.doesNotMatch(error.message, /검증용 비공개 값/);
      return true;
    });
    assert.deepEqual(restoreContainers(), originalContainers);
  });

  await t.test("검증 중 종료 신호를 받으면 임시 DB와 볼륨을 제거한다", async subtest => {
    const moduleUrl = new URL("./db-backup.mjs", import.meta.url).href;
    const child = spawn(process.execPath, ["--input-type=module", "-e", `
      import { withRestoredBackup } from ${JSON.stringify(moduleUrl)};
      await withRestoredBackup(${JSON.stringify(destination)}, async container => {
        console.log(container);
        await new Promise(resolve => setTimeout(resolve, 60000));
      }, ${JSON.stringify(image)});
    `], { stdio: ["ignore", "pipe", "pipe"] });
    subtest.after(() => { if (child.exitCode === null) child.kill("SIGTERM"); });
    const exited = once(child, "exit");
    const ready = await Promise.race([
      once(child.stdout, "data").then(([data]) => data.toString().trim()),
      exited.then(() => { throw new Error("중단 검사 준비 중 복구 프로세스가 종료됐습니다."); }),
    ]);
    const volumes = JSON.parse(docker("inspect", ready))[0].Mounts.filter(mount => mount.Type === "volume").map(mount => mount.Name);
    child.kill("SIGTERM");
    assert.equal((await exited)[0], 143);
    assert.deepEqual(restoreContainers(), originalContainers);
    for (const volume of volumes) assert.throws(() => docker("volume", "inspect", volume));
  });
});
