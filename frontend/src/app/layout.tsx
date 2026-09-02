import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "청년정책메이트",
  description: "내 조건에 맞는 서울 청년 정책을 간단하게 확인하고 관리하는 웹앱입니다.",
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
