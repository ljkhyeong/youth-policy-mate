import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { CategoryChips, categoryKey, categoryLabels, isPolicyCategory } from "./policy-category";

describe("정책 분야", () => {
  it("원천의 반각 가운뎃점을 일반 가운뎃점으로 표시하고 분야 키를 찾는다", () => {
    expect(categoryLabels("금융･복지･문화")).toEqual(["금융·복지·문화"]);
    expect(categoryKey("금융·복지·문화")).toBe("FINANCE");
    expect(categoryKey("일자리")).toBe("JOB");
  });

  it("쉼표로 묶인 복수 분류는 공백을 빼고 분야마다 나눈다", () => {
    expect(categoryLabels("교육･직업훈련 , 금융･복지･문화")).toEqual(["교육·직업훈련", "금융·복지·문화"]);
    expect(categoryLabels("일자리,일자리,")).toEqual(["일자리"]);
    expect(categoryLabels("일자리,\u00a0주거\t,\u3000참여･기반")).toEqual(["일자리", "주거", "참여·기반"]);
    const html = renderToStaticMarkup(CategoryChips({ category: "일자리,주거" }));
    expect(html).toContain('data-category="JOB">일자리<');
    expect(html).toContain('data-category="HOUSING">주거<');
  });

  it("분야가 없거나 모르는 값이면 분야 색 없이 기본 표시를 사용한다", () => {
    expect(categoryLabels("")).toEqual(["청년 정책"]);
    expect(categoryLabels(" , ")).toEqual(["청년 정책"]);
    expect(categoryKey("기타")).toBeUndefined();
    expect(isPolicyCategory("HOUSING")).toBe(true);
    expect(isPolicyCategory("housing")).toBe(false);
  });
});
