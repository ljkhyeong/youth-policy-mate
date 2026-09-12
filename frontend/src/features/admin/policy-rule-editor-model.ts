import type { components } from "@/generated/policy-api";

export type RuleDefinition = components["schemas"]["PolicyRuleDefinition"];
export type RuleCheck = RuleDefinition["checks"][number];
export type RuleCase = RuleCheck["cases"][number];

export function seoulDateTime(value: string) {
  return value ? new Date(new Date(value).getTime() + 9 * 60 * 60 * 1000).toISOString().slice(0, -1) : "";
}

export function ruleDraftJson(rule: RuleDefinition, originalVersion: string, contentHash: string, sourceReviewed: boolean) {
  if (!rule.ruleVersion.trim() || rule.ruleVersion.trim() === originalVersion || !sourceReviewed) return "";
  if (rule.checks.some(check => check.cases.some(row => Object.values(row.when).some(values => !values.length)))) return "";
  return JSON.stringify({ ...rule, ruleVersion: rule.ruleVersion.trim(), contentHash });
}

export function moveRuleCase(check: RuleCheck, index: number, direction: -1 | 1): RuleCheck {
  const cases = [...check.cases];
  const target = index + direction;
  if (target < 0 || target >= cases.length) return check;
  [cases[index], cases[target]] = [cases[target], cases[index]];
  return { ...check, cases };
}
