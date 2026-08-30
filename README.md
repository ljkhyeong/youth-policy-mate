# 청년정책메이트

서울 거주 청년이 내 조건에 맞는 정책과 판단 근거를 확인하고, 관심 정책의 신청 마감과 변경 내용을 관리하는 서비스입니다.

## MVP

- 서울 거주 만 19~34세 청년 대상. 온통청년의 전국 공통·서울시·서울 자치구 정책 제공
- 조건 입력에 따른 3단계 자격 안내와 항목별 근거
- 비회원 검색, 카카오·네이버 로그인 후 조건·관심 정책 저장
- 저장한 정책의 일정과 서비스 내 알림, 별도로 활성화하는 이메일 알림
- 정책 자동 수집·공개, 명확한 조건만 자동 판정, 예외만 선택 검수
- 실제 신청은 공식 사이트에서 진행. 증빙서류 수집과 신청 대행은 제외

## 현재 상태

2026-08-30에 MVP 범위·기술 스택을 확정하고 온통청년 API를 조사한 뒤, Next.js·Spring Boot와 로컬 PostgreSQL의 기본 개발 환경을 구성했습니다. 프런트엔드 빌드, 실제 DB를 사용하는 백엔드 테스트와 로컬 기동을 확인했습니다.

API 인증키 없이 사용할 수 있는 비회원 조건 입력 화면(`/conditions`)을 구현했습니다. 생년월일·서울 자치구·주된 취업상태를 입력하고, 확인·수정·초기화할 수 있습니다. 입력값은 화면 상태로만 사용하며 서버에 보내거나 저장하지 않습니다. 새로고침하면 초기화됩니다.

조건 입력의 로딩 화면과 공통 오류·404 안내를 추가했습니다. 개발 모드의 `/dev/states`에서 로딩·빈 결과·오류를 미리 볼 수 있습니다. 실제 검색 결과가 아닌 화면 점검용이며, 운영 빌드에서는 이 경로를 404로 처리합니다.

서버에는 항목별 결과를 3단계 자격 상태로 합치는 모델과 근거 구조를 추가했습니다. 확인한 연령 범위·거주 지역·단일 취업 요건·소득 구간을 비교합니다. 정책 미해석·예외·사용자 정보 누락을 구분하며, `npm run test:eligibility`로 인증키·DB 없이 단위 테스트를 실행할 수 있습니다.

취업 조건은 [설계](docs/design/employment-condition.md)에 따라 [단일 사실·명시적 답변 비교](docs/development/employment-condition.md)를 구현했습니다. 정책·개정·정의·기준일이 다른 답변은 재사용하지 않습니다. 개발 전용 `/dev/employment`에서 [추가 확인 질문](docs/development/employment-question-preview.md)을 인공 자료로 점검할 수 있습니다. 주된 상태의 자동 변환, 원천 코드 매핑과 실제 정책의 질문·판정 연결은 아직 없습니다.

소득은 [설계](docs/design/income-condition.md)에 따라 [명시적 원화 구간 비교기](docs/development/income-condition.md)를 구현했습니다. 개인·가구, 소득 정의·기간·적용 기준이 맞는 답변만 비교하며 구간 일부만 허용 범위에 겹치면 추가 확인으로 남깁니다. 소득 47건을 포함한 서버 판정 테스트 117건과 빌드가 통과했습니다. 원천 매핑·기준표 계산은 아직 없습니다.

개발 전용 `/dev/income`에서 [소득 질문 미리보기](docs/development/income-question-preview.md)를 제공합니다. 대상·기간·단위를 안내하고 구간·모름 답변의 확인·수정·삭제와 기간 변경 시 초기화를 점검합니다. 구간 추가 확인은 별도 인공 상황이며 현재 답변을 판정한 결과가 아닙니다. 실제 소득을 입력할 필요가 없고 서버 전송·저장·판정 연결은 없습니다. 웹 테스트 17건·린트·타입 검사·빌드와 운영 경로 404를 확인했습니다.

GitHub Actions에 웹·서버 병렬 CI를 구성했습니다. 인증키 없이 기존 테스트·린트·타입 검사·빌드를 실행하며 서버는 실제 PostgreSQL 통합 테스트를 포함합니다. 로컬 검사와 CI 문법 검사는 통과했지만, 원격 푸시와 GitHub 실행은 아직 하지 않았습니다. 범위와 남은 확인 사항은 [CI 안내](docs/development/ci.md)를 참고합니다.

