import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { EmploymentAnswerSummary, EmploymentQuestionPreview } from "./employment-question-preview";

describe("개발 전용 취업 질문", () => {
  it("인공 자료의 정의·기준일·근거와 미선택 라디오 그룹을 제공한다", () => {
    const html = renderToStaticMarkup(<EmploymentQuestionPreview />);

    expect(html).toContain("실제 정책 원문 아님");
    expect(html).toContain("근무처와 근로계약을 맺고 일하고 있는 상태");
    expect(html).toContain('dateTime="2026-08-15"');
    expect(html).toContain("질문이 필요한 이유");
    expect(html).toContain("<fieldset");
    expect(html.match(/type="radio"/g)).toHaveLength(3);
    expect(html).not.toContain("checked=");
    expect(html).not.toContain("<form");
    expect(html).toContain("서버로 보내거나 저장하지 않습니다");
  });

  it.each(["applies", "does-not-apply"] as const)("%s 답변을 자격 충족·불충족으로 바꾸지 않는다", (answer) => {
    const html = renderToStaticMarkup(<EmploymentAnswerSummary answer={answer} />);

    expect(html).toContain(answer === "applies" ? "해당함" : "해당하지 않음");
    expect(html).toContain("자격 판정을 하지 않으며");
    expect(html).toContain("다른 취업 형태나 재학 여부까지 답한 것은 아닙니다");
    expect(html).not.toMatch(/신청 가능|조건 불충족/);
  });

  it("모름은 비해당으로 바꾸지 않고 확인할 정보가 남은 답변으로 표시한다", () => {
    const html = renderToStaticMarkup(<EmploymentAnswerSummary answer="unknown" />);

    expect(html).toContain("모름");
    expect(html).toContain("아직 알 수 없는 답변");
    expect(html).toContain("‘해당하지 않음’으로 바꾸지 않습니다");
  });
});
