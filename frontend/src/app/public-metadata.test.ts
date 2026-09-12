import { afterEach, describe, expect, it, vi } from "vitest";
import { generateMetadata as homeMetadata } from "./page";
import { generateMetadata as policyMetadata } from "./policies/[policyNumber]/page";
import { loadPolicy } from "./policies/load-policies";
import robots from "./robots";
import sitemap from "./sitemap";

vi.mock("./policies/load-policies", () => ({ loadPolicy: vi.fn() }));
afterEach(() => { vi.resetAllMocks(); vi.unstubAllEnvs(); });

describe("공개 화면의 검색·공유 정보", () => {
  it("개발 환경이나 공개 HTTPS 주소가 없는 실행에서는 검색 주소를 만들지 않는다", () => {
    for (const [mode, address] of [["development", "https://policy.example.test"], ["production", ""], ["production", "http://localhost:3000"], ["production", "https://user:secret@policy.example.test"], ["production", "https://policy.example.test/subpath"]]) {
      vi.stubEnv("NODE_ENV", mode); vi.stubEnv("PUBLIC_APP_URL", address);
      expect(homeMetadata().robots).toEqual({ index: false, follow: false });
      expect(homeMetadata().alternates).toBeUndefined();
      expect(homeMetadata().openGraph).toBeUndefined();
      expect(robots()).toEqual({ rules: { userAgent: "*", disallow: "/" } });
      expect(sitemap()).toEqual([]);
    }
  });

  it("공개 주소는 모듈을 다시 불러오지 않아도 실행 시점의 환경변수로 만든다", () => {
    vi.stubEnv("NODE_ENV", "production");
    for (const domain of ["first.example.test", "second.example.test"]) {
      vi.stubEnv("PUBLIC_APP_URL", `https://${domain}/`);
      const metadata = homeMetadata();
      expect(metadata.alternates).toEqual({ canonical: `https://${domain}/` });
      expect(metadata.openGraph).toMatchObject({ url: `https://${domain}/`, locale: "ko_KR" });
      expect(robots()).toMatchObject({ sitemap: `https://${domain}/sitemap.xml` });
      expect(sitemap()).toEqual([{ url: `https://${domain}/` }, { url: `https://${domain}/policies` }]);
    }
  });

  it("정책 공유에는 공개 제목·설명과 정책 주소만 넣고 공고 누락·장애는 검색 제외한다", async () => {
    vi.stubEnv("NODE_ENV", "production"); vi.stubEnv("PUBLIC_APP_URL", "https://policy.example.test");
    vi.mocked(loadPolicy).mockResolvedValue({ status: "available", data: {
      policyNumber: "123", revision: 1, collectedAt: "2026-09-12T00:00:00Z", sourceUrl: "https://www.youthcenter.go.kr/",
      content: { title: "청년 주거 지원", description: "  신청 기간을\n 확인하세요.  ", category: "주거", organization: "서울시", applicationPeriod: "공고 확인", regionCodes: [], links: [], sections: [], sourceModifiedAtText: "" },
      recruitment: { status: "UNKNOWN", explanation: "기간 확인 필요", evaluatedAt: "2026-09-12T00:00:00Z" }, sourceNotices: [],
    } });
    const metadata = await policyMetadata({ params: Promise.resolve({ policyNumber: "123" }) });
    expect(metadata.alternates).toEqual({ canonical: "https://policy.example.test/policies/123" });
    expect(metadata.openGraph).toMatchObject({ title: "청년 주거 지원 · 청년정책메이트", description: "신청 기간을 확인하세요.", url: "https://policy.example.test/policies/123" });
    expect(metadata.robots).toEqual({ index: true, follow: true });
    for (const status of ["missing", "unavailable"] as const) {
      vi.mocked(loadPolicy).mockResolvedValue({ status });
      const failed = await policyMetadata({ params: Promise.resolve({ policyNumber: "123" }) });
      expect(failed.robots).toEqual({ index: false, follow: false });
      expect(failed.openGraph).toBeUndefined();
      expect(failed.alternates).toBeUndefined();
    }
  });
});
