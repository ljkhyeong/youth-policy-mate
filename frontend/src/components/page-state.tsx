import Link from "next/link";
import { useId, type ReactNode, type Ref } from "react";

type PageStateProps = {
  kind: "loading" | "empty" | "error" | "not-found";
  title: string;
  description: string;
  label?: string;
  actions?: ReactNode;
  headingAs?: "h1" | "h2";
  headingRef?: Ref<HTMLHeadingElement>;
};

const STATE_LABELS = {
  loading: "불러오는 중",
  empty: "검색 결과 없음",
  error: "화면 불러오기 실패",
  "not-found": "404 · 페이지 없음",
};

export function PageState({ kind, title, description, label, actions, headingAs: Heading = "h2", headingRef }: PageStateProps) {
  const titleId = useId();
  const role = kind === "error" ? "alert" : kind === "not-found" ? undefined : "status";

  return (
    <section className="state-panel" data-kind={kind} aria-labelledby={titleId}>
      <div role={role} aria-atomic={role ? true : undefined}>
        <p className={`mb-5 flex items-center gap-3 text-sm font-semibold ${kind === "error" ? "text-rose-800" : "text-teal-800"}`}>
          {kind === "loading" && <span aria-hidden="true" className="size-4 shrink-0 rounded-full border-2 border-teal-200 border-t-teal-800 motion-safe:animate-spin" />}
          {label ?? STATE_LABELS[kind]}
        </p>
        <Heading id={titleId} ref={headingRef} tabIndex={headingRef ? -1 : undefined} className="max-w-xl text-2xl leading-relaxed font-bold tracking-tight sm:text-3xl">
          {title}
        </Heading>
        <p className="mt-4 max-w-lg text-sm leading-7 text-stone-600 sm:text-base sm:leading-8">{description}</p>
      </div>
      {actions && <div className="state-actions">{actions}</div>}
    </section>
  );
}

export function LoadingState({ headingAs = "h2" }: { headingAs?: "h1" | "h2" }) {
  return <PageState kind="loading" headingAs={headingAs} title="화면을 불러오고 있어요." description="잠시만 기다려주세요." />;
}

export function LoadErrorState({ onRetry, headingAs = "h2", headingRef }: {
  onRetry: () => void;
  headingAs?: "h1" | "h2";
  headingRef?: Ref<HTMLHeadingElement>;
}) {
  return (
    <PageState
      kind="error" headingAs={headingAs} headingRef={headingRef}
      title="화면을 불러오지 못했어요."
      description="다시 시도해주세요. 저장하지 않은 입력 내용은 사라질 수 있어요."
      actions={<>
        <button type="button" className="button-primary" onClick={onRetry}>다시 불러오기</button>
        <Link href="/" className="button-secondary">홈으로</Link>
      </>}
    />
  );
}
