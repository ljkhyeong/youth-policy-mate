import { LoadingState } from "@/components/page-state";
import { SiteShell } from "@/components/site-shell";

export default function Loading() {
  return (
    <SiteShell active="conditions">
      <main id="main-content" className="state-page">
        <LoadingState headingAs="h1" />
      </main>
    </SiteShell>
  );
}
