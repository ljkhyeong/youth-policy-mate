package kr.youthpolicymate.devpreview;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import kr.youthpolicymate.eligibility.AgeCondition;
import kr.youthpolicymate.eligibility.AgeConditionEvaluator;
import kr.youthpolicymate.eligibility.EligibilityDecision;
import kr.youthpolicymate.eligibility.EmploymentCondition;
import kr.youthpolicymate.eligibility.EmploymentConditionEvaluator;
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
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@Profile("preview")
public class EligibilityPreviewController {
    private static final String POLICY_ID = "sample-eligibility-policy";
    private static final LocalDate REFERENCE_DATE = LocalDate.parse("2026-08-01");
    private static final Clock SAMPLE_CLOCK = Clock.fixed(Instant.parse("2026-08-30T15:30:00Z"), ZoneId.of("Asia/Seoul"));

    @GetMapping(value = "/api/dev/eligibility-examples", produces = "application/json")
    @Operation(operationId = "listDevelopmentEligibilityExamples", summary = "개발 전용 자격 판정 예시 조회",
            description = "고정 인공 규칙·답변을 기존 비교기와 집계 모델로 계산한다. 사용자 입력·저장·외부 호출 없이 preview 프로필에서만 제공한다.")
    @ApiResponse(responseCode = "200", description = "인공 자료의 자격·근거와 별도 모집 상태. 실제 정책 추천이나 자격 인증이 아니다.")
    @ApiResponse(responseCode = "403", description = "preview 프로필이 아니면 접근 거부. 오류 본문은 사용하지 않는다.", content = @Content)
    public ResponseEntity<EligibilityExamplesResponse> examples() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new EligibilityExamplesResponse(
                EligibilityExamplesResponse.DataKind.SYNTHETIC, List.of(
                        example(Scenario.ELIGIBLE_CLOSED), example(Scenario.PARTIAL_INCOME),
                        example(Scenario.UNRESOLVED_POLICY), example(Scenario.INELIGIBLE))));
    }

    private EligibilityExamplesResponse.ExampleResponse example(Scenario scenario) {
        String revision = scenario == Scenario.UNRESOLVED_POLICY ? "sample-revision-2" : "sample-revision-1";
        var age = new AgeCondition.CompletedYears("sample-age", 19, 34, REFERENCE_DATE,
                evidence("인공 자료 1항 · 연령", "2026년 8월 1일 기준 만 19세 이상 34세 이하."));
        var residence = new ResidenceCondition.RegisteredIn("sample-residence", ResidenceCondition.Area.SEOUL,
                Set.of(), REFERENCE_DATE, evidence("인공 자료 2항 · 거주", "2026년 8월 1일 기준 서울특별시 거주."));
        EmploymentCondition employment = scenario == Scenario.UNRESOLVED_POLICY
                ? new EmploymentCondition.Unresolved("sample-employment", "취업 제외 대상의 세부 정의 미확인", Optional.empty(),
                        "제외 대상의 정의를 확인하지 못했습니다. 사용자 답변만으로 확정할 수 없습니다.",
                        evidence("인공 자료 3항의 별도 안내 · 내용 미확인", null))
                : new EmploymentCondition.NoRestriction("sample-employment", Optional.empty(),
                        evidence("인공 자료 3항 · 취업", "취업 상태에 따른 제한 없음."));
        var incomeBasis = new IncomeBasis(POLICY_ID, revision, "sample-income", IncomeBasis.Personal.INSTANCE,
                "세전 근로소득 합계", new IncomeBasis.Period(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31"), "연간 합계"),
                Optional.empty(), Optional.of(LocalDate.parse("2025-12-31")));
        var income = new IncomeCondition.RangeRequirement(incomeBasis,
                new IncomeRange(IncomeRange.Unbounded.INSTANCE, new IncomeRange.Amount(new BigDecimal("25000000"), true)),
                "인공 문구의 원 단위를 그대로 사용하며 환산하지 않음",
                evidence("인공 자료 4항 · 소득", "본인의 2025년 세전 근로소득 합계 25,000,000원 이하. 기준일은 2025년 12월 31일."));
        Optional<IncomeAnswer> incomeAnswer = scenario == Scenario.INELIGIBLE ? Optional.empty()
                : Optional.of(new IncomeAnswer.KnownRange(incomeBasis,
                        new IncomeRange(new IncomeRange.Amount(new BigDecimal("20000000"), false),
                                new IncomeRange.Amount(new BigDecimal(scenario == Scenario.PARTIAL_INCOME ? "30000000" : "25000000"), true))));
        var review = scenario == Scenario.UNRESOLVED_POLICY
                ? PolicyReview.incomplete(List.of(new PolicyReview.PendingIssue(
                        "연령 상한의 예외 대상과 연장 범위를 아직 확인하지 못했습니다.",
                        evidence("인공 자료 부록 · 예외", "연령 상한의 예외는 별도 안내를 따릅니다."))))
                : PolicyReview.complete();
        var birthDate = LocalDate.parse(scenario == Scenario.UNRESOLVED_POLICY || scenario == Scenario.INELIGIBLE
                ? "1990-08-01" : "1999-08-01");
        var decision = new EligibilityDecision(new EvaluationBasis(POLICY_ID, revision, "sample-rule-1", SAMPLE_CLOCK.instant()), review,
                List.of(AgeConditionEvaluator.evaluate(age, Optional.of(birthDate)),
                        ResidenceConditionEvaluator.evaluate(residence, Optional.of(new SeoulResidence(SeoulDistrict.MAPO, REFERENCE_DATE))),
                        EmploymentConditionEvaluator.evaluate(employment, Optional.empty()),
                        IncomeConditionEvaluator.evaluate(income, incomeAnswer)));
        var endsOn = LocalDate.parse(scenario == Scenario.ELIGIBLE_CLOSED ? "2026-08-30" : "2026-09-07");
        var recruitment = RecruitmentAssessment.evaluate(new RecruitmentSchedule(POLICY_ID, revision,
                new ApplicationPeriod.Dates(LocalDate.parse("2026-08-20"), endsOn),
                "sample-eligibility-source · 실제 정책 원문 아님", "인공 자료 · 신청기간",
                Optional.of("인공 신청기간: 2026-08-20부터 " + endsOn + "까지")), SAMPLE_CLOCK);
        return new EligibilityExamplesResponse.ExampleResponse(scenario.id, scenario.label, scenario.description,
                EligibilityResultResponse.from(decision),
                new EligibilityExamplesResponse.RecruitmentResponse(recruitment.status(), recruitment.explanation()));
    }

    private static SourceEvidence evidence(String location, String excerpt) {
        return new SourceEvidence("sample-eligibility-source · 실제 정책 원문 아님", location, Optional.ofNullable(excerpt));
    }

    private enum Scenario {
        ELIGIBLE_CLOSED("eligible-closed", "전체 충족 · 모집 종료", "모든 인공 조건이 충족돼도 별도 모집 계산 결과는 마감입니다."),
        PARTIAL_INCOME("missing-input", "사용자 정보 부족", "인공 소득 답변 구간이 허용 경계에 걸쳐 추가 확인이 필요합니다."),
        UNRESOLVED_POLICY("unresolved-policy", "정책 조건 미해석", "연령 불충족이 있어도 미확인 예외와 취업 정의 때문에 전체 판정을 보류합니다."),
        INELIGIBLE("ineligible", "명확한 조건 불충족", "명확한 연령 불충족과 소득 답변 누락을 서로 다른 상태로 유지합니다.");

        private final String id;
        private final String label;
        private final String description;

        Scenario(String id, String label, String description) {
            this.id = id;
            this.label = label;
            this.description = description;
        }
    }
}
