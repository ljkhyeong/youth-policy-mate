"use client";

import { useEffect, useRef } from "react";
import { LoadErrorState } from "@/components/page-state";
import { SiteShell } from "@/components/site-shell";

export default function ErrorPage({ retry }: { error: unknown; retry: () => void }) {
  const headingRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  return (
    <SiteShell>
      <main id="main-content" className="state-page">
        <LoadErrorState onRetry={retry} headingAs="h1" headingRef={headingRef} />
      </main>
    </SiteShell>
  );
}
