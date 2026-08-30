"use client";

import { useState } from "react";
import { DeadlineReminder, REMINDER_OUTCOME_LABELS } from "@/features/reminders/deadline-reminder";
import { REMINDER_EXAMPLES } from "./reminder-preview-data";

export function ReminderPreview() {
  const [exampleIndex, setExampleIndex] = useState(0);
  const [notice, setNotice] = useState("");
  const example = REMINDER_EXAMPLES[exampleIndex];

  return (
    <div className="mt-9">
      <div role="group" aria-label="마감 알림 예시 선택" className="flex flex-wrap gap-x-3 border-b border-stone-300">
        {REMINDER_EXAMPLES.map((item, index) => (
          <button key={item.id} type="button" className="preview-choice" aria-pressed={index === exampleIndex}
            onClick={() => {
              if (index === exampleIndex) return;
              setExampleIndex(index);
              setNotice(`${item.label} 예시로 변경했습니다. ${REMINDER_OUTCOME_LABELS[item.result.outcome]}.`);
            }}>
            {item.label}
          </button>
        ))}
      </div>
      <p className="mt-4 text-sm leading-7 text-stone-700">{example.description}</p>
      <p role="status" className="mt-2 min-h-7 text-xs leading-7 text-teal-900">{notice}</p>
      <div className="mt-4"><DeadlineReminder key={example.id} result={example.result} /></div>
    </div>
  );
}
