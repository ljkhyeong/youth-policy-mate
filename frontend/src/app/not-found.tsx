import type { Metadata } from "next";
import Link from "next/link";
import { PageState } from "@/components/page-state";
import { SiteShell } from "@/components/site-shell";

export const metadata: Metadata = {
  title: "페이지를 찾을 수 없어요 · 청년정책메이트",
};

export default function NotFound() {
  return (
    <SiteShell>
      <main id="main-content" className="state-page">
        <PageState
          kind="not-found" headingAs="h1"
          title="페이지를 찾을 수 없어요."
          description="주소를 다시 확인해주세요. 서비스 소개로 돌아가거나 내 조건 입력을 시작할 수 있어요."
          actions={<>
            <Link href="/" className="button-primary">서비스 소개로</Link>
            <Link href="/conditions" className="button-secondary">내 조건 입력하기</Link>
          </>}
        />
      </main>
    </SiteShell>
  );
}
