import { spawnSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { closeSync, existsSync, linkSync, mkdirSync, mkdtempSync, openSync, rmSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { fileURLToPath, pathToFileURL } from "node:url";

const root = fileURLToPath(new URL("../", import.meta.url));

function docker(args, operation, { input = "ignore", output = "pipe" } = {}) {
  const result = spawnSync("docker", args, { cwd: root, encoding: "utf8", stdio: [input, output, "pipe"] });
  if (result.error || result.status !== 0) {
    // PostgreSQL 오류에는 복원 중인 행이나 SQL 본문이 포함될 수 있다.
    throw new Error(`${operation} 실패 (종료 코드 ${result.status ?? result.error?.code ?? "미확인"}). Docker 실행 상태와 입력을 확인하세요.`);
  }
  return result.stdout?.trim();
}

export function postgresImage() {
  return docker(["compose", "config", "--images", "postgres"], "PostgreSQL 이미지 확인");
}

export function backupDatabase(outputPath, container = docker(["compose", "ps", "-q", "postgres"], "원본 DB 확인")) {
  if (!container) throw new Error("실행 중인 PostgreSQL이 없습니다. npm run db:up으로 시작하세요.");
  const path = resolve(outputPath);
  if (existsSync(path)) throw Object.assign(new Error("같은 이름의 백업이 있습니다."), { code: "EEXIST" });
  mkdirSync(dirname(path), { recursive: true, mode: 0o700 });
  const temporary = mkdtempSync(join(dirname(path), ".backup-"));
  const partial = join(temporary, "database.dump");
  try {
    const descriptor = openSync(partial, "wx", 0o600);
    try {
      docker(["exec", container, "sh", "-ec",
      'exec pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom'], "백업 생성", { output: descriptor });
    } finally {
      closeSync(descriptor);
    }
    // 완료된 파일만 공개하고 같은 이름의 기존 백업은 덮어쓰지 않는다.
    linkSync(partial, path);
    return path;
  } finally {
    rmSync(temporary, { recursive: true, force: true });
  }
}

export async function waitForPostgres(container) {
  const until = Date.now() + 60000;
  while (Date.now() < until) {
    const ready = spawnSync("docker", ["exec", container, "sh", "-ec",
      'pg_isready --host=127.0.0.1 --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --quiet'], { stdio: "ignore" });
    if (ready.status === 0) return;
    await delay(1000);
  }
  throw new Error("임시 PostgreSQL이 60초 안에 준비되지 않았습니다.");
}

export async function withRestoredBackup(path, inspect, image = postgresImage()) {
  const descriptor = openSync(resolve(path), "r");
  const name = `ypm-restore-check-${randomUUID()}`;
  let container;
  const interrupt = code => {
    try { if (container) docker(["rm", "--force", "--volumes", container], `임시 DB 제거 (${name})`); }
    finally { process.exit(code); }
  };
  const onInterrupt = () => interrupt(130);
  const onTerminate = () => interrupt(143);
  process.once("SIGINT", onInterrupt);
  process.once("SIGTERM", onTerminate);
  try {
    container = docker(["run", "--detach", "--rm", "--network", "none", "--name", name,
      "--env", "POSTGRES_HOST_AUTH_METHOD=trust", "--env", "POSTGRES_USER=postgres",
      "--env", "POSTGRES_DB=restore_check", image], "임시 DB 생성");
    await waitForPostgres(container);
    docker(["exec", "-i", container, "pg_restore", "--username=postgres", "--dbname=restore_check",
      "--no-owner", "--no-acl", "--exit-on-error", "--single-transaction"], "백업 복구", { input: descriptor, output: "ignore" });
    return await inspect(container);
  } finally {
    process.off("SIGINT", onInterrupt);
    process.off("SIGTERM", onTerminate);
    closeSync(descriptor);
    if (container) docker(["rm", "--force", "--volumes", container], `임시 DB 제거 (${name})`);
  }
}

async function main(args) {
  const [command, path, ...extra] = args;
  if (extra.length || !["backup", "verify"].includes(command) || (command === "verify" && !path)) {
    throw new Error("사용법: npm run db:backup -- [파일.dump] 또는 npm run db:verify-backup -- 파일.dump");
  }
  if (command === "backup") {
    const destination = path || join(root, ".local/backups", `youth-policy-${new Date().toISOString().replaceAll(":", "-")}.dump`);
    console.log(`백업 생성 완료: ${backupDatabase(destination)}`);
  } else {
    const tables = await withRestoredBackup(path, container => docker(["exec", container, "psql", "--username=postgres",
      "--dbname=restore_check", "-X", "-v", "ON_ERROR_STOP=1", "-Atc", "SELECT count(*) FROM pg_stat_user_tables"], "복구 결과 확인"));
    console.log(`복구 검증 완료: 테이블 ${tables}개. 임시 DB를 제거했습니다.`);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main(process.argv.slice(2)).catch(error => {
    console.error(error.code === "EEXIST" ? "같은 이름의 백업이 있습니다. 다른 파일명을 지정하세요." : error.message);
    process.exitCode = 1;
  });
}
