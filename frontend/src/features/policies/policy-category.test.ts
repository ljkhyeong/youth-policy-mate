import { describe, expect, it } from "vitest";
import { categoryKey, categoryLabel, isPolicyCategory } from "./policy-category";

describe("정책 분야", () => {
  it("원천의 반각 가운뎃점을 일반 가운뎃점으로 표시하고 분야 키를 찾는다", () => {
    expect(categoryLabel("금융･복지･문화")).toBe("금융·복지·문화");
    expect(categoryKey("금융･복지･문화")).toBe("FINANCE");
    expect(categoryKey("금융·복지·문화")).toBe("FINANCE");
    expect(categoryKey("일자리")).toBe("JOB");
  });

  it("분야가 없거나 모르는 값이면 분야 색 없이 기본 표시를 사용한다", () => {
    expect(categoryLabel("")).toBe("청년 정책");
    expect(categoryKey("기타")).toBeUndefined();
    expect(isPolicyCategory("HOUSING")).toBe(true);
    expect(isPolicyCategory("housing")).toBe(false);
  });
});
