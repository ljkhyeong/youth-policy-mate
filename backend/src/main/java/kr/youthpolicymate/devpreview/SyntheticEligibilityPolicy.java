package kr.youthpolicymate.devpreview;

import kr.youthpolicymate.eligibility.AgeCondition;
import kr.youthpolicymate.eligibility.AgeConditionEvaluator;
import kr.youthpolicymate.eligibility.EligibilityDecision;
import kr.youthpolicymate.eligibility.EmploymentAnswer;
import kr.youthpolicymate.eligibility.EmploymentCondition;
import kr.youthpolicymate.eligibility.EmploymentConditionEvaluator;
import kr.youthpolicymate.eligibility.EmploymentFact;
import kr.youthpolicymate.eligibility.EvaluationBasis;
import kr.youthpolicymate.eligibility.IncomeAnswer;
import kr.youthpolicymate.eligibility.IncomeBasis;
import kr.youthpolicymate.eligibility.IncomeCondition;
import kr.youthpolicymate.eligibility.IncomeConditionEvaluator;
import kr.youthpolicymate.eligibility.IncomeRange;
import kr.youthpolicymate.eligibility.PolicyReview;
import kr.youthpolicymate.eligibility.ResidenceCondition;
import kr.youthpolicymate.eligibility.ResidenceConditionEvaluator;
import kr.youthpolicymate.eligibility.SeoulDistrict;
import kr.youthpolicymate.eligibility.SeoulResidence;
import kr.youthpolicymate.eligibility.SourceEvidence;
import kr.youthpolicymate.policy.ApplicationPeriod;
import kr.youthpolicymate.policy.RecruitmentAssessment;
import kr.youthpolicymate.policy.RecruitmentSchedule;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static kr.youthpolicymate.devpreview.EligibilityTrialRequest.QuestionSetId;

// 개발용 질문과 답변 코드의 의미를 함께 관리한다. 실제 정책 파서나 범용 규칙 엔진이 아니다.
record SyntheticEligibilityPolicy(QuestionSetId id) {
    private static final String POLICY_ID = "sample-answer-policy";
    private static final Instant EVALUATED_AT = Instant.parse("2026-08-30T15:30:00Z");

    String revision() { return id == QuestionSetId.INITIAL ? "sample-revision-1" : "sample-revision-2"; }
    private LocalDate referenceDate() { return LocalDate.parse(id == QuestionSetId.INITIAL ? "2026-08-01" : "2026-08-30"); }

    private EmploymentFact employmentFact() {
        return new EmploymentFact(POLICY_ID, revision(), "sample-employment",
                id == QuestionSetId.INITIAL ? "근무처와 근로계약을 맺고 일하고 있는 상태" : "유급 근로계약을 맺고 일하고 있는 상태",
                referenceDate());
    }

    private EmploymentCondition.FactRequirement employmentCondition() {
        var fact = employmentFact();
        return new EmploymentCondition.FactRequirement(fact, false,
                evidence("인공 취업 요건", fact.referenceDate() + " 기준 " + fact.definition() + "에 해당하지 않아야 함."));
    }

    private IncomeBasis incomeBasis() {
        String year = id == QuestionSetId.INITIAL ? "2025" : "2024";
        return new IncomeBasis(POLICY_ID, revision(), "sample-income", IncomeBasis.Personal.INSTANCE,
                "모든 근무처의 세전 급여·상여금 합계. 가족·사업 소득 제외",
                new IncomeBasis.Period(LocalDate.parse(year + "-01-01"), LocalDate.parse(year + "-12-31"), "연간 합계"),
                Optional.empty(), Optional.of(LocalDate.parse(year + "-12-31")));
    }

    private IncomeCondition.RangeRequirement incomeCondition() {
        var basis = incomeBasis();
        return new IncomeCondition.RangeRequirement(basis, new IncomeRange(IncomeRange.Unbounded.INSTANCE, amount("25000000", true)),
                "인공 자료에 원 단위로 명시한 값. 환산 없음",
                evidence("인공 소득 요건", basis.description() + " · 25,000,000원 이하."));
    }

    EligibilityQuestionsResponse.QuestionSetResponse question() {
        var employment = employmentCondition();
        var income = incomeCondition();
        return new EligibilityQuestionsResponse.QuestionSetResponse(id, id == QuestionSetId.INITIAL ? "처음 질문" : "개정·기준 변경 질문",
                POLICY_ID, revision(), "양력 1999-08-01 출생 · " + referenceDate() + " 기준 서울 마포구 거주로 고정. 실제 개인정보 아님",
                employment.fact().definition(), employment.appliedCondition(), EligibilityResultResponse.EvidenceResponse.from(employment.evidence()),
                income.basis().description(), "허용 소득 구간: " + income.allowedRange().description() + " · " + income.amountBasis(),
                EligibilityResultResponse.EvidenceResponse.from(income.evidence()));
    }

