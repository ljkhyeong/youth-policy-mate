# 공개 페이지 검색·공유 정보

운영 공개 주소를 기준으로 홈페이지와 공개 정책의 검색·공유 정보를 만든다. Next.js의 `generateMetadata`, `robots.ts`, `sitemap.ts`를 사용한다. 검색 엔진 등록과 실제 검색 결과 반영은 운영자가 확인한다.

## 페이지별 동작

| 화면 | 검색·공유 정보 |
|---|---|
| 홈페이지 | 검색 허용, 홈페이지 대표 주소, 공개 제목·설명 |
| 기본 정책 목록 | 정책이 있는 페이지만 검색 허용. 2페이지부터 `?page=2`처럼 각 페이지의 대표 주소를 사용 |
| 정책 상세 | 조회 성공 시 검색 허용. 공고 제목·설명과 정책번호의 대표 주소를 사용 |
| 검색어·질문/접수 상태 필터 결과 | `noindex, follow`. 검색어나 필터를 공유 정보에 넣지 않음 |
| 빈 목록·정책 없음·조회 오류 | 검색 제외, 대표 주소와 공유 정보 없음 |
| 내 조건·내 정책·로그인·관리자·수신 해제 | 기존 최상위 `noindex, nofollow` 유지 |

대표 주소에는 검색어·추적 매개변수·회원 정보·수신 해제 토큰을 넣지 않는다. 공개 페이지에만 Open Graph와 Twitter 제목·설명을 제공하며 개인 화면에 상속하지 않는다. 기존 회원 API의 로그인·소유권 검사는 그대로 적용된다.

## 실행 설정

웹을 운영 모드로 실행하면서 기존 `PUBLIC_APP_URL=https://<공개 도메인>`을 전달한다. HTTPS의 루트 주소만 사용하며 사용자 정보·경로·쿼리·조각이 포함된 값은 검색용 주소로 쓰지 않는다. 주소 미설정이나 개발 모드에서는 전체 검색 제외와 빈 사이트맵을 제공한다. 별도 환경변수는 없다.

공개 주소는 실행 시 읽는다. Docker 이미지 빌드 때 도메인을 넣을 필요가 없으며 요청의 Host 헤더로 대표 주소를 만들지 않는다. 홈페이지·정책 페이지·robots·사이트맵은 동적 응답이다. 정책 목록은 메타데이터와 본문에서 한 번의 조회 결과를 공유하고, 기존 공개 API만 호출한다.

## robots와 사이트맵

- `/robots.txt`: 운영 공개 주소가 있으면 페이지 탐색을 허용하고 API·OAuth 처리·개발·상태 확인 경로는 제외한다. 개인 HTML 화면은 검색 로봇이 `noindex`를 읽을 수 있도록 robots.txt로 막지 않는다. 공개 주소가 없으면 전체 탐색을 차단한다.
- `/sitemap.xml`: 홈페이지와 기본 정책 목록 두 주소를 제공한다. 상세 정책은 목록의 기존 페이지 이동·상세 링크로 탐색한다. 사이트맵 요청마다 모든 정책을 순회하거나 실제 수정 시각이 아닌 임의의 `lastmod`를 넣지 않는다.

운영 주소·TLS·Ingress와 검색 엔진 도구 등록은 운영자 작업이다. 배포 후 `/robots.txt`, `/sitemap.xml`, 대표 정책의 원본 HTML에서 실제 도메인·검색 제외 여부를 확인한다. 검색 허용 설정은 검색 엔진의 색인이나 순위를 보장하지 않는다.

## 검증

2026-09-12 코드 `37c26df`. 이후 문서만 변경했다.

| 명령·범위 | 결과·로그 |
|---|---|
| `npm run verify -- check:web` | 린트·타입 통과. `.local/verification/1789190827740-13569714.log` |
| `npm run verify -- test:web -- src/app/public-metadata.test.ts src/app/policies/page.test.tsx src/app/policies/load-policies.test.ts` | 운영 주소·공유 정보·페이지/필터·빈 결과·장애·기존 조회 검사 통과. `.local/verification/1789190828858-1ce697ca.log` |
| `PUBLIC_APP_URL= npm run verify -- build:web` | 도메인 없이 운영 빌드 통과. `.local/verification/1789190946315-07325f41.log` |

첫 빌드는 샌드박스의 포트 바인딩 제한으로 실패했다. 권한을 바꾼 뒤에도 같은 실패가 남아 포트 접근을 별도로 확인했고, 실패한 `frontend/.next/cache/turbopack` 캐시만 분리한 뒤 통과했다. 소스나 빌드 도구는 바꾸지 않았다.

같은 단독 실행 파일을 Node 24.21.0으로 두 번 기동해 공개 주소 설정·미설정을 확인했다. 공개 페이지 4종, 개인/검색/빈 페이지 10종, 모의 API 오류 2종의 실제 HTML을 확인했다. 대표 주소·공유 정보·robots·사이트맵, 요청 Host 무시, 목록의 메타데이터/본문 중복 조회 없음이 통과했다. 공개 정책은 로컬 API를 읽고, 장애만 임시 API 중계에서 모의 처리했다. 실제 회원 변경·외부 공급자 호출은 없었다.

임시 웹 3100·3101 포트와 API 중계는 종료했다. 결과는 `/tmp/youth-public-metadata-runtime-result.json`, 웹 로그는 `/tmp/youth-public-metadata-runtime-3100.log`·`3101.log`다. 이 검사는 HTML 메타데이터 변경에 한정하며 화면 배치·입력 동작은 바꾸지 않았다.

서버·API 계약은 바뀌지 않아 `8bc3fbf`의 전체 서버 검사·계약 결과를 재사용했다. Docker 이미지 빌드·운영 배포·원격 CI·실제 검색 엔진 색인은 확인하지 않았다.

## 참고

- [Next.js 메타데이터](https://nextjs.org/docs/app/api-reference/functions/generate-metadata)
- [Next.js robots](https://nextjs.org/docs/app/api-reference/file-conventions/metadata/robots)·[사이트맵](https://nextjs.org/docs/app/api-reference/file-conventions/metadata/sitemap)
- [Google 페이지 구분과 필터](https://developers.google.com/search/docs/specialty/ecommerce/pagination-and-incremental-page-loading)·[noindex](https://developers.google.com/search/docs/crawling-indexing/block-indexing)
