// 개발 미리보기의 예시 선택 버튼. 이미 선택한 예시를 다시 누르면 상태를 바꾸지 않는다.
export function PreviewChoices<T extends { label: string }>({ label, items, selected, keyOf, onSelect }: {
  label: string; items: readonly T[]; selected: number; keyOf: (item: T) => string; onSelect: (index: number, item: T) => void;
}) {
  return <div role="group" aria-label={label} className="flex flex-wrap gap-x-3 border-b border-stone-300">
    {items.map((item, index) => <button key={keyOf(item)} type="button" className="preview-choice" aria-pressed={index === selected}
      onClick={() => { if (index !== selected) onSelect(index, item); }}>{item.label}</button>)}
  </div>;
}
