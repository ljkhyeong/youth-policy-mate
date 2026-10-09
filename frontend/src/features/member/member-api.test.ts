import { describe, expect, it } from "vitest";
import { describeFailure, MemberApiError } from "./member-api";

describe("회원 API 실패 안내", () => {
  it("로그인이 끊기면 로그인 안내를 보여주고 그 밖의 실패는 화면별 안내를 쓴다", () => {
    expect(describeFailure(new MemberApiError(401, "로그인이 필요해요."), "다시 시도해주세요."))
      .toEqual({ loginRequired: true, message: "로그인이 필요해요." });
    for (const failure of [new MemberApiError(500, "요청을 완료하지 못했어요."), new TypeError("Failed to fetch")]) {
      expect(describeFailure(failure, "다시 시도해주세요.")).toEqual({ loginRequired: false, message: "다시 시도해주세요." });
    }
  });
});
