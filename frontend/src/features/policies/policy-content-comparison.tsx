import type { components } from "@/generated/policy-api";

type PolicyContent = components["schemas"]["PolicyContent"];
const comparisonFields = {
  title: "정책명", description: "정책 설명", category: "정책 분야", organization: "운영 기관",
  applicationPeriod: "신청 기간", sections: "상세 안내", links: "공식 링크",
  regionCodes: "지역 코드", sourceModifiedAtText: "온통청년 수정일",
} satisfies Record<keyof PolicyContent, string>;

export function PolicyContentComparison({ previous, current, previousLabel, currentLabel, showUnchanged = false, fields = Object.keys(comparisonFields) as (keyof PolicyContent)[] }: {
  previous: PolicyContent; current: PolicyContent; previousLabel: string; currentLabel: string; showUnchanged?: boolean; fields?: readonly (keyof PolicyContent)[];
}) {
  const changed = fields.filter(field => JSON.stringify(previous[field]) !== JSON.stringify(current[field]));
  const unchanged = fields.filter(field => !changed.includes(field));
  return <>
    <p>{changed.length ? `변경된 항목 ${changed.length}개` : "표시 항목의 변경이 없습니다."}</p>
    {changed.map(field => <section className="exception-section revision-field" key={field}>
      <h3>{comparisonFields[field]}</h3>
      <dl className="revision-values">
        <div><dt>{previousLabel}</dt><dd><RevisionValue value={previous[field]} /></dd></div>
        <div><dt>{currentLabel}</dt><dd><RevisionValue value={current[field]} /></dd></div>
      </dl>
    </section>)}
    {showUnchanged && unchanged.length > 0 && <details className="revision-unchanged">
      <summary>동일한 항목 {unchanged.length}개</summary>
      {unchanged.map(field => <section className="exception-section revision-field" key={field}>
        <h3>{comparisonFields[field]}</h3><RevisionValue value={current[field]} />
      </section>)}
    </details>}
  </>;
}

function RevisionValue({ value }: { value: PolicyContent[keyof PolicyContent] }) {
  if (typeof value === "string") return <p className="exception-text">{value || "내용 없음"}</p>;
  if (!value.length) return <p>내용 없음</p>;
  return <ul className="revision-list">{value.map((entry, index) => <li key={index}>
    {typeof entry === "string" ? entry : "text" in entry ? <>
      <strong>{entry.title}</strong><p className="exception-text">{entry.text}</p>
    </> : <><strong>{entry.label}</strong><p className="exception-text">{entry.url}</p></>}
  </li>)}</ul>;
}
