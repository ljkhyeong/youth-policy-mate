import type { Metadata } from "next";
import "./globals.css";
import { AccountTransitions } from "@/features/member/account-transitions";
import { siteName, siteDescription } from "@/lib/public-metadata";

export const metadata: Metadata = {
  title: siteName,
  description: siteDescription,
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body><AccountTransitions />{children}</body>
    </html>
  );
}
