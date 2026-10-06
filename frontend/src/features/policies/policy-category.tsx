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

export function CategoryChips({ category }: { category: string | null | undefined }) {
  return <span className="category-chips">
    {categoryLabels(category).map(label => <span key={label} className="category-chip" data-category={categoryKey(label)}>{label}</span>)}
  </span>;
}
