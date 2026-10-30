import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { PageCount } from "./page-count";

describe("페이지 이동의 전체 쪽수", () => {
  it("전체 건수를 쪽 크기로 올림해 전체 쪽수를 쓴다", () => {
    expect(renderToStaticMarkup(<PageCount page={2} total={41} pageSize={20} />)).toContain("2 / 3페이지");
    expect(renderToStaticMarkup(<PageCount page={2} total={40} pageSize={20} />)).toContain("2 / 2페이지");
    expect(renderToStaticMarkup(<PageCount page={1} total={0} pageSize={20} />)).toContain("1 / 1페이지");
  });

  it("목록이 줄어 전체 쪽수를 넘은 쪽은 현재 쪽만 쓴다", () => {
    expect(renderToStaticMarkup(<PageCount page={3} total={40} pageSize={20} />)).toBe("<span>3페이지</span>");
  });
});
