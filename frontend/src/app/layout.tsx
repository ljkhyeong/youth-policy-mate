import type { Metadata } from "next";
import "./globals.css";
import { AccountTransitions } from "@/features/member/account-transitions";

export const metadata: Metadata = {
  title: "청년정책메이트",
  description: "서울 청년 정책의 지원 내용과 신청 조건을 확인하고, 관심 정책과 마감일을 관리하세요.",
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body><AccountTransitions />{children}</body>
    </html>
  );
}
