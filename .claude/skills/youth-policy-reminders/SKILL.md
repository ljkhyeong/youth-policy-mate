---
name: youth-policy-reminders
description: 청년정책메이트의 관심 정책 저장/해제·마감 일정·서비스 내/이메일 알림·수신 동의·수신 해제·예약 취소·Outbox 전달·공급자 상태 변경 기준. 저장소 공통 스킬에 Claude Code 작업 보충과 실제 발송 경계를 더한다.
when_to_use: backend member의 저장 정책·알림·이메일(MemberReminderScheduler, MemberEmail*, Smtp/Resend*, EmailKeyRotation*) 코드, admin 이메일 발송 현황, 화면의 내 정책·일정·알림·이메일 설정을 바꿀 때.
---

# 일정과 알림

공통 기준은 저장소 `skills/youth-policy-reminders/SKILL.md`가 원본이다. 아래에 내용이 보이지 않으면 그 파일을 직접 읽는다. 공통 기준의 상대 링크는 원본 위치 기준이며 `../../docs/`는 저장소 루트의 `docs/`다.

## 공통 기준

!`cat "${CLAUDE_SKILL_DIR}/../../../skills/youth-policy-reminders/SKILL.md"`

## Claude Code 보충

### 코드와 문서

| 영역 | 코드 | 문서(`docs/development/`) |
|---|---|---|
| 저장 정책·D-7·D-3·D-1 예약·서비스 내 알림 | `member/MemberPolicyStore`, `MemberReminderScheduler`, `policy/catalog/PolicyDeadline` | `member-policy-flow.md`, `member-notifications.md`, `member-calendar.md`, `recruitment-period.md` |
| 이메일 Outbox·발송 | `member/MemberEmail*`, `SmtpMemberEmailSender`, `ResendMemberEmailSender` | `member-email-reminders.md` |
| 공급자 결과·웹훅·상태 조회 | `member/Resend*`, `admin/EmailDelivery*` | `email-provider-status.md`, `admin-email-deliveries.md` |
| 수신 해제·설정 복구·키 교체 | `member/MemberEmailUnsubscribe*`, `EmailCrypto`, `EmailKeyRotation*` | `email-unsubscribe.md`, `email-settings-recovery.md`, `email-key-rotation.md` |
| 화면 | `frontend/src/features/member/`의 알림·이메일·저장 컴포넌트 | `member-navigation.md` |

### 실제 발송 경계

- `EMAIL_ENABLED`·`REMINDERS_ENABLED`의 기본값은 `false`다. 테스트나 로컬 확인을 위해 켜지 않고, 실제 SMTP·Resend로 메일을 보내지 않는다. 전달은 모의 전송과 PostgreSQL 테스트로 확인한다.
- `npm run email:rotate-key`는 저장된 이메일 주소를 다시 암호화한다. 사용자가 명시적으로 요청한 경우에만 실행한다.
- 이메일 주소·수신 해제 토큰·웹훅 서명·공급자 키를 로그·테스트 고정값·커밋에 실제 값으로 남기지 않는다.

### 검증

- 저장·예약·알림·이메일 흐름은 `test:member-flow`·`test:email`, 관리자 발송 현황은 `test:admin-email`, 키 교체는 `test:email-key-rotation`이다. 서울 날짜 경계는 고정 `Clock`으로 만든다.
- 테스트 선택과 기록은 `youth-policy-verify` 스킬을 따른다.
