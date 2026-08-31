import { afterEach, describe, expect, it, vi } from "vitest";
import { ELIGIBILITY_EXAMPLES } from "@/app/dev/eligibility/eligibility-preview-data";
import { loadEligibilityExamples } from "@/app/dev/eligibility/server/load-eligibility-examples";
import { toEligibilityExamplesView, type EligibilityExamplesResponse } from "./eligibility-api-view";

// 기존 인공 자료를 전송 계약에 맞춰 사용한다. 서버 비교기의 입력 행렬을 다시 만들지 않는다.
function response(index = 2): EligibilityExamplesResponse {
  const example = ELIGIBILITY_EXAMPLES[index];
  return { dataKind: "SYNTHETIC", examples: [{
    id: example.id, label: example.label, description: example.description, result: example.result,
    recruitment: { status: index === 0 ? "CLOSED" : "OPEN", explanation: "서버에서 받은 별도 모집 안내" },
  }] };
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("자격 API 표시 변환", () => {
  it("불충족 항목이 있어도 서버의 전체 보류 상태·검토 이슈·개정과 근거를 보존한다", () => {
    const source = response();
    const view = toEligibilityExamplesView(source)[0];
    expect(view.result).toEqual(source.examples[0].result);
    expect(view.result.status).toBe("NEEDS_REVIEW");
    expect(view.result.conditions[0].outcome).toBe("NOT_MET");
    expect(view.result.conditions[2].uncertainty).toBe("UNRESOLVED_POLICY");
    expect(view.result.conditions[2].evidence.excerpt).toBeNull();
    expect(view.recruitment).toEqual({ label: "접수 기간", explanation: "서버에서 받은 별도 모집 안내" });
  });

  it("전체 충족·모집 종료와 비교하지 않은 값·날짜의 null을 유지한다", () => {
    const view = toEligibilityExamplesView(response(0))[0];
    expect(view.result.status).toBe("ELIGIBLE");
    expect(view.recruitment.label).toBe("모집 마감");
    expect(view.result.conditions[2]).toMatchObject({ comparedValue: null, referenceDate: null, uncertainty: null });
    expect(view.result.conditions[3].referenceDate).toBe("2025-12-31");
  });

  it("항목 결과와 미확인 원인이 맞지 않는 응답을 임의로 보완하지 않는다", () => {
    const source = response();
    const example = source.examples[0];
    const invalid: EligibilityExamplesResponse = { ...source, examples: [{ ...example,
      result: { ...example.result, conditions: [{ ...example.result.conditions[2], uncertainty: null }] },
    }] };
    expect(() => toEligibilityExamplesView(invalid)).toThrow("항목 결과와 미확인 원인");
  });
});

describe("개발 전용 자격 서버 조회", () => {
  it("개인정보 없이 고정 주소를 캐시 없이 조회하고 생성 계약을 표시 모델로 변환한다", async () => {
    vi.stubEnv("NODE_ENV", "development");
    const fetchMock = vi.fn().mockResolvedValue(Response.json(response()));
    vi.stubGlobal("fetch", fetchMock);
    expect(await loadEligibilityExamples()).toEqual({ status: "available", examples: toEligibilityExamplesView(response()) });
    expect(fetchMock).toHaveBeenCalledWith("http://127.0.0.1:8081/api/dev/eligibility-examples", {
      cache: "no-store", redirect: "error", signal: expect.any(AbortSignal), headers: { Accept: "application/json" },
    });
  });

  it("HTTP·해석·빈 예시·연결 실패는 판정 결과나 오프라인 자료로 대체하지 않는다", async () => {
    vi.stubEnv("NODE_ENV", "development");
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response("내부 오류 원문", { status: 503 }))
      .mockResolvedValueOnce(new Response("<html>오류</html>", { headers: { "Content-Type": "application/json" } }))
      .mockResolvedValueOnce(Response.json({ dataKind: "SYNTHETIC", examples: [] }))
      .mockRejectedValueOnce(new Error("내부 연결 오류"));
    vi.stubGlobal("fetch", fetchMock);
    for (let i = 0; i < 4; i++) expect(await loadEligibilityExamples()).toEqual({ status: "unavailable" });
  });

  it("운영 모드에서는 자격 서버를 호출하지 않는다", async () => {
    vi.stubEnv("NODE_ENV", "production");
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    expect(await loadEligibilityExamples()).toEqual({ status: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
