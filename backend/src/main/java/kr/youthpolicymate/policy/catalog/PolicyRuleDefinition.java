package kr.youthpolicymate.policy.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import kr.youthpolicymate.eligibility.*;
import java.time.*;
import java.util.*;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN;

/** 공고의 선택지 판정표. 첫 일치 행을 적용하고 미일치는 추가 확인으로 남긴다. */
public record PolicyRuleDefinition(
        @Pattern(regexp = "[0-9]{20}") @NotNull String policyNumber,
        @NotBlank @Size(max = 80) String ruleVersion,
        @Pattern(regexp = "[a-f0-9]{64}") @NotNull String contentHash,
        @NotNull Instant validFrom, @NotNull Instant validUntil,
        @NotBlank String scope, @NotBlank String reason, @NotBlank String explanation,
        @NotBlank @Pattern(regexp = "https://[^\\s]+") String sourceUrl,
        @NotEmpty List<@NotBlank String> remainingChecks,
        @NotEmpty @Size(max = 20) List<@NotNull Question> questions,
        @NotEmpty List<@NotNull @Valid RuleCheck> checks,
        @Valid BirthBinding birthBinding) {

    public record RuleCheck(@NotBlank String questionId, @NotBlank String label, @NotBlank String evidence,
                            @NotEmpty List<@NotNull @Valid RuleCase> cases, @NotBlank String unknownExplanation) {}
    public record RuleCase(@NotEmpty Map<@NotBlank String, @NotEmpty Set<@NotBlank String>> when,
                           @NotNull ConditionAssessment.Outcome outcome, @NotBlank String explanation) {}
    public record BirthBinding(@NotBlank String questionId, LocalDate minimumInclusive, LocalDate maximumInclusive,
                               String below, @NotBlank String within, String above) {
        String answer(LocalDate birth) {
            if (minimumInclusive != null && birth.isBefore(minimumInclusive)) return below;
            if (maximumInclusive != null && birth.isAfter(maximumInclusive)) return above;
            return within;
        }
    }

    public void validate(jakarta.validation.Validator validator) {
        var violations = validator.validate(this);
        if (!violations.isEmpty()) throw new IllegalArgumentException("규칙 형식 오류: " + violations.stream()
                .map(v -> v.getPropertyPath().toString()).sorted().toList());
        if (!validFrom.isBefore(validUntil)) throw new IllegalArgumentException("적용 종료는 시작 이후여야 합니다.");
        var options = new HashMap<String, Set<String>>();
        for (var question : questions) {
            if (question.id() == null || !question.id().matches("[a-zA-Z][a-zA-Z0-9_]{0,59}")
                    || question.label() == null || question.label().isBlank() || question.help() == null
                    || question.options() == null || question.options().isEmpty()) throw new IllegalArgumentException("질문 형식을 확인해주세요.");
            var values = new HashSet<String>();
            for (var option : question.options()) {
                if (option == null || option.value() == null || option.value().isBlank() || option.value().length() > 40
                        || option.label() == null || option.label().isBlank() || !values.add(option.value()))
                    throw new IllegalArgumentException("선택지 형식과 중복을 확인해주세요.");
            }
            if (options.putIfAbsent(question.id(), values) != null) throw new IllegalArgumentException("질문 식별자가 중복됐습니다.");
        }
        for (var check : checks) {
            if (!options.containsKey(check.questionId())) throw new IllegalArgumentException("판정 항목의 질문이 없습니다.");
            for (var row : check.cases()) row.when().forEach((id, values) -> {
                if (!options.containsKey(id) || !options.get(id).containsAll(values)) throw new IllegalArgumentException("판정표에 없는 질문·선택지를 참조했습니다.");
            });
        }
        if (birthBinding != null) {
            var b = birthBinding;
            if ((b.minimumInclusive() == null && b.maximumInclusive() == null)
                    || (b.minimumInclusive() != null && b.maximumInclusive() != null && b.minimumInclusive().isAfter(b.maximumInclusive())))
                throw new IllegalArgumentException("출생일 범위를 확인해주세요.");
            var refs = new ArrayList<String>(); refs.add(b.within());
            if (b.minimumInclusive() != null) refs.add(b.below());
            if (b.maximumInclusive() != null) refs.add(b.above());
            if (!options.containsKey(b.questionId()) || refs.stream().anyMatch(v -> !options.get(b.questionId()).contains(v))
                    || checks.stream().filter(c -> c.questionId().equals(b.questionId())).count() != 1
                    || checks.stream().filter(c -> c.questionId().equals(b.questionId())).flatMap(c -> c.cases().stream())
                    .anyMatch(c -> !c.when().keySet().equals(Set.of(b.questionId()))))
                throw new IllegalArgumentException("출생일 연결은 출생일 질문 하나만으로 비교해야 합니다.");
        }
    }

    public boolean appliesAt(Instant now) { return !now.isBefore(validFrom) && now.isBefore(validUntil); }
    public Questionnaire questionnaire(long revision, String currentHash, Instant now) {
        boolean available = contentHash.equals(currentHash) && appliesAt(now);
        return new Questionnaire(policyNumber, revision, available ? ruleVersion : "", available, scope,
                available ? reason : "공고가 바뀌었거나 적용 기간이 끝나 조건을 다시 확인하고 있어요. 공식 안내를 확인해주세요.",
                sourceUrl, available ? questions : List.of());
    }
    public Evaluation evaluate(long revision, Request input, Instant now) {
        if (!appliesAt(now)) throw new IllegalArgumentException("검토한 적용 기간의 규칙만 사용할 수 있습니다.");
        var values = validatedAnswers(questions, input.answers());
        var results = checks.stream().map(c -> evaluateCheck(c, values)).toList();
        var source = new SourceEvidence(sourceUrl, scope, Optional.empty());
        var assessments = results.stream().map(c -> new ConditionAssessment(c.label(), c.label(), Optional.of(c.providedValue()),
                Optional.empty(), c.outcome(), c.outcome() == UNKNOWN ? Optional.of(ConditionAssessment.Uncertainty.MISSING_USER_INPUT) : Optional.empty(),
                c.explanation(), source)).toList();
        var basis = new EvaluationBasis(policyNumber, Long.toString(revision), ruleVersion, now);
        var common = new EligibilityDecision(basis, PolicyReview.complete(), assessments).status();
        var whole = new EligibilityDecision(basis, PolicyReview.incomplete(remainingChecks.stream()
                .map(message -> new PolicyReview.PendingIssue(message, source)).toList()), assessments).status();
        return new Evaluation(policyNumber, revision, ruleVersion, whole, common, scope, explanation, remainingChecks, sourceUrl, now, results);
    }
    private Check evaluateCheck(RuleCheck check, Map<String, String> values) {
        var row = check.cases().stream().filter(c -> c.when().entrySet().stream()
                .allMatch(e -> values.containsKey(e.getKey()) && e.getValue().contains(values.get(e.getKey())))).findFirst();
        var provided = questions.stream().filter(q -> q.id().equals(check.questionId())).flatMap(q -> q.options().stream())
                .filter(o -> o.value().equals(values.get(check.questionId()))).map(Option::label).findFirst().orElse("미응답");
        return new Check(check.label(), provided, row.map(RuleCase::outcome).orElse(UNKNOWN),
                row.map(RuleCase::explanation).orElse(check.unknownExplanation()), check.evidence());
    }
    public List<Answer> prefill(LocalDate birth) {
        return birthBinding == null ? List.of() : List.of(new Answer(birthBinding.questionId(), birthBinding.answer(birth)));
    }
    BasicConditionRules.Comparison compareBirth(LocalDate birth) {
        if (birthBinding == null) return null;
        var answer = prefill(birth).getFirst();
        var check = checks.stream().filter(c -> c.questionId().equals(answer.questionId())).findFirst().orElseThrow();
        return new BasicConditionRules.Comparison(contentHash, ruleVersion, sourceUrl,
                evaluateCheck(check, Map.of(answer.questionId(), answer.value())), "");
    }
}
