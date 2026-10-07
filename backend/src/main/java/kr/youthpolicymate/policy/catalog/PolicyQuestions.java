package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import kr.youthpolicymate.eligibility.ConditionOutcome;
import kr.youthpolicymate.eligibility.EligibilityStatus;
import java.time.Instant;
import java.util.List;

public final class PolicyQuestions {
    @Schema(name = "PolicyQuestionnaire", requiredProperties = {"policyNumber", "revision", "ruleVersion", "available", "scope", "reason", "sourceUrl", "questions"})
    public record Questionnaire(String policyNumber, long revision, String ruleVersion, boolean available, String scope,
                                String reason, String sourceUrl, List<Question> questions) {}
    @Schema(name = "PolicyQuestion", requiredProperties = {"id", "label", "help", "options"})
    public record Question(@NotNull @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_]{0,59}") String id, @NotBlank String label,
                           @NotNull String help, @NotEmpty List<@NotNull @Valid Option> options) {}
    @Schema(name = "PolicyAnswerOption", requiredProperties = {"value", "label"})
    public record Option(@NotBlank @Size(max = 40) String value, @NotBlank String label) {}
    @Schema(name = "PolicyAnswer", requiredProperties = {"questionId", "value"})
    public record Answer(@NotBlank @Size(max = 60) String questionId, @NotBlank @Size(max = 40) String value) {}
    @Schema(name = "PolicyEvaluationRequest", requiredProperties = {"revision", "ruleVersion", "answers"})
    public record Request(@Positive long revision, @NotBlank @Size(max = 80) String ruleVersion,
                          @NotNull @Size(max = 20) List<@Valid Answer> answers) {
        @Override public String toString() { return "PolicyEvaluationRequest[답변 내용 제외]"; }
    }
    @Schema(name = "PolicyPrefillRequest", requiredProperties = {"revision", "ruleVersion", "birthDate"})
    public record PrefillRequest(@Positive long revision, @NotBlank @Size(max = 80) String ruleVersion,
                                 @NotNull java.time.LocalDate birthDate) {
        @Override public String toString() { return "PolicyPrefillRequest[생년월일 제외]"; }
    }
    @Schema(name = "PolicyPrefill", requiredProperties = {"answers"})
    public record Prefill(List<Answer> answers) {}
    @Schema(name = "PolicyEvaluation", requiredProperties = {"policyNumber", "revision", "ruleVersion", "status", "commonCriteriaStatus", "scope", "explanation", "remainingChecks", "sourceUrl", "evaluatedAt", "checks"})
    public record Evaluation(String policyNumber, long revision, String ruleVersion, EligibilityStatus status,
                             EligibilityStatus commonCriteriaStatus, String scope, String explanation, List<String> remainingChecks,
                             String sourceUrl, Instant evaluatedAt, List<Check> checks) {}
    @Schema(name = "PolicyEvaluatedCheck", requiredProperties = {"label", "providedValue", "outcome", "explanation", "evidence"})
    public record Check(String label, String providedValue, ConditionOutcome outcome, String explanation, String evidence) {}
}
