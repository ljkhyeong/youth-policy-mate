import { notFound } from "next/navigation";
import type { ReactNode } from "react";

export default function ServerEligibilityLayout({ children }: { children: ReactNode }) {
  // 로딩 스트리밍 전에 차단해 운영 환경에서 실제 HTTP 404를 반환한다.
  if (process.env.NODE_ENV !== "development") notFound();
  return children;
}
