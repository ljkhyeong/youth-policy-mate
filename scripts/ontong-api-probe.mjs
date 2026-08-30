import { randomUUID } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { parseArgs } from "node:util";

const ENDPOINT = "https://www.youthcenter.go.kr/go/ythip/getPlcy";
const MAX_RESPONSE_BYTES = 1024 * 1024;
const OUTPUT_DIRECTORY = fileURLToPath(new URL("../.local/ontong-api/", import.meta.url));

class ProbeError extends Error {}

export async function probeOntong({ apiKey, policyNumber, region }, fetchResponse = fetch) {
  if (!apiKey?.trim()) {
    throw new ProbeError("ONTONG_API_KEY가 없습니다. 발급된 키를 로컬 .env에 설정하세요.");
  }
  if (region !== undefined && !/^\d{5}$/.test(region)) {
    throw new ProbeError("--region에는 5자리 지역 코드 하나를 입력하세요.");
  }
  if (policyNumber !== undefined && !policyNumber.trim()) {
    throw new ProbeError("--policy-number에는 확인할 정책번호를 입력하세요.");
  }

  const parameters = { pageNum: "1", pageSize: "1", pageType: policyNumber ? "2" : "1", rtnType: "json" };
  if (policyNumber) parameters.plcyNo = policyNumber;
  if (region) parameters.zipCd = region;
  const url = new URL(ENDPOINT);
  url.search = new URLSearchParams({ ...parameters, apiKeyNm: apiKey }).toString();

  try {
    const response = await fetchResponse(url, {
      headers: { Accept: "application/json" },
      redirect: "error",
      signal: AbortSignal.timeout(15_000),
    });
    if (response.status !== 200) {
      await response.body?.cancel();
      throw new ProbeError(`HTTP ${response.status}: 응답을 저장하지 않았습니다. 승인 상태와 호출 조건을 확인하세요.`);
    }

    const chunks = [];
    let byteLength = 0;
    for await (const chunk of response.body) {
      byteLength += chunk.byteLength;
      if (byteLength > MAX_RESPONSE_BYTES) {
        throw new ProbeError("응답이 점검 도구의 1 MiB 제한을 넘었습니다. 저장하지 않았습니다.");
      }
      chunks.push(chunk);
    }
    const rawBody = new TextDecoder("utf-8", { fatal: true }).decode(Buffer.concat(chunks));
    let parsedBody;
    try {
      parsedBody = JSON.parse(rawBody);
    } catch {
      throw new ProbeError("HTTP 200이지만 JSON 응답이 아닙니다. 본문은 출력하거나 저장하지 않았습니다.");
    }

    const capture = {
      capturedAt: new Date().toISOString(),
      reviewStatus: "UNVERIFIED",
      request: { endpoint: ENDPOINT, parameters },
      response: { status: response.status, contentType: response.headers.get("content-type"), byteLength, rawBody },
    };
    // JSON 이스케이프와 URL 인코딩으로 반사된 키도 원본 보관에서 제외한다.
    const encodedKey = url.searchParams.toString().split("apiKeyNm=")[1];
    const keyVariants = [apiKey, encodeURIComponent(apiKey), encodedKey];
    const containsKey = (value) => {
      if (typeof value === "string") return keyVariants.some((key) => value.includes(key));
      return value !== null && typeof value === "object"
        && Object.entries(value).some(([key, item]) => containsKey(key) || containsKey(item));
    };
    if (containsKey(capture) || containsKey(parsedBody)) {
      throw new ProbeError("응답 또는 요청 조건에 인증키가 포함되어 저장을 중단했습니다.");
    }
    return capture;
  } catch (error) {
    if (error instanceof ProbeError) throw error;
    throw new ProbeError("응답을 확보하지 못했습니다. 연결·본문 읽기·15초 시간 제한·리다이렉트 여부를 확인하세요.");
  }
}

export async function saveCapture(capture, directory = OUTPUT_DIRECTORY) {
  await mkdir(directory, { recursive: true, mode: 0o700 });
  const filename = join(directory, `${Date.now()}-${randomUUID()}.json`);
  await writeFile(filename, `${JSON.stringify(capture, null, 2)}\n`, { flag: "wx", mode: 0o600 });
  return filename;
}

async function main() {
  let values;
  try {
    ({ values } = parseArgs({
      options: {
        "policy-number": { type: "string" },
        region: { type: "string" },
        help: { type: "boolean" },
      },
    }));
  } catch {
    console.error("지원하지 않는 인수입니다. npm run probe:ontong -- --help로 사용법을 확인하세요.");
    process.exitCode = 1;
    return;
  }
  if (values.help) {
    console.log("목록 1건: npm run probe:ontong\n상세 1건: npm run probe:ontong -- --policy-number 정책번호\n지역 목록 1건: npm run probe:ontong -- --region 11000\n인증키는 인수가 아닌 로컬 .env의 ONTONG_API_KEY로 설정하세요.");
    return;
  }

  try {
    const capture = await probeOntong({
      apiKey: process.env.ONTONG_API_KEY,
      policyNumber: values["policy-number"],
      region: values.region,
    });
    const filename = await saveCapture(capture);
    console.log(`미검증 JSON 응답 저장: ${filename}\n정상 정책 응답 여부와 필드 구조는 파일 내용을 확인해야 합니다.`);
  } catch (error) {
    console.error(error instanceof ProbeError ? error.message : "점검 결과를 저장하지 못했습니다. 로컬 저장 경로의 권한과 용량을 확인하세요.");
    process.exitCode = 1;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await main();
}
