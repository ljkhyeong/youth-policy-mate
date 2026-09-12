import type { Metadata } from "next";

export const siteName = "청년정책메이트";
export const siteDescription = "서울 청년 정책의 지원 내용과 신청 조건을 확인하고, 관심 정책과 마감일을 관리하세요.";

// 이미지 빌드 시의 값이나 요청 Host 대신 실행 환경의 공개 주소만 사용한다.
export function publicSiteUrl(): URL | undefined {
  const value = process.env.PUBLIC_APP_URL;
  if (!value || process.env.NODE_ENV !== "production") return undefined;
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password || url.pathname !== "/" || url.search || url.hash) return undefined;
    return url;
  } catch { return undefined; }
}

export function publicMetadata(path: string, title: string, description: string): Metadata {
  const base = publicSiteUrl();
  if (!base) return { title, description, robots: { index: false, follow: false } };
  const url = new URL(path, base).href;
  return {
    title, description, robots: { index: true, follow: true },
    alternates: { canonical: url },
    openGraph: { type: "website", locale: "ko_KR", siteName, title, description, url },
    twitter: { card: "summary", title, description },
  };
}
