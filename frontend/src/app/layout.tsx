import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "청년정책메이트 · 개발 환경",
  description: "서울 청년을 위한 정책 안내 서비스의 개발 준비 화면입니다.",
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
