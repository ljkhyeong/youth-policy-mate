package kr.youthpolicymate.devpreview;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(requiredProperties = {"questionSet", "employmentQuestionSet", "employmentChoice", "incomeQuestionSet", "incomeChoice"})
public record EligibilityTrialRequest(
        @NotNull QuestionSetId questionSet,
        @NotNull QuestionSetId employmentQuestionSet,
        @NotNull EmploymentChoice employmentChoice,
        @NotNull QuestionSetId incomeQuestionSet,
        @NotNull IncomeChoice incomeChoice
) {
    public enum QuestionSetId { INITIAL, REVISED }
    public enum EmploymentChoice { UNANSWERED, APPLIES, DOES_NOT_APPLY, UNKNOWN }
    public enum IncomeChoice { UNANSWERED, UNKNOWN, ZERO, UP_TO_20M, BETWEEN_20M_30M, BETWEEN_20M_25M, OVER_25M }
}
