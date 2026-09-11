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
        @Valid BirthBinding birthBinding, @Valid AgeBinding ageBinding,
        Boolean monthly, @Valid PeriodNotice periodNotice, String ageNotice,
        @Valid RemainingVariant remainingVariant) {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    public PolicyRuleDefinition { monthly = Boolean.TRUE.equals(monthly); }

    public record RuleCheck(@NotBlank String questionId, @NotBlank String label, @NotBlank String evidence,
                            @NotEmpty List<@NotNull @Valid RuleCase> cases, @NotBlank String unknownExplanation,
                            List<@NotNull @Valid ProvidedAnswer> providedAnswers, String separator) {}
    public record ProvidedAnswer(@NotBlank String questionId, @NotNull String prefix) {}
    public record PeriodNotice(@NotNull Instant opensAt, @NotNull Instant closesAt,
                               @NotBlank String before, @NotBlank String open, @NotBlank String closed) {
        String at(Instant now) { return now.isBefore(opensAt) ? before : now.isBefore(closesAt) ? open : closed; }
    }
    public record RemainingVariant(@PositiveOrZero int index, @NotBlank String questionId,
                                    @NotEmpty Map<@NotBlank String, @NotBlank String> byValue) {}
    public record AgeBinding(@NotBlank String questionId, @PositiveOrZero int minimumInclusive,
                             @PositiveOrZero Integer maximumInclusive, LocalDate referenceDate,
                             @NotBlank String below, @NotBlank String within, String above, boolean showCalculatedAge) {
        LocalDate referenceAt(Instant now) { return referenceDate == null ? now.atZone(SEOUL).toLocalDate() : referenceDate; }
        ConditionAssessment assessment(LocalDate birth, Instant now, SourceEvidence evidence) {
            return AgeConditionEvaluator.evaluate(new AgeCondition.CompletedYears(questionId, minimumInclusive,
                    maximumInclusive == null ? Integer.MAX_VALUE : maximumInclusive, referenceAt(now), evidence), Optional.of(birth));
        }
        String answer(LocalDate birth, Instant now, SourceEvidence evidence) {
            var result = assessment(birth, now, evidence);
            if (result.outcome() == UNKNOWN) return null;
            return result.outcome() == ConditionAssessment.Outcome.MET ? within
                    : birth.isAfter(referenceAt(now).minusYears(minimumInclusive)) ? below : above;
        }
    }
    public record RuleCase(@NotEmpty Map<@NotBlank String, @NotEmpty Set<@NotNull @Size(max = 40) String>> when,
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
        if (monthly && ruleVersion.length() > 72) throw new IllegalArgumentException("월별 규칙 버전은 72자 이하여야 합니다.");
        if (periodNotice != null && !periodNotice.opensAt().isBefore(periodNotice.closesAt()))
            throw new IllegalArgumentException("모집 종료는 시작 이후여야 합니다.");
        if (remainingVariant != null && (remainingVariant.index() >= remainingChecks.size()
                || !options.containsKey(remainingVariant.questionId()) || !options.get(remainingVariant.questionId()).containsAll(remainingVariant.byValue().keySet())))
            throw new IllegalArgumentException("추가 안내의 질문·선택지·위치를 확인해주세요.");
        for (var check : checks) {
            if (!options.containsKey(check.questionId())) throw new IllegalArgumentException("판정 항목의 질문이 없습니다.");
            if (check.providedAnswers() != null && (check.providedAnswers().isEmpty() || check.separator() == null
                    || check.providedAnswers().stream().anyMatch(answer -> !options.containsKey(answer.questionId()))))
                throw new IllegalArgumentException("결과에 표시할 답변을 확인해주세요.");
            for (var row : check.cases()) row.when().forEach((id, values) -> {
                if (!options.containsKey(id) || !values.stream().allMatch(value -> value.isEmpty() || options.get(id).contains(value))) throw new IllegalArgumentException("판정표에 없는 질문·선택지를 참조했습니다.");
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
            validateBinding(options, b.questionId(), refs);
        }
        if (ageBinding != null) {
            if (birthBinding != null || (ageBinding.maximumInclusive() != null && ageBinding.maximumInclusive() < ageBinding.minimumInclusive()))
                throw new IllegalArgumentException("출생일·만 나이 연결과 연령 범위를 확인해주세요.");
            var refs = new ArrayList<String>(); refs.add(ageBinding.below()); refs.add(ageBinding.within());
            if (ageBinding.maximumInclusive() != null) refs.add(ageBinding.above());
            validateBinding(options, ageBinding.questionId(), refs);
        }
    }
    private void validateBinding(Map<String, Set<String>> options, String id, List<String> refs) {
        if (!options.containsKey(id) || refs.stream().anyMatch(value -> !options.get(id).contains(value))
                || checks.stream().filter(c -> c.questionId().equals(id)).count() != 1
                || checks.stream().filter(c -> c.questionId().equals(id)).flatMap(c -> c.cases().stream())
                    .anyMatch(c -> !c.when().keySet().equals(Set.of(id))))
            throw new IllegalArgumentException("연령 연결은 해당 질문 하나만으로 비교해야 합니다.");
    }

    public boolean appliesAt(Instant now) { return !now.isBefore(validFrom) && now.isBefore(validUntil); }
    public Questionnaire questionnaire(long revision, String currentHash, Instant now) {
        boolean available = contentHash.equals(currentHash) && appliesAt(now);
        return new Questionnaire(policyNumber, revision, available ? versionAt(now) : "", available, scopeAt(now),
                available ? noticeAt(now) + reason : "공고가 바뀌었거나 적용 기간이 끝나 조건을 다시 확인하고 있어요. 공식 안내를 확인해주세요.",
                sourceUrl, available ? questions : List.of());
    }
    public Evaluation evaluate(long revision, Request input, Instant now) {
        if (!appliesAt(now)) throw new IllegalArgumentException("검토한 적용 기간의 규칙만 사용할 수 있습니다.");
        if (!versionAt(now).equals(input.ruleVersion())) throw new IllegalArgumentException("질문 기준 시점이 바뀌었습니다.");
        var values = validatedAnswers(questions, input.answers());
        var results = checks.stream().map(c -> evaluateCheck(c, values)).toList();
        var source = evidence(now);
        var remaining = new ArrayList<>(remainingChecks);
        if (remainingVariant != null) remaining.set(remainingVariant.index(), remainingVariant.byValue()
                .getOrDefault(values.getOrDefault(remainingVariant.questionId(), ""), remaining.get(remainingVariant.index())));
        var assessments = results.stream().map(c -> new ConditionAssessment(c.label(), c.label(), Optional.of(c.providedValue()),
                Optional.empty(), c.outcome(), c.outcome() == UNKNOWN ? Optional.of(ConditionAssessment.Uncertainty.MISSING_USER_INPUT) : Optional.empty(),
                c.explanation(), source)).toList();
        var basis = new EvaluationBasis(policyNumber, Long.toString(revision), versionAt(now), now);
        var common = new EligibilityDecision(basis, PolicyReview.complete(), assessments).status();
        var whole = new EligibilityDecision(basis, PolicyReview.incomplete(remaining.stream()
                .map(message -> new PolicyReview.PendingIssue(message, source)).toList()), assessments).status();
        return new Evaluation(policyNumber, revision, versionAt(now), whole, common, scopeAt(now), noticeAt(now) + explanation, remaining, sourceUrl, now, results);
    }
    private Check evaluateCheck(RuleCheck check, Map<String, String> values) {
        var row = check.cases().stream().filter(c -> c.when().entrySet().stream()
                .allMatch(e -> e.getValue().contains(values.getOrDefault(e.getKey(), "")))).findFirst();
        var provided = check.providedAnswers() == null ? provided(check.questionId(), values)
                : check.providedAnswers().stream().map(answer -> answer.prefix() + provided(answer.questionId(), values))
                    .collect(java.util.stream.Collectors.joining(check.separator()));
        return new Check(check.label(), provided, row.map(RuleCase::outcome).orElse(UNKNOWN),
                row.map(RuleCase::explanation).orElse(check.unknownExplanation()), check.evidence());
    }
    private String provided(String id, Map<String, String> values) {
        return questions.stream().filter(q -> q.id().equals(id)).flatMap(q -> q.options().stream())
                .filter(option -> option.value().equals(values.get(id))).map(Option::label).findFirst().orElse("미응답");
    }
    public String versionAt(Instant now) { return monthly ? ruleVersion + "-" + YearMonth.from(now.atZone(SEOUL)) : ruleVersion; }
    private String scopeAt(Instant now) {
        var date = now.atZone(SEOUL);
        return monthly ? date.getYear() + "년 " + date.getMonthValue() + "월 " + scope : scope;
    }
    private String noticeAt(Instant now) { return periodNotice == null ? "" : periodNotice.at(now) + " "; }
    private SourceEvidence evidence(Instant now) { return new SourceEvidence(sourceUrl, scopeAt(now), Optional.empty()); }
    public List<Answer> prefill(LocalDate birth, Instant now) {
        if (birthBinding != null) return List.of(new Answer(birthBinding.questionId(), birthBinding.answer(birth)));
        if (ageBinding == null) return List.of();
        var value = ageBinding.answer(birth, now, evidence(now));
        return value == null ? List.of() : List.of(new Answer(ageBinding.questionId(), value));
    }
    PolicyAgeComparison compareBirth(LocalDate birth, Instant now) {
        var answers = prefill(birth, now);
        if (answers.isEmpty()) return null;
        var answer = answers.getFirst();
        var definition = checks.stream().filter(c -> c.questionId().equals(answer.questionId())).findFirst().orElseThrow();
        var check = evaluateCheck(definition, Map.of(answer.questionId(), answer.value()));
        if (ageBinding != null && ageBinding.showCalculatedAge()) {
            var provided = ageBinding.assessment(birth, now, evidence(now)).comparedValue().orElseThrow()
                    + " (" + ageBinding.referenceAt(now) + " · 서울)";
            check = new Check(check.label(), provided, check.outcome(), check.explanation(), check.evidence());
        }
        return new PolicyAgeComparison(contentHash, versionAt(now), sourceUrl, check,
                (noticeAt(now) + (ageNotice == null ? "" : ageNotice)).strip());
    }
}
