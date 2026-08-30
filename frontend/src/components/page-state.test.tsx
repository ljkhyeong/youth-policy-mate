import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import ErrorPage from "@/app/error";
import NotFound from "@/app/not-found";
import { LoadingState, PageState } from "./page-state";

describe("공통 상태 화면", () => {
  it("로딩과 빈 결과는 일반 상태로 알리고 오류 경고와 구분한다", () => {
    const loading = renderToStaticMarkup(<LoadingState />);
    const empty = renderToStaticMarkup(<PageState kind="empty" title="검색 결과가 없어요." description="검색 조건을 바꿔주세요." />);

    expect(loading).toContain('role="status"');
    expect(loading).toContain('aria-hidden="true"');
    expect(loading).toContain("motion-safe:animate-spin");
    expect(empty).toContain('role="status"');
    expect(empty).not.toContain('role="alert"');
  });

  it("오류 화면에 원본 오류를 노출하지 않고 재시도와 나가기 경로를 제공한다", () => {
    const html = renderToStaticMarkup(<ErrorPage error={new Error("비공개 오류 원문 예시")} retry={() => {}} />);

    expect(html).toContain('role="alert"');
    expect(html).toContain("다시 불러오기");
    expect(html).toContain('href="/"');
    expect(html).not.toContain("비공개 오류 원문 예시");
  });

  it("404 화면은 자격 결과를 표시하지 않고 실제 이동 경로를 제공한다", () => {
    const html = renderToStaticMarkup(<NotFound />);

    expect(html).toContain("페이지를 찾을 수 없어요.");
    expect(html).toContain('href="/conditions"');
    expect(html).not.toContain('aria-current="page"');
    expect(html).not.toContain("조건 불충족");
  });
});
