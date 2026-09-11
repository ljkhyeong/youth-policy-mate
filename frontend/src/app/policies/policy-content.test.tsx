import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { components } from "@/generated/policy-api";
import { PolicyArticle } from "./policy-content";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

const policy: components["schemas"]["PolicyDetailResponse"] = {
  policyNumber: "123", revision: 1, collectedAt: "2026-09-05T01:00:00Z", sourceUrl: "https://www.youthcenter.go.kr/",
  content: {
    title: "청년내일저축계좌", description: "지원 안내", category: "복지", organization: "보건복지부",
    applicationPeriod: "20260504 ~ 20260520", regionCodes: [], links: [], sourceModifiedAtText: "",
    sections: [
      { title: "추가 신청 자격", text: "가구 소득인정액 기준 중위소득 100% 이하" },
      { title: "소득 안내", text: "가구 소득인정액 기준 중위소득 50% 이하" },
    ],
  },
  recruitment: { status: "CLOSED", explanation: "접수가 마감됐어요.", evaluatedAt: "2026-09-08T00:00:00Z" },
  sourceNotices: [],
};

describe("정책 상세의 원문 충돌 안내", () => {
  it("서버의 안내와 근거 링크를 질문보다 먼저 표시하고 수집 본문을 유지한다", () => {
    const notice = {
      title: "소득 기준 확인 필요", description: "수집 안내와 모집 공고의 소득 기준이 달라요.",
      sourceLabel: "복지로 2026년 모집 공고",
      sourceUrl: "https://www.bokjiro.go.kr/ssis-tbu/cms/pc/customer/notice/1309680_1141.html",
    };
    const html = renderToStaticMarkup(<PolicyArticle policy={{ ...policy, sourceNotices: [notice] }} />);
    expect(html).toContain(`aria-label="${notice.title}"`);
    expect(html).toContain(notice.description);
    expect(html).toContain(`href="${notice.sourceUrl}" target="_blank" rel="noopener noreferrer"`);
    expect(html).toContain(`${notice.sourceLabel} `);
    expect(html.indexOf(notice.title)).toBeLessThan(html.indexOf('id="policy-questions"'));
    for (const section of policy.content.sections) expect(html).toContain(section.text);
  });

  it("개별 안내가 없어도 검토 완료로 표시하지 않고 일반적인 추가 확인 안내를 유지한다", () => {
    const html = renderToStaticMarkup(<PolicyArticle policy={policy} />);
    expect(html).not.toContain("소득 기준 확인 필요");
    expect(html).not.toContain("복지로 2026년 모집 공고");
    expect(html).toContain("신청 전 공식 공고를 확인하세요");
    expect(html).toContain("온통청년에서 수집한 내용");
  });
});
