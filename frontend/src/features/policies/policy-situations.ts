import type { PolicyCategoryKey } from "./policy-category";

// 홈의 상황 선택. 상황 하나가 온통청년 대분류 하나에 대응하는 분야 필터이며 자격이나 추천을 뜻하지 않는다.
export const policySituations: readonly { category: PolicyCategoryKey; label: string }[] = [
  { category: "JOB", label: "일을 구하고 있어요" },
  { category: "EDUCATION", label: "공부하거나 기술을 배우려고 해요" },
  { category: "HOUSING", label: "집을 구하거나 옮기려고 해요" },
  { category: "FINANCE", label: "생활비나 목돈 마련이 고민이에요" },
  { category: "PARTICIPATION", label: "활동하거나 의견을 내고 싶어요" },
];
