package kr.youthpolicymate.devpreview;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"dataKind", "questionSets", "employmentChoices", "incomeChoices"})
public record EligibilityQuestionsResponse(
        EligibilityExamplesResponse.DataKind dataKind,
        List<QuestionSetResponse> questionSets,
        List<EmploymentChoiceResponse> employmentChoices,
        List<IncomeChoiceResponse> incomeChoices
) {
    @Schema(name = "EligibilityQuestionSet", requiredProperties = {"id", "label", "policyId", "policyRevision", "fixedInputs",
            "employmentDescription", "employmentRequirement", "employmentEvidence", "incomeDescription", "incomeRequirement", "incomeEvidence"})
    public record QuestionSetResponse(
            EligibilityTrialRequest.QuestionSetId id, String label, String policyId, String policyRevision, String fixedInputs,
            String employmentDescription, String employmentRequirement, EligibilityResultResponse.EvidenceResponse employmentEvidence,
            String incomeDescription, String incomeRequirement, EligibilityResultResponse.EvidenceResponse incomeEvidence
    ) {}

    @Schema(name = "TrialEmploymentChoice", requiredProperties = {"value", "label"})
    public record EmploymentChoiceResponse(EligibilityTrialRequest.EmploymentChoice value, String label) {}

    @Schema(name = "TrialIncomeChoice", requiredProperties = {"value", "label"})
    public record IncomeChoiceResponse(EligibilityTrialRequest.IncomeChoice value, String label) {}
}
