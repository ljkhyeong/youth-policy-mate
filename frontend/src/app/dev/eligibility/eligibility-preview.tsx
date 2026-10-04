"use client";

import { useState } from "react";
import { PreviewChoices } from "@/components/dev-preview/preview-choices";
import { EligibilityResult, ELIGIBILITY_STATUS_LABELS } from "@/features/eligibility/eligibility-result";
import type { EligibilityExampleView } from "@/features/eligibility/eligibility-result-view";

export function EligibilityPreview({ examples }: { examples: readonly EligibilityExampleView[] }) {
  const [exampleIndex, setExampleIndex] = useState(0);
  const [notice, setNotice] = useState("");
  const example = examples[exampleIndex];

  return (
    <div className="mt-9">
      <PreviewChoices label="자격 결과 예시 선택" items={examples} selected={exampleIndex} keyOf={item => item.id} onSelect={(index, item) => {
        setExampleIndex(index);
        setNotice(`${item.label} 예시로 변경했습니다. 전체 자격 안내: ${ELIGIBILITY_STATUS_LABELS[item.result.status]}.`);
      }} />
      <p className="mt-4 text-sm leading-7 text-stone-700">{example.description}</p>
      <p role="status" className="mt-2 min-h-7 text-xs leading-7 text-teal-900">{notice}</p>
      <div className="mt-4">
        <EligibilityResult key={example.id} result={example.result} recruitment={example.recruitment} />
      </div>
    </div>
  );
}
