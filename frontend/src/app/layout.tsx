import type { Metadata } from "next";
import { Nanum_Pen_Script, Noto_Serif_KR } from "next/font/google";
import "./globals.css";
import { ThemeSync } from "@/components/theme-switch";
import { AccountTransitions } from "@/features/member/account-transitions";
import { siteName, siteDescription } from "@/lib/public-metadata";
import { themeInitScript } from "@/lib/theme-preference";

export const metadata: Metadata = {
  title: siteName,
  description: siteDescription,
  robots: { index: false, follow: false },
};

// 한글 웹 폰트는 빌드 때 받아 직접 서빙한다. 미리 받지 않고 화면에 쓰인 글자 조각만 내려받으며, 오기 전에도 글자를 먼저 보여준다.
const serif = Noto_Serif_KR({ weight: ["400", "600"], subsets: ["latin"], display: "swap", preload: false, variable: "--font-serif" });
const pen = Nanum_Pen_Script({ weight: "400", subsets: ["latin"], display: "swap", preload: false, variable: "--font-pen" });

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    // 저장된 화면 색을 그리기 전에 <html data-theme>로 적용한다. 서버 HTML과 달라지는 속성이라 하이드레이션 경고를 끈다.
    <html lang="ko" className={`${serif.variable} ${pen.variable}`} suppressHydrationWarning>
      <head><script dangerouslySetInnerHTML={{ __html: themeInitScript }} /></head>
      <body><ThemeSync /><AccountTransitions />{children}</body>
    </html>
  );
}
