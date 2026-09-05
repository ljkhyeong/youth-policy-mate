import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { components } from "@/generated/policy-api";
import { PolicyQuestionResult } from "./policy-questionnaire";

describe("실제 정책 공통요건 결과 표시", () => {
  it("공통요건 충족을 최종 신청 가능으로 확대하지 않고 대학별 확인 사항과 비교 답변을 남긴다", () => {
    const result: components["schemas"]["PolicyEvaluation"] = {
      policyNumber: "123", revision: 2, ruleVersion: "test-rule", evaluatedAt: "2026-09-05T00:00:00Z",
      status: "NEEDS_REVIEW", commonCriteriaStatus: "ELIGIBLE", scope: "공통요건",
      explanation: "대학별 요건 확인이 남아 있어요.", remainingChecks: ["소속 대학의 선발요건"], sourceUrl: "https://example.invalid",
      checks: [{ label: "성적", providedValue: "70점 미만", outcome: "MET", explanation: "적용 제외를 확인받았다는 답변이에요.", evidence: "인정된 예외 적용 가능" }],
    };
    const html = renderToStaticMarkup(<PolicyQuestionResult result={result} />);
    expect(html).toContain("확인한 조건 충족");
    expect(html).toContain("입력한 답변으로 확인한 결과예요. 공식 기관의 자격 심사 결과는 아니에요.");
    expect(html).toContain("최종 신청 자격 · 추가 확인 필요");
    expect(html).toContain("소속 대학의 선발요건");
    expect(html).toContain("70점 미만");
    expect(html).toContain("인정된 예외 적용 가능");
    expect(html).not.toContain("신청 가능");
  });
});
