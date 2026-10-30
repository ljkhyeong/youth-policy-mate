import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { components } from "@/generated/policy-api";
import { PolicyContentComparison } from "./policy-content-comparison";

type PolicyContent = components["schemas"]["PolicyContent"];
const content = (applicationPeriod: string): PolicyContent => ({
  title: "정책", description: "설명", category: "일자리", organization: "기관", applicationPeriod,
  sections: [], links: [], regionCodes: [], sourceModifiedAtText: "",
});

describe("정책 내용 비교", () => {
  it("내 정책 비교는 신청 기간을 YYYY.MM.DD로, 관리자 원본 비교는 수집 값 그대로 보여준다", () => {
    const props = { previous: content("20260701 ~ 20261031"), current: content("20260701 ~ 20261117"),
      previousLabel: "저장 당시", currentLabel: "현재", fields: ["applicationPeriod"] as const };
    const member = renderToStaticMarkup(<PolicyContentComparison {...props} formatPeriod />);
    expect(member).toContain("변경된 항목 1개");
    expect(member).toContain("2026.07.01 ~ 2026.10.31");
    expect(member).toContain("2026.07.01 ~ 2026.11.17");
    const admin = renderToStaticMarkup(<PolicyContentComparison {...props} />);
    expect(admin).toContain("20260701 ~ 20261031");
    expect(admin).not.toContain("2026.07.01");
  });
});
