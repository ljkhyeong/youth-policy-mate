import { defineConfig } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";

// .next/**·next-env.d.ts는 eslint-config-next의 기본 무시 목록에 있다.
export default defineConfig([
  ...nextVitals,
  ...nextTypescript,
]);
