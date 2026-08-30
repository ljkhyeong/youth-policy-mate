export const SEOUL_DISTRICTS = [
  "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구", "노원구",
  "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구", "성북구", "송파구",
  "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구",
] as const;

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
  if (!draft.birthDate) {
    errors.birthDate = "생년월일을 입력해주세요.";
  } else {
    const parsed = new Date(`${draft.birthDate}T00:00:00Z`);
    const validDate = /^\d{4}-\d{2}-\d{2}$/.test(draft.birthDate)
      && draft.birthDate.slice(0, 4) !== "0000"
      && !Number.isNaN(parsed.getTime())
      && parsed.toISOString().slice(0, 10) === draft.birthDate;
    if (!validDate) errors.birthDate = "실제로 존재하는 날짜를 입력해주세요.";
    else if (draft.birthDate > today) errors.birthDate = "생년월일은 오늘보다 늦을 수 없어요.";
  }
  if (!SEOUL_DISTRICTS.some((district) => district === draft.district)) {
    errors.district = "주민등록상 거주하는 서울 자치구를 선택해주세요.";
  }
  if (!EMPLOYMENT_OPTIONS.some(({ value }) => value === draft.employmentStatus)) {
    errors.employmentStatus = "현재 주된 취업상태를 선택해주세요.";
  }
  return errors;
}
