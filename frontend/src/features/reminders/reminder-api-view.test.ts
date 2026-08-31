import { afterEach, describe, expect, it, vi } from "vitest";
import type { components } from "@/generated/preview-api";
import { loadReminderExamples } from "@/app/dev/reminders/server/load-reminder-examples";
import { toReminderExamplesView, type ReminderExamplesResponse } from "./reminder-api-view";

const result: components["schemas"]["ReminderResultResponse"] = {
  outcome: "CANDIDATES_AVAILABLE", explanation: "인공 후보 계산 결과",
  applicationPeriod: {
    kind: "DATES", startsOnInclusive: "2026-08-20", endsOnInclusive: "2026-09-07",
    opensAtInclusive: null, openingTimeZone: null, closesAtExclusive: null, closingTimeZone: null, reason: null,
  },
  deadlineOnSeoul: "2026-09-07",
  recruitment: { status: "OPEN", explanation: "계산 시점의 서울 날짜 기준 접수 기간" },
  dates: [{ daysBeforeDeadline: 7, date: "2026-08-31", status: "TODAY_REQUIRES_SEND_TIME_CHECK" }],
  basis: {
    policyId: "sample-policy", policyRevision: "sample-revision", evaluatedAt: "2026-08-30T15:30:00Z",
    evaluatedOnSeoul: "2026-08-31", sourceReference: "sample-source", sourceLocation: "인공 신청기간", sourceExcerpt: null,
  },
};

function response(value = result): ReminderExamplesResponse {
  return { dataKind: "SYNTHETIC", examples: [{ id: "sample", label: "인공 예시", description: "표시 변환 점검", result: value }] };
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("개발 API 표시 변환", () => {
  it("날짜·오늘 상태·숫자 간격과 계산 근거를 변환 없이 유지한다", () => {
    const view = toReminderExamplesView(response())[0].result;
    expect(view.applicationPeriod).toEqual({ kind: "DATES", startsOnInclusive: "2026-08-20", endsOnInclusive: "2026-09-07" });
    expect(view.dates).toEqual(result.dates);
    expect(view.deadlineOnSeoul).toBe("2026-09-07");
    expect(view.basis).toEqual(result.basis);
    expect(view.recruitment).toEqual({ label: "접수 기간", explanation: result.recruitment.explanation });
  });

  it("정확한 시작·마감 시각의 시간대와 별도 서울 날짜를 보존한다", () => {
    const times: components["schemas"]["ApplicationPeriodResponse"] = {
      kind: "TIMES", startsOnInclusive: null, endsOnInclusive: null, reason: null,
      opensAtInclusive: "2026-08-20T00:00:00Z", openingTimeZone: "UTC",
      closesAtExclusive: "2026-09-06T18:00:00Z", closingTimeZone: "UTC",
    };
    const view = toReminderExamplesView(response({ ...result, applicationPeriod: times }))[0].result;
    expect(view.applicationPeriod).toEqual({
      kind: "TIMES", opensAtInclusive: times.opensAtInclusive, openingTimeZone: "UTC",
      closesAtExclusive: times.closesAtExclusive, closingTimeZone: "UTC",
    });
    expect(view.deadlineOnSeoul).toBe("2026-09-07");
  });

  it("미확인 이유·명시적 null·빈 후보를 보존한다", () => {
    const view = toReminderExamplesView(response({
      ...result, outcome: "NO_CONFIRMED_DEADLINE", deadlineOnSeoul: null, dates: [],
      applicationPeriod: { ...result.applicationPeriod, kind: "UNRESOLVED", startsOnInclusive: null, endsOnInclusive: null, reason: "마감 날짜가 충돌합니다." },
      recruitment: { status: "UNKNOWN", explanation: "기간 미확인" },
    }))[0].result;
    expect(view.applicationPeriod).toEqual({ kind: "UNRESOLVED", reason: "마감 날짜가 충돌합니다." });
    expect(view.deadlineOnSeoul).toBeNull();
    expect(view.basis.sourceExcerpt).toBeNull();
    expect(view.dates).toEqual([]);
    expect(view.outcome).toBe("NO_CONFIRMED_DEADLINE");
  });

  it("필수 기간 값과 지원하지 않는 간격을 가짜 값으로 보완하지 않는다", () => {
    expect(() => toReminderExamplesView(response({ ...result, applicationPeriod: { ...result.applicationPeriod, endsOnInclusive: null } }))).toThrow();
    expect(() => toReminderExamplesView(response({ ...result, dates: [{ ...result.dates[0], daysBeforeDeadline: 2 }] }))).toThrow();
  });
});

describe("개발 전용 서버 조회", () => {
  it("고정 루프백 주소를 캐시 없이 조회하고 서버 결과를 전달한다", async () => {
    vi.stubEnv("NODE_ENV", "development");
    const fetchMock = vi.fn().mockResolvedValue(Response.json(response()));
    vi.stubGlobal("fetch", fetchMock);
    const loaded = await loadReminderExamples();
    expect(loaded).toEqual({ status: "available", examples: toReminderExamplesView(response()) });
    expect(fetchMock).toHaveBeenCalledWith("http://127.0.0.1:8081/api/dev/reminder-examples", expect.objectContaining({
      cache: "no-store", redirect: "error", signal: expect.any(AbortSignal),
    }));
  });

  it("HTTP·응답 해석·연결 실패를 후보 없음이나 고정 자료로 바꾸지 않는다", async () => {
    vi.stubEnv("NODE_ENV", "development");
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response("내부 오류 원문", { status: 503 }))
      .mockResolvedValueOnce(new Response("<html>내부 오류</html>", { headers: { "Content-Type": "application/json" } }))
      .mockResolvedValueOnce(Response.json({ dataKind: "SYNTHETIC", examples: [] }))
      .mockRejectedValueOnce(new Error("내부 연결 오류"));
    vi.stubGlobal("fetch", fetchMock);
    for (let i = 0; i < 4; i++) expect(await loadReminderExamples()).toEqual({ status: "unavailable" });
  });

  it("운영 모드에서는 서버를 호출하지 않는다", async () => {
    vi.stubEnv("NODE_ENV", "production");
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    expect(await loadReminderExamples()).toEqual({ status: "unavailable" });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
