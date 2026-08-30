import { describe, expect, it } from "vitest";
import { EMPTY_CONDITION_DRAFT, validateConditionDraft } from "./condition-draft";
import { getSeoulDate } from "../../lib/seoul-date";

const draft = { birthDate: "2000-02-29", district: "마포구", employmentStatus: "NOT_EMPLOYED" };
const today = "2026-08-30";

describe("비회원 기본 조건 입력", () => {
  it("입력하지 않은 기본 항목을 각각 안내한다", () => {
    expect(Object.keys(validateConditionDraft(EMPTY_CONDITION_DRAFT, today)))
      .toEqual(["birthDate", "district", "employmentStatus"]);
  });

  it("유효한 윤일은 허용하고 존재하지 않는 날짜와 미래 날짜는 거절한다", () => {
    expect(validateConditionDraft(draft, today)).toEqual({});
    for (const birthDate of ["2001-02-29", "2000-02-30", "2026-08-31"]) {
      expect(validateConditionDraft({ ...draft, birthDate }, today).birthDate).toBeDefined();
    }
  });

  it("화면의 선택지에 없는 지역과 취업상태를 통과시키지 않는다", () => {
    expect(Object.keys(validateConditionDraft({ ...draft, district: "부산", employmentStatus: "UNKNOWN" }, today)))
      .toEqual(["district", "employmentStatus"]);
  });

  it("19~34세를 모든 정책의 연령 제한처럼 적용하지 않는다", () => {
    for (const birthDate of ["1960-01-01", "2015-01-01"]) {
      expect(validateConditionDraft({ ...draft, birthDate }, today)).toEqual({});
    }
  });

  it("브라우저나 서버의 기본 시간대 대신 서울 날짜를 사용한다", () => {
    expect(getSeoulDate(new Date("2026-08-30T14:59:59Z"))).toBe("2026-08-30");
    expect(getSeoulDate(new Date("2026-08-30T15:00:00Z"))).toBe("2026-08-31");
  });
});
