import { toReminderExamplesView } from "@/features/reminders/reminder-api-view";
import { loadPreviewExamples } from "../../load-preview-examples";

export const loadReminderExamples = () => loadPreviewExamples("reminder-examples", toReminderExamplesView);
