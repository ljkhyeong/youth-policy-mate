import type { MetadataRoute } from "next";
import { publicSiteUrl } from "@/lib/public-metadata";

export const dynamic = "force-dynamic";

export default function robots(): MetadataRoute.Robots {
  const base = publicSiteUrl();
  if (!base) return { rules: { userAgent: "*", disallow: "/" } };
  // 개인 화면의 noindex를 읽을 수 있도록 HTML 접근을 robots.txt로 막지 않는다.
  return {
    rules: { userAgent: "*", allow: "/", disallow: ["/api/", "/oauth2/", "/login/oauth2/", "/dev/", "/healthz"] },
    sitemap: new URL("/sitemap.xml", base).href,
  };
}
