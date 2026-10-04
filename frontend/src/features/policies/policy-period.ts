// 원천의 YYYYMMDD 날짜만 읽기 쉬운 형식으로 바꾼다. 범위 표기와 안내 문구는 그대로 둔다.
export function formatPolicyPeriod(text: string) {
  return text.replace(/(?<!\d)(\d{4})(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])(?!\d)/g, "$1.$2.$3");
}
