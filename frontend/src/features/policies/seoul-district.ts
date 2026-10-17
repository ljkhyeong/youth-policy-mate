import type { components, operations } from "@/generated/policy-api";

// 서버 SeoulDistrict의 REST 값(표시 이름)이다. 기본 조건 입력과 공개 목록의 지역 필터가 같은 목록을 쓴다.
export type SeoulDistrictName = NonNullable<components["schemas"]["BasicConditions"]["district"]>;
type ListDistrict = NonNullable<NonNullable<operations["listPolicies"]["parameters"]["query"]>["district"]>;

// 생성 계약에 없는 값이 있으면 타입 검사에서 실패하지만 빠진 값은 잡지 않는다.
export const SEOUL_DISTRICTS = [
  "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구", "노원구",
  "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구", "성북구", "송파구",
  "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구",
] as const satisfies readonly (SeoulDistrictName & ListDistrict)[];

export const isSeoulDistrict = (value: unknown): value is SeoulDistrictName => SEOUL_DISTRICTS.some(district => district === value);
