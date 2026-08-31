import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { ELIGIBILITY_EXAMPLES } from "@/app/dev/eligibility/eligibility-preview-data";
import { EligibilityPreview } from "@/app/dev/eligibility/eligibility-preview";
import { EligibilityResult } from "./eligibility-result";

const [allMet, moreInput, unresolved, notMet] = ELIGIBILITY_EXAMPLES;

describe("자격 결과와 근거 표시", () => {
  it("입력 기준 신청 가능과 모집 종료를 별도로 표시하고 신청을 유도하지 않는다", () => {
    const html = renderToStaticMarkup(<EligibilityPreview examples={ELIGIBILITY_EXAMPLES} />);

    expect(html).toContain("입력 조건 기준 신청 가능");
    expect(html).toContain("모집 종료 · 예시");
    expect(html).toContain("공식 자격 인증이나 선정 보장이 아닙니다");
    expect(html.match(/aria-pressed="true"/g)).toHaveLength(1);
    expect(html).not.toContain("<form");
    expect(html).not.toContain("href=");
  });

  it("사용자 구간이 경계에 걸린 이유와 원래 구간을 표시한다", () => {
    const html = renderToStaticMarkup(<EligibilityResult result={moreInput.result} recruitment={moreInput.recruitment} />);

    expect(html).toContain("사용자 정보 추가 확인");
    expect(html).toContain("20,000,000원 초과 ~ 30,000,000원 이하");
    expect(html).toContain("25,000,000원 경계에 걸쳐 있습니다");
    expect(html).not.toContain("정책 조건 해석 필요");
  });

  it("미해석 정책과 검토 미완료를 표시하고 불충족 항목만으로 전체 결과를 바꾸지 않는다", () => {
    const html = renderToStaticMarkup(<EligibilityResult result={unresolved.result} recruitment={unresolved.recruitment} />);

    expect(html).toMatch(/<h2[^>]*>추가 확인 필요<\/h2>/);
    expect(html).toContain("정책 조건 해석 필요");
    expect(html).toContain("정책 검토 · 미완료");
    expect(html).toContain("연령 상한의 예외 대상과 연장 범위");
    expect(html).toContain("만 36세");
    expect(html).toContain("기록된 발췌문 없음");
    expect(html).not.toMatch(/<h2[^>]*>조건 불충족<\/h2>/);
  });

  it("명확한 불충족 결과에서도 다른 항목의 답변 누락을 따로 표시한다", () => {
    const html = renderToStaticMarkup(<EligibilityResult result={notMet.result} recruitment={notMet.recruitment} />);

    expect(html).toMatch(/<h2[^>]*>조건 불충족<\/h2>/);
    expect(html).toContain("사용자 정보 추가 확인");
    expect(html).toContain("0원이나 조건 불충족으로 처리하지 않습니다");
    expect(html).toContain("정책 검토 · 완료");
  });

  it("비교하지 않은 값을 입력 누락으로 단정하지 않고 기준일·판정 시각·개정을 보존한다", () => {
    const html = renderToStaticMarkup(<EligibilityResult result={allMet.result} recruitment={allMet.recruitment} />);

    expect(html).toContain("사용자 답변을 비교하지 않았으며 이 항목만 충족합니다");
    expect(html).toContain("이 결과에 기록된 비교 값 없음");
    expect(html).toContain("이 결과에 기록된 기준일 없음");
    expect(html).toContain('dateTime="2025-12-31"');
    expect(html).toContain('dateTime="2026-08-01"');
    expect(html).toContain('dateTime="2026-08-30T00:00:00Z"');
    expect(html).toContain("2026년 8월 30일 09:00");
    expect(html).toContain("sample-revision-1");
    expect(html).toContain("sample-rule-1");
  });

  it("검토 미완료의 전체 충족 목록과 빈 목록 모두 전달받은 보류 결과를 유지한다", () => {
    for (const conditions of [allMet.result.conditions, []]) {
      const html = renderToStaticMarkup(<EligibilityResult result={{ ...unresolved.result, conditions }} recruitment={unresolved.recruitment} />);

      expect(html).toMatch(/<h2[^>]*>추가 확인 필요<\/h2>/);
      expect(html).not.toContain("입력 조건 기준 신청 가능");
      expect(html).toContain("연령 상한의 예외 대상과 연장 범위");
      if (conditions.length === 0) expect(html).toContain("표시할 항목별 결과가 없습니다");
    }
  });

  it("근거 위치와 문구를 접어 보여주며 근거 문자열을 HTML이나 링크로 실행하지 않는다", () => {
    const condition = allMet.result.conditions[0];
    const html = renderToStaticMarkup(<EligibilityResult result={{
      ...allMet.result,
      conditions: [{ ...condition, evidence: { ...condition.evidence, excerpt: '<a href="https://example.invalid">인공 문구</a>' } }],
    }} recruitment={allMet.recruitment} />);

    expect(html).toContain("<details");
    expect(html).not.toMatch(/<details[^>]*\sopen/);
    expect(html).toContain("연령 근거 보기");
    expect(html).toContain("인공 자료 1항 · 연령");
    expect(html).toContain("실제 정책 원문 아님");
    expect(html).toContain("&lt;a href=");
    expect(html).not.toContain("<a ");
  });
});
