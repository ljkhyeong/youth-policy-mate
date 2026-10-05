import type { ReactNode } from "react";
import type { operations } from "@/generated/policy-api";

export type PolicyCategoryKey = NonNullable<NonNullable<operations["listPolicies"]["parameters"]["query"]>["category"]>[number];

// 온통청년 대분류. 원천의 반각 가운뎃점(･)은 화면에서 일반 가운뎃점으로 표시한다.
export const policyCategories: readonly { key: PolicyCategoryKey; label: string }[] = [
  { key: "JOB", label: "일자리" }, { key: "HOUSING", label: "주거" }, { key: "FINANCE", label: "금융·복지·문화" },
  { key: "EDUCATION", label: "교육·직업훈련" }, { key: "PARTICIPATION", label: "참여·기반" },
];

// 원천 대분류는 쉼표로 묶인 복수 값일 수 있어 분야마다 나눈다. 분야가 없으면 기본 표시를 사용한다.
// 앞뒤 공백은 서버 분야 필터(PolicyCatalogStore)와 같은 일반·탭·줄바꿈·NBSP·전각 공백만 뺀다.
const sourceSpaces = /^[ \t\n\r\u00a0\u3000]+|[ \t\n\r\u00a0\u3000]+$/g;
export const categoryLabels = (category: string | null | undefined) => {
  const labels = [...new Set((category ?? "").split(",").map(part => part.replace(sourceSpaces, "").replaceAll("･", "·")).filter(Boolean))];
  return labels.length > 0 ? labels : ["청년 정책"];
};
export const categoryKey = (label: string) => policyCategories.find(item => item.label === label)?.key;
export const isPolicyCategory = (value: unknown): value is PolicyCategoryKey => policyCategories.some(item => item.key === value);

const icons: Record<PolicyCategoryKey, ReactNode> = {
  JOB: <><rect x="3" y="7" width="18" height="13" rx="2" /><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 13h18" /></>,
  HOUSING: <path d="M4 11 12 4l8 7M6 10v10h12V10M10 20v-5h4v5" />,
  FINANCE: <><rect x="3" y="6" width="18" height="14" rx="2" /><path d="M3 10h18" /><circle cx="16.5" cy="15" r="1.2" /></>,
  EDUCATION: <><path d="M3 9l9-5 9 5-9 5z" /><path d="M7 11.5V16c0 1.5 2.2 3 5 3s5-1.5 5-3v-4.5" /></>,
  PARTICIPATION: <><circle cx="9" cy="8" r="3.2" /><path d="M3 20a6 6 0 0 1 12 0" /><circle cx="17" cy="9" r="2.5" /><path d="M15.5 14.5A5 5 0 0 1 21 19" /></>,
};

export function CategoryIcon({ category }: { category: PolicyCategoryKey }) {
  return <svg className="category-icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
    strokeLinecap="round" strokeLinejoin="round">{icons[category]}</svg>;
}

export function CategoryChips({ category }: { category: string | null | undefined }) {
  return <span className="category-chips">
    {categoryLabels(category).map(label => <span key={label} className="category-chip" data-category={categoryKey(label)}>{label}</span>)}
  </span>;
}
