import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { PolicyRecruitment } from "./policy-recruitment";

describe("공개 정책의 접수 상태", () => {
  it("접수 기간을 신청 가능으로 바꾸지 않고 서버의 시간 제한 설명을 표시한다", () => {
    const html = renderToStaticMarkup(<PolicyRecruitment recruitment={{status:"OPEN", explanation:"마감 시각은 공식 안내를 확인해주세요.", evaluatedAt:"2026-09-06T00:00:00Z"}} />);
    expect(html).toContain("접수 기간");
    expect(html).toContain("마감 시각은 공식 안내를 확인해주세요.");
    expect(html).not.toContain("신청 가능");
  });
  it("미확인을 마감으로 표시하지 않는다", () => {
    const html = renderToStaticMarkup(<PolicyRecruitment recruitment={{status:"UNKNOWN", explanation:"복수 회차 안내를 확인해주세요.", evaluatedAt:"2026-09-06T00:00:00Z"}} />);
    expect(html).toContain("기간 미확인");
    expect(html).toContain("복수 회차 안내를 확인해주세요.");
    expect(html).not.toContain('data-status="CLOSED"');
  });
});
