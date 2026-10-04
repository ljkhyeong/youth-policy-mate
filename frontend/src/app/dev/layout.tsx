import type { Metadata } from "next";
import { notFound } from "next/navigation";
import type { ReactNode } from "react";

export const metadata: Metadata = { robots: { index: false, follow: false } };

export default function DevLayout({ children }: { children: ReactNode }) {
  // 로딩 화면이 먼저 전송되면 페이지의 notFound가 HTTP 200이 될 수 있어 개발 화면 전체를 상위에서 차단한다.
  if (process.env.NODE_ENV !== "development") notFound();
  return children;
}
