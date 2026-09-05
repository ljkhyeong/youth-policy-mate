---
name: youth-policy-docs
description: 청년정책메이트의 PRD·ADR·README·HANDOFF·문서 링크를 작성하거나 정리할 때 사용한다.
---

# 문서 관리

## 기록할 위치

- 제품 요구와 예외는 [PRD](../../docs/PRD/0001_product-baseline/spec.md), 구조·기술 선택과 이유는 [ADR](../../docs/ADR/)에 기록한다.
- [README](../../README.md)는 현재 기능과 실행·문서 안내, [HANDOFF](../../HANDOFF.md)는 진행 상황·남은 작업·확인한 제약을 맡는다. 상세 구현과 검증 기록은 `docs/development/`에 둔다.
- 최초 합의 HTML은 당시 승인 기록으로 보존한다. 사용자가 요청한 변경은 현재 기준 문서에 반영하고, 합의하지 않은 제안은 구분해서 적는다.
- OpenAPI와 TypeScript 계약은 서버 DTO에서 생성한다. 계약 변경에는 [API 스킬](../youth-policy-api-contract/SKILL.md)을 적용한다.
- 스킬의 관리 방식은 [스킬 관리 문서](../../docs/development/skill-reuse.md)에 기록한다. 제품 정책 전문을 스킬에 복사하지 않는다.

## 확인할 사항

- 기준 문서를 수정한 뒤 영향받은 요약과 링크만 갱신한다. 설명한 기능·명령은 코드와 빌드 설정으로 확인한다.
- 계획·구현·실제 연동 검증을 구분한다. 코드와 문서가 다르면 문구 수정만으로 구현이 끝났다고 기록하지 않는다.
- 문서 변경은 링크·경로·내용의 일치 여부를 확인한다. 앱 동작이 바뀌지 않았다면 앱 테스트를 다시 실행하지 않는다.
