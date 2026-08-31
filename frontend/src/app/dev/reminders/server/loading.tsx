import { LoadingState } from "@/components/page-state";
import { SiteShell } from "@/components/site-shell";

export default function ServerReminderLoading() {
  return <SiteShell><main id="main-content" className="state-page"><LoadingState headingAs="h1" /></main></SiteShell>;
}