온통청년 API 인증키는 신청 후 승인 대기 중입니다. 인증된 성공 응답, 정책 조회·수집, 실제 정책을 연결한 자격 판정, 로그인·저장·알림 기능은 아직 구현하지 않았습니다. 연령·거주·취업·소득 비교기와 결과 집계 모델도 화면과 연결하지 않았습니다. 조건 입력 검사는 신청 자격 판정이 아니며 실제 정책이나 가상 추천 결과를 표시하지 않습니다.

발급 후 사용할 단건 응답 점검 명령 `npm run probe:ontong`을 준비했습니다. 인증키를 노출하지 않고 미검증 JSON 응답을 로컬에 보관하는 개발 도구이며, 운영 수집기나 실제 API 검증 완료를 뜻하지 않습니다.

주요 스택은 Next.js 16·React 19·TypeScript, Java 25·Spring Boot 4.1 모듈러 모놀리스, PostgreSQL 18입니다. 전체 선택과 책임 경계는 ADR을 기준으로 합니다. 월 운영비 상한은 3만 원이며 운영 장비·클라우드는 아직 정하지 않았습니다.

## 문서

- [PRD-0001: MVP 기준과 완료 조건](docs/PRD/0001_product-baseline/spec.md)
- [ADR-0001: 기술 스택과 책임 분리](docs/ADR/0001_기술스택과_책임_분리.md)
- [최초 제품 합의 기록](docs/alignments/seoul-mvp.html)
- [전용 스킬과 재사용 출처](docs/development/skill-reuse.md)
- [로컬 개발 환경과 검증 명령](docs/development/local-development.md)
- [CI 구성과 검증 범위](docs/development/ci.md)
- [비회원 조건 입력의 구현 범위](docs/development/guest-conditions.md)
- [공통 상태 화면과 개발 미리보기](docs/development/page-states.md)
- [추가 확인 질문과 답변 미리보기](docs/development/employment-question-preview.md)
- [소득 구간 질문과 추가 확인 미리보기](docs/development/income-question-preview.md)
- [자격 판정 결과 집계와 근거 구조](docs/development/eligibility-decision.md)
- [명시적 연령 조건 비교와 미지원 범위](docs/development/age-condition.md)
- [명시적 거주 조건 비교와 기준일 처리](docs/development/residence-condition.md)
- [단일 취업 조건 비교와 답변 재사용 제한](docs/development/employment-condition.md)
- [명시적 소득 구간 비교와 입력 기준 확인](docs/development/income-condition.md)
- [온통청년 인증키 설정과 응답 점검](docs/development/ontong-api-probe.md)
- [온통청년 API 조사와 확인할 계약](docs/research/ontong-api-contract.md)
- [정책 수집·판정 데이터 구조 초안](docs/design/policy-data-model.md)
- [취업 조건 비교 범위와 추가 확인 설계](docs/design/employment-condition.md)
- [소득 입력 의미와 구간 비교 설계](docs/design/income-condition.md)
- [현재 작업 인계](HANDOFF.md)

## 개발 시작

[AGENTS.md](AGENTS.md)와 HANDOFF를 읽고 요청에 맞는 `skills/`의 전용 스킬을 적용합니다. 웹만 실행할 때는 Node.js 24 LTS와 npm이 필요합니다. 서버까지 실행하려면 JDK 21 이상과 실행 중인 Docker도 필요합니다. 앱에 필요한 Java 25는 Gradle 도구체인으로 준비합니다.

조건 입력 화면은 인증키·DB·백엔드 없이 실행할 수 있습니다. 저장소 루트에서 실행합니다.

```sh
npm ci
npm run dev:web
```

서버도 확인하려면 DB를 준비하고 다른 터미널에서 백엔드를 실행합니다.

```sh
npm run db:up
npm run dev:backend
```

- 웹: <http://127.0.0.1:3000>
- 조건 입력: <http://127.0.0.1:3000/conditions>
- 백엔드 상태 확인: <http://127.0.0.1:8080/actuator/health>
- 설정 변경·검증·종료 방법: [로컬 개발 안내](docs/development/local-development.md)
