import { describe, expect, it } from "vitest";
import { getMemberHref, getMemberLocation, getMemberLoginHref, parseMemberDestination } from "./member-location";

describe("내 정책 화면 주소", () => {
  it("탭을 바꾸어도 일정 필터를 보관하고 로그인 링크로 전달한다", () => {
    expect(getMemberLocation("calendar", "CLOSED")).toEqual({ view: "calendar", status: "CLOSED" });
    expect(getMemberHref("calendar", "CLOSED")).toBe("/my?view=calendar&status=CLOSED");
    expect(getMemberHref("saved", "CLOSED")).toBe("/my?status=CLOSED");
    expect(getMemberHref("saved")).toBe("/my");
    expect(getMemberLoginHref("email", "CLOSED")).toBe("/login?view=email&status=CLOSED&next=my");
  });

  it("알 수 없는 탭·필터와 개인 정보는 복귀 주소에 전달하지 않는다", () => {
    expect(getMemberLocation("other", "toString")).toEqual({ view: "saved", status: "" });
    expect(parseMemberDestination("/my?view=email&status=unknown&address=private&token=secret")).toBe("/my?view=email");
    expect(parseMemberDestination("/my?next=https://external.example")).toBe("/my");
  });

  it.each(["https://external.example/my?view=email", "//external.example/my", "/my/../admin", "/my-other", "/my#email"])("정해진 내 정책 경로가 아닌 %s는 거부한다", value => {
    expect(parseMemberDestination(value)).toBeNull();
  });
});
