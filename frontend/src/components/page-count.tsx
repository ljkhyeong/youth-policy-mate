// 페이지 이동의 현재 쪽과 전체 쪽수. 화면 낭독기는 ‘5페이지 중 1페이지’로 읽는다.
// 목록이 줄어 전체 쪽수를 넘은 쪽에서는 틀린 전체 쪽수를 보이지 않도록 현재 쪽만 쓴다.
export function PageCount({ page, total, pageSize }: { page: number; total: number; pageSize: number }) {
  const pages = Math.max(1, Math.ceil(total / Math.max(1, pageSize)));
  if (page > pages) return <span>{page}페이지</span>;
  return <span><span aria-hidden="true">{page} / {pages}페이지</span><span className="sr-only">{pages}페이지 중 {page}페이지</span></span>;
}
