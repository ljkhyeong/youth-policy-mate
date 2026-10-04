import { describe, expect, it } from "vitest";
import { formatPolicyPeriod } from "./policy-period";

describe("신청기간 표시", () => {
  it("원천의 YYYYMMDD 날짜만 점 구분 날짜로 바꾼다", () => {
    expect(formatPolicyPeriod("20260327 ~ 20260831")).toBe("2026.03.27 ~ 2026.08.31");
    expect(formatPolicyPeriod("20251222~20261211 (예산 소진 시 조기 마감)")).toBe("2025.12.22~2026.12.11 (예산 소진 시 조기 마감)");
  });

  it("날짜가 아닌 숫자와 안내 문구는 바꾸지 않는다", () => {
    for (const text of ["상시 접수 · 종료 조건은 공식 안내 확인", "20261399 ~ 20260001", "202603271200", "공고번호 120260327"])
      expect(formatPolicyPeriod(text)).toBe(text);
  });
});
