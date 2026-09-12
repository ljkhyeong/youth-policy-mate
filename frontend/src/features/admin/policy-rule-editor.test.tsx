import { readFileSync } from "node:fs";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { RuleEditor } from "./policy-rule-editor";
import { moveRuleCase, ruleDraftJson, seoulDateTime, type RuleDefinition } from "./policy-rule-editor-model";

const rules = ["V23__seed_reviewed_policy_rules.sql", "V24__migrate_policy_question_rules.sql"].flatMap(file =>
  [...readFileSync(new URL(`../../../../backend/src/main/resources/db/migration/${file}`, import.meta.url), "utf8")
    .matchAll(/\$rule\$([\s\S]*?)\$rule\$/g)].map(match => JSON.parse(match[1]) as RuleDefinition));

describe("관리자 규칙 편집", () => {
  it("원문 재검토와 새 버전명이 있어야 초안 요청을 만든다", () => {
    const original = rules[0];
    const next = { ...original, ruleVersion: "next-version" };
    expect(ruleDraftJson(original, original.ruleVersion, original.contentHash, true)).toBe("");
    expect(ruleDraftJson({ ...next, ruleVersion: " " }, original.ruleVersion, original.contentHash, true)).toBe("");
    expect(ruleDraftJson(next, original.ruleVersion, "b".repeat(64), false)).toBe("");
    expect(JSON.parse(ruleDraftJson(next, original.ruleVersion, "b".repeat(64), true)).contentHash).toBe("b".repeat(64));
    expect(original.contentHash).not.toBe("b".repeat(64));
  });

  it("기존 공고의 문구를 수정해도 연령·기간·답변별 예외 설정을 보존한다", () => {
    for (const original of rules) {
      const next = { ...original, ruleVersion: "editor-next", questions: original.questions.map((question, index) =>
        index === 0 ? { ...question, label: "수정한 질문" } : question) };
      const result = JSON.parse(ruleDraftJson(next, original.ruleVersion, original.contentHash, true));
      expect(result.questions[0].label).toBe("수정한 질문");
      expect(result.checks).toEqual(original.checks);
      for (const key of ["ageBinding", "birthBinding", "periodNotice", "monthly", "ageNotice", "remainingVariant", "validFrom", "validUntil"] as const) {
        expect(result[key]).toEqual(original[key]);
      }
      expect(() => renderToStaticMarkup(<RuleEditor definition={original} revision={1} contentHash={original.contentHash} />)).not.toThrow();
    }
  });

  it("판정 우선순위를 바꾸고 미응답 조건을 빈 선택과 구분한다", () => {
    const original = rules[0];
    const check = original.checks[0];
    const moved = moveRuleCase(check, 0, 1);
    expect(moved.cases[0]).toEqual(check.cases[1]);
    expect(moved.cases[1]).toEqual(check.cases[0]);
    expect(moveRuleCase(moved, 1, -1)).toEqual(check);
    const base = { ...original, ruleVersion: "next", checks: [{ ...check, cases: [{ ...check.cases[0], when: { [check.questionId]: [""] } }] }] };
    expect(ruleDraftJson(base, original.ruleVersion, original.contentHash, true)).not.toBe("");
    expect(ruleDraftJson({ ...base, checks: [{ ...check, cases: [{ ...check.cases[0], when: { [check.questionId]: [] } }] }] }, original.ruleVersion, original.contentHash, true)).toBe("");
  });

  it("서울 날짜 경계와 밀리초를 표시하고 기존 시각은 편집 전까지 유지한다", () => {
    expect(seoulDateTime("2026-12-31T15:00:00.125Z")).toBe("2027-01-01T00:00:00.125");
    expect(seoulDateTime("2027-01-01T00:00:00.125+09:00")).toBe("2027-01-01T00:00:00.125");
    expect(seoulDateTime("")).toBe("");
    const rule = { ...rules[0], ruleVersion: "next", validUntil: "2026-12-31T15:00:00.123456Z" };
    expect(JSON.parse(ruleDraftJson(rule, rules[0].ruleVersion, rule.contentHash, true)).validUntil).toBe(rule.validUntil);
  });
});
