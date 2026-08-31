import { toReminderExamplesView, type ReminderExamplesResponse } from "@/features/reminders/reminder-api-view";
import type { ReminderExampleView } from "@/features/reminders/deadline-reminder-view";

type PreviewLoadResult = { status: "available"; examples: readonly ReminderExampleView[] } | { status: "unavailable" };

// 이 함수는 개발 전용 서버 컴포넌트에서만 호출한다. 사용자 입력으로 조회 주소를 바꾸지 않는다.
export async function loadReminderExamples(): Promise<PreviewLoadResult> {
  if (process.env.NODE_ENV !== "development") return { status: "unavailable" };
  try {
    const response = await fetch("http://127.0.0.1:8081/api/dev/reminder-examples", {
      cache: "no-store", redirect: "error", signal: AbortSignal.timeout(5000),
      headers: { Accept: "application/json" },
    });
    if (!response.ok || !response.headers.get("content-type")?.includes("application/json")) {
      return { status: "unavailable" };
    }
    const body = await response.json() as ReminderExamplesResponse;
    return { status: "available", examples: toReminderExamplesView(body) };
  } catch {
    // 외부 오류 본문·내부 주소를 표시하지 않고, 고정 예시로 조용히 대체하지 않는다.
    return { status: "unavailable" };
  }
}
