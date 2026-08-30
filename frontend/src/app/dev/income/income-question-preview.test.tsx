import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { INCOME_EXAMPLES } from "./income-preview-data";
import { IncomeAnswerSummary, IncomeQuestionPreview } from "./income-question-preview";

describe("개발 전용 소득 질문", () => {
  it("소득 정의·대상·기간·원화 단위와 미선택 구간을 표시한다", () => {
    const html = renderToStaticMarkup(<IncomeQuestionPreview />);

    expect(html).toContain("본인 소득만");
    expect(html).toContain("세금 공제 전 금액");
    expect(html).toContain('dateTime="2025-01-01"');
    expect(html).toContain('dateTime="2025-12-31"');
    expect(html).toContain("원 (KRW)");
    expect(html).toContain("실제 정책 원문 아님");
    expect(html).toContain("<fieldset");
    expect(html.match(/type="radio"/g)).toHaveLength(5);
    expect(html).not.toContain("checked=");
    expect(html).not.toContain("<form");
    expect(html).not.toContain('type="number"');
    expect(html).toContain("서버로 보내거나 저장하지 않습니다");
  });

  it.each(["0원", "20,000,000원 초과 ~ 30,000,000원 이하"])("%s 답변은 선택한 구간 그대로 표시한다", (label) => {
    const html = renderToStaticMarkup(<IncomeAnswerSummary answer={{ kind: "range", label }} />);

    expect(html).toContain(label);
    expect(html).toContain("대표 금액으로 바꾸지 않습니다");
    expect(html).toContain("자격 판정을 하지 않으며");
    expect(html).not.toMatch(/신청 가능|조건 불충족/);
  });

  it("모름을 0원이나 소득 제한 없음으로 바꾸지 않는다", () => {
    const html = renderToStaticMarkup(<IncomeAnswerSummary answer={{ kind: "unknown" }} />);

    expect(html).toContain("모름");
    expect(html).toContain("0원이나 소득 제한 없음으로 처리하지 않습니다");
    expect(html).toContain("공식 소득 확인도 아닙니다");
  });

  it("추가 확인 자료는 별도 인공 상황이며 경계를 포함한 구간과 모름을 제공한다", () => {
    const refinement = INCOME_EXAMPLES.find((example) => example.refinement);

    expect(refinement?.refinement).toEqual({ previousAnswer: "20,000,000원 초과 ~ 30,000,000원 이하", boundary: "25,000,000원 이하" });
    expect(refinement?.choices.map((choice) => choice.answer)).toEqual([
      { kind: "range", label: "20,000,000원 초과 ~ 25,000,000원 이하" },
      { kind: "range", label: "25,000,000원 초과 ~ 30,000,000원 이하" },
      { kind: "unknown" },
    ]);
  });
});
