import { toEligibilityExamplesView } from "@/features/eligibility/eligibility-api-view";
import { loadPreviewExamples } from "../../load-preview-examples";

export const loadEligibilityExamples = () => loadPreviewExamples("eligibility-examples", toEligibilityExamplesView);
