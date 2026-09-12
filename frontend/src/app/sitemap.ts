import type { MetadataRoute } from "next";
import { publicSiteUrl } from "@/lib/public-metadata";

export const dynamic = "force-dynamic";

export default function sitemap(): MetadataRoute.Sitemap {
  const base = publicSiteUrl();
  if (!base) return [];
  // 상세 정책은 목록의 페이지 이동 링크로 탐색한다. 전체 공고를 요청마다 조회하지 않는다.
  return ["/", "/policies"].map(path => ({ url: new URL(path, base).href }));
}
