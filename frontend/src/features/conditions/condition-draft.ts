import type { BasicConditions } from "@/features/member/member-api";
import { SEOUL_DISTRICTS } from "@/features/policies/seoul-district";

// 자치구 선택지는 공개 목록의 지역 필터와 같은 목록(features/policies/seoul-district.ts)을 쓴다.
export { SEOUL_DISTRICTS };

// 화면 입력용 선택지다. 온통청년 코드나 자격 판정 규칙으로 사용하지 않는다.
export const EMPLOYMENT_OPTIONS = [
  { value: "EMPLOYED", label: "재직자" },
  { value: "SELF_EMPLOYED", label: "자영업자" },
  { value: "NOT_EMPLOYED", label: "미취업자" },
  { value: "FREELANCER", label: "프리랜서" },
  { value: "DAY_WORKER", label: "일용근로자" },
  { value: "ENTREPRENEUR", label: "(예비)창업자" },
  { value: "SHORT_TERM_WORKER", label: "단기근로자" },
  { value: "FARMER", label: "영농종사자" },
  { value: "OTHER", label: "기타" },
] as const;

export type ConditionDraft = { birthDate: string; district: string; employmentStatus: string };
export type ConditionDraftErrors = Partial<Record<keyof ConditionDraft, string>>;

export const EMPTY_CONDITION_DRAFT: ConditionDraft = { birthDate: "", district: "", employmentStatus: "" };

export function validateConditionDraft(draft: ConditionDraft, today: string): ConditionDraftErrors {
  const errors: ConditionDraftErrors = {};
  if (draft.birthDate) {
    const parsed = new Date(`${draft.birthDate}T00:00:00Z`);
    const validDate = /^\d{4}-\d{2}-\d{2}$/.test(draft.birthDate)
      && draft.birthDate.slice(0, 4) !== "0000"
      && !Number.isNaN(parsed.getTime())
      && parsed.toISOString().slice(0, 10) === draft.birthDate;
    if (!validDate) errors.birthDate = "실제로 존재하는 날짜를 입력해주세요.";
    else if (draft.birthDate > today) errors.birthDate = "생년월일은 오늘보다 늦을 수 없어요.";
  }
  if (draft.district && !SEOUL_DISTRICTS.some((district) => district === draft.district)) {
    errors.district = "주민등록상 거주하는 서울 자치구를 선택해주세요.";
  }
  if (draft.employmentStatus && !EMPLOYMENT_OPTIONS.some(({ value }) => value === draft.employmentStatus)) {
    errors.employmentStatus = "현재 주된 취업상태를 선택해주세요.";
  }
  return errors;
}

export function conditionInput(draft: ConditionDraft): BasicConditions {
  const employment = EMPLOYMENT_OPTIONS.find(option => option.value === draft.employmentStatus);
  const district = SEOUL_DISTRICTS.find(value => value === draft.district);
  return {
    ...(draft.birthDate ? { birthDate: draft.birthDate } : {}),
    ...(district ? { district } : {}),
    ...(employment ? { employmentStatus: employment.value } : {}),
  };
}
