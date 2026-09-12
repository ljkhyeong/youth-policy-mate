# 비회원 조건 입력

2026-09-12 기준. 제품 요구는 [PRD](../PRD/0001_product-baseline/spec.md), 화면 구조는 [웹앱 기준](../design/webapp-interface.md), 실행 방법은 [개발 환경](local-development.md)을 따른다.

## 현재 동작

`/conditions`는 로그인 없이 양력 생년월일·주민등록상 서울 자치구·주된 취업상태를 입력하고 확인하는 화면이다. 회원 조회에 실패해도 직접 입력을 계속할 수 있다.

1. 세 항목을 입력하고 `입력 내용 확인`을 누르면 내용을 요약한다. 이 단계에서는 입력을 전송하지 않는다.
2. `연령 조건 비교`를 누르면 입력을 서버에 전송해 [정책별 연령 조건](condition-policy-discovery.md)을 비교한다. 거주·취업·소득과 최종 신청 자격은 추가 확인으로 남긴다.
3. `입력 수정`은 현재 값을 유지한다. `입력 내용 모두 지우기`는 화면 입력과 재사용 생년월일을 지우며, 계정에 저장한 조건은 삭제하지 않는다.
4. 로그인한 회원은 저장 조건을 불러오거나 삭제할 수 있다. 확인 화면의 `내 조건 저장`을 누를 때만 계정에 저장한다. [회원 조건 조회와 오류 복구](condition-member-recovery.md)

입력 초안은 React 상태에만 보관한다. 확인한 생년월일은 메모리에서 정책 질문에 재사용하며, 수정·전체 삭제·로그아웃·계정 변경·새로고침 시 지운다. 개인정보를 URL·쿠키·브라우저 저장소에 기록하지 않고, 기본 폼 제출로 직렬화되지 않도록 입력 요소에 `name`을 두지 않는다.

저장 조건을 불러오는 동안에도 입력·전체 삭제를 허용한다. 요청 후 입력이 바뀌었다면 늦은 응답을 적용하지 않는다. 소셜 생년월일도 직접 수정·삭제한 값이나 전체 삭제 상태를 덮어쓰지 않는다.

## 입력 검사와 자격 판정

- 필수값·날짜 유효성·미래 생년월일·선택지 밖 값을 검사한다. 19~34세를 모든 정책의 공통 제한으로 적용하지 않는다.
- 오늘 날짜는 서울 기준이며 페이지를 열 때 계산한다. 계속 열어둔 화면은 새로고침해야 날짜가 갱신된다.
- 서울 자치구 선택은 정책 기준일의 거주 사실을 증명하지 않는다. 주된 취업상태 하나를 골랐다고 다른 취업 형태·겸업·재학 여부를 비해당으로 추정하지 않는다. [거주 조건](residence-condition.md)·[취업 조건](../design/employment-condition.md)
- 입력 검사는 프런트엔드에서, 정책별 기준일·연령 범위·예외 비교는 서버에서 처리한다. [연령 조건](age-condition.md)

## 코드와 검증

- `frontend/src/app/conditions/page.tsx`: 서울 날짜와 페이지 구성.
- `frontend/src/features/conditions/condition-form.tsx`: 입력·확인·수정·초기화와 늦은 응답의 적용 여부.
- `frontend/src/features/conditions/condition-draft.ts`: 화면 선택지와 입력 검사.
- `frontend/src/features/conditions/confirmed-birth.ts`: 확인한 생년월일의 메모리 보관.
- `frontend/src/features/conditions/policy-check-results.tsx`: 연령 비교 결과와 검색·필터·페이지 이동.
- `frontend/src/features/member/condition-member-controls.tsx`: 회원 조회·조건 불러오기·저장·삭제. API 타입은 생성 계약을 사용한다.

입력 검사와 생년월일 메모리는 기존 단위 테스트로 확인한다. 조회 지연·입력 수정·초기화·저장·삭제·오류·키보드 초점은 헤드리스 브라우저의 모의 회원 API로 검증했다. 최신 명령·로그와 실제 연동의 한계는 [회원 조건 검증](condition-member-recovery.md#검증)에 기록한다.
