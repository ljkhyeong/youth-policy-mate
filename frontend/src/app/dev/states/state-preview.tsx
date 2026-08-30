"use client";

import Link from "next/link";
import { useState } from "react";
import { LoadingState, LoadErrorState, PageState } from "@/components/page-state";

const STATES = [
  { value: "loading", label: "로딩" },
  { value: "empty", label: "빈 결과" },
  { value: "error", label: "오류" },
] as const;

export function StatePreview() {
  const [selected, setSelected] = useState<(typeof STATES)[number]["value"]>("loading");
  const [notice, setNotice] = useState("");

  return (
    <div className="mt-9">
      <div role="group" aria-label="미리 볼 상태" className="flex flex-wrap gap-x-3 border-b border-stone-300">
        {STATES.map(({ value, label }) => (
          <button key={value} type="button" className="preview-choice" aria-pressed={selected === value} onClick={() => { setSelected(value); setNotice(""); }}>
            {label}
          </button>
        ))}
      </div>
      <div className="flex min-h-96 items-center py-12 sm:py-16">
        {selected === "loading" && <LoadingState />}
        {selected === "empty" && <PageState
          kind="empty" title="검색 결과가 없어요."
          description="검색어나 필터를 바꿔 다시 찾아보세요. 검색 결과가 없다는 뜻이며, 신청 자격이 없다는 뜻은 아니에요."
          actions={<button type="button" className="button-primary" onClick={() => setNotice("검색 조건 변경 버튼을 확인했어요. 이 미리보기에는 실제 검색 기능이 없습니다.")}>검색 조건 바꾸기</button>}
        />}
        {selected === "error" && <LoadErrorState onRetry={() => setNotice("재시도 버튼을 확인했어요. 이 미리보기에서는 실제 요청을 보내지 않습니다.")} />}
      </div>
      <p role="status" className="min-h-7 text-sm leading-7 text-teal-900">{notice}</p>
      <div className="mt-8 border-t border-stone-300 pt-5">
        <Link href="/dev/states/missing-page" className="text-link" prefetch={false}>없는 주소에서 404 화면 확인하기 →</Link>
      </div>
    </div>
  );
}
