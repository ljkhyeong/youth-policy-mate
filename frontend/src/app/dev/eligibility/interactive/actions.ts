"use server";

import { evaluateTrial, type TrialRequest } from "./trial-api";

export async function evaluateTrialAction(request: TrialRequest) {
  return evaluateTrial(request);
}
