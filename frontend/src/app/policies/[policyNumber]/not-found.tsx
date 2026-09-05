import Link from "next/link";
import { SiteShell } from "@/components/site-shell";
import { PageState } from "@/components/page-state";

export default function MissingPolicy() {
  return <SiteShell active="policies"><main id="main-content" className="state-page">
    <PageState kind="not-found" headingAs="h1" title="정책을 찾을 수 없어요" description="주소가 올바른지 확인하거나 목록에서 정책을 다시 찾아주세요."
      actions={<Link href="/policies" className="button-primary">정책 목록 보기</Link>} />
  </main></SiteShell>;
}
