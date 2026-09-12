import { describe, expect, it } from "vitest";
import { getMemberHref, getMemberLocation, getMemberLoginHref, parseMemberDestination, readMemberLocation } from "./member-location";

describe("내 정책 화면 주소", () => {
  it("탭을 바꾸어도 일정 필터를 보관하고 로그인 링크로 전달한다", () => {
    expect(getMemberLocation({ view: "calendar", status: "CLOSED" })).toEqual({ view: "calendar", status: "CLOSED", page: 1, filter: "ALL" });
    expect(getMemberHref({ view: "calendar", status: "CLOSED" })).toBe("/my?view=calendar&status=CLOSED");
    expect(getMemberHref({ view: "saved", status: "CLOSED" })).toBe("/my?status=CLOSED");
    expect(getMemberHref()).toBe("/my");
    expect(getMemberLoginHref({ view: "email", status: "CLOSED" })).toBe("/login?view=email&status=CLOSED&next=my");
  });

  it("알 수 없는 탭·필터와 개인 정보는 복귀 주소에 전달하지 않는다", () => {
    expect(getMemberLocation({ view: "other", status: "toString", page: "wrong", filter: "other" })).toEqual({ view: "saved", status: "", page: 1, filter: "ALL" });
    expect(parseMemberDestination("/my?view=email&status=unknown&address=private&token=secret")).toBe("/my?view=email");
    expect(parseMemberDestination("/my?next=https://external.example")).toBe("/my");
  });

  it("알림 페이지·필터를 다른 탭과 로그인 복귀에서도 유지한다", () => {
    const location = readMemberLocation(new URLSearchParams("view=notifications&status=OPEN&page=3&filter=UNREAD"));
    expect(location).toEqual({ view: "notifications", status: "OPEN", page: 3, filter: "UNREAD" });
    expect(getMemberHref({ ...location, view: "calendar" })).toBe("/my?view=calendar&status=OPEN&page=3&filter=UNREAD");
    expect(getMemberLoginHref(location)).toBe("/login?view=notifications&status=OPEN&page=3&filter=UNREAD&next=my");
    expect(parseMemberDestination("/my?view=email&page=3&filter=UNREAD&memberId=private")).toBe("/my?view=email&page=3&filter=UNREAD");
  });

  it.each(["0", "-1", "1.5", "NaN", "Infinity", "2147483648", ["2", "3"]])("유효하지 않은 페이지 %s는 첫 페이지를 사용한다", page => {
    expect(getMemberLocation({ page }).page).toBe(1);
  });

  it("서버가 받는 정수 페이지 범위를 유지하고 기본값은 주소에서 생략한다", () => {
    expect(getMemberLocation({ page: "2147483647" }).page).toBe(2147483647);
    expect(getMemberHref({ view: "notifications", page: 1, filter: "ALL" })).toBe("/my?view=notifications");
    expect(getMemberLocation({ view: ["email", "saved"], status: ["OPEN"], filter: ["UNREAD"] })).toEqual({ view: "saved", status: "", page: 1, filter: "ALL" });
  });

  it.each(["https://external.example/my?view=email", "//external.example/my", "/my/../admin", "/my-other", "/my#email"])("정해진 내 정책 경로가 아닌 %s는 거부한다", value => {
    expect(parseMemberDestination(value)).toBeNull();
  });
});
