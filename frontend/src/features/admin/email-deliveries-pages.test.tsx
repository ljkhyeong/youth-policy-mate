import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import EmailDeliveriesPage from "@/app/admin/collection-exceptions/email/page";
import { loadEmailDeliveries, type EmailDeliveryPage } from "./load-collection-exceptions";

vi.mock("./load-collection-exceptions", async original => ({
  ...await original<typeof import("./load-collection-exceptions")>(), loadEmailDeliveries: vi.fn(),
}));
afterEach(() => vi.resetAllMocks());
const at = "2026-09-12T00:00:00Z";
const item: EmailDeliveryPage["items"][number] = {
  id: "10000000-0000-0000-0000-000000000001", kind: "VERIFICATION", state: "UNKNOWN", provider: "resend",
  providerMessageId: null, createdAt: at, startedAt: at, finishedAt: at, providerEventAt: null,
};
const data: EmailDeliveryPage = { items: [item], page: 2, pageSize: 20, total: 41, hasNext: true,
  since: at, checkedAt: at, sendingEnabled: false, provider: "resend", summary: { total: 60, failed: 3, unknown: 41 } };

describe("관리자 이메일 발송 화면", () => {
  it("공급자 발송 ID가 있는 Resend 기록에만 상태 조회 버튼을 제공한다", async () => {
    vi.mocked(loadEmailDeliveries).mockResolvedValue({ status: "available", data: { ...data, items: [
      { ...item, providerMessageId: "20000000-0000-0000-0000-000000000002" },
      { ...item, id: "missing" }, { ...item, id: "smtp", provider: "smtp", providerMessageId: "20000000-0000-0000-0000-000000000002" },
    ] } });
    const html = renderToStaticMarkup(await EmailDeliveriesPage({ searchParams: Promise.resolve({}) }));
    expect(html.match(/>Resend 상태 조회<\/button>/g)).toHaveLength(1);
    expect(html).toContain("발송 ID가 없어 Resend 상태를 조회할 수 없습니다.");
    expect(html).not.toContain("조회 시각:");
  });
  it("조회 조건과 기간 전체 요약을 구분하고 결과 미확인 상태에 재발송 버튼을 만들지 않는다", async () => {
    vi.mocked(loadEmailDeliveries).mockResolvedValue({ status: "available", data });
    const html = renderToStaticMarkup(await EmailDeliveriesPage({ searchParams: Promise.resolve({ page: "2", days: "30", state: "UNKNOWN", kind: "VERIFICATION" }) }));
    expect(loadEmailDeliveries).toHaveBeenCalledWith(2, 30, "UNKNOWN", "VERIFICATION");
    for (const text of ["이메일 발송 꺼짐", "기간 내 전체 요청", "60건", "조회 결과 41건", "결과 미확인", "공급자 기록을 확인하기 전에는 다시 보내지 마세요.", "기록 없음"]) expect(html).toContain(text);
    expect(html).toContain("?page=3&amp;days=30&amp;state=UNKNOWN&amp;kind=VERIFICATION");
    expect(html).not.toContain(">재발송</button>");
  });

  it("권한·조회 실패에서는 빈 결과나 발송 설정을 추정하지 않는다", async () => {
    for (const [status, label] of [["unauthenticated", "관리자 계정으로 로그인하세요"], ["forbidden", "관리자 권한이 없습니다"],
      ["unavailable", "내역을 불러오지 못했습니다"], ["invalid", "기간·상태·종류·페이지를 확인해주세요"]] as const) {
      vi.mocked(loadEmailDeliveries).mockResolvedValue({ status });
      const html = renderToStaticMarkup(await EmailDeliveriesPage({ searchParams: Promise.resolve({}) }));
      expect(html).toContain(label);
      expect(html).not.toContain("기간 내 전체 요청");
      expect(html).not.toContain("조회 조건에 맞는 발송 내역이 없습니다");
    }
  });

  it("유효하지 않은 필터를 초기화하고 빈 뒷 페이지에서 조건을 유지한 첫 페이지 이동을 제공한다", async () => {
    vi.mocked(loadEmailDeliveries).mockResolvedValue({ status: "available", data: { ...data, items: [], total: 1, hasNext: false } });
    const html = renderToStaticMarkup(await EmailDeliveriesPage({ searchParams: Promise.resolve({ page: "2", days: "91", state: ["UNKNOWN"], kind: "POLICY" }) }));
    expect(loadEmailDeliveries).toHaveBeenCalledWith(2, 7, "ALL", "POLICY");
    expect(html).toContain("이 페이지에 발송 내역이 없습니다");
    expect(html).toContain("?page=1&amp;days=7&amp;state=ALL&amp;kind=POLICY");
    expect(html).not.toContain(">다음</a>");
  });

  it("공급자 접수와 수신 서버 전달을 구분하고 빈 공급자 기록을 유지한다", async () => {
    vi.mocked(loadEmailDeliveries).mockResolvedValue({ status: "available", data: { ...data,
      items: [{ ...item, state: "SENT", provider: null }, { ...item, id: "another", state: "DELIVERED" }] } });
    const html = renderToStaticMarkup(await EmailDeliveriesPage({ searchParams: Promise.resolve({}) }));
    expect(html).toContain("공급자 기록 없음");
    expect(html).toContain("수신 서버 전달 여부는 아직 확인되지 않았습니다");
    expect(html).toContain("사용자의 열람을 뜻하지 않습니다");
  });
});
