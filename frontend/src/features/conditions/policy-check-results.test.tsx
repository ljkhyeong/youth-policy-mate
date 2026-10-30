import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { PolicyCheckResults } from "./policy-check-results";

describe("내 조건 결과 정렬", () => {
  it("생년월일 반영 전에는 접수 중 우선, 반영 뒤에는 연령 조건 충족 우선을 기본으로 고른다", () => {
    const browsing = renderToStaticMarkup(<PolicyCheckResults input={{}} />);
    expect(browsing).toContain('value="OPEN_FIRST" selected=""');
    // 생년월일이 없으면 연령 정렬은 고를 수 없다.
    expect(browsing).toContain('value="AGE_MATCH" disabled=""');
    const compared = renderToStaticMarkup(<PolicyCheckResults input={{ birthDate: "1998-05-01" }} />);
    expect(compared).toContain('value="AGE_MATCH" selected=""');
    expect(compared).not.toContain('value="OPEN_FIRST" selected=""');
  });
});
