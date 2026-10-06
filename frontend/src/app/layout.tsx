import type { Metadata } from "next";
import { Nanum_Pen_Script, Noto_Serif_KR } from "next/font/google";
import "./globals.css";
import { AccountTransitions } from "@/features/member/account-transitions";
import { siteName, siteDescription } from "@/lib/public-metadata";

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
    <html lang="ko" className={`${serif.variable} ${pen.variable}`}>
      <body><AccountTransitions />{children}</body>
    </html>
  );
}
