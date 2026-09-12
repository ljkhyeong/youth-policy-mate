import type { NextConfig } from "next";
import path from "node:path";

const nextConfig: NextConfig = {
  output: "standalone",
  outputFileTracingRoot: path.join(import.meta.dirname, ".."),
  // 저장소의 작업 지침을 유지하고 개발 서버가 별도 지침 파일을 생성하지 않게 한다.
  agentRules: false,
};

export default nextConfig;
