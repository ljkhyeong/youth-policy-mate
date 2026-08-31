import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { REMINDER_EXAMPLES } from "@/app/dev/reminders/reminder-preview-data";
import { ReminderPreview } from "@/app/dev/reminders/reminder-preview";
import { DeadlineReminder } from "./deadline-reminder";

const [dates, times, unresolved, rolling, exhausted, closed, noRemaining] = REMINDER_EXAMPLES;

describe("마감과 알림 후보 표시", () => {
  it("날짜형 마감과 오늘·미래 후보를 시각 변환 없이 보여주고 예약 동작을 제공하지 않는다", () => {
    const html = renderToStaticMarkup(<ReminderPreview examples={REMINDER_EXAMPLES} />);

    expect(html).toContain("날짜형 신청기간 · 시작일과 종료일 포함");
    expect(html).toContain('dateTime="2026-09-07"');
    expect(html).not.toContain('dateTime="2026-09-07T');
    expect(html).toContain("오늘 후보 · 발송 시각 확인 필요");
    expect(html).toContain("미래 후보 · 예약 미확정");
    expect(html).toContain("즉시 발송 대상이나 이미 놓친 알림으로 판단하지 않습니다");
    expect(html).toContain("이메일은 별도 활성화가 필요합니다");
    expect(html.match(/aria-pressed="true"/g)).toHaveLength(1);
    expect(html.match(/<li\s/g)).toHaveLength(3);
    expect(html).not.toContain("<form");
    expect(html).not.toContain("href=");
  });

  it("원문 마감 시각과 시간대를 보존하고 같은 순간의 서울 시각을 함께 표시한다", () => {
    const html = renderToStaticMarkup(<DeadlineReminder result={times.result} />);

    expect(html).toContain('dateTime="2026-09-06T18:00:00Z"');
    expect(html).toContain("2026년 9월 6일 18:00:00");
    expect(html).toContain("(UTC)");
    expect(html).toContain("2026년 9월 7일 03:00:00");
    expect(html).toContain("(Asia/Seoul)");
    expect(html).toContain("접수 마감 · 해당 시각부터 종료");
    expect(html).toContain('dateTime="2026-09-07"');
    expect(html).toContain('dateTime="2026-08-31"');
  });

  it("화면을 보는 날짜가 달라도 전달받은 오늘 상태·계산 기준·후보 순서를 그대로 표시한다", () => {
    vi.useFakeTimers();
    try {
      vi.setSystemTime(new Date("2035-01-01T00:00:00Z"));
      const html = renderToStaticMarkup(<DeadlineReminder result={dates.result} />);

      expect(html).toContain("오늘 후보 · 발송 시각 확인 필요");
      expect(html).toContain('dateTime="2026-08-30T15:30:00Z"');
      expect(html).toContain("2026년 8월 31일 00:30:00");
      expect(html).toContain("현재 시각에 맞춰 자동 갱신하지 않습니다");
      expect(html.indexOf("D-7</span>")).toBeLessThan(html.indexOf("D-3</span>"));
      expect(html.indexOf("D-3</span>")).toBeLessThan(html.indexOf("D-1</span>"));
      expect(html).not.toContain("2035");
    } finally {
      vi.useRealTimers();
    }
  });

  it("마감일 충돌 이유를 보존하고 임의 날짜나 후보를 만들지 않는다", () => {
    const html = renderToStaticMarkup(<DeadlineReminder result={unresolved.result} />);

    expect(html).toContain("확인된 마감 날짜가 없습니다");
    expect(html).toContain("신청기간 항목은 9월 7일, 본문은 9월 10일로 서로 다릅니다");
    expect(html).toContain("확인된 날짜 없음");
    expect(html).not.toContain("<ol");
    expect(html).not.toContain('dateTime="2026-09-07"');
  });

  it("상시·소진 시 종료의 후보 없음과 현재 접수 여부 확인을 구분한다", () => {
    for (const example of [rolling, exhausted]) {
      const html = renderToStaticMarkup(<DeadlineReminder result={example.result} />);

      expect(html).toContain(example.result.recruitment.label);
      expect(html).toMatch(/현재 접수 여부[는를] 공식 신청처에서 확인해야 합니다/);
      expect(html).toContain("확인된 날짜 없음");
      expect(html).not.toContain("<ol");
    }
  });

  it("모집 마감과 후보 날짜가 모두 지남을 구분하고 지난 후보를 보충하지 않는다", () => {
    const closedHtml = renderToStaticMarkup(<DeadlineReminder result={closed.result} />);
    const passedHtml = renderToStaticMarkup(<DeadlineReminder result={noRemaining.result} />);

    expect(closedHtml).toContain("모집이 마감되어 후보가 없습니다");
    expect(closedHtml).toContain('dateTime="2026-08-30"');
    expect(passedHtml).toContain("남은 알림 후보 날짜가 없습니다");
    expect(passedHtml).toContain("접수 기간 · 날짜 기준");
    expect(passedHtml).toContain("지난 날짜의 알림을 오늘로 옮기지 않습니다");
    for (const html of [closedHtml, passedHtml]) {
      expect(html).not.toContain("<ol");
      expect(html).toContain("후보가 없다는 것만으로 기존 예약이 취소된 것은 아닙니다");
    }
  });

  it("날짜 없이 확인한 모집 마감과 발췌문 누락을 임의 값으로 채우지 않는다", () => {
    const html = renderToStaticMarkup(<DeadlineReminder result={{
      ...closed.result,
      applicationPeriod: { kind: "CLOSED" }, deadlineOnSeoul: null,
      recruitment: { label: "모집 마감", explanation: "원문에서 마감을 확인했습니다. 정확한 날짜는 알 수 없습니다." },
      basis: { ...closed.result.basis, sourceExcerpt: null },
    }} />);

    expect(html).toContain("정확한 마감 날짜·시각은 기록되지 않았습니다");
    expect(html).toContain("기록된 발췌문 없음");
    expect(html).toContain("확인된 날짜 없음");
    expect(html).not.toContain('dateTime="2026-08-30"');
  });

  it("개정·자료 위치와 접힌 근거를 표시하고 원문 문자열을 HTML로 실행하지 않는다", () => {
    const html = renderToStaticMarkup(<DeadlineReminder result={{
      ...dates.result,
      basis: { ...dates.result.basis, sourceExcerpt: '<a href="https://example.invalid">인공 문구</a>' },
    }} />);

    expect(html).toContain("sample-revision-1");
    expect(html).toContain("인공 자료 · 신청기간 항목");
    expect(html).toContain("실제 정책 원문 아님");
    expect(html).toContain("계산 시각 · 수집 시각과 별개");
    expect(html).toContain("<details");
    expect(html).not.toMatch(/<details[^>]*\sopen/);
    expect(html).toContain("&lt;a href=");
    expect(html).not.toContain("<a ");
  });
});
