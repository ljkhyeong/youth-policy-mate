package kr.youthpolicymate.eligibility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.NOT_MET;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.UNKNOWN;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.MISSING_USER_INPUT;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Uncertainty.UNRESOLVED_POLICY;
import static kr.youthpolicymate.eligibility.EligibilityStatus.ELIGIBLE;
import static kr.youthpolicymate.eligibility.EligibilityStatus.INELIGIBLE;
import static kr.youthpolicymate.eligibility.EligibilityStatus.NEEDS_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EligibilityDecisionTest {

    private static final EvaluationBasis BASIS = new EvaluationBasis(
            "test-policy", "internal-revision-1", "aggregation-v1", Instant.parse("2026-08-30T01:00:00Z"));

    @Test
    @DisplayName("필수 조건과 예외 검토가 끝나고 모든 항목이 충족해야 신청 가능으로 안내한다")
    void requiresCompletePolicyReviewAndAllConditionsMet() {
        var decision = decision(PolicyReview.complete(), List.of(metRegion()));

        assertThat(decision.status()).isEqualTo(ELIGIBLE);
        assertThat(decision.explanation()).contains("공식 자격 인증이나 선정 보장은 아닙니다");
    }

    @Test
    @DisplayName("판정 항목이 없으면 검토 완료 표시가 있어도 신청 가능으로 안내하지 않는다")
    void emptyConditionsNeedReview() {
        var decision = decision(PolicyReview.complete(), List.of());

        assertThat(decision.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(decision.explanation()).contains("판정할 조건이 없어");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "정책의 연령 기준일을 확인할 수 없습니다.",
            "정책 코드의 의미를 확인할 수 없습니다.",
            "조건 후보 추출에 실패했습니다."
    })
    @DisplayName("정책 조건의 미확인 이유를 보존하며 다른 항목의 불충족으로 덮어쓰지 않는다")
    void unknownConditionRetainsItsReason(String reason) {
        var unknown = new ConditionAssessment("unresolved", "추가 확인할 조건", Optional.empty(),
                Optional.empty(), UNKNOWN, Optional.of(UNRESOLVED_POLICY), reason,
                evidence("자격 조건", Optional.empty()));
        var decision = decision(PolicyReview.complete(), List.of(unmetRegion(), unknown));

        assertThat(decision.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(decision.conditions().get(1).explanation()).isEqualTo(reason);
        assertThat(decision.conditions().get(1).comparedValue()).isEmpty();
        assertThat(decision.conditions().get(1).evidence().excerpt()).isEmpty();
    }

    @Test
    @DisplayName("다른 조건이 맞아도 필요한 사용자 정보가 없으면 추가 확인 필요로 안내한다")
    void missingUserInputNeedsReviewWhenThereIsNoDefiniteMismatch() {
        assertThat(decision(PolicyReview.complete(), List.of(metRegion(), missingIncome())).status())
                .isEqualTo(NEEDS_REVIEW);
    }

    @Test
    @DisplayName("명확한 필수 조건 불충족은 다른 사용자 정보의 누락으로 사라지지 않는다")
    void definiteMismatchRemainsDespiteUnrelatedMissingInput() {
        var missingIncome = missingIncome();
        var decision = decision(PolicyReview.complete(), List.of(unmetRegion(), missingIncome));

        assertThat(decision.status()).isEqualTo(INELIGIBLE);
        assertThat(decision.conditions()).containsExactly(unmetRegion(), missingIncome);
    }

    @Test
    @DisplayName("불충족을 뒤집을 수 있는 미해석 예외가 있으면 추가 확인 필요로 남긴다")
    void unresolvedExceptionTakesPriorityOverMismatch() {
        var issue = new PolicyReview.PendingIssue("지역 제한의 예외 적용 여부를 확인해야 합니다.",
                evidence("추가사항", Optional.of("거주지 예외는 별도 기준에 따름")));
        var decision = decision(PolicyReview.incomplete(List.of(issue)), List.of(unmetRegion()));

        assertThat(decision.status()).isEqualTo(NEEDS_REVIEW);
        assertThat(decision.conditions().getFirst().outcome()).isEqualTo(NOT_MET);
        assertThat(decision.policyReview().pendingIssues()).containsExactly(issue);
    }

    @Test
    @DisplayName("학력 제한없음과 대학생 참여 불가의 충돌을 해결하기 전에는 신청 가능으로 안내하지 않는다")
    void conflictingEducationConditionNeedsReview() {
        // 공개 사례의 위험만 재현한 테스트이며 실제 정책 응답이나 자격 판정 자료가 아니다.
        var education = new ConditionAssessment("education", "학력 제한없음", Optional.empty(),
                Optional.empty(), MET, Optional.empty(), "학력 코드만 비교한 결과입니다.",
                evidence("학력", Optional.of("제한없음")));
        var issue = new PolicyReview.PendingIssue("학력 코드와 추가 참여 제한을 함께 확인해야 합니다.",
                evidence("추가사항", Optional.of("대학생 참여 불가")));

        assertThat(decision(PolicyReview.incomplete(List.of(issue)), List.of(education)).status())
                .isEqualTo(NEEDS_REVIEW);
    }

    @Test
    @DisplayName("전국 대상 분류만 맞고 동아리 신청 요건이 미확인이면 신청 가능으로 안내하지 않는다")
    void unresolvedApplicantRequirementsNeedReview() {
        var issue = new PolicyReview.PendingIssue("개인 입력으로 동아리 신청 요건을 확인할 수 없습니다.",
                evidence("지원 대상·신청절차", Optional.of("서울 소재 대학 동아리와 재학생 수 요건")));

        assertThat(decision(PolicyReview.incomplete(List.of(issue)), List.of(metRegion())).status())
                .isEqualTo(NEEDS_REVIEW);
    }

    @Test
    @DisplayName("같은 판정 자료는 같은 결과이며 비교값·원문·개정·규칙·기준일을 보존한다")
    void retainsDeterministicEvidenceAndEvaluationBasis() {
        var referenceDate = LocalDate.of(2026, 5, 1);
        var ageEvidence = evidence("연령 조건", Optional.of("공고일 기준 연령 요건"));
        var age = new ConditionAssessment("age", "공고일 기준 연령 요건", Optional.of("만 26세"),
                Optional.of(referenceDate), MET, Optional.empty(), "확인한 공고일의 연령 요건을 충족합니다.", ageEvidence);
        var original = new ArrayList<>(List.of(age));
        var first = decision(PolicyReview.complete(), original);
        original.clear();
        var second = decision(PolicyReview.complete(), List.of(age));

        assertThat(first).isEqualTo(second);
        assertThat(first.basis()).isEqualTo(BASIS);
        assertThat(first.status()).isEqualTo(second.status());
        assertThat(first.explanation()).isEqualTo(second.explanation());
        assertThat(first.conditions()).containsExactly(age);
        assertThat(first.conditions().getFirst().referenceDate()).contains(referenceDate);
        assertThat(first.conditions().getFirst().comparedValue()).contains("만 26세");
        assertThat(first.conditions().getFirst().evidence()).isEqualTo(ageEvidence);
        assertThat(new EvaluationBasis(BASIS.policyId(), "internal-revision-2", BASIS.ruleVersion(), BASIS.evaluatedAt()))
                .isNotEqualTo(first.basis());
    }

    @Test
    @DisplayName("근거 없는 검토 미완료와 미확인 항목이 남은 검토 완료를 만들지 않는다")
    void rejectsInconsistentPolicyReviewState() {
        var issues = new ArrayList<>(List.of(new PolicyReview.PendingIssue("예외 확인이 필요합니다.",
                evidence("추가사항", Optional.of("별도 예외 기준")))));
        var incomplete = PolicyReview.incomplete(issues);
        issues.clear();

        assertThat(incomplete.pendingIssues()).hasSize(1);
        assertThatIllegalArgumentException().isThrownBy(() -> PolicyReview.incomplete(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                new PolicyReview(PolicyReview.Completion.COMPLETE, incomplete.pendingIssues()));
    }

    @Test
    @DisplayName("같은 조건을 중복해서 넣어 결과와 근거가 충돌하는 것을 막는다")
    void rejectsDuplicateConditionIds() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                decision(PolicyReview.complete(), List.of(metRegion(), unmetRegion())));
    }

    @Test
    @DisplayName("미확인 원인의 누락이나 확정 결과와 미확인 원인의 혼용을 막는다")
    void rejectsInconsistentUncertainty() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ConditionAssessment(
                "income", "소득 조건", Optional.empty(), Optional.empty(), UNKNOWN, Optional.empty(),
                "소득 정보가 없습니다.", evidence("소득 조건", Optional.of("테스트 조건"))));
        assertThatIllegalArgumentException().isThrownBy(() -> new ConditionAssessment(
                "income", "소득 조건", Optional.empty(), Optional.empty(), MET, Optional.of(MISSING_USER_INPUT),
                "소득 정보가 없습니다.", evidence("소득 조건", Optional.of("테스트 조건"))));
    }

    private static EligibilityDecision decision(PolicyReview review, List<ConditionAssessment> conditions) {
        return new EligibilityDecision(BASIS, review, conditions);
    }

    private static ConditionAssessment metRegion() {
        return new ConditionAssessment("region", "서울 거주", Optional.of("서울 마포구"), Optional.empty(),
                MET, Optional.empty(), "입력한 거주지는 서울입니다.", evidence("거주 조건", Optional.of("서울 거주")));
    }

    private static ConditionAssessment unmetRegion() {
        return new ConditionAssessment("region", "서울 강남구 거주", Optional.of("서울 마포구"), Optional.empty(),
                NOT_MET, Optional.empty(), "입력한 거주지는 강남구가 아닙니다.", evidence("거주 조건", Optional.of("서울 강남구 거주")));
    }

    private static ConditionAssessment missingIncome() {
        return new ConditionAssessment("income", "확인된 소득 조건", Optional.empty(),
                Optional.empty(), UNKNOWN, Optional.of(MISSING_USER_INPUT), "소득 정보가 입력되지 않았습니다.",
                evidence("소득 조건", Optional.of("테스트용 소득 조건")));
    }

    private static SourceEvidence evidence(String location, Optional<String> excerpt) {
        return new SourceEvidence("test-source-revision-1", location, excerpt);
    }
}
