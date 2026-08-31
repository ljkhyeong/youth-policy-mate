import { notFound } from "next/navigation";
import type { ReactNode } from "react";

export default function ServerReminderLayout({ children }: { children: ReactNode }) {
  // 로딩 화면이 먼저 전송되면 페이지의 notFound가 HTTP 200이 될 수 있어 상위에서 차단한다.
  if (process.env.NODE_ENV !== "development") notFound();
  return children;
}
