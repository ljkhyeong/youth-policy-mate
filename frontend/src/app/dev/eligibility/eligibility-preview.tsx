"use client";

import { useState } from "react";
import { EligibilityResult, ELIGIBILITY_STATUS_LABELS } from "@/features/eligibility/eligibility-result";
import { ELIGIBILITY_EXAMPLES } from "./eligibility-preview-data";

export function EligibilityPreview() {
  const [exampleIndex, setExampleIndex] = useState(0);
  const [notice, setNotice] = useState("");
  const example = ELIGIBILITY_EXAMPLES[exampleIndex];

  return (
    <div className="mt-9">
      <div role="group" aria-label="자격 결과 예시 선택" className="flex flex-wrap gap-x-3 border-b border-stone-300">
        {ELIGIBILITY_EXAMPLES.map((item, index) => (
          <button key={item.id} type="button" className="preview-choice" aria-pressed={index === exampleIndex}
            onClick={() => {
              if (index === exampleIndex) return;
              setExampleIndex(index);
              setNotice(`${item.label} 예시로 변경했습니다. 전체 자격 안내: ${ELIGIBILITY_STATUS_LABELS[item.result.status]}.`);
            }}>
            {item.label}
          </button>
        ))}
      </div>
      <p className="mt-4 text-sm leading-7 text-stone-700">{example.description}</p>
      <p role="status" className="mt-2 min-h-7 text-xs leading-7 text-teal-900">{notice}</p>
      <div className="mt-4">
        <EligibilityResult key={example.id} result={example.result} recruitment={example.recruitment} />
      </div>
    </div>
  );
}