    EligibilityTrialResponse evaluate(EligibilityTrialRequest request) {
        var age = new AgeCondition.CompletedYears("sample-age", 19, 34, referenceDate(), evidence("인공 연령 요건", "기준일에 만 19세 이상 34세 이하."));
        var residence = new ResidenceCondition.RegisteredIn("sample-residence", ResidenceCondition.Area.SEOUL, Set.of(), referenceDate(),
                evidence("인공 거주 요건", "기준일에 주민등록상 서울특별시 거주."));
        // 답변이 작성된 질문의 정의·개정·기간으로 복원한다. 현재 질문의 기준으로 덮어쓰지 않는다.
        var employmentAnswer = new SyntheticEligibilityPolicy(request.employmentQuestionSet()).employmentAnswer(request.employmentChoice());
        var incomeAnswer = new SyntheticEligibilityPolicy(request.incomeQuestionSet()).incomeAnswer(request.incomeChoice());
        var decision = new EligibilityDecision(new EvaluationBasis(POLICY_ID, revision(), "sample-rule-1", EVALUATED_AT), PolicyReview.complete(), List.of(
                AgeConditionEvaluator.evaluate(age, Optional.of(LocalDate.parse("1999-08-01"))),
                ResidenceConditionEvaluator.evaluate(residence, Optional.of(new SeoulResidence(SeoulDistrict.MAPO, referenceDate()))),
                EmploymentConditionEvaluator.evaluate(employmentCondition(), employmentAnswer),
                IncomeConditionEvaluator.evaluate(incomeCondition(), incomeAnswer)));
        var recruitment = new RecruitmentAssessment(new RecruitmentSchedule(POLICY_ID, revision(),
                new ApplicationPeriod.Dates(LocalDate.parse("2026-08-20"), LocalDate.parse("2026-09-07")),
                "sample-answer-source", "인공 신청기간", Optional.of("2026-08-20~2026-09-07")), EVALUATED_AT);
        return new EligibilityTrialResponse(EligibilityExamplesResponse.DataKind.SYNTHETIC, id,
                new EligibilityExamplesResponse.ExampleResponse(id.name(), question().label(), "선택한 인공 답변의 서버 판정 결과",
                        EligibilityResultResponse.from(decision),
                        new EligibilityExamplesResponse.RecruitmentResponse(recruitment.status(), recruitment.explanation())));
    }

    private Optional<EmploymentAnswer> employmentAnswer(EligibilityTrialRequest.EmploymentChoice choice) {
        return switch (choice) {
            case UNANSWERED -> Optional.empty();
            case APPLIES -> Optional.of(new EmploymentAnswer(employmentFact(), EmploymentAnswer.Response.APPLIES));
            case DOES_NOT_APPLY -> Optional.of(new EmploymentAnswer(employmentFact(), EmploymentAnswer.Response.DOES_NOT_APPLY));
            case UNKNOWN -> Optional.of(new EmploymentAnswer(employmentFact(), EmploymentAnswer.Response.UNKNOWN));
        };
    }

    private Optional<IncomeAnswer> incomeAnswer(EligibilityTrialRequest.IncomeChoice choice) {
        if (choice == EligibilityTrialRequest.IncomeChoice.UNANSWERED) return Optional.empty();
        if (choice == EligibilityTrialRequest.IncomeChoice.UNKNOWN) return Optional.of(new IncomeAnswer.Unknown(incomeBasis()));
        var range = switch (choice) {
            case ZERO -> IncomeRange.exact(BigDecimal.ZERO);
            case UP_TO_20M -> new IncomeRange(amount("0", false), amount("20000000", true));
            case BETWEEN_20M_30M -> new IncomeRange(amount("20000000", false), amount("30000000", true));
            case BETWEEN_20M_25M -> new IncomeRange(amount("20000000", false), amount("25000000", true));
            case OVER_25M -> new IncomeRange(amount("25000000", false), IncomeRange.Unbounded.INSTANCE);
            default -> throw new IllegalStateException("금액 구간이 아닌 인공 답변입니다.");
        };
        return Optional.of(new IncomeAnswer.KnownRange(incomeBasis(), range));
    }

    private static IncomeRange.Amount amount(String value, boolean inclusive) { return new IncomeRange.Amount(new BigDecimal(value), inclusive); }
    private static SourceEvidence evidence(String location, String excerpt) {
        return new SourceEvidence("sample-answer-source · 실제 정책 원문 아님", location, Optional.of(excerpt));
    }
}
