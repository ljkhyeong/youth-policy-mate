import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { PolicyChecks } from "@/features/member/member-api";
import { PolicyCheckCard } from "./policy-check-card";

const unknown = { label: "연령", providedValue: "미입력", explanation: "생년월일을 추가하면 확인된 연령 조건을 비교할 수 있어요.", evidence: "원문", outcome: "UNKNOWN" as const };
const policy: PolicyChecks["items"][number] = {
  policyNumber: "123", revision: 1, title: "시험 지원", status: "NEEDS_REVIEW", explanation: "지원 내용을 살펴보고 필요한 조건을 추가해보세요.",
  applicationPeriod: "20260701 ~ 20261117", sourceUrl: "https://www.youthcenter.go.kr/", collectedAt: "2026-09-05T01:00:00Z",
  checks: [unknown], questionnaireAvailable: true, ruleVersion: "",
  recruitment: { status: "OPEN", explanation: "접수 기간이에요.", evaluatedAt: "2026-10-05T00:00:00Z", deadlineOnSeoul: "2026-11-17", daysUntilDeadline: 43 },
};

describe("내 조건 결과 카드", () => {
  it("생년월일 없이 둘러볼 때 같은 안내를 반복하지 않고 날짜·접수 상태·질문 이동을 표시한다", () => {
    const html = renderToStaticMarkup(<PolicyCheckCard policy={policy} showExplanation={false} />);
    expect(html).toContain("조건 확인 전");
    expect(html).not.toContain("필요한 조건을 추가해보세요");
    expect(html).toContain("2026.07.01 ~ 2026.11.17");
    expect(html).toContain("D-43");
    expect(html).toContain('href="/policies/123#policy-questions"');
    expect(html).toContain("입력: 미입력");
  });

  it("연령을 비교한 결과는 다른 조건 확인이 남았다는 표시와 서버 설명을 함께 보여준다", () => {
    const compared = { ...policy, explanation: "확인한 연령 조건을 충족해요.", checks: [{ ...unknown, providedValue: "만 26세", outcome: "MET" as const }] };
    const html = renderToStaticMarkup(<PolicyCheckCard policy={compared} showExplanation />);
    expect(html).toContain("연령 비교됨 · 다른 조건 확인 필요");
    expect(html).toContain("data-compared");
    expect(html).toContain("확인한 연령 조건을 충족해요.");
    expect(html).toContain("연령 · 충족");
  });
});
